import { DevicePresenceService } from './device-presence.service'

jest.mock('firebase-admin', () => ({
  messaging: jest.fn().mockReturnValue({ sendEach: jest.fn() }),
}))

describe('DevicePresenceService', () => {
  const deviceId = '64b7f0c2a1b2c3d4e5f60718'
  let deviceModel: { updateOne: jest.Mock; findOneAndUpdate: jest.Mock }
  let fcm: { sendEach: jest.Mock }
  let service: DevicePresenceService

  beforeEach(() => {
    deviceModel = {
      updateOne: jest.fn().mockResolvedValue({}),
      findOneAndUpdate: jest.fn(),
    }
    fcm = {
      sendEach: jest.fn().mockResolvedValue({
        successCount: 1,
        failureCount: 0,
        responses: [{ success: true }],
      }),
    }
    service = new DevicePresenceService(deviceModel as any, fcm as any)
  })

  it('marks a device connected', async () => {
    const at = new Date()
    await service.recordStatus(deviceId, true, at)

    expect(deviceModel.updateOne).toHaveBeenCalledWith(
      { _id: expect.anything() },
      { $set: { mqttConnected: true, mqttStatusAt: at } },
    )
    expect(fcm.sendEach).not.toHaveBeenCalled()
  })

  it('sends one FCM wake when a connected device drops', async () => {
    deviceModel.findOneAndUpdate.mockResolvedValue({
      _id: deviceId,
      mqttConnected: true,
      fcmToken: 'fcm-token',
    })

    await service.recordStatus(deviceId, false)

    expect(fcm.sendEach).toHaveBeenCalledTimes(1)
    expect(fcm.sendEach.mock.calls[0][0][0].message).toEqual(
      expect.objectContaining({
        data: { type: 'heartbeat_check' },
        token: 'fcm-token',
      }),
    )
  })

  it('does not wake again for an already offline device', async () => {
    deviceModel.findOneAndUpdate.mockResolvedValue(null)

    await service.recordStatus(deviceId, false)

    expect(fcm.sendEach).not.toHaveBeenCalled()
  })

  it('does not wake a device without a usable FCM token', async () => {
    deviceModel.findOneAndUpdate.mockResolvedValue({
      _id: deviceId,
      mqttConnected: true,
      fcmToken: 'fcm-token',
      fcmTokenInvalidatedAt: new Date(),
    })

    await service.recordStatus(deviceId, false)

    expect(fcm.sendEach).not.toHaveBeenCalled()
  })
})
