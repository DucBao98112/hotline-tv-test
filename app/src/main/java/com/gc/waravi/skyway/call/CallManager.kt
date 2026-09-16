package com.gc.waravi.skyway.call

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.gc.waravi.CallData
import com.gc.waravi.MessageCode
import com.gc.waravi.R
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.models.AtpopPushData
import com.gc.waravi.models.DeviceInfoEntity
import com.gc.waravi.models.RoomMetadata
import com.gc.waravi.models.SocketData
import com.gc.waravi.network.authService
import com.gc.waravi.notification.FCMData
import com.gc.waravi.notification.FirebaseUtils
import com.gc.waravi.notification.NotifyManager
import com.gc.waravi.skyway.AudioFocusManager
import com.gc.waravi.skyway.DeviceManager
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.SkywayService
import com.gc.waravi.skyway.SocketManager
import com.gc.waravi.utils.Constant
import com.gc.waravi.utils.MediaManager
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.views.activities.HomeActivity
import com.gc.waravi.views.activities.InCallActivity
import com.google.gson.Gson
import com.gc.waravi.skyway.popservice.PopService
import com.gc.waravi.skyway.room.RoomSession
import com.ntt.skyway.room.p2p.P2PRoom
import io.socket.client.Ack
import io.socket.client.SocketIOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.schedule
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val ARG_CALL_ACTION = "call-type"
const val ARG_CALL_ID = "call-id"
const val ARG_SHOULD_FINISH = "is-should-finish"

enum class CallType {
    INCOMING_CALL,
    OUTGOING_CALL;
}

object CallManager {
    var isInRoom = false
    private val mutableCallEvents: MutableSharedFlow<CallEvent> = MutableSharedFlow()
    val callEvents: SharedFlow<CallEvent> = mutableCallEvents.asSharedFlow()
    private val callScope = CoroutineScope(Dispatchers.IO)
    private var callEventJob: Job? = null
    private var intervalJob: Job? = null
    private var callSession: CallSession? = null
    var roomSession: RoomSession? = null
    private var waitingCallTimer: Timer? = null

    var cameraEnable: Boolean
        get() = callSession?.cameraEnable ?: true
        set(enable) {
            callSession?.setCameraEnable(enable)
        }

    var microphoneEnable: Boolean
        get() = callSession?.microphoneEnable ?: true
        set(enable) {
            callSession?.setMicrophoneEnable(enable)
        }

    fun registerForegroundCall(id: String? = null) {
        Log.e(this::class.simpleName, "registerForegroundCall...$id")
        if (callEventJob?.isActive == true) {
            callEventJob?.cancel()
        }
        if (SocketManager.isConnected()) {
            SocketManager.disconnectSocket()
        }
        SocketManager.connectSocket(id)
        SocketManager.socketEvents.let { sharedFlow ->
            callEventJob = callScope.launch {
                sharedFlow.collect { event ->
                    when (event) {
                        is SocketEvent.IncomingCall -> {
                            onSocketIncomingCall(event.data)
                        }

                        is SocketEvent.EndCall -> {
                            val data = event.data
                            if (data.sessionId == callSession?.sessionId && data.senderId == callSession?.calleeId) {
                                onSocketIncomingCallEnded(data.type)
                            }
                        }
                    }
                }
            }
        }
    }

    fun registerBackgroundCall() {
        Log.e(this::class.simpleName, "registerBackgroundCall...")
        if (callEventJob?.isActive == true) {
            callEventJob?.cancel()
        }
        this.disconnectSocket()
        val intent = Intent(BaseApplication.get(), SkywayService::class.java)
        ContextCompat.startForegroundService(BaseApplication.get(), intent)
    }

    fun unregisterBackgroundCall() {
        Log.e(this::class.simpleName, "unregisterBackgroundCall...")
        callScope.launch(Dispatchers.Main) {
            val intent = Intent(BaseApplication.get(), SkywayService::class.java)
            BaseApplication.get().stopService(intent)
            delay(2000L)
            this@CallManager.registerForegroundCall(SkywayManager.selfId)
        }
    }

    private fun sendBusyEvent(sessionId: String, calleeId: String, roomId: String) {
        val callSession =
            CallSession(sessionId, calleeId, roomId, CallType.INCOMING_CALL, callScope)
        callScope.launch {
            sendPushEvent(callSession, PushEvent.BUSY)
        }
    }

