package com.sdvsync.logging

import `in`.dragonbra.javasteam.util.log.LogListener

class JavaSteamLogBridge : LogListener {

    override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
        val tag = "JavaSteam"
        val msg = "[${clazz.simpleName}] ${message ?: ""}"
        if (throwable != null) {
            AppLogger.d(tag, "$msg - $throwable")
        } else {
            AppLogger.d(tag, msg)
        }
    }

    override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) {
        val tag = "JavaSteam"
        val msg = "[${clazz.simpleName}] ${message ?: ""}"
        if (throwable != null) {
            AppLogger.e(tag, msg, throwable)
        } else {
            AppLogger.e(tag, msg)
        }
    }
}
