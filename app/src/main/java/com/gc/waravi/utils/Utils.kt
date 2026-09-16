package com.gc.waravi.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.hardware.Camera
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gc.waravi.BuildConfig
import com.gc.waravi.R
import com.gc.waravi.base.BaseApplication
import com.google.auth.oauth2.GoogleCredentials
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


/**
 * サポートメソッド定義クラス
 */
class Utils {
    companion object {

        suspend fun isOnline() : Boolean{
            return try {
                // Connect to Google DNS to check for connection
                val timeoutMs = 1500
                val socket = Socket()
                val socketAddress = InetSocketAddress("8.8.8.8", 53)

                withContext(Dispatchers.IO) {
                    socket.connect(socketAddress, timeoutMs)
                    socket.close()
                }

                true
            } catch (e: IOException) {
                false
            }
        }

        fun hasInternetConnection(): Single<Boolean> {
            return Single.fromCallable {
                try {
                    // Connect to Google DNS to check for connection
                    val timeoutMs = 1500
                    val socket = Socket()
                    val socketAddress = InetSocketAddress("8.8.8.8", 53)

                    socket.connect(socketAddress, timeoutMs)
                    socket.close()

                    true
                } catch (e: IOException) {
                    false
                }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
        }

        fun getFormattedTime(timestamp: Long, format: String): String {
            val calendar = Calendar.getInstance(Locale.JAPAN)
            calendar.timeInMillis = timestamp
            return DateFormat.format(format, calendar).toString()
        }

        fun isRunningOnTV(context: Context): Boolean {
            val uiModeManager = context.getSystemService(Activity.UI_MODE_SERVICE) as UiModeManager;
            return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }

        fun isCameraAvailable(context: Context): Boolean {
            try {
                val cameraManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(CameraManager::class.java) ?: return false
                } else {
                    return Camera.getNumberOfCameras() > 0
                }
                if (cameraManager.cameraIdList.isEmpty()) return false
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    cameraManager.openCamera(cameraManager.cameraIdList.first(), object : CameraDevice.StateCallback(){
                        override fun onOpened(p0: CameraDevice) {
                            p0.close()
                        }

                        override fun onDisconnected(p0: CameraDevice) {

                        }

                        override fun onError(p0: CameraDevice, p1: Int) {

                        }
                    }, null)
                } else return false
            } catch (ex: IllegalArgumentException){
                Log.e("CAMERA MANAGER", "Camera is not available.")
                return false
            }
            return true
        }

