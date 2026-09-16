package com.gc.waravi.notification

import com.gc.waravi.base.BaseApplication
import com.gc.waravi.models.FCMAndroidMessage
import com.gc.waravi.models.FCMPush
import com.gc.waravi.network.fcmService
import com.gc.waravi.skyway.DeviceManager
import com.gc.waravi.utils.AppPrefs
import com.google.auth.oauth2.GoogleCredentials
import retrofit2.HttpException
import java.io.InputStream

object FirebaseUtils {
    private val appPrefs : AppPrefs by lazy {
        AppPrefs(BaseApplication.get())
    }

    fun getFcmOAuth2Token(): String{
        return appPrefs.fcmOAuth2Token.ifEmpty {
            appPrefs.fcmOAuth2Token = createAuthenticationToken()
            appPrefs.fcmOAuth2Token
        }
    }

    private fun createAuthenticationToken() : String{
        val firebaseMessagingScope = "https://www.googleapis.com/auth/firebase.messaging"
        val stream: InputStream = BaseApplication.get().assets.open("authServiceAccount.json")
        val googleCredentials: GoogleCredentials =
            GoogleCredentials.fromStream(stream).createScoped(firebaseMessagingScope)
        googleCredentials.refresh()
        return googleCredentials.accessToken.tokenValue
    }

    suspend fun sendPushNotification(theirId: String, sessionId: String, roomName: String,
                                     pushType: String) : Boolean{
        val firebaseData = DeviceManager.getDeviceInfo(theirId) ?: return false
        val theirToken = firebaseData.fcmToken
        val fcmData = FCMData(
            callerId = appPrefs.selfId,
            calleeId = theirId,
            sessionId = sessionId,
            room = roomName,
            type = pushType
        )
        val fcmMessage = FCMAndroidMessage(FCMPush(token = theirToken, data = fcmData))
//        val fcmMessage = FCMAndroidMessage(FCMPush(token = theirToken, data = fcmData,
//            notification = NotificationBody("新規の着信", "「${appPrefs.selfId}」より着信がありました。")))
        try {
            fcmService.sendVoIPNotification(fcmMessage)
        } catch (ex: HttpException) {
            if (ex.code() == 401 || ex.code() == 403) {
                val newAuthenticationToken = createAuthenticationToken()
                appPrefs.fcmOAuth2Token = newAuthenticationToken
                fcmService.sendVoIPNotification(fcmMessage)
            } else {
                ex.printStackTrace()
                return false
            }
        }
        return true
    }

}