package com.vernu.sms.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vernu.sms.AppConstants
import com.vernu.sms.BuildConfig
import com.vernu.sms.R

// One notification per newer release on this build's GitHub repository
// (see ReleaseChecker). Tapping opens the release's APK.
object UpdateNotifier {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 7392

    fun shouldNotify(enabled: Boolean, latestCode: Int, installedCode: Int, lastNotifiedCode: Int): Boolean =
        enabled && latestCode > installedCode && latestCode != lastNotifiedCode

    fun maybeNotify(context: Context) {
        val release = ReleaseChecker.latest(context) ?: return
        val latest = release.rank
        val installed = ReleaseChecker.rank(BuildConfig.VERSION_NAME) ?: return
        val lastNotified = SharedPreferenceHelper.getSharedPreferenceInt(
            context, AppConstants.SHARED_PREFS_LAST_UPDATE_NOTIFIED_VERSION_CODE_KEY, 0
        )
        if (!shouldNotify(true, latest, installed, lastNotified)) return
        if (!DeviceHealth.evaluate(context).hasPostNotificationsPermission) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW)
            )
        }
        // A blocked channel shows nothing; do not record the release as notified
        val blocked = !NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE)
        if (blocked) return

        val open = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val versionName = release.versionName
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bee)
            .setContentTitle("textbeeqtt $versionName is available")
            .setContentText("This update improves message sending in the background. Tap to download.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        SharedPreferenceHelper.setSharedPreferenceInt(
            context, AppConstants.SHARED_PREFS_LAST_UPDATE_NOTIFIED_VERSION_CODE_KEY, latest
        )
        DeviceLog.log(context, "update_notified", "version $versionName ($latest)")
    }
}
