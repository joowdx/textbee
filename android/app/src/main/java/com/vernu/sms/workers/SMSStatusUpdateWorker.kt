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

class SMSStatusUpdateWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    companion object {
        private const val TAG = "SMSStatusUpdateWorker"

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

            val workRequest = OneTimeWorkRequest.Builder(SMSStatusUpdateWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(inputData)
                .build()

            // One upload per (message, status). A second report of the same
            // status is redundant, and REPLACE on a timestamp name cancelled
            // uploads that landed in the same millisecond.
            val uniqueWorkName = "sms_status_${smsDTO.smsId ?: System.currentTimeMillis()}_${smsDTO.status}"
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.KEEP, workRequest)

            Log.d(TAG, "Work enqueued for SMS status update - ID: ${smsDTO.smsId}")
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
        smsDTO.reportAttempt = runAttemptCount + 1

        // Same body as the HTTP call; HTTP is the fallback when not connected
        // or when the broker does not acknowledge in time
        if (Uplink.overMqtt(MqttClientManager, MqttTopics.UP_SMS_STATUS, Gson().toJson(smsDTO))) {
            Log.d(TAG, "Reported over MQTT - ID: ${smsDTO.smsId}")
            DeviceLog.log(applicationContext, "status_uploaded", "${smsDTO.status}, attempt ${smsDTO.reportAttempt}, mqtt", smsDTO.smsId)
            return Result.success()
        }

        return try {
            val response = ApiManager.getApiService().updateSMSStatus(deviceId, apiKey, smsDTO).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "SMS status updated successfully - ID: ${smsDTO.smsId}, Status: ${smsDTO.status}")
                DeviceLog.log(applicationContext, "status_uploaded", "${smsDTO.status}, attempt ${smsDTO.reportAttempt}", smsDTO.smsId)
                Result.success()
            } else {
                Log.e(TAG, "Failed to update SMS status. Response code: ${response.code()}")
                DeviceLog.log(applicationContext, "status_upload_retry", "http ${response.code()}, attempt ${smsDTO.reportAttempt}", smsDTO.smsId)
                retryOrFail(response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "API call failed: ${e.message}")
            DeviceLog.log(applicationContext, "status_upload_retry", "network, attempt ${smsDTO.reportAttempt}", smsDTO.smsId)
            retryOrFail(null)
        }
    }

    private fun retryOrFail(responseCode: Int?): Result {
        if (WorkerRetryPolicy.shouldRetry(responseCode, runAttemptCount)) return Result.retry()
        Log.e(TAG, "Giving up on SMS status update after ${runAttemptCount + 1} attempts")
        return Result.failure()
    }
}
