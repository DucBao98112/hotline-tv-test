package com.gc.waravi.base

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gc.waravi.database.AppDatabase
import com.gc.waravi.database.CallRepository
import com.gc.waravi.skyway.AudioOutputManager
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.call.CallManager

class BaseApplication : Application(), LifecycleEventObserver {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { CallRepository(database.contactDao(), database.recentDao()) }

    override fun onCreate() {
        super.onCreate()
        app = this
        audioOutputManager = AudioOutputManager(this)
        SkywayManager.initialize(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {

            Lifecycle.Event.ON_CREATE -> {
                Log.e(this::class.simpleName, "ON_CREATE")
            }

            Lifecycle.Event.ON_START -> {
                isAppInForeground = true
                audioOutputManager.checkAudioOutput()
                CallManager.unregisterBackgroundCall()
                Log.e(this::class.simpleName, "ON_START")
            }

            Lifecycle.Event.ON_STOP -> {
                isAppInForeground = false
                Log.e(this::class.simpleName, "ON_STOP")
                CallManager.registerBackgroundCall()
            }

            Lifecycle.Event.ON_DESTROY -> {
                audioOutputManager.destroy()
                SkywayManager.disposeSkywayContext()
                Log.e(this::class.simpleName, "ON_DESTROY")
            }

            else -> {

            }
        }
    }

    companion object{
        var isAppInForeground : Boolean = false
        private lateinit var app: BaseApplication
        private lateinit var audioOutputManager: AudioOutputManager

        fun audioOutputManager() = audioOutputManager
        fun get(): BaseApplication = app
    }
}