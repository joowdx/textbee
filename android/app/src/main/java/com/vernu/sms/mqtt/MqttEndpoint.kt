package com.vernu.sms.mqtt

import java.net.URI

// Where to connect, read from the url the server returns (wss://host/mqtt).
data class MqttEndpoint(
    val host: String,
    val port: Int,
    val secure: Boolean,
    val webSocket: Boolean,
    val path: String,
    val query: String,
) {
    companion object {
        fun parse(url: String?): MqttEndpoint? {
            if (url.isNullOrBlank()) return null
            val uri = try {
                URI(url.trim())
            } catch (e: Exception) {
                return null
            }
            val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
            val (secure, webSocket, defaultPort) = when (uri.scheme?.lowercase()) {
                "wss" -> Triple(true, true, 443)
                "ws" -> Triple(false, true, 80)
                "mqtts", "ssl" -> Triple(true, false, 8883)
                "mqtt", "tcp" -> Triple(false, false, 1883)
                else -> return null
            }
            return MqttEndpoint(
                host = host,
                port = if (uri.port > 0) uri.port else defaultPort,
                secure = secure,
                webSocket = webSocket,
                path = (uri.rawPath ?: "").trimStart('/'),
                query = uri.rawQuery ?: "",
            )
        }
    }
}
