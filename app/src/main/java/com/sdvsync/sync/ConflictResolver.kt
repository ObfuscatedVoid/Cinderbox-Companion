package com.sdvsync.sync

import android.content.Context
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.saves.SaveMetadata

enum class SyncDirection {
    PULL,  // Cloud -> Local
    PUSH,  // Local -> Cloud
    SKIP,  // Already in sync
    CONFLICT,  // Need user decision
}

data class SyncComparison(
    val direction: SyncDirection,
    val cloudMeta: SaveMetadata?,
    val localMeta: SaveMetadata?,
    val message: String,
)

class ConflictResolver(private val context: Context) {

    companion object {
        private const val TAG = "ConflictResolver"
    }

    /**
     * Compare cloud and local save metadata to determine sync direction.
     */
    fun compare(cloudMeta: SaveMetadata?, localMeta: SaveMetadata?): SyncComparison {
        // Cloud only
        if (cloudMeta != null && localMeta == null) {
            return SyncComparison(
                direction = SyncDirection.PULL,
                cloudMeta = cloudMeta,
                localMeta = null,
                message = context.getString(R.string.conflict_cloud_only),
            )
        }

        // Local only
        if (cloudMeta == null && localMeta != null) {
            return SyncComparison(
                direction = SyncDirection.PUSH,
                cloudMeta = null,
                localMeta = localMeta,
                message = context.getString(R.string.conflict_local_only),
            )
        }

        // Neither exists
        if (cloudMeta == null && localMeta == null) {
            return SyncComparison(
                direction = SyncDirection.SKIP,
                cloudMeta = null,
                localMeta = null,
                message = context.getString(R.string.conflict_no_data),
            )
        }

        // Both exist - compare progress
        val cloud = cloudMeta!!
        val local = localMeta!!

        val cloudDays = cloud.daysPlayed
        val localDays = local.daysPlayed

        val result = when {
            cloudDays > localDays -> SyncComparison(
                direction = SyncDirection.PULL,
                cloudMeta = cloud,
                localMeta = local,
                message = context.getString(R.string.conflict_cloud_ahead, cloudDays, localDays),
            )
            localDays > cloudDays -> SyncComparison(
                direction = SyncDirection.PUSH,
                cloudMeta = cloud,
                localMeta = local,
                message = context.getString(R.string.conflict_device_ahead, localDays, cloudDays),
            )
            else -> {
                // Same day - check if identical
                if (cloud.gameVersion == local.gameVersion &&
                    cloud.farmerName == local.farmerName
                ) {
                    SyncComparison(
                        direction = SyncDirection.SKIP,
                        cloudMeta = cloud,
                        localMeta = local,
                        message = context.getString(R.string.conflict_in_sync, cloudDays),
                    )
                } else {
                    SyncComparison(
                        direction = SyncDirection.CONFLICT,
                        cloudMeta = cloud,
                        localMeta = local,
                        message = context.getString(R.string.conflict_same_progress_differ),
                    )
                }
            }
        }
        AppLogger.d(TAG, "compare: ${result.direction} — ${result.message}")
        return result
    }

    /**
     * Check if a save version is compatible with the local game.
     * Returns a warning message if there might be issues, null if OK.
     */
    fun checkVersionCompatibility(
        cloudVersion: String,
        localVersion: String?,
    ): String? {
        if (localVersion == null) return null

        val cloudParts = cloudVersion.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = localVersion.split(".").mapNotNull { it.toIntOrNull() }

        if (cloudParts.size < 2 || localParts.size < 2) return null

        // Major version mismatch (e.g., 1.5 vs 1.6) is a problem
        if (cloudParts[0] != localParts[0] || cloudParts[1] != localParts[1]) {
            if (cloudParts[0] > localParts[0] ||
                (cloudParts[0] == localParts[0] && cloudParts[1] > localParts[1])
            ) {
                return context.getString(R.string.conflict_version_warning, cloudVersion, localVersion)
            }
        }

        return null
    }
}
