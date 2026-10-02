import { EventEmitter } from 'events'
import { HttpException } from '@nestjs/common'
import { connect } from 'mqtt'
import { MqttService } from './mqtt.service'
import { DYNSEC_REQUEST_TOPIC, DYNSEC_RESPONSE_TOPIC } from './dynsec'

jest.mock('mqtt', () => ({ connect: jest.fn() }))

class FakeClient extends EventEmitter {
  connected = true
  subscribe = jest.fn()
  endAsync = jest.fn().mockResolvedValue(undefined)
  // Answers dynsec requests the way the broker does, echoing correlationData
  respond: (commands: any[]) => any[] = (commands) =>
    commands.map((c) => ({ command: c.command, correlationData: c.correlationData }))
  publishAsync = jest.fn(async (topic: string, payload: string, _options?: object) => {
    if (topic === DYNSEC_REQUEST_TOPIC) {
      const { commands } = JSON.parse(payload)
      setImmediate(() =>
        this.emit(
          'message',
          DYNSEC_RESPONSE_TOPIC,
          Buffer.from(JSON.stringify({ responses: this.respond(commands) })),
        ),
      )
    }
  })
}

describe('MqttService', () => {
  const originalEnv = { ...process.env }
  const deviceId = '64b7f0c2a1b2c3d4e5f60718'
  let client: FakeClient
  let service: MqttService

  beforeEach(() => {
    jest.clearAllMocks()
    process.env = {
      ...originalEnv,
      MQTT_ENABLED: 'true',
      MQTT_URL: 'mqtt://broker:1883',
      MQTT_PUBLIC_URL: 'wss://textbee-mqtt.example.com/mqtt',
      MQTT_ADMIN_USERNAME: 'admin',
      MQTT_ADMIN_PASSWORD: 'admin-pass',
    }
    client = new FakeClient()
    ;(connect as jest.Mock).mockReturnValue(client)
    service = new MqttService()
  })

  afterAll(() => {
    process.env = originalEnv
  })

  it('does not connect when MQTT is disabled', () => {
    process.env.MQTT_ENABLED = 'false'
    service.onModuleInit()
    expect(connect).not.toHaveBeenCalled()
  })

  it('connects with the admin client and subscribes on connect', () => {
    service.onModuleInit()
    expect(connect).toHaveBeenCalledWith(
      'mqtt://broker:1883',
      expect.objectContaining({
        username: 'admin',
        password: 'admin-pass',
        protocolVersion: 5,
        clean: false,
      }),
    )

    client.emit('connect')
    expect(client.subscribe).toHaveBeenCalledWith(
      [
        '$share/api/textbee/devices/+/up/#',
        'textbee/devices/+/status',
        DYNSEC_RESPONSE_TOPIC,
      ],
      { qos: 1 },
      expect.any(Function),
    )
  })

  it('hands device messages to registered handlers', async () => {
    const handler = jest.fn()
    service.onMessage(handler)
    service.onModuleInit()

    const payload = Buffer.from('{}')
    client.emit('message', 'textbee/devices/x/status', payload)
    await new Promise((resolve) => setImmediate(resolve))

    expect(handler).toHaveBeenCalledWith('textbee/devices/x/status', payload)
  })

  it('refuses to publish while disconnected', async () => {
    service.onModuleInit()
    client.connected = false
    await expect(service.publish('t', '{}')).rejects.toThrow('not connected')
    expect(client.publishAsync).not.toHaveBeenCalled()
  })

  it('rotates device credentials over dynamic security', async () => {
    service.onModuleInit()

    const credentials = await service.rotateDeviceCredentials(deviceId)

    expect(credentials).toEqual({
      url: 'wss://textbee-mqtt.example.com/mqtt',
      username: `device-${deviceId}`,
      password: expect.stringMatching(/^[A-Za-z0-9_-]{43}$/),
      topicPrefix: `textbee/devices/${deviceId}/`,
    })

    const [topic, payload, options] = client.publishAsync.mock.calls[0]
    expect(topic).toBe(DYNSEC_REQUEST_TOPIC)
    expect(options).toEqual({ qos: 1 })
    const { commands } = JSON.parse(payload)
    expect(commands.map((c: any) => c.command)).toEqual([
      'deleteClient',
      'deleteRole',
      'createRole',
      'createClient',
    ])
    expect(commands[3]).toEqual(
      expect.objectContaining({
        username: `device-${deviceId}`,
        password: credentials.password,
        roles: [{ rolename: `device-${deviceId}`, priority: 0 }],
      }),
    )
  })

  it('reports a broker rejection', async () => {
    service.onModuleInit()
    client.respond = (commands) =>
      commands.map((c) => ({
        command: c.command,
        correlationData: c.correlationData,
        ...(c.command === 'createClient' && { error: 'Client already exists' }),
      }))

    await expect(service.rotateDeviceCredentials(deviceId)).rejects.toMatchObject({
      status: 502,
    })
  })

  it('refuses credentials with 409 when MQTT is disabled', async () => {
    process.env.MQTT_ENABLED = 'false'

    const error = await service.rotateDeviceCredentials(deviceId).catch((e) => e)

    expect(error).toBeInstanceOf(HttpException)
    expect(error.getStatus()).toBe(409)
    expect(error.getResponse()).toEqual({
      success: false,
      error: 'MQTT is not enabled on this server',
    })
  })

  it('answers 503 when the broker is unreachable', async () => {
    service.onModuleInit()
    client.connected = false

    await expect(service.rotateDeviceCredentials(deviceId)).rejects.toMatchObject({
      status: 503,
    })
  })
})
