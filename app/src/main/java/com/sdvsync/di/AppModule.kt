package com.sdvsync.di

import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.fileaccess.FileAccessStrategy
import com.sdvsync.fileaccess.SAFFileAccess
import com.sdvsync.mods.ModDataStore
import com.sdvsync.mods.ModDownloadManager
import com.sdvsync.mods.ModFileManager
import com.sdvsync.mods.ModManifestParser
import com.sdvsync.mods.api.NexusModSource
import com.sdvsync.mods.api.SmapiUpdateChecker
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveMetadataParser
import com.sdvsync.saves.SaveValidator
import com.sdvsync.download.GameDownloadManager
import com.sdvsync.steam.SteamAuthenticator
import com.sdvsync.steam.SteamClientManager
import com.sdvsync.steam.SteamCloudService
import com.sdvsync.steam.SteamContentService
import com.sdvsync.steam.SteamSessionStore
import com.sdvsync.sync.ConflictResolver
import com.sdvsync.sync.SyncEngine
import com.sdvsync.sync.SyncHistoryStore
import com.sdvsync.ui.viewmodels.DashboardViewModel
import com.sdvsync.ui.viewmodels.InstalledModDetailViewModel
import com.sdvsync.ui.viewmodels.LoginViewModel
import com.sdvsync.ui.viewmodels.ModBrowseViewModel
import com.sdvsync.ui.viewmodels.ModDetailViewModel
import com.sdvsync.ui.viewmodels.ModManagerViewModel
import com.sdvsync.ui.viewmodels.SettingsViewModel
import com.sdvsync.ui.viewmodels.SyncDetailViewModel
import com.sdvsync.ui.viewmodels.GameDownloadViewModel
import com.sdvsync.ui.viewmodels.SyncLogViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {
    // Networking
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    // Steam
    single { SteamSessionStore(androidContext()) }
    single { SteamClientManager() }
    single { SteamAuthenticator(androidContext(), get(), get()) }
    single { SteamCloudService(get(), get()) }
    single { SteamContentService(get()) }
    single { GameDownloadManager(androidContext(), get()) }

    // File access
    single { FileAccessDetector(androidContext()) }
    factory<FileAccessStrategy> { get<FileAccessDetector>().resolveStrategy() }

    // Saves
    single { SaveMetadataParser() }
    single { SaveValidator() }
    single { SaveBackupManager(androidContext()) }
    factory {
        val strategy = get<FileAccessStrategy>()
        val basePath = if (strategy is SAFFileAccess) strategy.basePath else SaveFileManager.SDV_SAVE_PATH
        SaveFileManager(strategy, get(), basePath)
    }

    // Sync
    single { ConflictResolver(androidContext()) }
    single { SyncHistoryStore(androidContext()) }
    factory { SyncEngine(androidContext(), get(), get(), get(), get(), get(), get()) }

    // Mod management
    single { ModManifestParser() }
    single { ModFileManager(androidContext(), get()) }
    single { ModDataStore(androidContext()) }
    single { ModDownloadManager(androidContext()) }
    single { NexusModSource(get(), get()) }
    single { SmapiUpdateChecker(get()) }

    // ViewModels
    viewModel { LoginViewModel(get()) }
    viewModel { DashboardViewModel(androidContext(), get(), get(), get(), get(), get()) }
    viewModel { SyncDetailViewModel(androidContext(), get(), get(), get()) }
    viewModel { SettingsViewModel(androidContext(), get(), get(), get(), get(), get()) }
    viewModel { SyncLogViewModel(get()) }
    viewModel { GameDownloadViewModel(androidContext(), get(), get()) }
    viewModel { ModManagerViewModel(androidContext(), get(), get(), get()) }
    viewModel { ModBrowseViewModel(get(), get(), get()) }
    viewModel { (modId: String, source: String) -> ModDetailViewModel(get(), get(), get(), modId, source) }
    viewModel { (uniqueId: String) -> InstalledModDetailViewModel(get(), get(), get(), uniqueId) }
}
