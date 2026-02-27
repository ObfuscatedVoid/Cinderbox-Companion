package com.sdvsync.saves

data class BundleManifest(
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String,
    val save: BundleSaveInfo,
    val mods: List<BundleModInfo>
)

data class BundleSaveInfo(
    val folderName: String,
    val farmerName: String,
    val farmName: String,
    val season: Int,
    val dayOfMonth: Int,
    val year: Int,
    val gameVersion: String
)

data class BundleModInfo(val uniqueID: String, val name: String, val version: String, val enabled: Boolean)

sealed class ImportResult {
    data class Success(val manifest: BundleManifest) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
