package com.gc.waravi.utils

import android.os.Build
import com.gc.waravi.BuildConfig

/**
 *　デフォルトのコール設定＆ダータキ
 */
object Constant {
    const val KEYBOARD_SOUND_VOLUME = 80
    const val PREF_NAME = "hotline_prefs"
    const val PREF_ID_KEY = "id_key"
    const val PREF_CHECK_HOME_ALIVE = "home_alive"
    const val PREF_SOUND_VOLUME_KEY = "sound_volume_key"
    const val INCOMING_CALL_TIMEOUT = 30000L //(30s)
    const val OUTGOING_CALL_TIMEOUT = 45000L //(45s)
    const val PREF_AUTO_ANSWERING = "w_auto_answering"
    const val PREF_CALL_VOLUME = "w_call_volume"
    const val PREF_VIDEO_RESOLUTION = "w_video_resolution"

    const val PREF_AUTH_KEY = "w_auth_key"
    const val PREF_OVERLAY = "w_overlay"
    const val PREF_OVERLAY_POPUP_SIZE = "w_overlay_popup_size"
    const val PREF_INIT_CAMERA_STATE = "w_camera_state"
    const val PREF_VIDEO_MIRROR = "w_video_mirror"
    const val PREF_SAVED_CAMERA_STATE = "w_camera_saved_state"
    const val PREF_CAMERA_SOURCE = "w_camera_source"
    const val PREF_LAST_TAB_INDEX = "w_tab_index"
    const val PREF_AUTH_CODE = "w_auth_code"
    const val PREF_AUDIO_DEVICE = "w_audio_device"
    const val PREF_LOAD_REDUCTION = "w_load_reduction"
    const val PREF_SHOW_ACCESSIBILITY = "w_show_accessibility"
    const val PREF_TOKEN = "twilio_token"
    const val PREF_FCM_OAUTH2_TOKEN = "fcm_oauth2_token"
    
    const val PREF_BLUR_ENABLED = "w_blur_enabled"
    const val PREF_BLUR_RADIUS = "w_blur_radius"
    const val PREF_VIRTUAL_BG_ENABLED = "w_virtual_bg_enabled"
    const val PREF_VIRTUAL_BG_IMAGE = "w_virtual_bg_image"
    const val PREF_VIRTUAL_BG_BLUR = "w_virtual_bg_blur"

    const val SFU_ROOM_PREFIX = 590

    object FireBaseFireStore {
        const val KEY_DEVICE_TYPE = "device_type"
        const val KEY_PUSH_TOKEN = "push_token"
        const val KEY_FCM_TOKEN = "fcm_token"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_AUTH_KEY = "authKey"
        const val KEY_REDUCTION_KEY = "load_reduction"

        const val DEVICE_TYPE_ANDROID = "android"
        const val DEVICE_TYPE_ANDROID_TV = "android_tv"
        const val DEVICE_TYPE_IOS = "ios"
        const val DEVICE_TYPE_WEB_APPLICATION = "web"
    }

    object NetWork {
        const val CONNECT_TIMEOUT = 100L
        const val READ_TIMEOUT = 100L
        const val WRITE_TIMEOUT = 100L
        const val FCM_BASE_URL = "https://fcm.googleapis.com"
        const val FCM_APP_ID = BuildConfig.FirebaseAppId
        const val APK_DOWNLOAD_URL = "https://bot.atpop.info/apk/"
        const val BASE_SERVER_URL = BuildConfig.BaseServerUrl
        const val AUTH_SERVER_URL_PATH = BuildConfig.ServerUrlPath
        const val AUTH_APP_ID = BuildConfig.AuthAppId
        const val AUTH_APP_DOMAIN = BuildConfig.AppDomain
    }

    object MailService {
        const val DEFAULT_MAIL_ACCOUNT = BuildConfig.ServiceMailAccount
        const val DEFAULT_MAIL_HOST = BuildConfig.ServiceMailHost
        const val DEFAULT_MAIL_PASSWORD = "64641286464128"
    }

}