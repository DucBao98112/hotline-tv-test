package com.gc.waravi.utils

import android.content.Context

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)

    var token : String by prefs.preferences(Constant.PREF_TOKEN, "")
//    var passcode : String by prefs.preferences(PASSCODE, "")
    var selfId : String by prefs.preferences(Constant.PREF_ID_KEY, "")
    var fcmOAuth2Token : String by prefs.preferences(Constant.PREF_FCM_OAUTH2_TOKEN, "")
}