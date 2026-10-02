package com.vernu.sms

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Every web address the app opens, taken from the build (see build.gradle).
 * The optional ones are empty unless the build sets them, and an empty one
 * hides the row or button that would open it.
 */
object Links {
    val web: String = BuildConfig.WEB_BASE_URL.trimEnd('/')

    /** The web address without its scheme, for display ("textbee.example.com"). */
    val webHost: String = web.substringAfter("://")

    val dashboard: String = "$web/dashboard"
    val account: String = "$web/dashboard/account"
    val repo: String = "https://github.com/${BuildConfig.RELEASES_REPO}"
    val releases: String = "$repo/releases"

    val support: String = BuildConfig.SUPPORT_URL
    val community: String = BuildConfig.COMMUNITY_URL
    val docs: String = BuildConfig.DOCS_URL
    val pricing: String = BuildConfig.PRICING_URL
    val terms: String = BuildConfig.TERMS_URL
    val privacy: String = BuildConfig.PRIVACY_URL

    /** Opens [url] in a browser; returns false when nothing can open it. */
    fun open(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
