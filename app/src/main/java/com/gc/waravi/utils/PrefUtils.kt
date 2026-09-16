package com.gc.waravi.utils

import android.content.Context
import com.gc.waravi.models.VideoResolution

/**
 * サポートメソッド定義クラス
 */
class PrefUtils {
    companion object {
        fun getLastId(context: Context): String{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(Constant.PREF_ID_KEY, "") ?: ""
        }

        fun saveLastId(context: Context, id: String){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(Constant.PREF_ID_KEY, id).apply()
        }

        fun isHomeRunning(context: Context) : Boolean{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_CHECK_HOME_ALIVE, true)
        }

        fun isAutoAnsweringEnable(context: Context) : Boolean{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_AUTO_ANSWERING, false)
        }

        fun setAutoAnsweringEnable(context: Context, isEnable: Boolean) {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_AUTO_ANSWERING, isEnable).apply()
        }

        fun setCurrentHomeState(context: Context, isOpen: Boolean){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_CHECK_HOME_ALIVE, isOpen).apply()
        }

        fun getSoundVolume(context: Context): MediaManager.Volume {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return when(prefs.getInt(Constant.PREF_SOUND_VOLUME_KEY, 2)){
                0 -> MediaManager.Volume.No
                1 -> MediaManager.Volume.Level1
                2 -> MediaManager.Volume.Level2
                3 -> MediaManager.Volume.Level3
                4 -> MediaManager.Volume.Level4
                5 -> MediaManager.Volume.Level5
                else -> MediaManager.Volume.Level3
            }
        }

        fun saveSoundVolume(context: Context, volume: MediaManager.Volume){
            val volumeKey = when(volume){
                MediaManager.Volume.No -> 0
                MediaManager.Volume.Level1 -> 1
                MediaManager.Volume.Level2 -> 2
                MediaManager.Volume.Level3 -> 3
                MediaManager.Volume.Level4 -> 4
                MediaManager.Volume.Level5 -> 5
            }
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(Constant.PREF_SOUND_VOLUME_KEY, volumeKey).apply()
        }

        fun getCallVolume(context: Context): Int{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(Constant.PREF_CALL_VOLUME, -1)
        }

        fun saveCallVolume(context: Context, value: Int){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(Constant.PREF_CALL_VOLUME, value).apply()
        }

        fun isOverlayEnable(context: Context) : Boolean{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_OVERLAY, true)
        }

        fun setOverlayEnable(context: Context, isEnable : Boolean){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_OVERLAY, isEnable).apply()
        }

        fun getOverlayPopupSize(context: Context) : Int{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(Constant.PREF_OVERLAY_POPUP_SIZE, 2)
        }

        fun setOverlayPopupSize(context: Context, size : Int){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(Constant.PREF_OVERLAY_POPUP_SIZE, size).apply()
        }

        fun getCameraSettingState(context: Context) : Int{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(Constant.PREF_INIT_CAMERA_STATE, 0)
        }

        fun setCameraSettingState(context: Context, state : Int){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(Constant.PREF_INIT_CAMERA_STATE, state).apply()
        }

        fun getCameraState(context: Context) : Boolean{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_SAVED_CAMERA_STATE, true)
        }

        fun setCameraSourceSetting(context: Context, state : Int){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(Constant.PREF_CAMERA_SOURCE, state).apply()
        }

        fun getCameraSourceSetting(context: Context) : Int{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(Constant.PREF_CAMERA_SOURCE, 0)
        }

        fun getLoadReductionSetting(context: Context) : Boolean{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_LOAD_REDUCTION, false)
        }

        fun saveLoadReductionSetting(context: Context, value : Boolean){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_LOAD_REDUCTION, value).apply()
        }

        fun getVideoMirror(context: Context) : Boolean{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_VIDEO_MIRROR, false)
        }

        fun saveVideoMirror(context: Context, mirror: Boolean){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_VIDEO_MIRROR, mirror).apply()
        }

        fun saveCameraState(context: Context, enable: Boolean){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_SAVED_CAMERA_STATE, enable).apply()
        }

        fun getAuthCode(context: Context): String {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(Constant.PREF_AUTH_CODE, null) ?: ""
        }

        fun saveAuthCode(context: Context, code: String) {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(Constant.PREF_AUTH_CODE, code).apply()
        }

        fun getLastTabIndex(context: Context) : Int{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(Constant.PREF_LAST_TAB_INDEX, -1)
        }

        fun saveLastTabIndex(context: Context, index: Int) {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(Constant.PREF_LAST_TAB_INDEX, index).apply()
        }

        fun getToken(context: Context): String? {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(Constant.PREF_TOKEN, "")
        }

        fun getVideoResolution(context: Context) : VideoResolution {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            val value = prefs.getInt(Constant.PREF_VIDEO_RESOLUTION, 0)
            val videoDimension = when(value){
                1 -> VideoResolution.VIDEO_VGA
                2 -> VideoResolution.VIDEO_HD
                3 -> VideoResolution.VIDEO_FHD
                else -> VideoResolution.VIDEO_VGA
            }
            return videoDimension
        }

        fun saveVideoResolution(context: Context, dimension: VideoResolution){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(Constant.PREF_VIDEO_RESOLUTION, dimension.value).apply()
        }

        fun getAuthKey(context: Context): String{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(Constant.PREF_AUTH_KEY, "") ?: ""
        }

        fun saveAuthKey(context: Context, id: String){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(Constant.PREF_AUTH_KEY, id).apply()
        }

        fun getAudioOutputDevice(context: Context) : String{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(Constant.PREF_AUDIO_DEVICE, "") ?: ""
        }

        fun settAudioOutputDevice(context: Context, deviceName: String){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(Constant.PREF_AUDIO_DEVICE, deviceName).apply()
        }

        fun getAccessibilitySetting(context: Context) : Boolean{
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_SHOW_ACCESSIBILITY, false)
        }

        fun saveAccessibilitySetting(context: Context, ignoreShowAgain: Boolean){
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_SHOW_ACCESSIBILITY, ignoreShowAgain).apply()
        }

        fun isBlurEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_BLUR_ENABLED, false)
        }

        fun saveBlurEnabled(context: Context, isEnabled: Boolean) {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_BLUR_ENABLED, isEnabled).apply()
        }

        fun getBlurRadius(context: Context): Int {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(Constant.PREF_BLUR_RADIUS, 20)
        }

        fun saveBlurRadius(context: Context, radius: Int) {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(Constant.PREF_BLUR_RADIUS, radius).apply()
        }

        fun isVirtualBackgroundEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_VIRTUAL_BG_ENABLED, false)
        }

        fun saveVirtualBackgroundEnabled(context: Context, isEnabled: Boolean) {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_VIRTUAL_BG_ENABLED, isEnabled).apply()
        }

        fun getVirtualBackgroundImage(context: Context): String {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(Constant.PREF_VIRTUAL_BG_IMAGE, "") ?: ""
        }

        fun saveVirtualBackgroundImage(context: Context, path: String) {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(Constant.PREF_VIRTUAL_BG_IMAGE, path).apply()
        }

        fun isVirtualBgBlurEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(Constant.PREF_VIRTUAL_BG_BLUR, false)
        }

        fun saveVirtualBgBlurEnabled(context: Context, isEnabled: Boolean) {
            val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constant.PREF_VIRTUAL_BG_BLUR, isEnabled).apply()
        }

    }

}