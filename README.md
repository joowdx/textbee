<p align="center">
  <img src="branding/icon-circle.png" alt="textbeeqtt" width="128">
</p>

# textbeeqtt - self-hosted android sms gateway over MQTT

Send and receive SMS with your own Android phone, through a dashboard, a REST API or an MCP server, on infrastructure you run yourself.

textbeeqtt is a fork of [vernu/textbee](https://github.com/vernu/textbee). It keeps the textbee API and app, and adds:

- **MQTT transport.** The API reaches devices over a persistent MQTT connection to a Mosquitto broker it runs alongside. FCM stays as the fallback and the wake-up push.
- **A private instance.** Accounts are limited to an email allowlist and public sign-up is closed.
- **No textbee.dev dependency.** Every link, address and logo comes from environment variables or build properties.
- **Its own releases.** The Android app checks this fork's GitHub releases for updates. CI builds the APK and the web app from this repository.

## Features

- Send and receive SMS via the REST API and the dashboard
- Use your own Android phone (or several) as the gateway
- Bulk SMS from a CSV file
- Webhooks for incoming messages
- API key authentication
- Near-instant delivery to connected devices over MQTT, with FCM as the fallback

## How it works

```
             REST / dashboard
  client ───────────────────────▶  api (NestJS) ──── MongoDB, Redis
                                     │      │
                    MQTT (QoS 1)     │      │  FCM (fallback, wake-up)
                ┌────────────────────┘      │
                ▼                           ▼
         mosquitto broker  ◀── wss ──▶  Android app ──▶ SMS
```

- **Sending.** The API publishes the send to `textbee/devices/{id}/down/send` when MQTT is enabled, the API is connected to the broker and the device is online. In every other case it pushes over FCM as before.
  - A failed MQTT publish is retried over FCM. The device drops duplicates, so a message is never sent twice.
- **Presence.** Each device holds a retained `status` topic with a last will. A device counts as online while it is connected, or for `MQTT_PRESENCE_GRACE_SECONDS` (default 90) after it drops. When it drops, the API sends one FCM heartbeat check to wake it.
- **Reports.** The app publishes delivery status and received messages on `up/sms-status` and `up/received`. When it has no MQTT connection, it falls back to HTTP.
- **Enabling.** Devices learn whether MQTT is on from the heartbeat reply, then fetch per-device broker credentials from the API. The app has no toggle. Device Health shows the live connection state.

Topics, ACLs and broker setup are in [mqtt/README.md](mqtt/README.md).

## Getting started

1. Deploy the stack (see [Self-hosting](#self-hosting)) and add your address to `ALLOWED_EMAILS`.
2. Sign in to the dashboard and generate an API key or register a device.
3. Install the APK from this repository's [releases](https://github.com/joowdx/textbee/releases), open it and grant the SMS permissions.
4. Scan the QR code from the dashboard, or enter the API key by hand.
5. Send from the dashboard or the REST API.

### Sending an SMS

Messages go out through your default device, or otherwise through the enabled device with the most recent heartbeat. To send from a specific device, pass an optional `deviceId`.

```bash
curl -X POST "https://api.example.com/api/v1/gateway/send-sms" \
  -H 'x-api-key: YOUR_API_KEY' \
  -H 'Content-Type: application/json' \
  -d '{ "recipients": [ "+12025550123" ], "message": "Hello World!" }'
```

### Receiving SMS

Enable receiving in the app, then read messages through the API, the dashboard or a webhook. History is account-level, so one call covers every device.

```bash
curl "https://api.example.com/api/v1/gateway/messages?direction=received" \
  -H 'x-api-key: YOUR_API_KEY'
```

### Use with AI agents (MCP)

The upstream [textbee MCP server](https://github.com/textbee/textbee-mcp) works against this instance. Point it at your API with `TEXTBEE_BASE_URL`:

```json
{
  "mcpServers": {
    "textbeeqtt": {
      "command": "npx",
      "args": ["-y", "@textbee/mcp"],
      "env": {
        "TEXTBEE_API_KEY": "YOUR_API_KEY",
        "TEXTBEE_BASE_URL": "https://api.example.com"
      }
    }
  }
}
```

## Self-hosting

**Stack**: Next.js (web), NestJS (api), MongoDB, Redis, Mosquitto, Android (Kotlin, Jetpack Compose).

### Docker

1. Copy the env files and fill them in:
   ```bash
   cp web/.env.example web/.env && cp api/.env.example api/.env
   ```
2. Start everything: web, api, MongoDB, mongo-express, Redis and the MQTT broker.
   ```bash
   docker compose up -d
   ```
3. On first run, initialise the broker's dynamic security and the API's role as described in [mqtt/README.md](mqtt/README.md), then set the `MQTT_*` variables and restart the API.

The broker publishes no ports. Expose its WebSocket listener (`textbee-mqtt:9001`) through your reverse proxy or tunnel at a public `wss://` URL. That URL must allow WebSockets and must not sit behind a login gate, because the broker does its own auth.

### API settings for this fork

| Variable | Purpose |
| --- | --- |
| `ALLOWED_EMAILS` | Comma-separated addresses that may sign up or sign in. If empty, no new accounts can be created and existing accounts sign in as before. |
| `API_PUBLIC_URL`, `APP_PUBLIC_URL` | Public addresses used in links and emails |
| `APP_LOGO_URL` | Email logo. Defaults to `<APP_PUBLIC_URL>/images/logo.png` |
| `MQTT_ENABLED` | `true` turns on the MQTT transport. Any other value means FCM only. |
| `MQTT_URL` | Broker address the API connects to, for example `mqtt://textbee-mqtt:1883` |
| `MQTT_PUBLIC_URL` | `wss://…/mqtt` address handed to devices |
| `MQTT_ADMIN_USERNAME`, `MQTT_ADMIN_PASSWORD` | Dynamic-security admin client the API uses |
| `MQTT_CLIENT_ID` | Fixed client id for the API's persistent session (default `textbee-api`) |
| `MQTT_PRESENCE_GRACE_SECONDS` | How long a dropped device still counts as online (default 90) |

Firebase is still needed for FCM. Set the `FIREBASE_*` variables in `api/.env` and supply `google-services.json` to the Android build.

### Building the Android app

The app's links and identity come from Gradle properties, so you don't edit any source to point it at your instance:

```bash
cd android
./gradlew assembleProdRelease \
  -PtextbeeApiBaseUrl=https://api.example.com/api/v1/ \
  -PtextbeeWebBaseUrl=https://app.example.com \
  -PtextbeeReleasesRepo=owner/repo
```

The other properties are `textbeeApplicationId`, `textbeeVersionCode`, `textbeeVersionName` and the `textbee*Url` links. See [android/BUILD_VARIANTS_SETUP.md](android/BUILD_VARIANTS_SETUP.md). The [fork-release workflow](.github/workflows/fork-release.yaml) passes them in from repository variables.

### Building the web and API

```bash
cd web && pnpm install && pnpm build
cd api && pnpm install && pnpm build
```

### Analytics and telemetry

A self-hosted instance reports nothing to anyone by default. The web app loads no analytics scripts and the API sends no events unless you set the opt-in variables: `NEXT_PUBLIC_ANALYTICS_PROVIDERS` and the provider ids in `web/.env`, and `ANALYTICS_PROVIDERS` in `api/.env`.

### Branding

All icons are generated from `branding/bee.png` by `branding/generate-icons.sh`. The app icon is a squircle; every other icon is circular.

## FAQ

<details>
<summary><b>Does my phone need to stay on?</b></summary>
Yes. Messages go out through the phone, so it must be powered on with the app running and online. A spare phone on a charger works well as a dedicated gateway. Allow the app to ignore battery optimisation so the MQTT connection survives.

</details>
<details>
<summary><b>Do I need MQTT?</b></summary>
No. With `MQTT_ENABLED` unset, the instance behaves like upstream textbee and uses FCM only. MQTT makes delivery faster and less dependent on Google's push timing.

</details>
<details>
<summary><b>Will my carrier block my number?</b></summary>
Carriers apply their own rate limits and anti-spam rules. For low-volume use this is rare. For more throughput, spread sends across several devices or SIMs. You are responsible for your carrier's terms and for consent and messaging laws where you and your recipients are.

</details>

## Upstream and license

Built on [textbee](https://github.com/vernu/textbee) by vernu and its contributors, under the same [license](LICENSE). Report textbeeqtt-specific bugs in this repository's [issues](https://github.com/joowdx/textbee/issues). For security issues, see [SECURITY.md](SECURITY.md).
