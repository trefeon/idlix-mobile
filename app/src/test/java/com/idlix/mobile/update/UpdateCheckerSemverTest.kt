package com.idlix.mobile.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: "update offered while on latest" (v1.0.2 loop). The old
 * trailing-int-vs-versionCode scheme broke whenever release tags outran the
 * installed versionCode; semver-vs-versionName decides now.
 */
class UpdateCheckerSemverTest {

    @Test
    fun parseSemverHandlesTags() {
        assertEquals(listOf(1, 0, 3), UpdateChecker.parseSemver("v1.0.3"))
        assertEquals(listOf(1, 0, 3), UpdateChecker.parseSemver("1.0.3"))
        assertEquals(listOf(2, 0, 0), UpdateChecker.parseSemver("v2"))
        assertEquals(listOf(1, 0, 0), UpdateChecker.parseSemver("1.0"))
        assertEquals(listOf(1, 0, 3), UpdateChecker.parseSemver("1.0.3-beta"))
        assertEquals(null, UpdateChecker.parseSemver("release-abc"))
        assertEquals(null, UpdateChecker.parseSemver("  "))
        assertEquals(null, UpdateChecker.parseSemver(""))
    }

    @Test
    fun compareSemverOrdersReleases() {
        assertTrue(UpdateChecker.compareSemver(listOf(1, 0, 3), listOf(1, 0, 3)) == 0)
        assertTrue(UpdateChecker.compareSemver(listOf(1, 0, 3), listOf(1, 0, 2)) > 0)
        assertTrue(UpdateChecker.compareSemver(listOf(1, 0, 0), listOf(1, 0, 2)) < 0)
        assertTrue(UpdateChecker.compareSemver(listOf(2, 0, 0), listOf(1, 9, 9)) > 0)
        assertTrue(UpdateChecker.compareSemver(listOf(1, 0), listOf(1, 0, 1)) < 0)
    }

    @Test
    fun installedLatestIsUpToDate() {
        // The exact reported loop: installed 1.0.3 against tag v1.0.3.
        val tag = UpdateChecker.parseSemver("v1.0.3")!!
        val app = UpdateChecker.parseSemver("1.0.3")!!
        assertTrue(UpdateChecker.compareSemver(app, tag) >= 0)
    }

    // Rate-limit quota saving: pure, network-free. No live GitHub fetch.

    @Test
    fun parseRateLimitResetHandlesHeaders() {
        assertEquals(1767225600L, UpdateChecker.parseRateLimitReset("1767225600"))
        assertEquals(1767225600L, UpdateChecker.parseRateLimitReset("  1767225600  "))
        assertEquals(0L, UpdateChecker.parseRateLimitReset(null))
        assertEquals(0L, UpdateChecker.parseRateLimitReset(""))
        assertEquals(0L, UpdateChecker.parseRateLimitReset("garbage"))
        assertEquals(0L, UpdateChecker.parseRateLimitReset("-5"))
        assertEquals(0L, UpdateChecker.parseRateLimitReset("0"))
    }

    @Test
    fun rateLimitWindowActiveOnlyWhenFuture() {
        assertTrue(UpdateChecker.isRateLimitWindowActive(1000L, 2000L))
        assertTrue(!UpdateChecker.isRateLimitWindowActive(2000L, 2000L))
        assertTrue(!UpdateChecker.isRateLimitWindowActive(3000L, 2000L))
        assertTrue(!UpdateChecker.isRateLimitWindowActive(1000L, 0L))
    }

    @Test
    fun rateLimitedBodyMatchesGitHubWording() {
        assertTrue(
            UpdateChecker.isRateLimitedBody(
                "{\"message\":\"API rate limit exceeded for 1.2.3.4.\"}"
            )
        )
        assertTrue(
            UpdateChecker.isRateLimitedBody(
                "{\"message\":\"You have exceeded a secondary rate limit.\"}"
            )
        )
        assertTrue(!UpdateChecker.isRateLimitedBody("{\"message\":\"Not Found\"}"))
        assertTrue(!UpdateChecker.isRateLimitedBody(""))
    }

    @Test
    fun retryTimeFormatsAsHourMinute() {
        val formatted = UpdateChecker.formatRetryTime(1767225600L)
        assertTrue("retry time must read HH:MM, got: $formatted", formatted.contains(":"))
    }

    @Test
    fun selectApkAssetPrefersApk() {
        val checksum = UpdateChecker.ReleaseAsset("https://x/app.zip", 10L)
        val apk = UpdateChecker.ReleaseAsset("https://x/app-release.apk", 20L)
        assertEquals(apk, UpdateChecker.selectApkAsset(listOf(checksum, apk)))
        assertEquals(checksum, UpdateChecker.selectApkAsset(listOf(checksum)))
        assertEquals(null, UpdateChecker.selectApkAsset(emptyList()))
    }

    @Test
    fun decideReleaseMatrix() {
        val apk = UpdateChecker.ReleaseAsset("https://x/app-release.apk", 42L)
        // Newer tag -> Available with trimmed version.
        val newer = UpdateChecker.decideRelease("v1.0.4", "notes", listOf(apk), "1.0.3", 3L)
        assertTrue(newer is UpdateChecker.ReleaseDecision.Available)
        assertEquals("1.0.4", (newer as UpdateChecker.ReleaseDecision.Available).version)
        assertEquals("https://x/app-release.apk", newer.assetUrl)
        // Equal tag -> UpToDate (the v1.0.2 loop regression).
        assertTrue(
            UpdateChecker.decideRelease("v1.0.3", "", listOf(apk), "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Newer tag but no asset -> NoAsset.
        assertTrue(
            UpdateChecker.decideRelease("v1.0.4", "", emptyList(), "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.NoAsset
        )
        // Blank tag -> BadData.
        assertTrue(
            UpdateChecker.decideRelease("v", "", listOf(apk), "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.BadData
        )
        // Non-semver tag falls back to trailing int.
        assertTrue(
            UpdateChecker.decideRelease("release-2", "", listOf(apk), "release-abc", 5L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
    }

    @Test
    fun decideCachedReleaseMatrix() {
        // Newer cached tag + asset -> Available (304 still offers download).
        val hit = UpdateChecker.decideCachedRelease(
            "v1.0.4", "notes", "https://x/app-release.apk", 42L, "1.0.3", 3L
        )
        assertTrue(hit is UpdateChecker.ReleaseDecision.Available)
        // Equal cached tag -> UpToDate.
        assertTrue(
            UpdateChecker.decideCachedRelease(
                "v1.0.3", "", "https://x/app-release.apk", 42L, "1.0.3", 3L
            ) is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Newer tag but lost asset URL -> UpToDate (nothing to download).
        assertTrue(
            UpdateChecker.decideCachedRelease("v1.0.4", "", "", 0L, "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Empty cache -> UpToDate.
        assertTrue(
            UpdateChecker.decideCachedRelease("", "", "", 0L, "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
    }
}
