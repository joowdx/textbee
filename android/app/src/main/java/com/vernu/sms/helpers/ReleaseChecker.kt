package com.vernu.sms.helpers

import android.content.Context
import com.google.gson.JsonParser
import com.vernu.sms.BuildConfig
import com.vernu.sms.Links
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Finds the newest release on this build's GitHub repository (RELEASES_REPO).
 * Releases are tagged `vX.Y.Z-fork.N`; a tag without the fork suffix counts
 * as fork 0, so a local build of the same upstream version is older.
 */
object ReleaseChecker {
    private const val PREFS = "release_checker"
    private const val KEY_CHECKED_AT = "checked_at"
    private const val KEY_RANK = "rank"
    private const val KEY_NAME = "name"
    private const val KEY_URL = "url"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    data class Release(val versionName: String, val rank: Int, val downloadUrl: String)

    private val tagPattern = Regex("""^v?(\d{1,3})\.(\d{1,2})\.(\d{1,2})(?:-fork\.(\d{1,2}))?$""")

    /** Orders versions: 2.9.0-fork.4 → 2090004. Null for anything else. */
    fun rank(versionName: String): Int? {
        val match = tagPattern.matchEntire(versionName.trim()) ?: return null
        val (major, minor, patch, fork) = match.destructured
        return major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + (fork.toIntOrNull() ?: 0)
    }

    /** Reads GitHub's `releases/latest` reply; the APK asset wins over the release page. */
    fun parse(json: String): Release? = try {
        val release = JsonParser.parseString(json).asJsonObject
        val tag = release.get("tag_name")?.asString.orEmpty()
        val rank = rank(tag)
        if (rank == null) {
            null
        } else {
            val apk = release.getAsJsonArray("assets")
                ?.map { it.asJsonObject }
                ?.firstOrNull { it.get("name")?.asString?.endsWith(".apk") == true }
                ?.get("browser_download_url")?.asString
            val page = release.get("html_url")?.asString ?: Links.releases
            Release(tag.removePrefix("v"), rank, apk ?: page)
        }
    } catch (e: Exception) {
        null
    }

    fun isNewer(release: Release, installedVersionName: String = BuildConfig.VERSION_NAME): Boolean =
        release.rank > (rank(installedVersionName) ?: Int.MAX_VALUE)

    /** Asks GitHub now. Blocking; call off the main thread. Null when it cannot tell. */
    fun fetch(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.RELEASES_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()?.let(::parse)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Fetches at most once a day and keeps the answer for [latest]. Blocking. */
    fun checkDaily(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_CHECKED_AT, 0L) < DAY_MS) return
        val release = fetch()
        prefs.edit().putLong(KEY_CHECKED_AT, now).apply()
        if (release != null) remember(context, release)
    }

    fun remember(context: Context, release: Release) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_RANK, release.rank)
            .putString(KEY_NAME, release.versionName)
            .putString(KEY_URL, release.downloadUrl)
            .apply()
    }

    /** The last release seen, if any. */
    fun latest(context: Context): Release? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rank = prefs.getInt(KEY_RANK, 0).takeIf { it > 0 } ?: return null
        return Release(
            prefs.getString(KEY_NAME, null) ?: return null,
            rank,
            prefs.getString(KEY_URL, null) ?: Links.releases
        )
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
