package com.vernu.sms.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture

class UplinkTest {
    private class FakePublisher(
        override val isConnected: Boolean,
        private val result: () -> CompletableFuture<Boolean>,
    ) : UplinkPublisher {
        val published = ArrayList<Pair<String, String>>()
        override fun publish(topicSuffix: String, payload: ByteArray): CompletableFuture<Boolean> {
            published += topicSuffix to String(payload, Charsets.UTF_8)
            return result()
        }
    }

    @Test
    fun connectedAndAcknowledgedGoesOverMqtt() {
        val p = FakePublisher(true) { CompletableFuture.completedFuture(true) }
        assertTrue(Uplink.overMqtt(p, MqttTopics.UP_SMS_STATUS, """{"status":"SENT"}"""))
        assertEquals(listOf(MqttTopics.UP_SMS_STATUS to """{"status":"SENT"}"""), p.published)
    }

    @Test
    fun notConnectedUsesHttpWithoutPublishing() {
        val p = FakePublisher(false) { CompletableFuture.completedFuture(true) }
        assertFalse(Uplink.overMqtt(p, MqttTopics.UP_RECEIVED, "{}"))
        assertTrue(p.published.isEmpty())
    }

    @Test
    fun noAcknowledgementInTimeUsesHttp() {
        val p = FakePublisher(true) { CompletableFuture<Boolean>() }
        assertFalse(Uplink.overMqtt(p, MqttTopics.UP_RECEIVED, "{}", timeoutMs = 50))
    }

    @Test
    fun refusedOrFailedPublishUsesHttp() {
        assertFalse(Uplink.overMqtt(FakePublisher(true) { CompletableFuture.completedFuture(false) }, MqttTopics.UP_RECEIVED, "{}"))
        val failed = CompletableFuture<Boolean>().apply { completeExceptionally(IllegalStateException("gone")) }
        assertFalse(Uplink.overMqtt(FakePublisher(true) { failed }, MqttTopics.UP_RECEIVED, "{}"))
        assertFalse(Uplink.overMqtt(FakePublisher(true) { throw IllegalStateException("gone") }, MqttTopics.UP_RECEIVED, "{}"))
    }
}
