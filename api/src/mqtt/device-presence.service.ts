import { Injectable, Logger } from '@nestjs/common'
import { InjectModel } from '@nestjs/mongoose'
import { Model, Types } from 'mongoose'
import { Device, DeviceDocument } from '../gateway/schemas/device.schema'
import { heartbeatAndroidConfig } from '../gateway/fcm-push-options'
import { FcmTransport } from '../gateway/transport/device-transport'

// Tracks the retained status each device publishes: online on connect, and
// the broker's last will when the connection drops
@Injectable()
export class DevicePresenceService {
  private readonly logger = new Logger(DevicePresenceService.name)

  constructor(
    @InjectModel(Device.name) private deviceModel: Model<DeviceDocument>,
    private readonly fcm: FcmTransport,
  ) {}

  async recordStatus(deviceId: string, online: boolean, at = new Date()) {
    const _id = new Types.ObjectId(deviceId)

    if (online) {
      await this.deviceModel.updateOne(
        { _id },
        { $set: { mqttConnected: true, mqttStatusAt: at } },
      )
      return
    }

    // Matching only a connected device makes the wake fire once per drop,
    // even when the retained offline status is replayed on resubscribe
    const before = await this.deviceModel.findOneAndUpdate(
      { _id, mqttConnected: { $ne: false } },
      { $set: { mqttConnected: false, mqttStatusAt: at } },
      { returnDocument: 'before' },
    )
    if (before?.mqttConnected === true) {
      await this.wake(before)
    }
  }

  // One FCM heartbeat check, so a dozing device reconnects
  private async wake(device: DeviceDocument) {
    if (!device.fcmToken || device.fcmTokenInvalidatedAt) return
    try {
      const response = await this.fcm.sendEach([
        {
          device,
          message: {
            data: { type: 'heartbeat_check' },
            token: device.fcmToken,
            android: heartbeatAndroidConfig(),
          },
        },
      ])
      if (response.failureCount > 0) {
        this.logger.warn(
          `FCM wake failed for device ${device._id}: ${response.responses[0]?.error?.message}`,
        )
      }
    } catch (error) {
      this.logger.warn(`FCM wake failed for device ${device._id}: ${error?.message}`)
    }
  }
}
