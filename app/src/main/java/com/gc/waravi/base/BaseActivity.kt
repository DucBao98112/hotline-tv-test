package com.gc.waravi.base

import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.gc.waravi.R
import com.gc.waravi.skyway.AudioFocusManager
import com.gc.waravi.utils.MediaManager
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.subjects.PublishSubject

open class BaseActivity : AppCompatActivity() {
    private var defaultStreamMode : Int = AudioManager.STREAM_MUSIC
    private var callVolumeLevel = 1
    var onKeyEvent = PublishSubject.create<KeyEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nightMode = Utils.getSystemNightMode(this)
        if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            setTheme(R.style.AppTheme_Dark)
        } else {
            setTheme(R.style.AppTheme)
        }
        defaultStreamMode = if(Utils.isRunningOnTV(this))
            AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_PROG_RED
            && (event.eventTime - event.downTime) < 700){
            onBackPressedDispatcher.onBackPressed()
            return false
        }
        onKeyEvent.onNext(event)
        return super.dispatchKeyEvent(event)
    }

    fun showSnackBar(content: String, @BaseTransientBottomBar.Duration duration: Int, @IdRes contentMain: Int = R.id.contentMain){
        if(content.isEmpty()){
            return
        }
        val view: View = findViewById(contentMain)
        val snackbar = Snackbar.make(
            view,
            content,
            duration
        )
        snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE
        val customSnackView = layoutInflater.inflate(R.layout.layout_snackbar, null)
        snackbar.view.setBackgroundColor(Color.TRANSPARENT)
        val snackbarLayout = snackbar.view as Snackbar.SnackbarLayout
        val layoutParams = snackbarLayout.layoutParams
        if (layoutParams is FrameLayout.LayoutParams) {
            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            snackbar.view.layoutParams = layoutParams
        }
        snackbarLayout.setPadding(0, 0, 0, 0);
        val tvMessage: TextView = customSnackView.findViewById(R.id.tv_message)
        tvMessage.text = content
        snackbarLayout.addView(customSnackView, 0)
        snackbar.show()
    }

    fun playSound(@RawRes idRes: Int, isLoop: Boolean){
        MediaManager.playSound(this, idRes, isLoop)
    }

    fun playSound(uri: Uri, isLoop: Boolean){
        MediaManager.playSound(this, uri, isLoop)
    }

    fun stopSound(){
        MediaManager.stopMedia()
    }

    fun adjustCallVolume(){
        checkSystemAudio()
        val savedVolume = PrefUtils.getCallVolume(this)
        if (savedVolume != -1 && savedVolume != callVolumeLevel) {
            setVoiceCallVolume(savedVolume)
        }
    }

    fun revertCallVolume(){
        val savedVolume = PrefUtils.getCallVolume(this)
        if (savedVolume != -1) {
            setVoiceCallVolume(callVolumeLevel)
        }
    }

    fun abandonAudioFocus(){
        AudioFocusManager.abandonAudioFocus(this)
        // Set default volume control stream type.
        volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
    }

    fun requestAudioFocus(){
        AudioFocusManager.requestAudioFocus(this)
        // Set volume control stream type to WebRTC audio.
        volumeControlStream = defaultStreamMode
    }

    private fun checkSystemAudio() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        callVolumeLevel = audioManager.getStreamVolume(defaultStreamMode)
    }

    private fun setVoiceCallVolume(volumeLevel : Int){
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if(audioManager.isVolumeFixed) return
        audioManager.setStreamVolume(defaultStreamMode, volumeLevel, 0)
    }
}