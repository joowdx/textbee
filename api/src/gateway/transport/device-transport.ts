import { Injectable, Logger } from '@nestjs/common'
import * as firebaseAdmin from 'firebase-admin'
import { BatchResponse, Message, SendResponse } from 'firebase-admin/messaging'
import { MqttService } from '../../mqtt/mqtt.service'
import {
  DOWN_HEARTBEAT_CHECK,
  DOWN_SEND,
  DevicePresence,
  deviceTopicPrefix,
  isDeviceOnline,
  mqttEnabled,
} from '../../mqtt/mqtt-config'

export type TransportDevice = DevicePresence & { _id?: unknown }

// One push to one device. The message is built as an FCM message; MQTT
// carries the same data map as JSON.
export type Delivery = {
  device: TransportDevice | null | undefined
  message: Message
}

export interface DeviceTransport {
  sendEach(deliveries: Delivery[]): Promise<BatchResponse>
}

function toBatchResponse(responses: SendResponse[]): BatchResponse {
  return {
    responses,
    successCount: responses.filter((r) => r.success).length,
    failureCount: responses.filter((r) => !r.success).length,
  }
}

@Injectable()
export class FcmTransport implements DeviceTransport {
  async sendEach(deliveries: Delivery[]): Promise<BatchResponse> {
    if (deliveries.length === 0) return toBatchResponse([])
    return firebaseAdmin.messaging().sendEach(deliveries.map((d) => d.message))
  }
}

export function mqttTopicFor(deviceId: string, message: Message): string {
  const data = (message as { data?: Record<string, string> }).data
  const subtopic =
    data?.type === 'heartbeat_check' ? DOWN_HEARTBEAT_CHECK : DOWN_SEND
  return `${deviceTopicPrefix(deviceId)}${subtopic}`
}

@Injectable()
export class MqttTransport implements DeviceTransport {
  constructor(private readonly mqttService: MqttService) {}

  isAvailable(): boolean {
    return mqttEnabled() && this.mqttService.isConnected()
  }

  // Never throws; a failed publish comes back as a failed response so the
  // caller can retry it over FCM
  async sendEach(deliveries: Delivery[]): Promise<BatchResponse> {
    const responses = await Promise.all(
      deliveries.map(async ({ device, message }): Promise<SendResponse> => {
        try {
          const deviceId = String(device?._id ?? '')
          if (!deviceId) throw new Error('No device id for MQTT publish')
          const data = (message as { data?: Record<string, string> }).data ?? {}
          await this.mqttService.publish(
            mqttTopicFor(deviceId, message),
            JSON.stringify(data),
          )
          return { success: true }
        } catch (error) {
          return {
            success: false,
            error: {
              code: 'mqtt/publish-failed',
              message: error?.message || 'MQTT publish failed',
            } as any,
          }
        }
      }),
    )
    return toBatchResponse(responses)
  }
}

// MQTT when it is enabled and the device is online, otherwise FCM. A failed
// MQTT publish is retried over FCM; the device dedupes repeat sends.
@Injectable()
export class DeviceTransportService implements DeviceTransport {
  private readonly logger = new Logger(DeviceTransportService.name)

  constructor(
    private readonly fcm: FcmTransport,
    private readonly mqtt: MqttTransport,
  ) {}

  async sendEach(deliveries: Delivery[]): Promise<BatchResponse> {
    const responses: SendResponse[] = new Array(deliveries.length)
    const viaMqtt: number[] = []
    const viaFcm: number[] = []

    const mqttAvailable = this.mqtt.isAvailable()
    deliveries.forEach((delivery, index) => {
      if (mqttAvailable && isDeviceOnline(delivery.device)) {
        viaMqtt.push(index)
      } else {
        viaFcm.push(index)
      }
    })

    // Nothing goes over MQTT: hand back FCM's own response untouched
    if (viaMqtt.length === 0) {
      return this.fcm.sendEach(deliveries)
    }

    const mqttResponse = await this.mqtt.sendEach(
      viaMqtt.map((index) => deliveries[index]),
    )
    mqttResponse.responses.forEach((response, i) => {
      if (response.success) {
        responses[viaMqtt[i]] = response
      } else {
        this.logger.warn(
          `MQTT publish failed, falling back to FCM: ${response.error?.message}`,
        )
        viaFcm.push(viaMqtt[i])
      }
    })

    if (viaFcm.length > 0) {
      viaFcm.sort((a, b) => a - b)
      const fcmResponse = await this.fcm.sendEach(
        viaFcm.map((index) => deliveries[index]),
      )
      fcmResponse.responses.forEach((response, i) => {
        responses[viaFcm[i]] = response
      })
    }

    return toBatchResponse(responses)
  }
}
