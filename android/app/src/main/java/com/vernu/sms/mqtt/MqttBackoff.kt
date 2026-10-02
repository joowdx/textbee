package com.vernu.sms.mqtt

// Reconnect delay: doubles from one second up to two minutes, with up to a
// fifth added at random so devices behind one outage do not retry together.
object MqttBackoff {
    const val BASE_MS = 1_000L
    const val MAX_MS = 120_000L

    fun delayMs(attempt: Int, random: () -> Double = Math::random): Long {
        val exp = BASE_MS shl attempt.coerceIn(0, 20)
        val capped = minOf(exp, MAX_MS)
        return capped + (capped * 0.2 * random()).toLong()
    }
}
