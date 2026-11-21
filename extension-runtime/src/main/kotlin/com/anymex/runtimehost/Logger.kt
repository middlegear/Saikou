package com.anymex.runtimehost

import android.util.Log
import java.lang.reflect.Method

object Logger {
    private var callbackInstance: Any? = null
    private var logMethod: Method? = null

    @JvmStatic
    fun setLogCallback(callback: Any, method: Method) {
        callbackInstance = callback
        logMethod = method
    }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        when (level) {
            LogLevel.ERROR -> Log.e("AnymeXRuntime", message)
            LogLevel.WARNING -> Log.w("AnymeXRuntime", message)
            LogLevel.INFO -> Log.i("AnymeXRuntime", message)
            LogLevel.DEBUG -> Log.d("AnymeXRuntime", message)
        }
        try {
            logMethod?.invoke(callbackInstance, level.name, "AnymeXRuntime", message)
        } catch (e: Exception) {
            Log.e("AnymeXRuntime", "Failed to forward log: ${e.message}")
        }
    }
}

enum class LogLevel {
    ERROR, WARNING, INFO, DEBUG
}