package com.vernu.sms.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCheckerTest {
    @Test
    fun ranksForkTagsAboveTheirUpstreamVersion() {
        assertEquals(2_090_004, ReleaseChecker.rank("v2.9.0-fork.4"))
        assertEquals(2_090_000, ReleaseChecker.rank("2.9.0"))
        assertTrue(ReleaseChecker.rank("v2.9.0-fork.1")!! > ReleaseChecker.rank("2.9.0")!!)
        assertTrue(ReleaseChecker.rank("v2.10.0-fork.1")!! > ReleaseChecker.rank("v2.9.0-fork.12")!!)
    }

    @Test
    fun ignoresTagsItDoesNotUnderstand() {
        assertNull(ReleaseChecker.rank("latest"))
        assertNull(ReleaseChecker.rank("v2.9.0-beta.1"))
        assertNull(ReleaseChecker.rank("fw-v1.0.0"))
    }

    @Test
    fun prefersTheApkAssetOverTheReleasePage() {
        val release = ReleaseChecker.parse(
            """
            {"tag_name":"v2.9.0-fork.5","html_url":"https://github.com/o/r/releases/tag/v2.9.0-fork.5",
             "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                       {"name":"textbee-v2.9.0-fork.5.apk","browser_download_url":"https://x/textbee.apk"}]}
            """
        )!!
        assertEquals("2.9.0-fork.5", release.versionName)
        assertEquals(2_090_005, release.rank)
        assertEquals("https://x/textbee.apk", release.downloadUrl)
    }

    @Test
    fun fallsBackToTheReleasePageWithoutAnApk() {
        val release = ReleaseChecker.parse(
            """{"tag_name":"v2.9.0-fork.5","html_url":"https://github.com/o/r/releases/tag/v2.9.0-fork.5","assets":[]}"""
        )!!
        assertEquals("https://github.com/o/r/releases/tag/v2.9.0-fork.5", release.downloadUrl)
    }

    @Test
    fun returnsNothingForAnUnusableReply() {
        assertNull(ReleaseChecker.parse("""{"message":"Not Found"}"""))
        assertNull(ReleaseChecker.parse("not json"))
    }

    @Test
    fun comparesAgainstTheInstalledVersion() {
        val release = ReleaseChecker.Release("2.9.0-fork.5", 2_090_005, "https://x/textbee.apk")
        assertTrue(ReleaseChecker.isNewer(release, "2.9.0-fork.4"))
        assertFalse(ReleaseChecker.isNewer(release, "2.9.0-fork.5"))
        assertFalse(ReleaseChecker.isNewer(release, "2.9.0-fork.6"))
        assertFalse(ReleaseChecker.isNewer(release, "unknown-build"))
    }
}
