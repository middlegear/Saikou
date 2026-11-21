package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

import com.lagradost.cloudstream3.mvvm.logError
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import androidx.core.content.edit

import com.lagradost.cloudstream3.mapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.databind.DeserializationFeature

const val PREFERENCES_NAME = "cloudstream_plugin_preferences"

class PreferenceDelegate<T : Any>(
    val key: String, val default: T
) {
    private val klass: KClass<out T> = default::class
    private var cache: T? = null

    operator fun getValue(self: Any?, property: KProperty<*>): T {
        if (cache != null) return cache!!
        val json = DataStore.getPrefs()?.getString(key, null)
        if (json == null) return default
        val parsed = mapper.readValue(json, klass.java)
        cache = parsed ?: default
        return cache!!
    }

    operator fun setValue(self: Any?, property: KProperty<*>, t: T?) {
        cache = t
        if (t == null) {
            DataStore.getPrefs()?.edit { remove(key) }
        } else {
            DataStore.getPrefs()?.edit {
                putString(key, mapper.writeValueAsString(t))
            }
        }
    }
}

object DataStore {

    @PublishedApi internal var appContext: Context? = null

    val mapper: JsonMapper = com.lagradost.cloudstream3.mapper

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @PublishedApi internal fun getPrefs(): SharedPreferences? {
        return appContext?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getSharedPrefs(): SharedPreferences {
        return getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @PublishedApi internal fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all.keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.removeKey(folder: String, path: String) = removeKey(getFolderName(folder, path))

    fun Context.containsKey(path: String): Boolean =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).contains(path)

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(path)) {
                prefs.edit { remove(path) }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        return try {
            getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
                keys.forEach { remove(it) }
            }
            keys.size
        } catch (e: Exception) {
            logError(e)
            0
        }
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
                putString(path, mapper.writeValueAsString(value))
            }
        } catch (e: Throwable) {
            logError(e)
        }
    }

    fun <T> Context.getKey(path: String, valueType: Class<T>): T? {
        return try {
            val json: String = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(path, null) ?: return null
            mapper.readValue(json, valueType)
        } catch (e: Exception) {
            null
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) = setKey(getFolderName(folder, path), value)

    inline fun <reified T : Any> String.toKotlinObject(): T = mapper.readValue(this, T::class.java)

    fun <T> String.toKotlinObject(valueType: Class<T>): T = mapper.readValue(this, valueType)

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        return try {
            val json: String = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(path, null) ?: return defVal
            json.toKotlinObject()
        } catch (e: Exception) {
            null
        }
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? =
        getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal


    fun <T> setKey(path: String, value: T) {
        appContext?.setKey(path, value)
    }

    fun <T> setKey(folder: String, path: String, value: T) {
        appContext?.setKey(folder, path, value)
    }

    inline fun <reified T : Any> getKey(path: String, defVal: T?): T? =
        appContext?.getKey(path, defVal)

    inline fun <reified T : Any> getKey(path: String): T? = appContext?.getKey(path)

    inline fun <reified T : Any> getKey(folder: String, path: String): T? =
        appContext?.getKey(folder, path)

    inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
        appContext?.getKey(folder, path, defVal)

    fun getKeys(folder: String): List<String>? = appContext?.getKeys(folder)

    fun removeKey(folder: String, path: String) { appContext?.removeKey(folder, path) }

    fun removeKey(path: String) { appContext?.removeKey(path) }

    fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? =
        appContext?.getKey(path, valueType)

    fun <T : Any> setKeyClass(path: String, value: T) { appContext?.setKey(path, value) }

    fun removeKeys(folder: String): Int? = appContext?.removeKeys(folder)

    fun Int.toYear(): Date = GregorianCalendar.getInstance().also { it.set(Calendar.YEAR, this) }.time
}
