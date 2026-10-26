package com.idlix.mobile.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: "update offered while on latest" (v1.0.2 loop). The old
 * trailing-int-vs-versionCode scheme broke whenever release tags outran the
 * installed versionCode; semver-vs-versionName decides now.
 *
 * The no-API redirect matrix (newer/equal/malformed/offline redirect ->
 * Available/UpToDate/Failed) runs offline against UpdateChecker.kt's pure
 * helpers; no live GitHub fetch is performed here.
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

    // No-API redirect flow: pure, network-free. No live GitHub fetch.

    @Test
    fun isRedirectAcceptsRedirectFamily() {
        assertTrue(UpdateChecker.isRedirect(301))
        assertTrue(UpdateChecker.isRedirect(302))
        assertTrue(UpdateChecker.isRedirect(303))
        assertTrue(UpdateChecker.isRedirect(307))
        assertTrue(UpdateChecker.isRedirect(308))
        assertTrue(!UpdateChecker.isRedirect(200))
        assertTrue(!UpdateChecker.isRedirect(404))
        assertTrue(!UpdateChecker.isRedirect(500))
    }

    @Test
    fun parseTagFromLocationHandlesForms() {
        assertEquals(
            "v1.0.5",
            UpdateChecker.parseTagFromLocation("/trefeon/idlix-mobile/releases/tag/v1.0.5")
        )
        assertEquals(
            "v1.0.5",
            UpdateChecker.parseTagFromLocation("https://github.com/trefeon/idlix-mobile/releases/tag/v1.0.5")
        )
        assertEquals(
            "v1.0.5",
            UpdateChecker.parseTagFromLocation("https://github.com/trefeon/idlix-mobile/releases/tag/v1.0.5/")
        )
        assertEquals(
            "v1.0.5",
            UpdateChecker.parseTagFromLocation("/trefeon/idlix-mobile/releases/tag/v1.0.5?foo=bar")
        )
        assertEquals(null, UpdateChecker.parseTagFromLocation(null))
        assertEquals(null, UpdateChecker.parseTagFromLocation(""))
        assertEquals(null, UpdateChecker.parseTagFromLocation("   "))
        // Unexpected redirect targets (login wall, tag list) carry no tag.
        assertEquals(null, UpdateChecker.parseTagFromLocation("https://github.com/login"))
        assertEquals(null, UpdateChecker.parseTagFromLocation("/trefeon/idlix-mobile/releases"))
        assertEquals(null, UpdateChecker.parseTagFromLocation("/trefeon/idlix-mobile/releases/tag/"))
    }

    @Test
    fun assetUrlForTagIsDeterministic() {
        assertEquals(
            "https://github.com/trefeon/idlix-mobile/releases/download/v1.0.5/idlix-mobile-v1.0.5.apk",
            UpdateChecker.assetUrlForTag("v1.0.5")
        )
    }

    @Test
    fun parseContentLengthHandlesHeaders() {
        assertEquals(42L, UpdateChecker.parseContentLength("42"))
        assertEquals(42L, UpdateChecker.parseContentLength("  42  "))
        assertEquals(0L, UpdateChecker.parseContentLength(null))
        assertEquals(0L, UpdateChecker.parseContentLength(""))
        assertEquals(0L, UpdateChecker.parseContentLength("garbage"))
        assertEquals(0L, UpdateChecker.parseContentLength("0"))
        assertEquals(0L, UpdateChecker.parseContentLength("-5"))
    }

    @Test
    fun resolveAssetSizeMatrix() {
        // HEAD served: size from Content-Length, 0 when absent.
        assertEquals(42L, UpdateChecker.resolveAssetSize(200, "42", null, null))
        assertEquals(0L, UpdateChecker.resolveAssetSize(200, null, null, null))
        assertEquals(0L, UpdateChecker.resolveAssetSize(200, "garbage", null, null))
        // HEAD blocked (405/403) -> range GET decides.
        assertEquals(99L, UpdateChecker.resolveAssetSize(405, null, 206, "99"))
        assertEquals(7L, UpdateChecker.resolveAssetSize(403, null, 200, "7"))
        assertEquals(0L, UpdateChecker.resolveAssetSize(405, null, 206, null))
        // Both fail -> APK missing.
        assertEquals(null, UpdateChecker.resolveAssetSize(404, null, 404, null))
        assertEquals(null, UpdateChecker.resolveAssetSize(500, null, null, null))
    }

    @Test
    fun decideReleaseMatrix() {
        val url = UpdateChecker.assetUrlForTag("v1.0.4")
        // Newer tag -> Available with trimmed version.
        val newer = UpdateChecker.decideRelease("v1.0.4", "1.0.3", 3L, url, 42L)
        assertTrue(newer is UpdateChecker.ReleaseDecision.Available)
        newer as UpdateChecker.ReleaseDecision.Available
        assertEquals("1.0.4", newer.version)
        assertEquals(url, newer.assetUrl)
        assertEquals(42L, newer.sizeBytes)
        // Unknown size (0) is still offered.
        val unknownSize = UpdateChecker.decideRelease("v1.0.4", "1.0.3", 3L, url, 0L)
        assertTrue(unknownSize is UpdateChecker.ReleaseDecision.Available)
        assertEquals(0L, (unknownSize as UpdateChecker.ReleaseDecision.Available).sizeBytes)
        // Equal tag -> UpToDate (the v1.0.2 loop regression).
        assertTrue(
            UpdateChecker.decideRelease(
                "v1.0.3", "1.0.3", 3L, UpdateChecker.assetUrlForTag("v1.0.3"), 1L
            ) is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Older tag -> UpToDate.
        assertTrue(
            UpdateChecker.decideRelease(
                "v1.0.2", "1.0.3", 3L, UpdateChecker.assetUrlForTag("v1.0.2"), 1L
            ) is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Newer tag but blank asset URL -> NoAsset.
        assertTrue(
            UpdateChecker.decideRelease("v1.0.4", "1.0.3", 3L, "", 0L)
                is UpdateChecker.ReleaseDecision.NoAsset
        )
        // Blank tag -> BadData.
        assertTrue(
            UpdateChecker.decideRelease("v", "1.0.3", 3L, url, 1L)
                is UpdateChecker.ReleaseDecision.BadData
        )
        // Non-semver tag falls back to trailing int.
        assertTrue(
            UpdateChecker.decideRelease("release-2", "release-abc", 5L, url, 1L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Non-semver tag with no trailing int falls back to versionName equality.
        assertTrue(
            UpdateChecker.decideRelease("release-abc", "release-abc", 0L, url, 1L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
        assertTrue(
            UpdateChecker.decideRelease("release-xyz", "release-abc", 0L, url, 1L)
                is UpdateChecker.ReleaseDecision.Available
        )
    }
}
