package com.sdvsync.ui.policy

import android.content.Context
import com.sdvsync.BuildConfig

/** Presentation-only gates. Never use these as authorization for filesystem writes. */
object DeveloperFeaturePolicy {
    private const val PREFERENCES = "hidden_developer_features"
    private const val DIAGNOSTICS_UNLOCKED = "steam_diagnostics_enabled"

    fun isUnlocked(context: Context): Boolean = context.getSharedPreferences(
        PREFERENCES, Context.MODE_PRIVATE
    ).getBoolean(DIAGNOSTICS_UNLOCKED, false)

    /** Release users must deliberately unlock diagnostics in About first. */
    fun canOpenDiagnostics(unlocked: Boolean): Boolean = unlocked

    fun canOpenDiagnostics(context: Context): Boolean = canOpenDiagnostics(isUnlocked(context))

    /** Alternative destinations are for instrumented debug builds only. */
    fun canChooseAlternativeDownloadPath(debugBuild: Boolean, unlocked: Boolean): Boolean =
        debugBuild && unlocked

    fun canChooseAlternativeDownloadPath(context: Context): Boolean =
        canChooseAlternativeDownloadPath(BuildConfig.DEBUG, isUnlocked(context))
}
