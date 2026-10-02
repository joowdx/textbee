package com.vernu.sms.mqtt

import com.google.gson.Gson
import com.vernu.sms.models.SMSPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MqttCommandParserTest {
    private val smsData = """{"smsId":"s1","smsBatchId":"b1","message":"hi","recipients":["+15550100","+15550101"]}"""

    @Test
    fun sendWithSmsDataStringMatchesTheFcmMap() {
        val payload = Gson().toJson(mapOf("smsData" to smsData))
        val data = MqttCommandParser.parse(MqttTopics.DOWN_SEND, payload)!!
        assertEquals(mapOf("smsData" to smsData), data)
        val sms = Gson().fromJson(data["smsData"], SMSPayload::class.java)
        assertEquals("s1", sms.smsId)
        assertEquals(2, sms.recipients?.size)
    }

    @Test
    fun sendWithSmsDataObjectIsTurnedIntoJsonText() {
        val data = MqttCommandParser.parse(MqttTopics.DOWN_SEND, """{"smsData":$smsData}""")!!
        val sms = Gson().fromJson(data["smsData"], SMSPayload::class.java)
        assertEquals("b1", sms.smsBatchId)
        assertEquals("hi", sms.message)
    }

    @Test
    fun bareSmsPayloadIsWrapped() {
        val data = MqttCommandParser.parse(MqttTopics.DOWN_SEND, smsData)!!
        assertEquals(setOf("smsData"), data.keys)
        assertEquals("s1", Gson().fromJson(data["smsData"], SMSPayload::class.java).smsId)
    }

    @Test
    fun heartbeatCheckAlwaysCarriesItsType() {
        assertEquals(mapOf("type" to "heartbeat_check"), MqttCommandParser.parse(MqttTopics.DOWN_HEARTBEAT_CHECK, "{}"))
        assertEquals(mapOf("type" to "heartbeat_check"), MqttCommandParser.parse(MqttTopics.DOWN_HEARTBEAT_CHECK, ""))
        assertEquals(
            mapOf("type" to "heartbeat_check"),
            MqttCommandParser.parse(MqttTopics.DOWN_HEARTBEAT_CHECK, """{"type":"heartbeat_check"}"""),
        )
    }

    @Test
    fun junkAndUnknownTopicsAreIgnored() {
        assertNull(MqttCommandParser.parse(MqttTopics.DOWN_SEND, "not json"))
        assertNull(MqttCommandParser.parse(MqttTopics.DOWN_SEND, "[1,2]"))
        assertNull(MqttCommandParser.parse(MqttTopics.DOWN_SEND, """{"foo":"bar"}"""))
        assertNull(MqttCommandParser.parse("down/other", smsData))
    }
}
