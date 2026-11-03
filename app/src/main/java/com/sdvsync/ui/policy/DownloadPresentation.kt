package com.sdvsync.ui.policy

import com.sdvsync.local.M23CinderboxFirstPolicy

/** Human-readable presentation of read-only status; no safety policy is bypassed. */
object DownloadPresentation {
    fun destinationStatus(code: M23CinderboxFirstPolicy.Code): String = when (code) {
        M23CinderboxFirstPolicy.Code.EMPTY_SEPARATE_DESTINATION -> "Download folder is available"
        M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE ->
            "This folder belongs to Cinderbox. Updating it safely is not supported yet."
        M23CinderboxFirstPolicy.Code.EXISTING_OR_PARTIAL_SELECTED_DESTINATION ->
            "Existing files were found. Only a matching interrupted download can resume."
        M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE ->
            "The download folder cannot be accessed. Check storage permissions."
        M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION ->
            "This folder cannot be used safely. No files were changed."
    }
}
