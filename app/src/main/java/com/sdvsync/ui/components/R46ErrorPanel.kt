package com.sdvsync.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sdvsync.ui.policy.R44BStateKind
import com.sdvsync.ui.policy.R46UiErrorArea
import com.sdvsync.ui.policy.R46UiErrorPolicy

/** R46: visual error copy and explicit retry; never auto-retries or exposes raw paths. */
@Composable
fun R46ErrorPanel(area: R46UiErrorArea, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val copy = R46UiErrorPolicy.forArea(area)
    R44BStatePanel(
        kind = R44BStateKind.NEEDS_ATTENTION,
        title = copy.title,
        description = copy.description,
        supportingText = copy.nextStep,
        actionLabel = if (onRetry != null) copy.retryLabel else null,
        onAction = onRetry,
        modifier = modifier
    )
}
