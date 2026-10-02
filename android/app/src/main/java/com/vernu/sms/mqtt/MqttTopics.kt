package com.vernu.sms.mqtt

// Topic names under the prefix the server hands out with the credentials,
// textbee/devices/{deviceId}/. The broker only lets a device use its own.
class MqttTopics(prefix: String) {
    companion object {
        const val DOWN_SEND = "down/send"
        const val DOWN_HEARTBEAT_CHECK = "down/heartbeat-check"
        const val UP_SMS_STATUS = "up/sms-status"
        const val UP_RECEIVED = "up/received"
        const val STATUS = "status"

        const val ONLINE_PAYLOAD = "{\"online\":true}"
        const val OFFLINE_PAYLOAD = "{\"online\":false}"

        fun defaultPrefix(deviceId: String) = "textbee/devices/$deviceId/"
        fun clientId(deviceId: String) = "device-$deviceId"
    }

    val base: String = prefix.trim().trimEnd('/') + "/"

    fun topic(suffix: String) = base + suffix.trimStart('/')

    val downSend get() = topic(DOWN_SEND)
    val downHeartbeatCheck get() = topic(DOWN_HEARTBEAT_CHECK)
    val status get() = topic(STATUS)

    /** The part after the prefix, or null for a topic outside it. */
    fun suffixOf(topic: String): String? =
        if (topic.startsWith(base)) topic.substring(base.length) else null
}
