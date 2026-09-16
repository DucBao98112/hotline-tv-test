package com.gc.waravi.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes

/**
 *　メディアマネージャークラス
 */
object MediaManager {
    private var mediaPlayer: MediaPlayer? = null
    init {
        mediaPlayer = MediaPlayer()
    }

    fun playSound(context: Context, @RawRes idRes: Int, isLoop: Boolean){
        this.stopMedia()
        mediaPlayer = MediaPlayer.create(context, idRes)
        playMedia(context, isLoop)
    }

    fun playSound(context: Context, uri: Uri, isLoop: Boolean){
        this.stopMedia()
        mediaPlayer = MediaPlayer.create(context, uri)
        playMedia(context, isLoop)
    }

    private fun playMedia(context: Context, isLoop: Boolean){
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
            .build()
        mediaPlayer?.setAudioAttributes(playbackAttributes)
        mediaPlayer?.setOnCompletionListener {
            stopMedia()
        }
        val volume = getVolume(context)
        mediaPlayer?.setVolume(volume, volume)
        mediaPlayer?.start()
        mediaPlayer?.isLooping = isLoop
    }

    fun stopMedia(){
        try {
            if(mediaPlayer == null) return
            if(mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (ex : IllegalStateException){
            Log.e(this::class.simpleName, "Stop media error: ${ex.message}")
        }
    }

    fun getVolume(context: Context): Float{
        val volume = PrefUtils.getSoundVolume(context)
        return when(volume){
            Volume.No -> 0f
            Volume.Level1 -> 0.1f
            Volume.Level2 -> 0.2f
            Volume.Level3 -> 0.3f
            Volume.Level4 -> 0.4f
            Volume.Level5 -> 0.5f
        }
    }

    enum class Volume{
        No, Level1, Level2, Level3, Level4, Level5
    }
}