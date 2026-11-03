package com.sdvsync.ui.policy

/** Visual severity only; never used for installing, updating or recovering files. */
enum class R44StatusTone(val label: String) {
    AVAILABLE("Available for checks"),
    SETUP("Setup required"),
    BLOCKED("Access blocked"),
    PROTECTED("Existing game protected"),
    REVIEW("Review required");

    companion object {
        fun fromDestination(kind: R43DDestinationPresentation.Kind): R44StatusTone =
            when (kind) {
                R43DDestinationPresentation.Kind.AVAILABLE_FOR_CHECKS -> AVAILABLE
                R43DDestinationPresentation.Kind.SETUP_REQUIRED -> SETUP
                R43DDestinationPresentation.Kind.ACCESS_BLOCKED -> BLOCKED
                R43DDestinationPresentation.Kind.EXISTING_PROTECTED -> PROTECTED
                R43DDestinationPresentation.Kind.MANUAL_REVIEW -> REVIEW
            }
    }
}
