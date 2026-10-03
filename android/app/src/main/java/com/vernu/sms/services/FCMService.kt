package com.vernu.sms.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vernu.sms.ApiManager
import com.vernu.sms.AppConstants
import com.vernu.sms.R
import com.vernu.sms.ui.splash.SplashActivity
import com.vernu.sms.dtos.RegisterDeviceInputDTO
import com.vernu.sms.dtos.RegisterDeviceResponseDTO
import com.vernu.sms.helpers.DeviceLog
import com.vernu.sms.helpers.SharedPreferenceHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FCMService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FirebaseMessagingService"
        private const val DEFAULT_NOTIFICATION_CHANNEL_ID = "N1"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, remoteMessage.data.toString())
        SmsCommandHandler.handle(this, remoteMessage.data, SmsCommandHandler.SOURCE_PUSH)
    }

    override fun onNewToken(token: String) {
        DeviceLog.log(this, "token_refreshed")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val deviceId = SharedPreferenceHelper.getSharedPreferenceString(
            this, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, ""
        ) ?: ""
        val apiKey = SharedPreferenceHelper.getSharedPreferenceString(
            this, AppConstants.SHARED_PREFS_API_KEY_KEY, ""
        ) ?: ""

        if (deviceId.isEmpty() || apiKey.isEmpty()) {
            Log.d(TAG, "Device ID or API key not available, skipping FCM token update")
            return
        }

        val updateInput = RegisterDeviceInputDTO().apply { fcmToken = token }
        Log.d(TAG, "Updating FCM token for device: $deviceId")

        ApiManager.getApiService()
            .updateDevice(deviceId, apiKey, updateInput)
            .enqueue(object : Callback<RegisterDeviceResponseDTO> {
                override fun onResponse(
                    call: Call<RegisterDeviceResponseDTO>,
                    response: Response<RegisterDeviceResponseDTO>
                ) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "FCM token updated successfully")
                    } else {
                        Log.e(TAG, "Failed to update FCM token. Response code: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<RegisterDeviceResponseDTO>, t: Throwable) {
                    Log.e(TAG, "Error updating FCM token: ${t.message}")
                }
            })
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, DEFAULT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bee)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    DEFAULT_NOTIFICATION_CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
