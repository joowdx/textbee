package com.vernu.sms.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.vernu.sms.AppConstants
import com.vernu.sms.TextbeeUtils
import com.vernu.sms.helpers.DeviceLog
import com.vernu.sms.helpers.HeartbeatHelper
import com.vernu.sms.helpers.HeartbeatManager
import com.vernu.sms.helpers.SharedPreferenceHelper
import com.vernu.sms.models.SMSPayload
import com.vernu.sms.workers.SmsSendWorker

// Runs a command from the server, whether it came by FCM or MQTT. Both carry
// the same data map. A message delivered both ways is sent once, because
// SmsSendWorker.enqueue skips what SmsDedupeStore has already sent.
object SmsCommandHandler {
    private const val TAG = "SmsCommandHandler"

    const val SOURCE_PUSH = "push"
    const val SOURCE_MQTT = "mqtt"

    /** [source] prefixes the log events: push_received, mqtt_received. */
    fun handle(context: Context, data: Map<String, String>, source: String, receivedAt: Long = System.currentTimeMillis()) {
        try {
            if (data["type"] == "heartbeat_check") {
                DeviceLog.log(context, "${source}_received", "heartbeat check")
                handleHeartbeatCheck(context)
                return
            }

            val smsPayload = Gson().fromJson(data["smsData"], SMSPayload::class.java)

            if (data.isNotEmpty()) {
                sendSMS(context, smsPayload, receivedAt, source)
            }
        } catch (e: Exception) {
            TextbeeUtils.logException(e, "Error processing $source message")
        }
    }

    private fun handleHeartbeatCheck(context: Context) {
        Log.d(TAG, "Received heartbeat check request from backend")

        if (!HeartbeatHelper.isDeviceEligibleForHeartbeat(context)) {
            Log.d(TAG, "Device not eligible for heartbeat, skipping heartbeat check")
            return
        }

        val deviceId = SharedPreferenceHelper.getSharedPreferenceString(
            context, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, ""
        ) ?: ""
        val apiKey = SharedPreferenceHelper.getSharedPreferenceString(
            context, AppConstants.SHARED_PREFS_API_KEY_KEY, ""
        ) ?: ""

        val success = HeartbeatHelper.sendHeartbeat(context, deviceId, apiKey)
        if (success) {
            Log.d(TAG, "Heartbeat sent successfully in response to backend check")
        } else {
            Log.e(TAG, "Failed to send heartbeat in response to backend check")
        }
        HeartbeatManager.scheduleHeartbeat(context)
    }

    private fun sendSMS(context: Context, smsPayload: SMSPayload?, receivedAt: Long, source: String) {
        if (smsPayload == null) {
            Log.e(TAG, "SMS payload is null")
            return
        }

        val recipients = smsPayload.recipients
        if (recipients == null || recipients.isEmpty()) {
            Log.e(TAG, "No recipients found in SMS payload")
            return
        }
        if (smsPayload.smsId.isNullOrEmpty() || smsPayload.message == null) {
            Log.e(TAG, "SMS payload is missing its id or message")
            DeviceLog.log(context, "${source}_invalid", "missing id or message")
            return
        }
        DeviceLog.log(context, "${source}_received", "${recipients.size} recipient(s)", smsPayload.smsId)

        for (recipient in recipients) {
            SmsSendWorker.enqueue(
                context, recipient, smsPayload.message ?: "",
                smsPayload.smsId, smsPayload.smsBatchId, smsPayload.simSubscriptionId,
                receivedAt
            )
        }

        Log.d(TAG, "Enqueued ${recipients.size} SMS for sending - Batch: ${smsPayload.smsBatchId}")
    }
}
