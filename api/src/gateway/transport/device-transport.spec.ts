import * as firebaseAdmin from 'firebase-admin'
import {
  DeviceTransportService,
  FcmTransport,
  MqttTransport,
  mqttTopicFor,
} from './device-transport'

jest.mock('firebase-admin', () => ({
  messaging: jest.fn().mockReturnValue({
    sendEach: jest.fn(),
  }),
}))

describe('DeviceTransportService', () => {
  const originalEnv = { ...process.env }
  const smsMessage = {
    token: 'fcm-token',
    data: { smsData: JSON.stringify({ smsId: 'sms-1' }) },
  }
  const onlineDevice = { _id: 'dev1', mqttConnected: true, mqttStatusAt: new Date() }
  const offlineDevice = {
    _id: 'dev2',
    mqttConnected: false,
    mqttStatusAt: new Date(Date.now() - 10 * 60 * 1000),
  }

  let mqttService: { isConnected: jest.Mock; publish: jest.Mock }
  let service: DeviceTransportService
  const sendEach = firebaseAdmin.messaging().sendEach as jest.Mock

  beforeEach(() => {
    jest.clearAllMocks()
    process.env = { ...originalEnv, MQTT_ENABLED: 'true' }
    mqttService = {
      isConnected: jest.fn().mockReturnValue(true),
      publish: jest.fn().mockResolvedValue(undefined),
    }
    sendEach.mockImplementation(async (messages: any[]) => ({
      successCount: messages.length,
      failureCount: 0,
      responses: messages.map(() => ({ success: true, messageId: 'fcm-id' })),
    }))
    service = new DeviceTransportService(
      new FcmTransport(),
      new MqttTransport(mqttService as any),
    )
  })

  afterAll(() => {
    process.env = originalEnv
  })

  it('publishes to an online device over MQTT with the FCM data map', async () => {
    const response = await service.sendEach([
      { device: onlineDevice, message: smsMessage },
    ])

    expect(mqttService.publish).toHaveBeenCalledWith(
      'textbee/devices/dev1/down/send',
      JSON.stringify(smsMessage.data),
    )
    expect(sendEach).not.toHaveBeenCalled()
    expect(response.successCount).toBe(1)
  })

  it('sends to an offline device over FCM', async () => {
    await service.sendEach([{ device: offlineDevice, message: smsMessage }])

    expect(mqttService.publish).not.toHaveBeenCalled()
    expect(sendEach).toHaveBeenCalledWith([smsMessage])
  })

  it('uses FCM for every device when MQTT is disabled', async () => {
    process.env.MQTT_ENABLED = 'false'

    await service.sendEach([{ device: onlineDevice, message: smsMessage }])

    expect(mqttService.publish).not.toHaveBeenCalled()
    expect(sendEach).toHaveBeenCalledWith([smsMessage])
  })

  it('uses FCM while the API is not connected to the broker', async () => {
    mqttService.isConnected.mockReturnValue(false)

    await service.sendEach([{ device: onlineDevice, message: smsMessage }])

    expect(mqttService.publish).not.toHaveBeenCalled()
    expect(sendEach).toHaveBeenCalledWith([smsMessage])
  })

  it('falls back to FCM when the MQTT publish fails', async () => {
    mqttService.publish.mockRejectedValue(new Error('timed out'))

    const response = await service.sendEach([
      { device: onlineDevice, message: smsMessage },
    ])

    expect(mqttService.publish).toHaveBeenCalled()
    expect(sendEach).toHaveBeenCalledWith([smsMessage])
    expect(response.responses[0]).toEqual({ success: true, messageId: 'fcm-id' })
  })

  it('keeps response order across a mixed batch', async () => {
    const second = { ...smsMessage, data: { smsData: '{"smsId":"sms-2"}' } }
    const third = { ...smsMessage, data: { smsData: '{"smsId":"sms-3"}' } }
    mqttService.publish
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new Error('down'))

    const response = await service.sendEach([
      { device: onlineDevice, message: smsMessage },
      { device: offlineDevice, message: second },
      { device: onlineDevice, message: third },
    ])

    expect(sendEach).toHaveBeenCalledWith([second, third])
    expect(response.responses).toEqual([
      { success: true },
      { success: true, messageId: 'fcm-id' },
      { success: true, messageId: 'fcm-id' },
    ])
    expect(response.successCount).toBe(3)
  })

  it('routes the heartbeat check to its own topic', () => {
    expect(
      mqttTopicFor('dev1', { token: 't', data: { type: 'heartbeat_check' } }),
    ).toBe('textbee/devices/dev1/down/heartbeat-check')
  })
})
