package com.sdvsync.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sdvsync.R
import com.sdvsync.ui.viewmodels.CinderboxMigrationState

@Composable
fun CinderboxMigrationDialog(
    state: CinderboxMigrationState,
    onMove: () -> Unit,
    onLater: () -> Unit,
    onDontAsk: () -> Unit,
    onDismissResult: () -> Unit
) {
    val result = state.result
    val showOutcome = result != null && !state.isMigrating

    Dialog(onDismissRequest = {
        if (state.isMigrating) return@Dialog
        if (showOutcome) onDismissResult() else onLater()
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pixelBorder()
                .padding(8.dp),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.cinderbox_migrate_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (showOutcome) {
                        val messages = mutableListOf<String>()
                        if (result.moved.isNotEmpty()) {
                            messages +=
                                stringResource(R.string.cinderbox_migrate_moved, result.moved.joinToString(", "))
                        }
                        if (result.leftoverConflicts.isNotEmpty()) {
                            messages +=
                                stringResource(
                                    R.string.cinderbox_migrate_conflicts,
                                    result.leftoverConflicts.joinToString("\n")
                                )
                        }
                        if (result.errors.isNotEmpty()) {
                            messages +=
                                stringResource(R.string.cinderbox_migrate_error, result.errors.joinToString("\n"))
                        }
                        messages.joinToString("\n\n").ifBlank { stringResource(R.string.cinderbox_migrate_nothing) }
                    } else {
                        stringResource(R.string.cinderbox_migrate_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (state.isMigrating) {
                    Spacer(Modifier.height(16.dp))
                    PixelLoadingSpinner(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.cinderbox_migrate_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (showOutcome) {
                    Row(modifier = Modifier.align(Alignment.End)) {
                        StardewButton(onClick = onDismissResult) {
                            Text(stringResource(R.string.action_done))
                        }
                        if (result.errors.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            StardewButton(
                                onClick = onMove,
                                variant = StardewButtonVariant.Action
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                } else {
                    TextButton(
                        onClick = onDontAsk,
                        enabled = !state.isMigrating
                    ) {
                        Text(
                            stringResource(R.string.cinderbox_migrate_dont_ask),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.align(Alignment.End)) {
                        StardewOutlinedButton(
                            onClick = onLater,
                            enabled = !state.isMigrating
                        ) {
                            Text(stringResource(R.string.cinderbox_migrate_later))
                        }
                        Spacer(Modifier.width(8.dp))
                        StardewButton(
                            onClick = onMove,
                            variant = StardewButtonVariant.Gold,
                            enabled = !state.isMigrating
                        ) {
                            Text(stringResource(R.string.cinderbox_migrate_move))
                        }
                    }
                }
            }
        }
    }
}
