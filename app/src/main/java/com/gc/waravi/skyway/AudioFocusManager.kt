package com.gc.waravi.skyway

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.gc.waravi.utils.Utils

object AudioFocusManager {
    private var audioFocusRequest : AudioFocusRequest? = null
    private var wasRequested = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN ->{
                Log.e(this::class.simpleName, "AUDIOFOCUS_GAIN")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.e(this::class.simpleName, "AUDIOFOCUS_LOSS")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.e(this::class.simpleName, "AUDIOFOCUS_LOSS_TRANSIENT")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // ... pausing or ducking depends on your app
            }
        }
    }

    fun requestAudioFocus(context: Context){
        if (!wasRequested){
            audioFocusRequest = Utils.requestAudioFocus(context, audioFocusChangeListener)
            wasRequested = audioFocusRequest != null
        }
    }

    fun abandonAudioFocus(context: Context){
        wasRequested = false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if(audioFocusRequest == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else{
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

}