package com.sdvsync.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sdvsync.local.InstallAssessment
import com.sdvsync.local.InstallHealth
import com.sdvsync.local.InstallationScanner
import com.sdvsync.local.InstallationSnapshot
import com.sdvsync.local.M23InstallationDiscovery
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.sdvsync.cinderbox.CinderboxPaths
import com.sdvsync.download.GameRepairPreview
import com.sdvsync.download.VerifiedGameRepair
import kotlinx.coroutines.launch
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.components.R44BResponsiveColumn
import com.sdvsync.ui.components.R44BStatePanel
import com.sdvsync.ui.policy.R44BStateKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Local-first, read-only status. Never moves/replaces game, saves or SMAPI data. */
@Composable
fun InstallationHealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("m4_local_install", android.content.Context.MODE_PRIVATE) }
    var selectedPath by remember { mutableStateOf(prefs.getString("game_dir", null) ?: "/storage/emulated/0/Download/StardewValley") }
    var refresh by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<InstallationSnapshot?>(null) }
    val repairEngine = remember(context) { VerifiedGameRepair(context.filesDir) }
    val repairScope = rememberCoroutineScope()
    var repairTarget by remember { mutableStateOf(
        prefs.getString("m10_repair_target", CinderboxPaths.GAME_FILES_DIR) ?: CinderboxPaths.GAME_FILES_DIR
    ) }
    var repairPreview by remember { mutableStateOf<GameRepairPreview?>(null) }
    var repairBusy by remember { mutableStateOf(false) }
    var repairMessage by remember { mutableStateOf("") }
    LaunchedEffect(selectedPath, repairTarget) { repairPreview = null }
    LaunchedEffect(selectedPath, refresh) {
        report = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val installed = try { context.packageManager.getPackageInfo("com.game.cinderbox", 0); true to true }
            catch (_: PackageManager.NameNotFoundException) { false to true }
            catch (_: SecurityException) { false to false } // Visibility failure does not prove absence.
            InstallationScanner.scan(File(selectedPath), installed.first,
                File(CinderboxPaths.ROOT_DIR), installed.second)
        }
    }
    Scaffold(topBar = { StardewTopAppBar("Installation health", navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        R44BResponsiveColumn(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text("Read-only checks. Detection is not cryptographic integrity verification.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = selectedPath, onValueChange = {
                selectedPath = it
                prefs.edit().putString("game_dir", it).apply()
            }, label = { Text("Selected game folder") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { refresh++ }) { Text("Rescan installations") }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("M10 · Repair existing game from verified Steam download",
                style = MaterialTheme.typography.titleMedium)
            Text("Source: the selected game folder above. It must match a completed Cinderbox " +
                "download integrity record. Repair never downloads or deletes unrelated files.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = repairTarget, onValueChange = {
                repairTarget = it
                prefs.edit().putString("m10_repair_target", it).apply()
            }, label = { Text("Existing installation to repair") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(enabled = !repairBusy, onClick = {
                repairScope.launch {
                    repairBusy = true
                    repairMessage = ""
                    val outcome = withContext(Dispatchers.IO) {
                        runCatching {
                            check(repairEngine.recover()) {
                                "Interrupted repair cannot be safely recovered; inspect files manually"
                            }
                            repairEngine.preview(File(selectedPath), File(repairTarget))
                        }
                    }
                    repairPreview = outcome.getOrNull()
                    repairMessage = outcome.exceptionOrNull()?.message ?: ""
                    repairBusy = false
                }
            }) { Text(if (repairBusy) "Checking files…" else "Preview verified repair") }
            if (repairMessage.isNotBlank()) {
                Text(repairMessage, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            report?.let { result ->
                HealthRow("Selected game directory", result.selectedGame)
                HealthRow("Cinderbox GameFiles", result.cinderboxGame)
                HealthRow("SMAPI", result.smapi)
                HealthRow("Cinderbox app", result.cinderboxApp)
                result.discovery?.let { inventory ->
                    Spacer(Modifier.height(12.dp))
                    Text("Game installation details", style = MaterialTheme.typography.titleMedium)
                    Text("No game, Steam, or SMAPI version is verified by filename detection. " +
                        "SAF, Root and Shizuku access require their own authorized provider checks.",
                        style = MaterialTheme.typography.bodySmall)
                    DiscoveryRow("Selected directory", inventory.selected)
                    DiscoveryRow("Cinderbox 0.8+", inventory.modern)
                    DiscoveryRow("Legacy pre-0.8", inventory.legacy)
                    DiscoveryRow("SMAPI internals", inventory.smapi)
                    if (inventory.layoutConflict) {
                        Text("Conflicting legacy and current layouts; automatic merging is forbidden.",
                            color = MaterialTheme.colorScheme.error)
                    }
                    Text("Access: ${inventory.storageAccess.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Download/install permission: NOT GRANTED by discovery. " +
                        "Use the existing explicit operation preflight and matching recovery checkpoint.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Destination access: Not tested for writes",
                            style = MaterialTheme.typography.titleSmall)
                        Text(result.storage.details, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } ?: R44BStatePanel(
                kind = R44BStateKind.LOADING,
                title = "Checking installation",
                description = "Reading local installation details without changing game files."
            )
            Spacer(Modifier.height(10.dp))
            Text("Missing, inaccessible and incomplete are different states. Cinderbox will not overwrite an existing game automatically.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    repairPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!repairBusy) repairPreview = null },
            title = { Text("Review selective game repair") },
            text = {
                Column {
                    Text("Source: ${preview.source}")
                    Text("Target: ${preview.destination}")
                    Text("Changes: ${preview.changes.size} · Unchanged: ${preview.unchangedFiles} · " +
                        "Target-only files preserved: ${preview.preservedFiles}")
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(preview.changes) { change ->
                            Text("${change.kind}: ${change.path} (${change.bytes} bytes)",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Every changed file is backed up before replacement. Changes to either " +
                        "directory invalidate this approval. A crash triggers safe rollback on rescan.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !repairBusy && preview.changes.isNotEmpty(), onClick = {
                    repairScope.launch {
                        repairBusy = true
                        repairMessage = ""
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching { repairEngine.apply(preview) }
                        }
                        repairMessage = outcome.fold(
                            onSuccess = { "Repaired $it file(s); other game files preserved." },
                            onFailure = { "Repair stopped: ${it.message ?: "manual review required"}" }
                        )
                        repairPreview = null
                        repairBusy = false
                        refresh++
                    }
                }) { Text("Approve repair") }
            },
            dismissButton = {
                TextButton(enabled = !repairBusy, onClick = { repairPreview = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HealthRow(title: String, result: InstallAssessment) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("$title: ${result.health.name.replace('_', ' ')}", style = MaterialTheme.typography.titleSmall)
            Text(result.details, style = MaterialTheme.typography.bodySmall)
            Text(result.location, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DiscoveryRow(title: String, finding: M23InstallationDiscovery.Finding) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("$title: ${finding.status.name.replace('_', ' ')}",
                style = MaterialTheme.typography.titleSmall)
            Text("Evidence: ${finding.evidence.joinToString { it.name.replace('_', ' ') }}",
                style = MaterialTheme.typography.bodySmall)
            Text("Reasons: ${finding.codes.joinToString()}", style = MaterialTheme.typography.bodySmall)
            Text("Game/SMAPI version: not verified; observed entries: ${finding.observedFiles}",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
