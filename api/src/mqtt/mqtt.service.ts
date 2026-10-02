import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common'
import { randomBytes, randomUUID } from 'crypto'
import { connect, MqttClient } from 'mqtt'
import {
  STATUS_SUBSCRIPTION,
  UPLINK_SUBSCRIPTION,
  deviceClientUsername,
  deviceTopicPrefix,
  mqttEnabled,
} from './mqtt-config'
import {
  DYNSEC_REQUEST_TOPIC,
  DYNSEC_RESPONSE_TOPIC,
  DynsecCommand,
  DynsecResponse,
  rotateDeviceClientCommands,
  rotationErrors,
} from './dynsec'

export type MqttMessageHandler = (
  topic: string,
  payload: Buffer,
) => void | Promise<void>

export type MqttCredentials = {
  url: string
  username: string
  password: string
  topicPrefix: string
}

const PUBLISH_TIMEOUT_MS = 5000
const DYNSEC_TIMEOUT_MS = 5000
// A day of queued uplinks survives an API restart
const SESSION_EXPIRY_SECONDS = 24 * 3600

type PendingDynsec = {
  resolve: (responses: DynsecResponse[]) => void
  reject: (error: Error) => void
  timer: NodeJS.Timeout
}

@Injectable()
export class MqttService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(MqttService.name)
  private client: MqttClient | null = null
  private readonly handlers: MqttMessageHandler[] = []
  private readonly pendingDynsec = new Map<string, PendingDynsec>()

  onModuleInit() {
    if (!mqttEnabled()) return
    this.start()
  }

  async onModuleDestroy() {
    const client = this.client
    this.client = null
    if (client) await client.endAsync().catch(() => undefined)
  }

  start() {
    if (this.client) return
    const url = process.env.MQTT_URL || 'mqtt://textbee-mqtt:1883'

    // A fixed client id with a persistent session keeps QoS 1 uplinks queued
    // at the broker while the API restarts
    const client = connect(url, {
      clientId: process.env.MQTT_CLIENT_ID || 'textbee-api',
      username: process.env.MQTT_ADMIN_USERNAME,
      password: process.env.MQTT_ADMIN_PASSWORD,
      protocolVersion: 5,
      clean: false,
      properties: { sessionExpiryInterval: SESSION_EXPIRY_SECONDS },
      reconnectPeriod: 5000,
      connectTimeout: 10000,
    })
    this.client = client

    client.on('connect', () => {
      this.logger.log(`Connected to MQTT broker at ${url}`)
      client.subscribe(
        [UPLINK_SUBSCRIPTION, STATUS_SUBSCRIPTION, DYNSEC_RESPONSE_TOPIC],
        { qos: 1 },
        (error) => {
          if (error) this.logger.error(`MQTT subscribe failed: ${error.message}`)
        },
      )
    })
    client.on('error', (error) =>
      this.logger.error(`MQTT client error: ${error.message}`),
    )
    client.on('offline', () => this.logger.warn('MQTT client offline'))
    client.on('message', (topic, payload) => this.dispatch(topic, payload))
  }

  isConnected(): boolean {
    return !!this.client?.connected
  }

  onMessage(handler: MqttMessageHandler) {
    this.handlers.push(handler)
  }

  // Fails fast while disconnected so the caller can fall back to FCM rather
  // than have the publish sit in the client's offline queue
  async publish(topic: string, payload: string): Promise<void> {
    const client = this.client
    if (!client?.connected) {
      throw new Error('MQTT client is not connected')
    }
    let timer: NodeJS.Timeout
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(
        () => reject(new Error('MQTT publish timed out')),
        PUBLISH_TIMEOUT_MS,
      )
    })
    try {
      await Promise.race([client.publishAsync(topic, payload, { qos: 1 }), timeout])
    } finally {
      clearTimeout(timer)
    }
  }

  async dynsec(commands: DynsecCommand[]): Promise<DynsecResponse[]> {
    const requestId = randomUUID()
    const tagged = commands.map((command, index) => ({
      ...command,
      correlationData: `${requestId}:${index}`,
    }))

    const result = new Promise<DynsecResponse[]>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingDynsec.delete(requestId)
        reject(new Error('MQTT dynamic-security request timed out'))
      }, DYNSEC_TIMEOUT_MS)
      this.pendingDynsec.set(requestId, { resolve, reject, timer })
    })

    try {
      await this.publish(DYNSEC_REQUEST_TOPIC, JSON.stringify({ commands: tagged }))
    } catch (error) {
      const pending = this.pendingDynsec.get(requestId)
      if (pending) {
        clearTimeout(pending.timer)
        this.pendingDynsec.delete(requestId)
      }
      throw error
    }
    return result
  }

  // Replaces the device's broker client with a fresh password
  async rotateDeviceCredentials(deviceId: string): Promise<MqttCredentials> {
    if (!mqttEnabled()) {
      throw new HttpException(
        { success: false, error: 'MQTT is not enabled on this server' },
        HttpStatus.CONFLICT,
      )
    }

    const password = randomBytes(32).toString('base64url')
    let responses: DynsecResponse[]
    try {
      responses = await this.dynsec(rotateDeviceClientCommands(deviceId, password))
    } catch (error) {
      this.logger.error(
        `MQTT credential rotation failed for device ${deviceId}: ${error?.message}`,
      )
      throw new HttpException(
        { success: false, error: 'MQTT broker is unavailable' },
        HttpStatus.SERVICE_UNAVAILABLE,
      )
    }

    const errors = rotationErrors(responses)
    if (errors.length > 0) {
      this.logger.error(
        `MQTT credential rotation rejected for device ${deviceId}: ${errors.join('; ')}`,
      )
      throw new HttpException(
        { success: false, error: 'MQTT broker rejected the credentials' },
        HttpStatus.BAD_GATEWAY,
      )
    }

    return {
      url: process.env.MQTT_PUBLIC_URL || '',
      username: deviceClientUsername(deviceId),
      password,
      topicPrefix: deviceTopicPrefix(deviceId),
    }
  }

  private dispatch(topic: string, payload: Buffer) {
    if (topic === DYNSEC_RESPONSE_TOPIC) {
      this.resolveDynsec(payload)
      return
    }
    for (const handler of this.handlers) {
      Promise.resolve()
        .then(() => handler(topic, payload))
        .catch((error) =>
          this.logger.error(
            `MQTT handler failed for ${topic}: ${error?.message ?? error}`,
          ),
        )
    }
  }

  private resolveDynsec(payload: Buffer) {
    let responses: DynsecResponse[]
    try {
      responses = JSON.parse(payload.toString())?.responses
    } catch {
      return
    }
    if (!Array.isArray(responses) || responses.length === 0) return

    const correlation = String(responses[0]?.correlationData ?? '')
    const requestId = correlation.slice(0, correlation.lastIndexOf(':'))
    const pending = this.pendingDynsec.get(requestId)
    if (!pending) return

    clearTimeout(pending.timer)
    this.pendingDynsec.delete(requestId)
    pending.resolve(responses)
  }
}
