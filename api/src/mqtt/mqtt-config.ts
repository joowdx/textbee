// MQTT is an optional device transport. With MQTT_ENABLED unset the API never
// connects to a broker and every push goes over FCM as before.

export const DEFAULT_MQTT_PRESENCE_GRACE_SECONDS = 90

export const DEVICE_TOPIC_ROOT = 'textbee/devices'

// Every device topic lives under its own prefix; the broker ACL pins a device
// to it, so the deviceId in an incoming topic can be trusted
export function deviceTopicPrefix(deviceId: string): string {
  return `${DEVICE_TOPIC_ROOT}/${deviceId}/`
}

export function deviceClientUsername(deviceId: string): string {
  return `device-${deviceId}`
}

export function deviceRoleName(deviceId: string): string {
  return `device-${deviceId}`
}

export const DOWN_SEND = 'down/send'
export const DOWN_HEARTBEAT_CHECK = 'down/heartbeat-check'
export const UP_SMS_STATUS = 'up/sms-status'
export const UP_RECEIVED = 'up/received'
export const STATUS = 'status'

// Uplinks are load-balanced across API instances; status is a plain
// subscription because retained messages are not sent to shared ones
export const UPLINK_SUBSCRIPTION = `$share/api/${DEVICE_TOPIC_ROOT}/+/up/#`
export const STATUS_SUBSCRIPTION = `${DEVICE_TOPIC_ROOT}/+/${STATUS}`

export function mqttEnabled(): boolean {
  return process.env.MQTT_ENABLED === 'true'
}

export function mqttPresenceGraceSeconds(): number {
  const raw = process.env.MQTT_PRESENCE_GRACE_SECONDS?.trim()
  const value = Number(raw)
  return raw && Number.isFinite(value) && value >= 0
    ? value
    : DEFAULT_MQTT_PRESENCE_GRACE_SECONDS
}

export type DevicePresence = {
  mqttConnected?: boolean | null
  mqttStatusAt?: Date | string | null
}

// Connected, or disconnected so recently that it is likely a network blip
// that the device is already reconnecting from
export function isDeviceOnline(
  device: DevicePresence | null | undefined,
  now: number = Date.now(),
): boolean {
  if (!mqttEnabled() || !device) return false
  if (device.mqttConnected === true) return true
  if (device.mqttConnected !== false || !device.mqttStatusAt) return false

  const since = new Date(device.mqttStatusAt).getTime()
  if (!Number.isFinite(since)) return false
  return now - since < mqttPresenceGraceSeconds() * 1000
}

// Mongo filter matching the same devices isDeviceOnline accepts
export function onlineDeviceFilter(now: number = Date.now()) {
  return {
    $or: [
      { mqttConnected: true },
      {
        mqttConnected: false,
        mqttStatusAt: { $gt: new Date(now - mqttPresenceGraceSeconds() * 1000) },
      },
    ],
  }
}

export type DeviceTopic = { deviceId: string; subtopic: string }

export function parseDeviceTopic(topic: string): DeviceTopic | null {
  const root = `${DEVICE_TOPIC_ROOT}/`
  if (!topic.startsWith(root)) return null
  const rest = topic.slice(root.length)
  const slash = rest.indexOf('/')
  if (slash <= 0) return null
  const subtopic = rest.slice(slash + 1)
  if (!subtopic) return null
  return { deviceId: rest.slice(0, slash), subtopic }
}
