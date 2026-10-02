package com.vernu.sms.mqtt

import org.junit.Assert.assertEquals
import org.junit.Test

class MqttBackoffTest {
    @Test
    fun doublesUpToTheCap() {
        val noJitter = { 0.0 }
        assertEquals(1_000L, MqttBackoff.delayMs(0, noJitter))
        assertEquals(8_000L, MqttBackoff.delayMs(3, noJitter))
        assertEquals(MqttBackoff.MAX_MS, MqttBackoff.delayMs(10, noJitter))
        assertEquals(MqttBackoff.MAX_MS, MqttBackoff.delayMs(500, noJitter))
    }

    @Test
    fun jitterAddsAtMostAFifth() {
        assertEquals(1_200L, MqttBackoff.delayMs(0) { 1.0 })
    }
}
