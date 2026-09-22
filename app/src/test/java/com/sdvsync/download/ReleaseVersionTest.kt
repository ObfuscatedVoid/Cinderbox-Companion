package com.sdvsync.download

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumericVersions() {
        assertTrue(isNewerRelease("0.1.1", "0.1.2"))
        assertTrue(isNewerRelease("1.9", "1.10"))
        assertFalse(isNewerRelease("2.0", "1.99"))
    }

    @Test fun stableReleaseReplacesItsPrerelease() {
        assertTrue(isNewerRelease("1.2.0-rc.3", "1.2.0"))
        assertFalse(isNewerRelease("1.2.0", "1.2.0"))
    }

    @Test fun prereleaseCannotReplaceSameStableVersion() {
        assertFalse(isNewerRelease("1.2.0", "1.2.0-beta.4"))
    }

    @Test fun comparesNumericPrereleaseIdentifiersNumerically() {
        assertTrue(isNewerRelease("1.2.0-rc.2", "1.2.0-rc.10"))
        assertFalse(isNewerRelease("1.2.0-rc.10", "1.2.0-rc.2"))
    }

    @Test fun numericPrereleaseIdentifiersPrecedeWords() {
        assertTrue(isNewerRelease("1.2.0-beta.9", "1.2.0-beta.next"))
        assertFalse(isNewerRelease("1.2.0-beta.next", "1.2.0-beta.9"))
        assertTrue(isNewerRelease("1.2.0-beta", "1.2.0-beta.1"))
        assertFalse(isNewerRelease("1.2.0-beta.1", "1.2.0-beta"))
    }

    @Test fun ignoresPrefixesBuildMetadataAndTrailingZeroComponents() {
        assertFalse(isNewerRelease("v1.2+build.1", "1.2.0+build.2"))
        assertFalse(isNewerRelease("1.2.0", "v1.2"))
    }

    @Test fun rejectsMalformedAndMissingVersions() {
        for (version in listOf(
            null, "", "release-100", "1.bad.99", "1..2", "1.2-", "1.0+", "1.0+..",
            "9".repeat(
                201
            )
        )) {
            assertFalse(isNewerRelease("1.0", version))
            assertFalse(isNewerRelease(version, "1.0"))
        }
    }

    @Test fun supportsAdditionalNumericComponentsWithoutOverflow() {
        assertTrue(isNewerRelease("1.6.15", "1.6.15.1"))
        assertTrue(isNewerRelease("1.2147483647", "1.2147483648"))
    }
}
