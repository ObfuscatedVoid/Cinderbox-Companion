package com.sdvsync.ui.viewmodels

import androidx.annotation.StringRes
import com.sdvsync.R
import com.sdvsync.mods.api.NexusApiException
import com.sdvsync.mods.api.NexusApiFailure
import java.io.IOException

enum class NexusErrorAction {
    RETRY,
    REPLACE_API_KEY,
    NONE
}

data class NexusUiError(@StringRes val messageRes: Int, val action: NexusErrorAction)

internal fun classifyNexusError(error: Throwable, @StringRes fallbackMessageRes: Int): NexusUiError = when (error) {
    is NexusApiException -> when (error.failure) {
        NexusApiFailure.API_KEY_REQUIRED,
        NexusApiFailure.AUTHENTICATION -> NexusUiError(
            R.string.mods_error_api_key_rejected,
            NexusErrorAction.REPLACE_API_KEY
        )

        NexusApiFailure.RATE_LIMITED -> NexusUiError(
            R.string.mods_error_rate_limited,
            NexusErrorAction.NONE
        )

        NexusApiFailure.SERVER -> NexusUiError(
            R.string.mods_error_service_unavailable,
            NexusErrorAction.RETRY
        )

        NexusApiFailure.GRAPHQL,
        NexusApiFailure.INVALID_RESPONSE -> NexusUiError(
            R.string.mods_error_incompatible_response,
            NexusErrorAction.NONE
        )

        NexusApiFailure.HTTP -> NexusUiError(
            R.string.mods_error_service_unavailable,
            NexusErrorAction.NONE
        )
    }

    is IOException -> NexusUiError(R.string.mods_error_network, NexusErrorAction.RETRY)
    else -> NexusUiError(fallbackMessageRes, NexusErrorAction.RETRY)
}
