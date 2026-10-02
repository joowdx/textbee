import { isDeviceOnline, onlineDeviceFilter, parseDeviceTopic } from './mqtt-config'

describe('mqtt presence', () => {
  const originalEnv = { ...process.env }
  const now = Date.parse('2026-10-02T12:00:00Z')

  beforeEach(() => {
    process.env = { ...originalEnv, MQTT_ENABLED: 'true' }
    delete process.env.MQTT_PRESENCE_GRACE_SECONDS
  })

  afterAll(() => {
    process.env = originalEnv
  })

  it('is online while connected', () => {
    expect(isDeviceOnline({ mqttConnected: true }, now)).toBe(true)
  })

  it('stays online for the grace window after a drop', () => {
    const at = new Date(now - 89 * 1000)
    expect(isDeviceOnline({ mqttConnected: false, mqttStatusAt: at }, now)).toBe(true)
  })

  it('is offline once the grace window has passed', () => {
    const at = new Date(now - 90 * 1000)
    expect(isDeviceOnline({ mqttConnected: false, mqttStatusAt: at }, now)).toBe(false)
  })

  it('reads the grace window from the environment', () => {
    process.env.MQTT_PRESENCE_GRACE_SECONDS = '10'
    const at = new Date(now - 30 * 1000)
    expect(isDeviceOnline({ mqttConnected: false, mqttStatusAt: at }, now)).toBe(false)

    process.env.MQTT_PRESENCE_GRACE_SECONDS = '0'
    expect(
      isDeviceOnline({ mqttConnected: false, mqttStatusAt: new Date(now) }, now),
    ).toBe(false)
  })

  it('is offline for a device that never connected', () => {
    expect(isDeviceOnline({}, now)).toBe(false)
    expect(isDeviceOnline(null, now)).toBe(false)
  })

  it('is always offline when MQTT is disabled', () => {
    process.env.MQTT_ENABLED = 'false'
    expect(isDeviceOnline({ mqttConnected: true }, now)).toBe(false)
  })

  it('builds a query matching the same grace window', () => {
    expect(onlineDeviceFilter(now)).toEqual({
      $or: [
        { mqttConnected: true },
        { mqttConnected: false, mqttStatusAt: { $gt: new Date(now - 90 * 1000) } },
      ],
    })
  })

  it('parses device topics', () => {
    expect(parseDeviceTopic('textbee/devices/abc/up/received')).toEqual({
      deviceId: 'abc',
      subtopic: 'up/received',
    })
    expect(parseDeviceTopic('textbee/devices/abc/status')).toEqual({
      deviceId: 'abc',
      subtopic: 'status',
    })
    expect(parseDeviceTopic('textbee/devices/abc')).toBeNull()
    expect(parseDeviceTopic('other/devices/abc/status')).toBeNull()
  })
})
