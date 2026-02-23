package com.sdvsync

import android.app.Application
import com.sdvsync.di.appModule
import com.sdvsync.logging.AppLogger
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SdvSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        startKoin {
            androidLogger()
            androidContext(this@SdvSyncApp)
            modules(appModule)
        }
    }
}
