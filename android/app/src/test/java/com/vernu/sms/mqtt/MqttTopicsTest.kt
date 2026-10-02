package com.vernu.sms.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MqttTopicsTest {
    private val topics = MqttTopics("textbee/devices/abc123/")

    @Test
    fun buildsTopicsUnderThePrefix() {
        assertEquals("textbee/devices/abc123/down/send", topics.downSend)
        assertEquals("textbee/devices/abc123/down/heartbeat-check", topics.downHeartbeatCheck)
        assertEquals("textbee/devices/abc123/status", topics.status)
        assertEquals("textbee/devices/abc123/up/sms-status", topics.topic(MqttTopics.UP_SMS_STATUS))
        assertEquals("textbee/devices/abc123/up/received", topics.topic(MqttTopics.UP_RECEIVED))
    }

    @Test
    fun prefixWithoutTrailingSlashGivesTheSameTopics() {
        assertEquals(topics.downSend, MqttTopics("textbee/devices/abc123").downSend)
        assertEquals(MqttTopics.defaultPrefix("abc123"), topics.base)
    }

    @Test
    fun suffixIsReadBackOnlyForOwnTopics() {
        assertEquals(MqttTopics.DOWN_SEND, topics.suffixOf("textbee/devices/abc123/down/send"))
        assertNull(topics.suffixOf("textbee/devices/other/down/send"))
    }

    @Test
    fun clientIdNamesTheDevice() {
        assertEquals("device-abc123", MqttTopics.clientId("abc123"))
    }
}