    fun onSocketIncomingCall(data: SocketData) {
        if (data.sessionId == callSession?.sessionId) return
        if (isInRoom || callSession?.inCall() == true) {
            this.sendBusyEvent(data.sessionId, data.senderId, data.room)
            return
        }
        Log.e(CallManager::class.simpleName, "onSocketIncomingCall: ${data.sessionId}")
        if (BaseApplication.isAppInForeground) {
            this@CallManager.onForegroundCall(
                BaseApplication.get(),
                data.sessionId,
                data.senderId,
                data.room
            )
        } else if (data.sessionId != callSession?.sessionId) {
            this@CallManager.onBackgroundCall(
                BaseApplication.get(),
                data.sessionId,
                data.senderId,
                data.room,
                null
            )
        }
    }

    private fun checkSocketConnection() {
        SocketManager.checkSocketConnection()
    }

    fun reconnectSocket() {
        SocketManager.reconnectSocket()
    }

    fun disconnectSocket() {
        SocketManager.disconnectSocket()
    }

    fun emitSocketEvent(event: String, data: String) {
        SocketManager.emitSocketEvent(event, data)
    }

    private fun emitSocketEvent(event: String, data: String, callback: Ack) {
        SocketManager.emitSocketEvent(event, data, callback)
    }

    private fun emitCallEvent(calleeId: String, data: String, callback: Ack) {
        SocketManager.emitCallEvent(calleeId, data, callback)
    }

    fun makeCall(context: Context, callerId: String) {
        val roomName = UUID.randomUUID().toString().substringBefore("-")
        this.createCallSession(
            UUID.randomUUID().toString(), callerId, roomName,
            CallType.OUTGOING_CALL
        )
        callSession?.makeCall(context)
        this.startCallTimeout(Constant.OUTGOING_CALL_TIMEOUT)
    }

    fun acceptCall() {
        waitingCallTimer?.cancel()
        intervalJob?.cancel()
        callSession?.answerCall()
//        callSession?.joinCall(context)
    }

    fun declineCall() {
        callSession?.declineCall()
        this.closeSession(1000L)
    }

    fun startLocalStream(context: Context) {
        callSession?.startLocalStream(context, cameraEnable, microphoneEnable)
    }

    fun switchCamera(context: Context) {
        callSession?.switchCamera(context)
    }

    fun sendMessage(message: String) {
        callSession?.sendDataEvent(CallData(MessageCode.Chat.code, message))
    }

    fun sendEvent(callData: CallData) {
        callSession?.sendDataEvent(callData)
    }

    fun updateVideoProcessors(context: Context) {
        callSession?.updateProcessors(context)
        roomSession?.updateProcessors(context)
    }

    fun endCall(sendEvent: Boolean = false) {
        NotifyManager.closeIncomingLayout()
        MediaManager.stopMedia()
        callSession?.endCall(sendEvent)
        this.closeSession()
    }

    fun closeSession(delay: Long = 500L) {
        callScope.launch {
            delay(delay)
            waitingCallTimer?.cancel()
            intervalJob?.cancel()
            callSession?.onCallEvent = null
            callSession = null
        }
    }

    fun onSocketIncomingCallEnded(reason: String) {
        if (callSession?.callType == CallType.OUTGOING_CALL) {
            val event = when (reason) {
                PushEvent.PushNotification.TYPE_BUSY -> CallEvent.CalleeBusy
                PushEvent.PushNotification.TYPE_CANCEL -> CallEvent.CallCanceled
                PushEvent.PushNotification.TYPE_DECLINE -> CallEvent.CallRejected
                PushEvent.PushNotification.TYPE_END -> CallEvent.CallEnded
                else -> CallEvent.CallEnded
            }
            this.sendCallEvent(event)
        } else if (callSession?.callType == CallType.INCOMING_CALL) {
            val event = when (reason) {
                PushEvent.PushNotification.TYPE_CANCEL -> CallEvent.CallCanceled
                else -> CallEvent.CallEnded
            }
            this.sendCallEvent(event)
        }
        this.endCall()
    }

    private fun createCallSession(
        sessionId: String,
        callerId: String,
        room: String,
        callType: CallType
    ) {
        callSession = CallSession(sessionId, callerId, room, callType, callScope).apply {
            setCameraEnable(CallManager.cameraEnable)
            setMicrophoneEnable(CallManager.microphoneEnable)
        }
        callSession?.onCallEvent = { callEvent ->
            callScope.launch { mutableCallEvents.emit(callEvent) }
            if (callEvent is CallEvent.CallStarted) {
                waitingCallTimer?.cancel()
            }
        }
        callSession?.onPushEvent = { pushEvent: PushEvent ->
            callScope.launch {
                if (pushEvent is PushEvent.VoIP) {
                    executeCallPush()
                } else {
                    callSession?.let {
                        sendPushEvent(it, pushEvent)
                    }
                }
            }
        }
    }