        fun openWebUrl(context: Context, urlStr: String) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)))
        }

        fun getFormattedRecentTime(timestamp: Long): String {
            val currentCalendar = Calendar.getInstance(Locale.JAPAN)
            val targetCalendar = Calendar.getInstance(Locale.JAPAN)
            targetCalendar.timeInMillis = timestamp
            val format = when {
                currentCalendar.get(Calendar.YEAR) != targetCalendar.get(Calendar.YEAR) -> {
                    "yyyy/MM/dd"
                }
                currentCalendar.get(Calendar.DAY_OF_YEAR) == targetCalendar.get(Calendar.DAY_OF_YEAR) -> {
                    "a hh:mm"
                }
                currentCalendar.get(Calendar.WEEK_OF_YEAR) == targetCalendar.get(Calendar.WEEK_OF_YEAR) -> {
                    "EEEE"
                }
                else -> {
                    "yyyy/MM/dd"
                }
            }
            val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
            return SimpleDateFormat(format, locale).format(targetCalendar.time)
        }

        fun getNextVersionName(context: Context): String {
            val nextVersionInt = BuildConfig.VERSION_NAME.substringAfterLast(".").toInt() + 1
            val nextVersion =
                BuildConfig.VERSION_NAME.replaceAfterLast(".", nextVersionInt.toString())
            return String.format(
                "%s_%s.apk",
                context.getString(R.string.app_name).lowercase(),
                nextVersion
            )
        }

        fun requestAudioFocus(
            context: Context,
            audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener
        ): AudioFocusRequest? {
            var audioFocusRequest: AudioFocusRequest? = null
            val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)
                ?: return null
            // initiate the audio playback attributes
            val requestResult: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build()
                requestResult = audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                requestResult = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }

            if (requestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(context.getString(R.string.app_name), "AUDIOFOCUS_REQUEST_GRANTED")
            }
            audioManager.mode = AudioManager.MODE_IN_CALL
            return audioFocusRequest
        }

        fun createDialog(context: Context, title: String, message: String? = null, buttonText : String? = null,
                                 action: () -> Unit) : AlertDialog.Builder {
            return AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(buttonText ?: context.getString(R.string.btn_ok)) { dialog, _ ->
                    action()
                    dialog.dismiss()
                }
                .setNegativeButton(context.getString(R.string.btn_cancel)){ dialog, _ ->
                    dialog.dismiss()
                }
        }

        fun getSystemNightMode(context: Context) : Int{
            return when(val defaultNightMode = AppCompatDelegate.getDefaultNightMode()){
                AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_NO -> defaultNightMode
                else ->
                    when (context.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
                        Configuration.UI_MODE_NIGHT_YES ->  AppCompatDelegate.MODE_NIGHT_YES
                        Configuration.UI_MODE_NIGHT_NO ->  AppCompatDelegate.MODE_NIGHT_NO
                        else ->  AppCompatDelegate.MODE_NIGHT_NO
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.N)
        fun usePixelCopy(videoView: SurfaceView, callback: (Bitmap?) -> Unit) {
            val bitmap: Bitmap = Bitmap.createBitmap(
                videoView.width,
                videoView.height,
                Bitmap.Config.ARGB_8888
            );
            try {
                // Create a handler thread to offload the processing of the image.
                val handlerThread = HandlerThread("PixelCopier")
                handlerThread.start()
                PixelCopy.request(
                    videoView, bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            callback(bitmap)
                        }
                        handlerThread.quitSafely();
                    },
                    Handler(handlerThread.looper)
                )
            } catch (e: IllegalArgumentException) {
                callback(null)
                e.printStackTrace()
            }
        }

        fun saveImageToLocal(context: Context, bitmap: Bitmap, fileName: String, onSuccess: (String?) -> Unit){
            val cw = ContextWrapper(context.applicationContext)
            val directory = cw.getDir("avatar", Context.MODE_PRIVATE)
            if (!directory.exists()) {
                directory.mkdir()
            }
            val file = File(directory, fileName)
            file.deleteOnExit()

            val fos: FileOutputStream?
            try {
                fos = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.close()
                onSuccess(file.path)
            } catch (e: Exception) {
                e.printStackTrace()
                onSuccess(null)
            }
        }

        fun getImageFileFromLocal(context: Context, fileName: String): File?{
            try {
                val cw = ContextWrapper(context.applicationContext)
                val path1 = cw.getDir("avatar", Context.MODE_PRIVATE)
                return File(path1, fileName)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                return null
            }
        }

        fun getDeviceId(context: Context): String {
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }

        @SuppressLint("WrongConstant")
        fun isBluetoothOutput(context: Context): Boolean {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return am.isBluetoothScoOn || am.isBluetoothA2dpOn
            } else {
                val audioDevices = am.getDevices(AudioManager.GET_DEVICES_ALL)
                for (adi in audioDevices) {
                    if (adi.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || adi.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    ) {
                        return true
                    }
                }
            }
            return false
        }

        @SuppressLint("WrongConstant")

        fun getBluetoothOutputName(context: Context): String? {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                if (am.isBluetoothScoOn || am.isBluetoothA2dpOn) return "Bluetooth"
            } else {
                val audioDevices = am.getDevices(AudioManager.GET_DEVICES_ALL)
                val audioBluetoothDevice = audioDevices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?:
                audioDevices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                return audioBluetoothDevice?.productName?.toString()
            }
            return null
        }

        @SuppressLint("WrongConstant")
        fun isWiredHeadsetOutput(context: Context): Boolean {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return am.isWiredHeadsetOn
            } else {
                val audioDevices = am.getDevices(AudioManager.GET_DEVICES_ALL)
                for (adi in audioDevices) {
                    if (adi.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || adi.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || adi.type == AudioDeviceInfo.TYPE_USB_HEADSET || adi.type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                        return true
                    }
                }
            }
            return false
        }

        fun playKeyTone(tone: Int){
            try {
                val toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, Constant.KEYBOARD_SOUND_VOLUME)
                toneGenerator.startTone(tone, 150)
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(Runnable {
                    toneGenerator.release()
                }, 150)
            } catch (e: Exception) {
                Log.d(this::class.simpleName, "Exception while playing sound:$e")
            }
        }

        fun dpToPx(context: Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }

        fun goToSetting(){

        }

    }

}