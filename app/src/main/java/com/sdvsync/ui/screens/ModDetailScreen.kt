package com.sdvsync.ui.screens

import android.text.method.LinkMovementMethod
import android.widget.TextView
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import com.sdvsync.R
import com.sdvsync.mods.models.ModDownloadState
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.PixelDivider
import com.sdvsync.ui.components.PixelIcon
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.PuzzleData
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.viewmodels.ModDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HTML_TAG_REGEX = Regex("<[a-zA-Z/]")
private val BBCODE_TAG_REGEX = Regex(
    "\\[(?:b|i|u|s|url|img|size|color|font|list|quote|code|center|spoiler|line|heading)[=\\]/]",
    RegexOption.IGNORE_CASE,
)

private fun bbCodeToHtml(bbcode: String): String {
    var html = bbcode

    // Simple paired tags: [b]→<b>, [i]→<i>, etc.
    for (tag in listOf("b", "i", "u", "s")) {
        html = html.replace(Regex("\\[$tag]", RegexOption.IGNORE_CASE), "<$tag>")
        html = html.replace(Regex("\\[/$tag]", RegexOption.IGNORE_CASE), "</$tag>")
    }

    // [url=X]Y[/url] → <a href="X">Y</a>
    html = html.replace(
        Regex("\\[url=([^\\]]+)](.*?)\\[/url]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    ) { "<a href=\"${it.groupValues[1]}\">${it.groupValues[2]}</a>" }

    // [url]X[/url] → <a href="X">X</a>
    html = html.replace(
        Regex("\\[url](.*?)\\[/url]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    ) { "<a href=\"${it.groupValues[1]}\">${it.groupValues[1]}</a>" }

    // [img]X[/img] → strip (can't render in TextView)
    html = html.replace(Regex("\\[img](.*?)\\[/img]", RegexOption.IGNORE_CASE), "")

    // [size=N] → <big>
    html = html.replace(Regex("\\[size=[^\\]]+]", RegexOption.IGNORE_CASE), "<big>")
    html = html.replace(Regex("\\[/size]", RegexOption.IGNORE_CASE), "</big>")

    // [color=X] → <font color="X">
    html = html.replace(Regex("\\[color=([^\\]]+)]", RegexOption.IGNORE_CASE)) {
        "<font color=\"${it.groupValues[1]}\">"
    }
    html = html.replace(Regex("\\[/color]", RegexOption.IGNORE_CASE), "</font>")

    // [font=X] → strip (not supported in HtmlCompat)
    html = html.replace(Regex("\\[font=[^\\]]+]", RegexOption.IGNORE_CASE), "")
    html = html.replace(Regex("\\[/font]", RegexOption.IGNORE_CASE), "")

    // Lists: [list]→remove, [*]→bullet
    html = html.replace(Regex("\\[list(?:=[^\\]]*)?]", RegexOption.IGNORE_CASE), "")
    html = html.replace(Regex("\\[/list]", RegexOption.IGNORE_CASE), "")
    html = html.replace("[*]", "<br>&#8226; ")

    // [quote] → blockquote
    html = html.replace(Regex("\\[quote(?:=[^\\]]*)?]", RegexOption.IGNORE_CASE), "<blockquote>")
    html = html.replace(Regex("\\[/quote]", RegexOption.IGNORE_CASE), "</blockquote>")

    // [code] → monospace
    html = html.replace(Regex("\\[code]", RegexOption.IGNORE_CASE), "<tt>")
    html = html.replace(Regex("\\[/code]", RegexOption.IGNORE_CASE), "</tt>")

    // Strip unsupported tags
    for (tag in listOf("center", "spoiler", "heading")) {
        html = html.replace(Regex("\\[/?$tag]", RegexOption.IGNORE_CASE), "")
    }

    // [line] → horizontal rule text
    html = html.replace(Regex("\\[line]", RegexOption.IGNORE_CASE), "<br>──────────<br>")

    // Newlines → <br>
    html = html.replace("\r\n", "<br>").replace("\n", "<br>")

    return html
}

/**
 * Detects BBCode or HTML in the text and returns processed HTML string,
 * or null if the text is plain.
 */
private fun toHtmlIfFormatted(text: String): String? = when {
    BBCODE_TAG_REGEX.containsMatchIn(text) -> bbCodeToHtml(text)
    HTML_TAG_REGEX.containsMatchIn(text) -> text
    else -> null
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

private fun formatCount(count: Int): String {
    if (count < 1000) return count.toString()
    if (count < 1_000_000) return "%.1fK".format(count / 1000.0)
    return "%.1fM".format(count / 1_000_000.0)
}

@Composable
fun ModDetailScreen(
    viewModel: ModDetailViewModel,
    onBack: () -> Unit,
) {
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
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
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
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
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
                .padding(16.dp),
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
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            PixelIcon(
                                pixelData = PuzzleData,
                                palette = listOf(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant),
                                size = 80.dp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                mod.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.mods_author, mod.author),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "v${mod.version}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (mod.categoryName != null) {
                                Text(
                                    mod.categoryName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(R.string.mods_downloads, formatCount(mod.downloads)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            stringResource(R.string.mods_endorsements, formatCount(mod.endorsements)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (mod.lastUpdated > 0) {
                        Text(
                            stringResource(R.string.mods_detail_last_updated, dateFormat.format(Date(mod.lastUpdated))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        val descriptionText = mod.description ?: mod.summary
                        val processedHtml = toHtmlIfFormatted(descriptionText)
                        if (processedHtml != null) {
                            HtmlText(
                                html = processedHtml,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
                                linkColor = MaterialTheme.colorScheme.primary.toArgb(),
                                textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value,
                            )
                        } else {
                            Text(
                                descriptionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (progress.totalBytes > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { progress.downloadedBytes.toFloat() / progress.totalBytes },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            ModDownloadState.EXTRACTING, ModDownloadState.INSTALLING -> {
                                Text(
                                    stringResource(R.string.mods_download_installing),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            ModDownloadState.COMPLETED -> {
                                Text(
                                    stringResource(R.string.mods_download_complete, progress.modName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            ModDownloadState.ERROR -> {
                                Text(
                                    stringResource(R.string.mods_download_failed, progress.errorMessage ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.downloadErrorUrl != null) {
                            Spacer(Modifier.height(8.dp))
                            val uriHandler = LocalUriHandler.current
                            StardewButton(
                                onClick = {
                                    uriHandler.openUri(state.downloadErrorUrl!!)
                                    viewModel.clearError()
                                },
                                variant = StardewButtonVariant.Gold,
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
                            color = MaterialTheme.colorScheme.primary,
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
                            val hasDetails = file.description.isNotBlank() || file.changelogHtml != null || file.modVersion != null
                            val chevronRotation by animateFloatAsState(
                                targetValue = if (expanded) -90f else -180f,
                                label = "chevron",
                            )

                            Column(
                                modifier = if (hasDetails) Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded }
                                else Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (file.fileVersion.isNotBlank()) {
                                                Text(
                                                    "v${file.fileVersion}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (file.fileSize > 0) {
                                                Text(
                                                    formatBytes(file.fileSize),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
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
                                            },
                                        )
                                    }
                                    if (hasDetails) {
                                        PixelIcon(
                                            pixelData = ArrowLeftData,
                                            palette = listOf(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant),
                                            size = 16.dp,
                                            modifier = Modifier.rotate(chevronRotation),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    StardewButton(
                                        onClick = { viewModel.installFile(file.fileId) },
                                        variant = StardewButtonVariant.Action,
                                        enabled = progress.state == ModDownloadState.IDLE || progress.state == ModDownloadState.COMPLETED || progress.state == ModDownloadState.ERROR,
                                    ) {
                                        Text(stringResource(R.string.mods_install))
                                    }
                                }

                                AnimatedVisibility(
                                    visible = expanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    Column(modifier = Modifier.padding(top = 8.dp)) {
                                        file.modVersion?.let { ver ->
                                            Text(
                                                "Mod version: $ver",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (file.uploadedAt > 0) {
                                            Text(
                                                "Uploaded: ${dateFormat.format(Date(file.uploadedAt))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                    textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value,
                                                )
                                            } else {
                                                Text(
                                                    file.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        file.changelogHtml?.let { changelog ->
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Changelog:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            val changelogProcessed = toHtmlIfFormatted(changelog) ?: changelog
                                            HtmlText(
                                                html = changelogProcessed,
                                                textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
                                                linkColor = MaterialTheme.colorScheme.primary.toArgb(),
                                                textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value,
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

@Composable
private fun HtmlText(
    html: String,
    textColor: Int,
    linkColor: Int,
    textSizeSp: Float,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = textSizeSp
            }
        },
        update = { tv ->
            tv.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        },
    )
}
