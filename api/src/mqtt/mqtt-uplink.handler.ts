import { Injectable, Logger, OnModuleInit } from '@nestjs/common'
import { Types } from 'mongoose'
import { GatewayService } from '../gateway/gateway.service'
import { MqttService } from './mqtt.service'
import { DevicePresenceService } from './device-presence.service'
import {
  STATUS,
  UP_RECEIVED,
  UP_SMS_STATUS,
  parseDeviceTopic,
} from './mqtt-config'

// Device-to-API messages. Each one goes through the same service method as
// its HTTP route, so validation, dedupe and webhooks are shared.
@Injectable()
export class MqttUplinkHandler implements OnModuleInit {
  private readonly logger = new Logger(MqttUplinkHandler.name)
  // One device's messages are handled in arrival order, one at a time, so a
  // repeat of a received SMS finds the first copy instead of racing it past
  // the dedupe check.
  private readonly queues = new Map<string, Promise<void>>()

  constructor(
    private readonly mqttService: MqttService,
    private readonly gatewayService: GatewayService,
    private readonly presence: DevicePresenceService,
  ) {}

  onModuleInit() {
    this.mqttService.onMessage((topic, payload) => this.handle(topic, payload))
  }

  handle(topic: string, payload: Buffer): Promise<void> {
    const parsed = parseDeviceTopic(topic)
    if (!parsed || !Types.ObjectId.isValid(parsed.deviceId)) {
      return Promise.resolve()
    }
    const { deviceId, subtopic } = parsed
    const next = (this.queues.get(deviceId) ?? Promise.resolve()).then(() =>
      this.process(deviceId, subtopic, topic, payload),
    )
    this.queues.set(deviceId, next)
    next.then(() => {
      if (this.queues.get(deviceId) === next) this.queues.delete(deviceId)
    })
    return next
  }

  private async process(
    deviceId: string,
    subtopic: string,
    topic: string,
    payload: Buffer,
  ): Promise<void> {

    let body: any
    try {
      body = JSON.parse(payload.toString())
    } catch {
      this.logger.warn(`Ignoring non-JSON MQTT payload on ${topic}`)
      return
    }
    if (!body || typeof body !== 'object') return

    try {
      switch (subtopic) {
        case UP_SMS_STATUS:
          await this.gatewayService.updateSMSStatus(deviceId, body)
          break
        case UP_RECEIVED:
          await this.gatewayService.receiveSMS(deviceId, body)
          break
        case STATUS:
          await this.presence.recordStatus(deviceId, body.online === true)
          break
      }
    } catch (error) {
      const detail = error?.getResponse?.() ?? error?.message ?? error
      this.logger.warn(
        `MQTT ${subtopic} from device ${deviceId} was rejected: ${JSON.stringify(detail)}`,
      )
    }
  }
}
