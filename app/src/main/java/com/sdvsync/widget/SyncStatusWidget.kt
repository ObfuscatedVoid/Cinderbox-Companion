package com.sdvsync.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sdvsync.MainActivity
import com.sdvsync.sync.SyncHistoryEntry
import com.sdvsync.sync.SyncHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val historyStore = SyncHistoryStore(context)
        val entries = withContext(Dispatchers.IO) { historyStore.getHistory() }

        // Group by save name, take latest per save, limit to 3
        val latestPerSave = entries
            .groupBy { it.saveName }
            .map { (_, saves) -> saves.first() }
            .take(3)

        provideContent {
            GlanceTheme {
                WidgetContent(context, latestPerSave)
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, entries: List<SyncHistoryEntry>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = context.getString(com.sdvsync.R.string.widget_title),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GlanceTheme.colors.onSurface
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (entries.isEmpty()) {
            Text(
                text = context.getString(com.sdvsync.R.string.widget_no_syncs),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        } else {
            entries.forEach { entry ->
                SyncEntryRow(entry)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SyncEntryRow(entry: SyncHistoryEntry) {
    val statusIcon = if (entry.success) "\u2714" else "\u2718"
    val directionIcon = if (entry.direction == "pull") "\u2B07" else "\u2B06"
    val farmerName = entry.saveName.substringBefore("_")

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = statusIcon,
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface)
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "$farmerName $directionIcon",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurface
            )
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = formatShortTimestamp(entry.timestamp),
            style = TextStyle(
                fontSize = 10.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

private fun formatShortTimestamp(timestamp: String): String = try {
    // Input: "yyyy-MM-dd HH:mm:ss" -> show "HH:mm" or "MMM d"
    val parts = timestamp.split(" ")
    if (parts.size == 2) parts[1].substring(0, 5) else timestamp
} catch (_: Exception) {
    timestamp
}
