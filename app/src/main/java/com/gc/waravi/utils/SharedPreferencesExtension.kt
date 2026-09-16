package com.gc.waravi.utils

import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

inline fun <reified T> SharedPreferences.preferences(key: String, defVal: T) =
    object : ReadWriteProperty<Any, T> {
        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            return getSharedValue(key, defVal)
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            setSharedValue(key, value)
        }
    }

inline fun <reified T> SharedPreferences.preferencesFlow(key: String, defVal: T) =
    ReadOnlyProperty<Any, Flow<T>> { _, _ ->
        callbackFlow {
            val initData = getSharedValue(key, defVal)
            trySend(initData)
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, k ->
                    if (k == key) {
                        trySend(sharedPreferences.getSharedValue(key, defVal))
                    }
                }
            registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }


@Suppress("UNCHECKED_CAST")
inline fun <reified T> SharedPreferences.getSharedValue(key: String, defaultValue: T): T {
    return when (T::class) {
        Boolean::class -> this.getBoolean(key, defaultValue as Boolean) as T
        Float::class -> this.getFloat(key, defaultValue as Float) as T
        Int::class -> this.getInt(key, defaultValue as Int) as T
        Long::class -> this.getLong(key, defaultValue as Long) as T
        String::class -> this.getString(key, defaultValue as String) as T
        Double::class -> Double.fromBits(
            this.getLong(
                key,
                (defaultValue as Double).toRawBits()
            )
        ) as T
        else -> try {
            Gson().fromJson(this.getString(key, ""), T::class.java)
                ?: defaultValue
        } catch (e: Throwable) {
            defaultValue
        }

    }
}

inline fun <reified T> SharedPreferences.setSharedValue(key: String, value: T) {
    val editor = this.edit()

    when (T::class) {
        Boolean::class -> editor.putBoolean(key, value as Boolean)
        Float::class -> editor.putFloat(key, value as Float)
        Int::class -> editor.putInt(key, value as Int)
        Long::class -> editor.putLong(key, value as Long)
        String::class -> editor.putString(key, value as String)
        Double::class -> editor.putLong(key, (value as Double).toRawBits())
        else -> {
            editor.putString(key, Gson().toJson(value))
        }
    }

    editor.commit()
}
