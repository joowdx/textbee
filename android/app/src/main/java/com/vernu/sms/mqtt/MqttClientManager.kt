package com.vernu.sms.mqtt

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode
import com.hivemq.client.mqtt.mqtt5.message.disconnect.Mqtt5DisconnectReasonCode
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.vernu.sms.AppConstants
import com.vernu.sms.TextbeeUtils
import com.vernu.sms.helpers.DeviceConfig
import com.vernu.sms.helpers.DeviceLog
import com.vernu.sms.helpers.SharedPreferenceHelper
import com.vernu.sms.services.SmsCommandHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

// The device's live link to the server. StickyNotificationService starts and
// stops it; the workers publish through it while it is connected. All state
// changes run on one thread, so the client callbacks hop onto it.
object MqttClientManager : UplinkPublisher {
    private const val TAG = "MqttClientManager"
    private const val KEEP_ALIVE_SECONDS = 30
    private const val SESSION_EXPIRY_SECONDS = 24 * 60 * 60L

    private val exec = Executors.newSingleThreadScheduledExecutor { Thread(it, "mqtt-connection") }
    private val commands = Executors.newSingleThreadExecutor { Thread(it, "mqtt-commands") }

    private var appContext: Context? = null
    private var running = false
    private var connecting = false
    private var attempt = 0
    private var retry: ScheduledFuture<*>? = null
    private var generation = 0

    private var client: Mqtt5AsyncClient? = null
    private var clientCredentials: MqttCredentials? = null
    private var topics: MqttTopics? = null

    private var lastNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** MQTT runs only when the server turned it on and the gateway is enabled. */
    @JvmStatic
    fun wanted(context: Context): Boolean {
        fun str(key: String) = SharedPreferenceHelper.getSharedPreferenceString(context, key, "") ?: ""
        return DeviceConfig.mqttEnabled(context) &&
            SharedPreferenceHelper.getSharedPreferenceBoolean(context, AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY, false) &&
            str(AppConstants.SHARED_PREFS_DEVICE_ID_KEY).isNotEmpty() &&
            str(AppConstants.SHARED_PREFS_API_KEY_KEY).isNotEmpty()
    }

