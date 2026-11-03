package com.sdvsync.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class R44BLayoutPolicyTest {
    @Test fun compactThresholds() {
        assertEquals(R44BLayoutPolicy.WidthClass.COMPACT, R44BLayoutPolicy.classify(-1))
        assertEquals(R44BLayoutPolicy.WidthClass.COMPACT, R44BLayoutPolicy.classify(0))
        assertEquals(R44BLayoutPolicy.WidthClass.COMPACT, R44BLayoutPolicy.classify(599))
        assertEquals(560, R44BLayoutPolicy.maxContentWidthDp(360))
    }

    @Test fun mediumAndExpandedThresholds() {
        assertEquals(R44BLayoutPolicy.WidthClass.MEDIUM, R44BLayoutPolicy.classify(600))
        assertEquals(R44BLayoutPolicy.WidthClass.MEDIUM, R44BLayoutPolicy.classify(839))
        assertEquals(R44BLayoutPolicy.WidthClass.EXPANDED, R44BLayoutPolicy.classify(840))
        assertEquals(680, R44BLayoutPolicy.maxContentWidthDp(600))
        assertEquals(840, R44BLayoutPolicy.maxContentWidthDp(900))
    }

    @Test fun operationStatusNeverGrantsActions() {
        assertEquals(R44BStateKind.LOADING, R44BStateKind.fromOperationStatus("ACTIVE"))
        assertEquals(R44BStateKind.NEEDS_ATTENTION,
            R44BStateKind.fromOperationStatus("ACTION_REQUIRED"))
        assertEquals(R44BStateKind.COMPLETED, R44BStateKind.fromOperationStatus("COMPLETED"))
        assertEquals(R44BStateKind.INFO, R44BStateKind.fromOperationStatus("UNKNOWN"))
    }
}
