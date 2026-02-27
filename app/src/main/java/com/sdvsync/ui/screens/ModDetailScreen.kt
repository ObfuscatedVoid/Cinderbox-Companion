package com.sdvsync.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sdvsync.R
import com.sdvsync.mods.models.ModDownloadState
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.HtmlText
import com.sdvsync.ui.components.PixelDivider
import com.sdvsync.ui.components.PixelIcon
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.PuzzleData
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.components.toHtmlIfFormatted
import com.sdvsync.ui.formatBytes
import com.sdvsync.ui.viewmodels.ModDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatCount(count: Int): String {
    if (count < 1000) return count.toString()
    if (count < 1_000_000) return "%.1fK".format(count / 1000.0)
    return "%.1fM".format(count / 1_000_000.0)
}

@Composable
fun ModDetailScreen(viewModel: ModDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = state.mod?.name ?: "Mod Details",
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
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                PixelLoadingSpinner()
            }
            return@Scaffold
        }

        if (state.error != null && state.mod == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    StardewButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                }
            }
            return@Scaffold
        }

        val mod = state.mod ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header card
            StardewCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row {
                        if (mod.pictureUrl != null) {
                            AsyncImage(
                                model = mod.pictureUrl,
                                contentDescription = mod.name,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RectangleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            PixelIcon(
                                pixelData = PuzzleData,
                                palette = listOf(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant),
                                size = 80.dp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                mod.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.mods_author, mod.author),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "v${mod.version}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (mod.categoryName != null) {
                                Text(
                                    mod.categoryName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(R.string.mods_downloads, formatCount(mod.downloads)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            stringResource(R.string.mods_endorsements, formatCount(mod.endorsements)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (mod.lastUpdated > 0) {
                        Text(
                            stringResource(R.string.mods_detail_last_updated, dateFormat.format(Date(mod.lastUpdated))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Description card
            if (mod.summary.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.mods_detail_description),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        val descriptionText = mod.description ?: mod.summary
                        val processedHtml = toHtmlIfFormatted(descriptionText)
                        if (processedHtml != null) {
                            HtmlText(
                                html = processedHtml,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
                                linkColor = MaterialTheme.colorScheme.primary.toArgb(),
                                textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value
                            )
                        } else {
                            Text(
                                descriptionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Download progress
            val progress = state.downloadProgress
            if (progress.state != ModDownloadState.IDLE) {
                Spacer(Modifier.height(16.dp))
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        when (progress.state) {
                            ModDownloadState.DOWNLOADING -> {
                                Text(
                                    stringResource(R.string.mods_download_downloading, progress.modName),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (progress.totalBytes > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { progress.downloadedBytes.toFloat() / progress.totalBytes },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${formatBytes(
                                            progress.downloadedBytes
                                        )} / ${formatBytes(progress.totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            ModDownloadState.EXTRACTING, ModDownloadState.INSTALLING -> {
                                Text(
                                    stringResource(R.string.mods_download_installing),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            ModDownloadState.COMPLETED -> {
                                Text(
                                    stringResource(R.string.mods_download_complete, progress.modName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            ModDownloadState.ERROR -> {
                                Text(
                                    stringResource(R.string.mods_download_failed, progress.errorMessage ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Download error with browser fallback
            if (state.error != null && state.mod != null) {
                Spacer(Modifier.height(16.dp))
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.downloadErrorUrl != null) {
                            Spacer(Modifier.height(8.dp))
                            val uriHandler = LocalUriHandler.current
                            StardewButton(
                                onClick = {
                                    uriHandler.openUri(state.downloadErrorUrl!!)
                                    viewModel.clearError()
                                },
                                variant = StardewButtonVariant.Gold
                            ) {
                                Text(stringResource(R.string.mods_open_nexus))
                            }
                        }
                    }
                }
            }

            // Files card
            if (state.files.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                StardewCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.mods_detail_files),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))

                        state.files.forEachIndexed { index, file ->
                            if (index > 0) {
                                Spacer(Modifier.height(8.dp))
                                PixelDivider()
                                Spacer(Modifier.height(8.dp))
                            }

                            key(file.fileId) {
                                var expanded by remember { mutableStateOf(false) }
                                val hasDetails =
                                    file.description.isNotBlank() ||
                                        file.changelogHtml != null ||
                                        file.modVersion != null
                                val chevronRotation by animateFloatAsState(
                                    targetValue = if (expanded) -90f else -180f,
                                    label = "chevron"
                                )

                                Column(
                                    modifier = if (hasDetails) {
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { expanded = !expanded }
                                    } else {
                                        Modifier.fillMaxWidth()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                file.fileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (file.fileVersion.isNotBlank()) {
                                                    Text(
                                                        "v${file.fileVersion}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (file.fileSize > 0) {
                                                    Text(
                                                        formatBytes(file.fileSize),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                            Text(
                                                file.categoryName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when (file.categoryName) {
                                                    "MAIN" -> MaterialTheme.colorScheme.primary
                                                    "OPTIONAL" -> MaterialTheme.colorScheme.tertiary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        if (hasDetails) {
                                            PixelIcon(
                                                pixelData = ArrowLeftData,
                                                palette = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                size = 16.dp,
                                                modifier = Modifier.rotate(chevronRotation)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        StardewButton(
                                            onClick = { viewModel.installFile(file.fileId) },
                                            variant = StardewButtonVariant.Action,
                                            enabled =
                                            progress.state == ModDownloadState.IDLE ||
                                                progress.state == ModDownloadState.COMPLETED ||
                                                progress.state == ModDownloadState.ERROR
                                        ) {
                                            Text(stringResource(R.string.mods_install))
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(modifier = Modifier.padding(top = 8.dp)) {
                                            file.modVersion?.let { ver ->
                                                Text(
                                                    "Mod version: $ver",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (file.uploadedAt > 0) {
                                                Text(
                                                    "Uploaded: ${dateFormat.format(Date(file.uploadedAt))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (file.description.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                val descHtml = toHtmlIfFormatted(file.description)
                                                if (descHtml != null) {
                                                    HtmlText(
                                                        html = descHtml,
                                                        textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
                                                        linkColor = MaterialTheme.colorScheme.primary.toArgb(),
                                                        textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value
                                                    )
                                                } else {
                                                    Text(
                                                        file.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            file.changelogHtml?.let { changelog ->
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "Changelog:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                val changelogProcessed = toHtmlIfFormatted(changelog) ?: changelog
                                                HtmlText(
                                                    html = changelogProcessed,
                                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
                                                    linkColor = MaterialTheme.colorScheme.primary.toArgb(),
                                                    textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value
                                                )
                                            }
                                        }
                                    }
                                }
                            } // key(file.fileId)
                        }
                    }
                }
            }
        }
    }
}
