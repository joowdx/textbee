package com.vernu.sms.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.vernu.sms.ApiManager
import com.vernu.sms.dtos.SMSDTO
import com.vernu.sms.helpers.DeviceLog
import com.vernu.sms.mqtt.MqttClientManager
import com.vernu.sms.mqtt.MqttTopics
import com.vernu.sms.mqtt.Uplink
import java.io.IOException
import java.util.concurrent.TimeUnit

class SMSReceivedWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    companion object {
        private const val TAG = "SMSReceivedWorker"

        const val KEY_DEVICE_ID = "device_id"
        const val KEY_API_KEY = "api_key"
        const val KEY_SMS_DTO = "sms_dto"

        fun enqueueWork(context: Context, deviceId: String, apiKey: String, smsDTO: SMSDTO) {
            val inputData = Data.Builder()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_API_KEY, apiKey)
                .putString(KEY_SMS_DTO, Gson().toJson(smsDTO))
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequest.Builder(SMSReceivedWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(inputData)
                .addTag("sms_received")
                .build()

            val fp = smsDTO.fingerprint
            val uniqueWorkName = if (!fp.isNullOrEmpty()) {
                "sms_received_$fp"
            } else {
                Log.w(TAG, "Fingerprint not available, using timestamp for work name")
                "sms_received_${System.currentTimeMillis()}"
            }

            WorkManager.getInstance(context)
                .beginUniqueWork(uniqueWorkName, ExistingWorkPolicy.KEEP, workRequest)
                .enqueue()

            Log.d(TAG, "Work enqueued for received SMS from: ${smsDTO.sender} with fingerprint: $uniqueWorkName")
        }
    }

    override fun doWork(): Result {
        val deviceId = inputData.getString(KEY_DEVICE_ID)
        val apiKey = inputData.getString(KEY_API_KEY)
        val smsDtoJson = inputData.getString(KEY_SMS_DTO)

        if (deviceId == null || apiKey == null || smsDtoJson == null) {
            Log.e(TAG, "Missing required parameters")
            return Result.failure()
        }

        val smsDTO = Gson().fromJson(smsDtoJson, SMSDTO::class.java)

        // Same body as the HTTP call; HTTP is the fallback when not connected
        // or when the broker does not acknowledge in time
        if (Uplink.overMqtt(MqttClientManager, MqttTopics.UP_RECEIVED, Gson().toJson(smsDTO))) {
            Log.d(TAG, "Reported over MQTT - ID: ${smsDTO.smsId}")
            DeviceLog.log(applicationContext, "sms_forwarded", "from ${smsDTO.sender}, mqtt")
            return Result.success()
        }

        return try {
            val response = ApiManager.getApiService().sendReceivedSMS(deviceId, apiKey, smsDTO).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "Received SMS sent to server successfully")
                DeviceLog.log(applicationContext, "sms_forwarded", "from ${smsDTO.sender}")
                Result.success()
            } else {
                Log.e(TAG, "Failed to send received SMS to server. Response code: ${response.code()}")
                DeviceLog.log(applicationContext, "sms_forward_retry", "http ${response.code()}, attempt ${runAttemptCount + 1}")
                retryOrFail(response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "API call failed: ${e.message}")
            retryOrFail(null)
        }
    }

    private fun retryOrFail(responseCode: Int?): Result {
        if (WorkerRetryPolicy.shouldRetry(responseCode, runAttemptCount)) return Result.retry()
        Log.e(TAG, "Giving up on received SMS after ${runAttemptCount + 1} attempts")
        return Result.failure()
    }
}
