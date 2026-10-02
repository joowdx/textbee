package com.vernu.sms.mqtt

import android.content.Context
import android.util.Log
import com.vernu.sms.ApiManager
import com.vernu.sms.AppConstants
import com.vernu.sms.helpers.SharedPreferenceHelper

data class MqttCredentials(
    val url: String,
    val username: String,
    val password: String,
    val topicPrefix: String,
)

// Kept with the API key in shared preferences. The server rotates them on
// every fetch, so they are fetched only when missing or refused.
object MqttCredentialStore {
    private const val TAG = "MqttCredentialStore"

    fun load(context: Context): MqttCredentials? {
        fun get(key: String) = SharedPreferenceHelper.getSharedPreferenceString(context, key, null)
        val url = get(AppConstants.SHARED_PREFS_MQTT_URL_KEY)
        val username = get(AppConstants.SHARED_PREFS_MQTT_USERNAME_KEY)
        val password = get(AppConstants.SHARED_PREFS_MQTT_PASSWORD_KEY)
        val prefix = get(AppConstants.SHARED_PREFS_MQTT_TOPIC_PREFIX_KEY)
        if (url.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty() || prefix.isNullOrEmpty()) return null
        return MqttCredentials(url, username, password, prefix)
    }

    fun save(context: Context, c: MqttCredentials) {
        SharedPreferenceHelper.setSharedPreferenceString(context, AppConstants.SHARED_PREFS_MQTT_URL_KEY, c.url)
        SharedPreferenceHelper.setSharedPreferenceString(context, AppConstants.SHARED_PREFS_MQTT_USERNAME_KEY, c.username)
        SharedPreferenceHelper.setSharedPreferenceString(context, AppConstants.SHARED_PREFS_MQTT_PASSWORD_KEY, c.password)
        SharedPreferenceHelper.setSharedPreferenceString(context, AppConstants.SHARED_PREFS_MQTT_TOPIC_PREFIX_KEY, c.topicPrefix)
    }

    fun clear(context: Context) {
        SharedPreferenceHelper.clearSharedPreference(context, AppConstants.SHARED_PREFS_MQTT_URL_KEY)
        SharedPreferenceHelper.clearSharedPreference(context, AppConstants.SHARED_PREFS_MQTT_USERNAME_KEY)
        SharedPreferenceHelper.clearSharedPreference(context, AppConstants.SHARED_PREFS_MQTT_PASSWORD_KEY)
        SharedPreferenceHelper.clearSharedPreference(context, AppConstants.SHARED_PREFS_MQTT_TOPIC_PREFIX_KEY)
    }

    /** Blocking. Returns null on any failure; the caller retries with backoff. */
    fun fetch(context: Context, deviceId: String, apiKey: String): MqttCredentials? {
        return try {
            val response = ApiManager.getApiService().getMqttCredentials(deviceId, apiKey).execute()
            val data = response.body()?.data
            if (!response.isSuccessful || data == null) {
                Log.e(TAG, "Fetching MQTT credentials failed. Response code: ${response.code()}")
                return null
            }
            val url = data.url ?: return null
            val username = data.username ?: return null
            val password = data.password ?: return null
            val prefix = data.topicPrefix?.takeIf { it.isNotEmpty() } ?: MqttTopics.defaultPrefix(deviceId)
            MqttCredentials(url, username, password, prefix).also { save(context, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Fetching MQTT credentials failed: ${e.message}")
            null
        }
    }
}
