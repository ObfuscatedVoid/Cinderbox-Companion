package com.sdvsync.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sdvsync.diagnostics.CenterItem
import com.sdvsync.diagnostics.CenterStatus
import com.sdvsync.diagnostics.OperationCenterEvidence
import com.sdvsync.diagnostics.OperationCenterEvidenceReader
import com.sdvsync.diagnostics.OperationCenterPolicy
import com.sdvsync.diagnostics.SyncOperationDiagnostics
import com.sdvsync.download.GameDownloadManager
import com.sdvsync.mods.ModDownloadManager
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.components.R44BResponsiveColumn
import com.sdvsync.ui.components.R44BStatePanel
import com.sdvsync.ui.policy.R44BStateKind
import com.sdvsync.ui.theme.SdvSyncThemeExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koin.compose.koinInject

/** Read-only operation status; no automatic retries, downloads, cleanup or recovery. */
@Composable
fun OperationCenterScreen(
    onBack: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenSyncLog: () -> Unit,
    onOpenTab: (String) -> Unit
) {
    val context = LocalContext.current
    val game: GameDownloadManager = koinInject()
    val mods: ModDownloadManager = koinInject()
    val gameProgress by game.progress.collectAsState()
    val smapiProgress by game.smapiProgress.collectAsState()
    val cinderboxProgress by game.cinderboxProgress.collectAsState()
    val modProgress by mods.progress.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val evidence by produceState(initialValue = OperationCenterEvidence(emptySet(), false), refresh) {
        value = withContext(Dispatchers.IO) { OperationCenterEvidenceReader.read(context) }
    }
    // Do not copy raw errors, file names, save names or paths into these cards or exports.
    val outcomes = SyncOperationDiagnostics.snapshot().map { it.outcome.name }
    val modFraction = if (modProgress.totalBytes > 0L)
        modProgress.downloadedBytes.toDouble() / modProgress.totalBytes.toDouble() else 0.0
    val cards = OperationCenterPolicy.build(
        gameState = gameProgress.state.name,
        modState = modProgress.state.name,
        smapiActive = smapiProgress.isRunning || smapiProgress.isDownloading,
        smapiError = smapiProgress.errorMessage != null,
        appDownloadActive = cinderboxProgress.isDownloading,
        pendingReview = evidence.pendingModReview,
        journals = evidence.journals,
        syncOutcomes = outcomes,
        gamePercent = OperationCenterPolicy.percent(gameProgress.overallPercent),
        modPercent = OperationCenterPolicy.percent(modFraction.toFloat())
    )
    val report = remember(cards) {
        val records = JSONArray()
        cards.forEach { card ->
            records.put(JSONObject().put("id", card.id).put("status", card.status.name)
                .put("progress_percent", card.percent ?: JSONObject.NULL))
        }
        JSONObject().put("schema_version", 1).put("producer", "cinderbox-operation-center")
            .put("privacy", "allowlisted-status-only").put("operations", records).toString(2)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val result = runCatching {
                val stream = checkNotNull(context.contentResolver.openOutputStream(uri))
                stream.use { it.write(report.toByteArray(Charsets.UTF_8)) }
            }
            Toast.makeText(context, if (result.isSuccess) "Status report exported" else "Could not export report",
                Toast.LENGTH_LONG).show()
        }
    }
    Scaffold(topBar = { StardewTopAppBar("Operation center", navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        R44BResponsiveColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text("Live, read-only status and recovery guidance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("No automatic retry or recovery is performed here. Status may be incomplete after Android stops the app.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refresh++ }) { Text("Refresh checks") }
                OutlinedButton(onClick = { export.launch("cinderbox-operation-status.json") }) {
                    Text("Export safe report")
                }
            }
            Spacer(Modifier.height(16.dp))
            cards.forEach { card ->
                CenterStatusCard(card, onClick = {
                    when (card.action) {
                        "Open Downloads" -> onOpenTab("game_download")
                        "Open Mods" -> onOpenTab("mods")
                        "Open Saves" -> onOpenTab("saves")
                        "Open Sync Log" -> onOpenSyncLog()
                        "Open Installation Health" -> onOpenHealth()
                    }
                })
                Spacer(Modifier.height(10.dp))
            }
            Text("Recovery journals are indicators, not proof that rollback succeeded. Inspect the relevant screen before modifying files.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CenterStatusCard(card: CenterItem, onClick: () -> Unit) {
    if (SdvSyncThemeExtras.isEarthy) {
        R44BStatePanel(
            kind = R44BStateKind.fromOperationStatus(card.status.name),
            title = card.title,
            description = card.detail,
            supportingText = card.percent?.let { "$it% reported" },
            actionLabel = card.action,
            onAction = onClick
        )
    } else {
        // Retain Classic's original status card and actions.
        Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Text(when (card.status) {
                CenterStatus.ACTIVE -> "In progress"
                CenterStatus.ACTION_REQUIRED -> "Needs attention"
                CenterStatus.COMPLETED -> "Completed"
                CenterStatus.IDLE -> "Idle"
            }, style = MaterialTheme.typography.labelMedium)
            card.percent?.let { Text("$it% reported", style = MaterialTheme.typography.labelSmall) }
            Spacer(Modifier.height(4.dp))
            Text(card.detail, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClick) { Text(card.action) }
        }
        }
    }
}
