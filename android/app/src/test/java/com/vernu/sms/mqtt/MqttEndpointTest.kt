package com.vernu.sms.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MqttEndpointTest {
    @Test
    fun wssUrlUsesTlsWebSocketsOn443() {
        assertEquals(
            MqttEndpoint("broker.example.com", 443, secure = true, webSocket = true, path = "mqtt", query = ""),
            MqttEndpoint.parse("wss://broker.example.com/mqtt"),
        )
    }

    @Test
    fun explicitPortAndQueryAreKept() {
        val e = MqttEndpoint.parse("ws://10.0.0.5:9001/mqtt?x=1")!!
        assertEquals(9001, e.port)
        assertEquals(false, e.secure)
        assertEquals("x=1", e.query)
    }

    @Test
    fun plainMqttHasNoWebSocket() {
        val e = MqttEndpoint.parse("mqtts://broker.example.com")!!
        assertEquals(8883, e.port)
        assertEquals(false, e.webSocket)
        assertEquals("", e.path)
    }

    @Test
    fun unusableUrlsGiveNull() {
        assertNull(MqttEndpoint.parse(null))
        assertNull(MqttEndpoint.parse(""))
        assertNull(MqttEndpoint.parse("https://broker.example.com/mqtt"))
        assertNull(MqttEndpoint.parse("not a url"))
    }
}
