package com.sdvsync.di

import com.sdvsync.fileaccess.FileAccessDetector
import com.sdvsync.fileaccess.FileAccessStrategy
import com.sdvsync.saves.SaveBackupManager
import com.sdvsync.saves.SaveFileManager
import com.sdvsync.saves.SaveMetadataParser
import com.sdvsync.saves.SaveValidator
import com.sdvsync.steam.SteamAuthenticator
import com.sdvsync.steam.SteamClientManager
import com.sdvsync.steam.SteamCloudService
import com.sdvsync.steam.SteamSessionStore
import com.sdvsync.sync.ConflictResolver
import com.sdvsync.sync.SyncEngine
import com.sdvsync.sync.SyncHistoryStore
import com.sdvsync.ui.viewmodels.DashboardViewModel
import com.sdvsync.ui.viewmodels.LoginViewModel
import com.sdvsync.ui.viewmodels.SettingsViewModel
import com.sdvsync.ui.viewmodels.SyncDetailViewModel
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
    single { SteamAuthenticator(get(), get()) }
    single { SteamCloudService(get(), get()) }

    // File access
    single { FileAccessDetector(androidContext()) }
    factory<FileAccessStrategy> { get<FileAccessDetector>().detectBestStrategy() }

    // Saves
    single { SaveMetadataParser() }
    single { SaveValidator() }
    single { SaveBackupManager(androidContext()) }
    single { SaveFileManager(get(), get()) }

    // Sync
    single { ConflictResolver(androidContext()) }
    single { SyncHistoryStore(androidContext()) }
    single { SyncEngine(androidContext(), get(), get(), get(), get(), get(), get()) }

    // ViewModels
    viewModel { LoginViewModel(get()) }
    viewModel { DashboardViewModel(androidContext(), get(), get(), get(), get(), get()) }
    viewModel { SyncDetailViewModel(androidContext(), get(), get()) }
    viewModel { SettingsViewModel(androidContext(), get(), get()) }
    viewModel { SyncLogViewModel(get()) }
}
