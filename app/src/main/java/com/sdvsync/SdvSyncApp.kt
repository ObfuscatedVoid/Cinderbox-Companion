package com.sdvsync

import android.app.Application
import com.sdvsync.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SdvSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SdvSyncApp)
            modules(appModule)
        }
    }
}
