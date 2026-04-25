package com.sdvsync

import android.app.Application
import com.sdvsync.di.appModule
import com.sdvsync.logging.AppLogger
import com.sdvsync.logging.JavaSteamLogBridge
import `in`.dragonbra.javasteam.util.log.LogManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SdvSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        patchVZipBufferPool()
        LogManager.addListener(JavaSteamLogBridge())
        startKoin {
            androidLogger()
            androidContext(this@SdvSyncApp)
            modules(appModule)
        }
    }

    /**
     * VZipUtil allocates an 8MB ThreadLocal per thread that touches decompression.
     * Replace with lazy allocation to avoid OOM on low-heap devices where Dispatchers.IO
     * rotates through many threads.
     */
    private fun patchVZipBufferPool() {
        try {
            val clazz = Class.forName("in.dragonbra.javasteam.util.VZipUtil")
            val field = clazz.getDeclaredField("windowBufferPool").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            field.set(null, ThreadLocal.withInitial { ByteArray(0) })
        } catch (e: Exception) {
            AppLogger.w("SdvSyncApp", "Could not patch VZipUtil ThreadLocal", e)
        }
    }
}