    private fun onForegroundCall(
        context: Context,
        sessionId: String,
        callerId: String,
        roomId: String
    ) {
        if(callSession != null && callSession?.sessionId == sessionId) return
        this.createCallSession(sessionId, callerId, roomId, CallType.INCOMING_CALL)
        this.sendCallEvent(CallEvent.IncomingCall(callerId))
        this.startCallTimeout(Constant.INCOMING_CALL_TIMEOUT)
        callSession?.joinCall(context)
    }

    private fun onBackgroundCall(
        context: Context,
        sessionId: String,
        callerId: String,
        roomId: String,
        displayName: String?
    ) {
        this.createCallSession(sessionId, callerId, roomId, CallType.INCOMING_CALL)
        callScope.launch {
            callSession?.joinCall(context)
            showIncomingPopup(context, callerId, displayName)
            startCallTimeout(Constant.INCOMING_CALL_TIMEOUT)
        }
    }

    private fun startCallTimeout(timeout: Long) {
        waitingCallTimer?.cancel()
        waitingCallTimer = Timer()
        waitingCallTimer?.schedule(timeout) {
//                AudioFocusManager.instance.abandonAudioFocus()
            this@CallManager.endCall(true)
        }
    }

    fun onFCMIncomingCall(context: Context, callData: FCMData, displayName: String?) {
        val sessionId = callData.sessionId
        val callerId = callData.callerId
        val room = callData.room
        if (isInRoom || callSession?.inCall() == true) {
            this.sendBusyEvent(sessionId, callerId, room)
            return
        }
        if (SocketManager.isConnected().not()) {
            reconnectSocket()
        } else {
            callScope.launch {
                if (isRoomAvailable(context, room)) {
                    this@CallManager.onBackgroundCall(
                        context,
                        sessionId,
                        callerId,
                        room,
                        displayName
                    )
                } else {
                    Handler(Looper.getMainLooper()).post {
                        NotifyManager.showNotificationAlert(
                            context,
                            callerId,
                            String.format(
                                ContextCompat.getString(context, R.string.msg_missed_call),
                                displayName ?: callerId
                            )
                        ) {
                            PrefUtils.saveLastTabIndex(context, 0)
                            val intent = Intent(context, HomeActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            ContextCompat.startActivity(context, intent, null)
                        }
                    }
                }
            }
        }
    }

    private suspend fun isRoomAvailable(context: Context, roomName: String) =
        suspendCancellableCoroutine { coroutine ->
            callScope.launch {
                SkywayManager.ensureSkywayInit(context) {
                    val room = P2PRoom.find(roomName)
                    if (room?.metadata != null) {
                        val metadata = Gson().fromJson(room.metadata, RoomMetadata::class.java)
                        coroutine.resume(metadata.state != "end")
                    } else {
                        coroutine.resume(false)
                    }
                }
            }
        }

    private suspend fun executeCallPush() {
        val calleeId = callSession?.calleeId ?: return
        val deviceInfo = DeviceManager.getDeviceInfo(calleeId)
        val isCalleeOnline = isPeerOnline(calleeId)
        if (isCalleeOnline) {
            this.sendSocketPush(callSession!!, PushEvent.VoIP)
            if (deviceInfo?.deviceType == Constant.FireBaseFireStore.DEVICE_TYPE_WEB_APPLICATION) {
                this.sendFCMPush(callSession!!, PushEvent.VoIP)
            }
        } else {
            this.sendBackgroundCallPush(deviceInfo)
        }
    }

    private suspend fun sendBackgroundCallPush(deviceInfo: DeviceInfoEntity?) {
        if (deviceInfo != null) {
            if (deviceInfo.deviceType == Constant.FireBaseFireStore.DEVICE_TYPE_ANDROID){
                this.sendAtPopCallPush(callSession!!)
            } else{
                this.sendFCMPush(callSession!!, PushEvent.VoIP)
            }
            intervalJob = intervalExecuteCheckCall(500.milliseconds, action = {
                val startTimeExecute = System.currentTimeMillis()
                val condition = !isPeerOnline(deviceInfo.uniqueNumber)
                Pair(condition, System.currentTimeMillis() - startTimeExecute)
            }, timeOut = (Constant.OUTGOING_CALL_TIMEOUT).milliseconds).onEach {
                if (it) {
                    //do nothing, wait for callee online
                } else {
                    this.sendSocketPush(callSession!!, PushEvent.VoIP)
                }
            }.catch {
                Log.e(this.javaClass.simpleName, "Error: ${it.message}")
                sendCallEvent(CallEvent.CallEnded)
            }.launchIn(callScope)
        } else {
            sendCallEvent(CallEvent.CallEnded)
        }
    }

    private fun sendCallEvent(callEvent: CallEvent) {
        callScope.launch { mutableCallEvents.emit(callEvent) }
    }

    private fun sendPushEvent(session: CallSession, pushEvent: PushEvent) = callScope.launch {
        try {
            val isCalleeOnline = isPeerOnline(session.calleeId)
            if (isCalleeOnline) {
                sendSocketPush(session, pushEvent)
            } else {
                sendFCMPush(session, pushEvent)
            }
        } catch (ex: SocketIOException) {
            sendFCMPush(session, pushEvent)
        }
    }

    private fun sendSocketPush(session: CallSession, pushEvent: PushEvent) {
        val onResult = Ack { objects ->
            if (objects.isNotEmpty()) {
                val data = JSONObject(objects[0].toString())
                val result = data.get("result")
                Log.i(this.javaClass.simpleName, "Socket result: $result")
                if (result != "ok") {
                    Log.i(this.javaClass.simpleName, "Socket fail, send fcm push...")
                    callScope.launch {
                        sendFCMPush(session, pushEvent)
                    }
                }
            }
        }
        val data = Gson().toJson(
            SocketData(
                session.sessionId,
                SkywayManager.selfId,
                session.calleeId,
                session.roomName,
                pushEvent.getPushType()
            )
        )
        Log.i(this.javaClass.simpleName, "Send socket event $data")
        emitCallEvent(session.calleeId, data, onResult)
    }

    private suspend fun sendFCMPush(session: CallSession, pushEvent: PushEvent): Boolean {
        return FirebaseUtils.sendPushNotification(
            session.calleeId, session.sessionId,
            session.roomName, pushEvent.getPushType()
        )
    }

    private suspend fun sendAtPopCallPush(session: CallSession): Boolean {
        val response = authService.sendAtPopPush(
            AtpopPushData(
                session.sessionId,
                SkywayManager.selfId,
                session.calleeId,
                session.roomName,
                "call"
            )
        )
        return response.isSuccessful
    }

    private suspend fun isPeerOnline(calleeId: String) = suspendCoroutine { continuation ->
        this.emitSocketEvent("fetchId", calleeId) { args ->
            val data = JSONObject(args[0].toString())
            Log.i(this.javaClass.simpleName, "Fetch result: $data")
            val isOnline = data.get("online") as? Boolean ?: false
            continuation.resumeWith(Result.success(isOnline))
        }
    }

    fun showIncomingPopup(context: Context, callerId: String, displayName: String?) =
        callScope.launch(Dispatchers.Main.immediate) {
            fun accept() {
                MediaManager.stopMedia()
                startCallActivity(context, callerId)
            }

            fun decline() {
                MediaManager.stopMedia()
                AudioFocusManager.abandonAudioFocus(context)
                this@CallManager.declineCall()
            }

            MediaManager.playSound(context, R.raw.ringtone, true)
            AudioFocusManager.requestAudioFocus(context)

            NotifyManager.showIncomingLayout(context, callerId, displayName,
                onAccept = {
                    accept()
                },
                onDecline = {
                    decline()
                }
            )
        }

    private fun startCallActivity(
        context: Context,
        peerId: String,
    ) {
        val intent = Intent(context, InCallActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val bundle =
            bundleOf(
                ARG_CALL_ACTION to CallType.INCOMING_CALL,
                ARG_CALL_ID to peerId
            )
        intent.putExtras(bundle)
        ContextCompat.startActivity(context, intent, null)
    }

    fun handleCancelVoIP(context: Context, data: FCMData) {
        if (callSession?.calleeId == data.callerId && data.sessionId == callSession?.sessionId) {
            MediaManager.stopMedia()
            AudioFocusManager.abandonAudioFocus(context)
            NotifyManager.closeIncomingLayout()
            this.onSocketIncomingCallEnded(data.type)
        }
    }

}

fun intervalExecuteCheckCall(
    period: Duration,
    initialDelay: Duration = Duration.ZERO,
    action: suspend () -> Pair<Boolean, Long> = { Pair(true, 0L) },
    timeOut: Duration = Duration.INFINITE
): Flow<Boolean> {
    var totalTime = 0L
    return flow {
        delay(initialDelay)
        do {
            val condition = action.invoke()
            emit(true)
            delay(period)
            totalTime += period.inWholeMilliseconds + condition.second
            if (totalTime >= timeOut.inWholeMilliseconds) {
                throw Exception("TimeOut!")
            }
        } while (condition.first)
        emit(false)
    }
}
