package com.vernu.sms.mqtt

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

interface UplinkPublisher {
    val isConnected: Boolean

    /** Completes true once the broker acknowledged the message (PUBACK). */
    fun publish(topicSuffix: String, payload: ByteArray): CompletableFuture<Boolean>
}

// Device-to-server reports: over MQTT while connected, else the caller uses
// HTTP. A report that times out may still arrive over MQTT later; the server
// treats a repeat status or received message as the same one.
object Uplink {
    const val PUBLISH_TIMEOUT_MS = 5_000L

    fun overMqtt(
        publisher: UplinkPublisher,
        topicSuffix: String,
        payload: String,
        timeoutMs: Long = PUBLISH_TIMEOUT_MS,
    ): Boolean {
        if (!publisher.isConnected) return false
        return try {
            publisher.publish(topicSuffix, payload.toByteArray(Charsets.UTF_8))
                .get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            false
        }
    }
}
