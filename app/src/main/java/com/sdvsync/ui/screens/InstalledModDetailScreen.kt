package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.PixelDivider
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewDialog
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.formatBytes
import com.sdvsync.ui.viewmodels.InstalledModDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InstalledModDetailScreen(viewModel: InstalledModDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    LaunchedEffect(state.removed) {
        if (state.removed) onBack()
    }

    val mod = state.mod

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = mod?.manifest?.name ?: stringResource(R.string.mods_title),
                navigationIcon = {
                    PixelIconButton(
                        pixelData = ArrowLeftData,
                        onClick = onBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    ) { padding ->
        if (mod == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("Mod not found")
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Info card
            SectionHeader(stringResource(R.string.mods_info))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        mod.manifest.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.mods_author, mod.manifest.author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    if (mod.manifest.description.isNotBlank()) {
                        Text(
                            mod.manifest.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        stringResource(R.string.mods_version, mod.manifest.version),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.mods_unique_id, mod.manifest.uniqueID),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.mods_folder_size, formatBytes(mod.fileSize)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (mod.installedAt > 0) {
                        Text(
                            stringResource(R.string.mods_installed_on, dateFormat.format(Date(mod.installedAt))),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Update card
            val updateInfo = state.updateInfo
            if (updateInfo != null) {
                Spacer(Modifier.height(16.dp))
                val uriHandler = LocalUriHandler.current
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.mods_update_available),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                R.string.mods_update_available_version,
                                updateInfo.installedVersion,
                                updateInfo.latestVersion
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        updateInfo.updateUrl?.let { url ->
                            Spacer(Modifier.height(8.dp))
                            StardewButton(
                                onClick = { uriHandler.openUri(url) },
                                variant = StardewButtonVariant.Gold
                            ) {
                                Text(stringResource(R.string.mods_view_update))
                            }
                        }
                    }
                }
            }

            // Nexus link
            val nexusModId = state.metadata?.installedFrom
                ?.takeIf { it.startsWith("nexus:") }
                ?.removePrefix("nexus:")
            if (nexusModId != null) {
                Spacer(Modifier.height(12.dp))
                val uriHandler = LocalUriHandler.current
                StardewOutlinedButton(
                    onClick = {
                        uriHandler.openUri("https://www.nexusmods.com/stardewvalley/mods/$nexusModId")
                    }
                ) {
                    Text(stringResource(R.string.mods_view_on_nexus))
                }
            }

            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            // Actions card
            SectionHeader(stringResource(R.string.mods_actions))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (mod.enabled) {
                                stringResource(
                                    R.string.mods_enabled
                                )
                            } else {
                                stringResource(R.string.mods_disabled)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = mod.enabled,
                            onCheckedChange = { viewModel.toggleMod(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    StardewOutlinedButton(
                        onClick = { viewModel.checkForUpdate() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isCheckingUpdate
                    ) {
                        if (state.isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.mods_checking_update))
                        } else {
                            Text(stringResource(R.string.mods_check_for_update))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    StardewButton(
                        onClick = { viewModel.showRemoveDialog() },
                        variant = StardewButtonVariant.Danger,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.mods_remove))
                    }
                }
            }

            // Dependencies
            if (mod.manifest.dependencies.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                PixelDivider()
                Spacer(Modifier.height(24.dp))

                SectionHeader(stringResource(R.string.mods_dependencies))
                Spacer(Modifier.height(8.dp))
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        mod.manifest.dependencies.forEach { dep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    dep.uniqueID,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    if (dep.isRequired) {
                                        stringResource(R.string.mods_dependency_required)
                                    } else {
                                        stringResource(R.string.mods_dependency_optional)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dep.isRequired) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Technical details
            Spacer(Modifier.height(24.dp))
            PixelDivider()
            Spacer(Modifier.height(24.dp))

            SectionHeader(stringResource(R.string.mods_details))
            Spacer(Modifier.height(8.dp))
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    mod.manifest.entryDll?.let {
                        Text(
                            stringResource(R.string.mods_entry_dll, it),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    mod.manifest.contentPackFor?.let {
                        Text(
                            stringResource(R.string.mods_content_pack_for, it.uniqueID),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    mod.manifest.minimumApiVersion?.let {
                        Text(
                            stringResource(R.string.mods_min_api_version, it),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (mod.manifest.updateKeys.isNotEmpty()) {
                        Text(
                            stringResource(R.string.mods_update_keys, mod.manifest.updateKeys.joinToString()),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        stringResource(R.string.mods_folder_path, mod.folderPath),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Remove confirmation dialog
        if (state.showRemoveDialog) {
            StardewDialog(
                onDismissRequest = { viewModel.dismissRemoveDialog() },
                title = stringResource(R.string.mods_remove_confirm_title, mod.manifest.name),
                text = stringResource(R.string.mods_remove_confirm_message),
                confirmButton = {
                    StardewButton(
                        onClick = { viewModel.confirmRemove() },
                        variant = StardewButtonVariant.Danger
                    ) {
                        Text(stringResource(R.string.mods_remove_confirm))
                    }
                },
                dismissButton = {
                    StardewButton(onClick = { viewModel.dismissRemoveDialog() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
}
