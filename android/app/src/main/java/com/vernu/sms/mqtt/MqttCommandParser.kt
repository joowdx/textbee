package com.vernu.sms.mqtt

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

// Turns a downlink message into the same string map an FCM data message
// carries, so SmsCommandHandler cannot tell the two apart.
object MqttCommandParser {
    const val TYPE_HEARTBEAT_CHECK = "heartbeat_check"

    fun parse(topicSuffix: String, payload: String): Map<String, String>? {
        val obj = parseObject(payload)
        return when (topicSuffix) {
            MqttTopics.DOWN_HEARTBEAT_CHECK -> (obj?.let { toDataMap(it) } ?: emptyMap()) + ("type" to TYPE_HEARTBEAT_CHECK)
            MqttTopics.DOWN_SEND -> {
                obj ?: return null
                // The FCM shape is {"smsData": "<json>"}. A bare payload object
                // is accepted too and wrapped the same way.
                if (obj.has("smsData")) toDataMap(obj)
                else if (obj.has("smsId") || obj.has("recipients")) mapOf("smsData" to obj.toString())
                else null
            }
            else -> null
        }
    }

    private fun parseObject(payload: String): JsonObject? = try {
        JsonParser.parseString(payload).takeIf { it.isJsonObject }?.asJsonObject
    } catch (e: Exception) {
        null
    }

    // FCM data values are strings; nested JSON is passed on as its JSON text
    private fun toDataMap(obj: JsonObject): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for ((key, value) in obj.entrySet()) {
            stringValue(value)?.let { map[key] = it }
        }
        return map
    }

    private fun stringValue(value: JsonElement): String? = when {
        value.isJsonNull -> null
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
        else -> value.toString()
    }
}
