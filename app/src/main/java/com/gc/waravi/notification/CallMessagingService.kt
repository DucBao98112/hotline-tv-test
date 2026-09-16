package com.gc.waravi.notification

import android.content.Intent
import android.util.Log
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.models.RecentType
import com.gc.waravi.skyway.DeviceManager
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.skyway.call.PushEvent
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.ntt.skyway.room.p2p.P2PRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.e(this.javaClass.simpleName, "onNewToken: $token")
        val lastId = PrefUtils.getLastId(this)
        val authKey = PrefUtils.getAuthKey(this)
        scope.launch(Dispatchers.IO) {
            DeviceManager.updateDeviceInfo(
                this@CallMessagingService,
                lastId,
                token,
                Utils.getDeviceId(this@CallMessagingService),
                authKey
            )
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        scope.launch {
            try {
                val data = Gson().fromJson(message.data.toString(), FCMData::class.java)
                Log.e(CallMessagingService::class.simpleName, "onMessageReceived: $data")
                val contact = (application as BaseApplication).repository.findContact(data.callerId)
                when (data.type) {
                    PushEvent.PushNotification.TYPE_VOIP -> {
                        val timeToNow = System.currentTimeMillis() - message.sentTime
                        Log.e(
                            CallMessagingService::class.simpleName,
                            "VoIP message time: $timeToNow"
                        )
                        CallManager.onFCMIncomingCall(
                            this@CallMessagingService,
                            data,
                            contact?.name
                        )
                    }

                    PushEvent.PushNotification.TYPE_CANCEL,
                    PushEvent.PushNotification.TYPE_END -> {
                        withContext(Dispatchers.IO) {
                            (application as BaseApplication).repository.insertOrUpdateRecent(
                                data.callerId,
                                RecentType.Missed
                            )
                        }
                        CallManager.handleCancelVoIP(this@CallMessagingService, data)
                    }

                    else -> {

                    }
                }
            } catch (ex: JsonSyntaxException){
                ex.printStackTrace()
                Log.e(CallMessagingService::class.simpleName, "fcm parse error: ${message.data}")
            }
        }

    }
}
