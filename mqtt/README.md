# textbee MQTT broker

An optional, two-way device transport. With `MQTT_ENABLED=true` the API sends
to connected devices over MQTT and keeps FCM as the fallback and wake-up push.
Devices learn the flag from the heartbeat reply and fetch their credentials
from `POST /api/v1/gateway/devices/:id/mqtt-credentials`.

The broker is the `textbee-mqtt` service in `docker-compose.yaml`
(`eclipse-mosquitto:2`, config in `mosquitto.conf`). Auth uses Mosquitto's
dynamic-security plugin: one admin client for the API, and one client and role
per device, created and rotated by the API with no broker restart.

## Topics

Each device uses `textbee/devices/{deviceId}/`, QoS 1:

| Topic | Direction | Payload |
|---|---|---|
| `down/send` | API to device | the FCM data map of an SMS send, as JSON |
| `down/heartbeat-check` | API to device | `{"type":"heartbeat_check"}` |
| `up/sms-status` | device to API | body of `PATCH /gateway/devices/:id/sms-status` |
| `up/received` | device to API | body of `POST /gateway/devices/:id/receive-sms` |
| `status` (retained) | device | `{"online":true}` on connect, last will `{"online":false}` |

## First-time setup

1. Create the dynamic-security file in the data volume. It prompts for the
   admin password:

   ```sh
   docker compose run --rm --no-deps --entrypoint mosquitto_ctrl textbee-mqtt \
     dynsec init /mosquitto/data/dynamic-security.json textbee-admin
   ```

2. Start the broker: `docker compose up -d textbee-mqtt`.

3. The admin role created by `init` can manage dynamic security but cannot
   publish to devices. Give the admin client a role for the device topics:

   ```sh
   ctrl() { docker exec -it textbee-mqtt mosquitto_ctrl -u textbee-admin dynsec "$@"; }
   ctrl createRole textbee-api
   ctrl addRoleACL textbee-api publishClientSend 'textbee/devices/+/down/#' allow
   ctrl addRoleACL textbee-api subscribePattern 'textbee/devices/+/up/#' allow
   # The API reads uplinks through a shared subscription, and the ACL is
   # checked against the full $share filter
   ctrl addRoleACL textbee-api subscribePattern '$share/api/textbee/devices/+/up/#' allow
   ctrl addRoleACL textbee-api publishClientReceive 'textbee/devices/+/up/#' allow
   ctrl addRoleACL textbee-api subscribePattern 'textbee/devices/+/status' allow
   ctrl addRoleACL textbee-api publishClientReceive 'textbee/devices/+/status' allow
   ctrl addClientRole textbee-admin textbee-api
   ```

4. Set in `api/.env`: `MQTT_ENABLED=true`, `MQTT_ADMIN_USERNAME=textbee-admin`,
   `MQTT_ADMIN_PASSWORD`, and `MQTT_PUBLIC_URL` (for example
   `wss://textbee-mqtt.example.com/mqtt`). Restart the API.

## Reverse proxy and tunnel

Devices connect over TLS WebSockets to the public hostname, which must forward
to `textbee-mqtt:9001`:

- WebSockets must be allowed on that hostname and through the tunnel.
- No access gate or login page on that hostname: the device cannot pass one,
  and the broker does its own auth.
- Long-lived connections must not be cut by idle timeouts shorter than the
  device keepalive (30 s).
