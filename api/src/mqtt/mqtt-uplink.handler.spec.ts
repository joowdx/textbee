import { HttpException, HttpStatus } from '@nestjs/common'
import { MqttUplinkHandler } from './mqtt-uplink.handler'

jest.mock('firebase-admin', () => ({
  messaging: jest.fn().mockReturnValue({ sendEach: jest.fn() }),
}))

describe('MqttUplinkHandler', () => {
  const deviceId = '64b7f0c2a1b2c3d4e5f60718'
  let gatewayService: { updateSMSStatus: jest.Mock; receiveSMS: jest.Mock }
  let presence: { recordStatus: jest.Mock }
  let mqttService: { onMessage: jest.Mock }
  let handler: MqttUplinkHandler

  const payload = (body: unknown) => Buffer.from(JSON.stringify(body))

  beforeEach(() => {
    gatewayService = {
      updateSMSStatus: jest.fn().mockResolvedValue({ success: true }),
      receiveSMS: jest.fn().mockResolvedValue({ success: true }),
    }
    presence = { recordStatus: jest.fn().mockResolvedValue(undefined) }
    mqttService = { onMessage: jest.fn() }
    handler = new MqttUplinkHandler(
      mqttService as any,
      gatewayService as any,
      presence as any,
    )
  })

  it('registers itself for broker messages', () => {
    handler.onModuleInit()
    expect(mqttService.onMessage).toHaveBeenCalledWith(expect.any(Function))
  })

  it('passes a status report to the service with the topic deviceId', async () => {
    const body = { smsId: 'sms-1', smsBatchId: 'batch-1', status: 'DELIVERED' }
    await handler.handle(`textbee/devices/${deviceId}/up/sms-status`, payload(body))

    expect(gatewayService.updateSMSStatus).toHaveBeenCalledWith(deviceId, body)
  })

  it('passes a received SMS to the service with the topic deviceId', async () => {
    const body = { message: 'hi', sender: '+15550001111', receivedAt: '2026-10-02T00:00:00Z' }
    await handler.handle(`textbee/devices/${deviceId}/up/received`, payload(body))

    expect(gatewayService.receiveSMS).toHaveBeenCalledWith(deviceId, body)
  })

  it('records presence from the retained status', async () => {
    await handler.handle(`textbee/devices/${deviceId}/status`, payload({ online: true }))
    await handler.handle(`textbee/devices/${deviceId}/status`, payload({ online: false }))

    expect(presence.recordStatus).toHaveBeenNthCalledWith(1, deviceId, true)
    expect(presence.recordStatus).toHaveBeenNthCalledWith(2, deviceId, false)
  })

  it('ignores bad JSON and bad device ids', async () => {
    await handler.handle(`textbee/devices/${deviceId}/up/received`, Buffer.from('nope'))
    await handler.handle('textbee/devices/not-an-id/up/received', payload({}))

    expect(gatewayService.receiveSMS).not.toHaveBeenCalled()
  })

  it('swallows a rejection from the service', async () => {
    gatewayService.receiveSMS.mockRejectedValue(
      new HttpException({ error: 'Device does not exist' }, HttpStatus.NOT_FOUND),
    )

    await expect(
      handler.handle(`textbee/devices/${deviceId}/up/received`, payload({})),
    ).resolves.toBeUndefined()
  })

  it("handles one device's messages one at a time, in order", async () => {
    const order: string[] = []
    let releaseFirst: () => void
    gatewayService.receiveSMS
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            order.push('first:start')
            releaseFirst = () => {
              order.push('first:end')
              resolve({})
            }
          }),
      )
      .mockImplementationOnce(async () => {
        order.push('second')
        return {}
      })
    const topic = `textbee/devices/${deviceId}/up/received`
    const first = handler.handle(topic, payload({ message: 'a' }))
    const second = handler.handle(topic, payload({ message: 'a' }))
    await new Promise((r) => setImmediate(r))
    expect(order).toEqual(['first:start'])
    releaseFirst()
    await Promise.all([first, second])
    expect(order).toEqual(['first:start', 'first:end', 'second'])
  })
})
