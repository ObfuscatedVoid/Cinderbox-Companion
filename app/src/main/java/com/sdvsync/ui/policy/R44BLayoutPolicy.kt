package com.sdvsync.ui.policy

/** Layout decision from available screen width (dp); no device identifiers or file access. */
object R44BLayoutPolicy {
    enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

    fun classify(availableWidthDp: Int): WidthClass = when {
        availableWidthDp < 600 -> WidthClass.COMPACT
        availableWidthDp < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }

    /** A readable single-column content measure for large fonts and landscape layouts. */
    fun maxContentWidthDp(availableWidthDp: Int): Int = when (classify(availableWidthDp)) {
        WidthClass.COMPACT -> 560
        WidthClass.MEDIUM -> 680
        WidthClass.EXPANDED -> 840
    }
}

/** Presentation only. Never authorizes retry, repair, download or other filesystem writes. */
enum class R44BStateKind {
    LOADING, EMPTY, NEEDS_ATTENTION, COMPLETED, INFO;

    companion object {
        fun fromOperationStatus(status: String): R44BStateKind = when (status) {
            "ACTIVE" -> LOADING
            "ACTION_REQUIRED" -> NEEDS_ATTENTION
            "COMPLETED" -> COMPLETED
            else -> INFO
        }
    }
}
