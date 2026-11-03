package com.sdvsync.ui.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperFeaturePolicyTest {
    @Test fun diagnosticsRequireExplicitUnlock() {
        assertFalse(DeveloperFeaturePolicy.canOpenDiagnostics(false))
        assertTrue(DeveloperFeaturePolicy.canOpenDiagnostics(true))
    }

    @Test fun releaseCanNeverSelectAlternateDownloadPath() {
        assertFalse(DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(false, false))
        assertFalse(DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(false, true))
        assertFalse(DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(true, false))
        assertTrue(DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(true, true))
    }
}
