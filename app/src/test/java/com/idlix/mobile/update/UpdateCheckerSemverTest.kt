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
}