    /**
     * Starts or stops the service after the server flipped mqttEnabled, or
     * restarts it when MQTT should run but is not. Android may refuse a start
     * from the background; the next heartbeat tries again.
     */
    @JvmStatic
    fun syncService(context: Context, changed: Boolean) {
        val want = wanted(context)
        if (!changed && !(want && MqttState.value == MqttConnectionState.OFF)) return
        val sticky = SharedPreferenceHelper.getSharedPreferenceBoolean(
            context, AppConstants.SHARED_PREFS_STICKY_NOTIFICATION_ENABLED_KEY, false
        )
        try {
            if (want || sticky) TextbeeUtils.startStickyNotificationService(context)
            else TextbeeUtils.stopStickyNotificationService(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not update the gateway service: ${e.message}")
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        exec.execute {
            appContext = app
            if (running) return@execute
            running = true
            attempt = 0
            Log.i(TAG, "Starting MQTT")
            DeviceLog.log(app, "mqtt_start")
            connectNow()
            registerNetworkCallback(app)
        }
    }

    fun stop() {
        exec.execute {
            if (!running) return@execute
            running = false
            retry?.cancel(false)
            retry = null
            unregisterNetworkCallback()
            generation++
            val c = client
            client = null
            clientCredentials = null
            connecting = false
            // Asks the broker to publish the retained offline status now
            // instead of waiting for the keepalive to lapse
            c?.disconnectWith()?.reasonCode(Mqtt5DisconnectReasonCode.DISCONNECT_WITH_WILL_MESSAGE)?.send()
            MqttState.set(MqttConnectionState.OFF)
            appContext?.let { DeviceLog.log(it, "mqtt_stop") }
            Log.i(TAG, "Stopped MQTT")
        }
    }

    override val isConnected: Boolean
        get() = MqttState.value == MqttConnectionState.CONNECTED && client?.state?.isConnected == true

    override fun publish(topicSuffix: String, payload: ByteArray): CompletableFuture<Boolean> {
        val c = client
        val t = topics
        if (c == null || t == null) return CompletableFuture.completedFuture(false)
        return c.publishWith()
            .topic(t.topic(topicSuffix))
            .payload(payload)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
            .thenApply { !it.error.isPresent }
    }

    // Runs on exec
    private fun connectNow() {
        if (!running || connecting) return
        val ctx = appContext ?: return
        retry?.cancel(false)
        retry = null
        MqttState.set(MqttConnectionState.CONNECTING)

        val deviceId = SharedPreferenceHelper.getSharedPreferenceString(ctx, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, "") ?: ""
        val apiKey = SharedPreferenceHelper.getSharedPreferenceString(ctx, AppConstants.SHARED_PREFS_API_KEY_KEY, "") ?: ""
        val creds = MqttCredentialStore.load(ctx) ?: MqttCredentialStore.fetch(ctx, deviceId, apiKey)
        val endpoint = MqttEndpoint.parse(creds?.url)
        if (creds == null || endpoint == null) {
            Log.w(TAG, "No usable MQTT credentials")
            DeviceLog.log(ctx, "mqtt_failed", "no credentials")
            scheduleRetry()
            return
        }

        val c = clientFor(deviceId, creds, endpoint)
        val t = topics ?: return
        val gen = generation
        connecting = true
        c.connectWith()
            .cleanStart(false)
            .sessionExpiryInterval(SESSION_EXPIRY_SECONDS)
            .keepAlive(KEEP_ALIVE_SECONDS)
            .simpleAuth()
            .username(creds.username)
            .password(creds.password.toByteArray(Charsets.UTF_8))
            .applySimpleAuth()
            .willPublish()
            .topic(t.status)
            .payload(MqttTopics.OFFLINE_PAYLOAD.toByteArray(Charsets.UTF_8))
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .applyWillPublish()
            .send()
            .whenComplete { _, error ->
                // A failed attempt is handled by the disconnected listener
                if (error == null) exec.execute { onConnected(gen) }
            }
    }

    private fun clientFor(deviceId: String, creds: MqttCredentials, endpoint: MqttEndpoint): Mqtt5AsyncClient {
        val existing = client
        if (existing != null && clientCredentials == creds) return existing

        generation++
        val gen = generation
        var builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(MqttTopics.clientId(deviceId))
            .serverHost(endpoint.host)
            .serverPort(endpoint.port)
            .addDisconnectedListener { context -> exec.execute { onDisconnected(gen, context) } }
        if (endpoint.secure) builder = builder.sslWithDefaultConfig()
        if (endpoint.webSocket) {
            builder = builder.webSocketConfig()
                .serverPath(endpoint.path)
                .queryString(endpoint.query)
                .applyWebSocketConfig()
        }
        val c = builder.buildAsync()
        val t = MqttTopics(creds.topicPrefix)
        // Registered before connecting, so messages the broker kept for this
        // session are not dropped when they arrive ahead of the subscribe
        c.publishes(MqttGlobalPublishFilter.ALL) { publish -> onMessage(t, publish) }
        client = c
        clientCredentials = creds
        topics = t
        return c
    }

    private fun onConnected(gen: Int) {
        if (gen != generation) return
        connecting = false
        if (!running) return
        val c = client ?: return
        val t = topics ?: return
        attempt = 0
        MqttState.set(MqttConnectionState.CONNECTED)
        appContext?.let { DeviceLog.log(it, "mqtt_connected") }
        Log.i(TAG, "MQTT connected")

        // Subscribing again is harmless when the broker kept the session
        c.subscribeWith().topicFilter(t.downSend).qos(MqttQos.AT_LEAST_ONCE).send()
        c.subscribeWith().topicFilter(t.downHeartbeatCheck).qos(MqttQos.AT_LEAST_ONCE).send()
        c.publishWith()
            .topic(t.status)
            .payload(MqttTopics.ONLINE_PAYLOAD.toByteArray(Charsets.UTF_8))
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .send()
    }

    private fun onDisconnected(gen: Int, context: MqttClientDisconnectedContext) {
        if (gen != generation) return
        connecting = false
        if (context.source == MqttDisconnectSource.USER || !running) return
        val ctx = appContext
        val cause = context.cause
        val reason = (cause as? Mqtt5ConnAckException)?.mqttMessage?.reasonCode
        if (reason == Mqtt5ConnAckReasonCode.NOT_AUTHORIZED || reason == Mqtt5ConnAckReasonCode.BAD_USER_NAME_OR_PASSWORD) {
            // Rotated or revoked on the server: fetch a new set on the next attempt
            Log.w(TAG, "MQTT credentials refused ($reason)")
            ctx?.let {
                MqttCredentialStore.clear(it)
                DeviceLog.log(it, "mqtt_failed", "credentials refused")
            }
        } else {
            Log.w(TAG, "MQTT disconnected: ${cause.message}")
            ctx?.let { DeviceLog.log(it, "mqtt_disconnected", cause.javaClass.simpleName) }
        }
        MqttState.set(MqttConnectionState.CONNECTING)
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!running) return
        retry?.cancel(false)
        val delay = MqttBackoff.delayMs(attempt)
        attempt++
        Log.d(TAG, "Reconnecting in ${delay}ms")
        retry = exec.schedule({ connectNow() }, delay, TimeUnit.MILLISECONDS)
    }

    private fun onMessage(t: MqttTopics, publish: Mqtt5Publish) {
        val ctx = appContext ?: return
        val receivedAt = System.currentTimeMillis()
        val suffix = t.suffixOf(publish.topic.toString()) ?: return
        val payload = String(publish.payloadAsBytes, Charsets.UTF_8)
        val data = MqttCommandParser.parse(suffix, payload)
        if (data == null) {
            Log.w(TAG, "Ignoring MQTT message on $suffix")
            DeviceLog.log(ctx, "mqtt_invalid", suffix)
            return
        }
        // Off the client thread: a heartbeat check makes a blocking HTTP call
        commands.execute { SmsCommandHandler.handle(ctx, data, SmsCommandHandler.SOURCE_MQTT, receivedAt) }
    }

    // A new default network means the old socket is gone or soon will be, so
    // reconnect now instead of waiting out the keepalive or the backoff
    private fun onNetworkAvailable(network: Network) {
        if (!running) return
        val previous = lastNetwork
        lastNetwork = network
        if (connecting) return
        val c = client
        if (MqttState.value == MqttConnectionState.CONNECTED && c != null) {
            if (previous == null || previous == network) return
            Log.i(TAG, "Default network changed, reconnecting")
            connecting = true
            c.disconnect().whenComplete { _, _ ->
                exec.execute {
                    connecting = false
                    attempt = 0
                    connectNow()
                }
            }
        } else {
            attempt = 0
            connectNow()
        }
    }

    private fun registerNetworkCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                exec.execute { onNetworkAvailable(network) }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch network changes: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        lastNetwork = null
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop watching network changes: ${e.message}")
        }
    }
}
