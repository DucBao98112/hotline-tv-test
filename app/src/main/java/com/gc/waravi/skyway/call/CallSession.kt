package com.gc.waravi.skyway.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import com.gc.waravi.CallData
import com.gc.waravi.MessageCode
import com.gc.waravi.models.RoomMetadata
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.UsbCameraSource
import com.gc.waravi.skyway.room.RoomEvent
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.views.fragments.CallSettingFragment
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import com.ntt.skyway.core.channel.Publication
import com.ntt.skyway.core.channel.member.Member
import com.ntt.skyway.core.content.Encoding
import com.ntt.skyway.core.content.Stream
import com.ntt.skyway.core.content.local.LocalAudioStream
import com.ntt.skyway.core.content.local.LocalDataStream
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.local.source.AudioSource
import com.ntt.skyway.core.content.local.source.CameraSource
import com.ntt.skyway.core.content.local.source.DataSource
import com.ntt.skyway.core.content.local.source.VideoProcessor
import com.ntt.skyway.videoprocessors.BlurProcessingConfig
import com.ntt.skyway.videoprocessors.VirtualBackgroundProcessingConfig
import com.gc.waravi.skyway.CpuVideoProcessor
import com.ntt.skyway.core.content.remote.RemoteDataStream
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.core.content.sink.SurfaceViewRenderer
import com.ntt.skyway.room.RoomPublication
import com.ntt.skyway.room.RoomSubscription
import com.ntt.skyway.room.member.LocalRoomMember
import com.ntt.skyway.room.member.RemoteRoomMember
import com.ntt.skyway.room.member.RoomMember
import com.ntt.skyway.room.p2p.P2PRoom
import com.serenegiant.widget.AspectRatioSurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class CallSession(val sessionId: String, val calleeId: String, val roomName: String, val callType: CallType,
                  private val callScope: CoroutineScope){
    var onCallEvent: ((event: CallEvent) -> Unit)? = null
    var onPushEvent: ((event: PushEvent) -> Unit)? = null
    private val TAG = this.javaClass.simpleName
//    val sessionId = UUID.randomUUID().toString()
    var callState = CallState.WAITING
    private var privateRoom : P2PRoom? = null
    private var intervalJob: Job? = null

    private var localVideoView: View? = null
    private var localVideoStream : LocalVideoStream? = null
    private var localAudioStream : LocalAudioStream? = null
    private var localDataStream : LocalDataStream? = null

    var cameraEnable : Boolean = true
        private set

    var microphoneEnable : Boolean = true
        private set

    private var isLoadReductionEnable = false
    private var isMirrorEnable = false

    private var localMember : LocalRoomMember? = null
    private var videoPublication: RoomPublication? = null
    private var audioPublication: RoomPublication? = null
    private var dataPublication: RoomPublication? = null
//    private var subs : ArrayList<RoomSubscription> = arrayListOf()
    var isFrontCamera = true
    var isCustomCameraSource = false

    private var cpuVideoProcessor: CpuVideoProcessor? = null
    private var currentBackgroundPath: String? = null
    private var currentBackgroundBitmap: Bitmap? = null

    private fun registerCallEvent(){
        privateRoom?.apply {
            onMemberJoinedHandler = { member ->
                Log.i(TAG, "onMemberJoinedHandler: ${member.name}")
                if (member is LocalRoomMember){
                    sendCallEvent(CallEvent.Connected(member))
//                    if(callType == CallType.OUTGOING_CALL){
//                        sendPushEvent(PushEvent.VoIP)
//                    }
                } else if(member is RemoteRoomMember) {
                    callState = CallState.IN_CALL
                    sendCallEvent(CallEvent.CallStarted(member))
                }
            }
            onMemberLeftHandler = {
                Log.i(TAG, "onMemberLeftHandler... ${it.name}")
                if(it.name == calleeId){
                    endCall(false)
                }
            }
            onMemberListChangedHandler = {
                Log.i(TAG, "onMemberListChangedHandler... Member size: ${privateRoom?.members?.size}")
            }
            onStreamPublishedHandler = { pub ->
                Log.i(TAG, "onStreamPublishedHandler: ${pub.publisher?.name} / Type: ${pub.contentType.name}")
                if (pub.publisher?.id != localMember?.id){
                    callScope.launch {
                        subscribeTo(pub)
                    }
                }
            }
            onStreamUnpublishedHandler = {
                Log.i(TAG, "onStreamUnpublishedHandler")
//                it.publisher?.id?.let {
//                    callScope.launch{
//                        unsubscribe(it)
//                    }
//                }
            }
            onClosedHandler = {
                callState = CallState.END
                exitPrivateRoom()
            }
            onErrorHandler = {
                sendCallEvent(CallEvent.Error(exception = it))
            }
        }
    }

    suspend fun isRoomAvailable(context: Context) : Boolean = suspendCancellableCoroutine { continuation ->
        callScope.launch {
            SkywayManager.ensureSkywayInit(context) {
                val p2pRoom = P2PRoom.find(roomName)
                val isAvailable = p2pRoom?.members.isNullOrEmpty().not()
                if(!isAvailable){
                    delay(1500L)
                    continuation.resume(P2PRoom.find(roomName) != null)
                } else{
                    continuation.resume(true)
                }
            }
        }
    }

    private suspend fun createOrJoinPrivateRoom(context: Context){
        this.isLoadReductionEnable = PrefUtils.getLoadReductionSetting(context)
        this.isMirrorEnable = PrefUtils.getVideoMirror(context)
        SkywayManager.ensureSkywayInit(context) {
            val p2PRoom = P2PRoom.findOrCreate(roomName)
            if(p2PRoom != null){
                joinRoom(p2PRoom)
            } else{
                SkywayManager.reinitSkywayContext(context)
                tryCreateRoom(roomName, delay = 1000L, period = 1000L, numberOfAttempts = 10, onSuccess =  { p2pRoom ->
                    joinRoom(p2pRoom)
                }, onFailure = {
                    sendCallEvent(CallEvent.Error())
                })
            }
        }
    }

    private fun joinRoom(room: P2PRoom) = callScope.launch{
        privateRoom = room
        try {
//            val metadata = RoomMetadata(requestedEncoding = if(isLoadReductionEnable) "low" else null,
//                state = if(callType == CallType.OUTGOING_CALL) "waiting" else null)
            if(isLoadReductionEnable){
                room.updateMetadata(Gson().toJson(RoomMetadata(requestedEncoding = "low")))
            } else if (room.metadata != null){
                val roomMetadata = Gson().fromJson(room.metadata, RoomMetadata::class.java)
                if(roomMetadata.requestedEncoding == "low"){
                    isLoadReductionEnable = true
                }
            }
        } catch (ex: JsonIOException){
            ex.printStackTrace()
        } catch (ex: JsonSyntaxException){
            ex.printStackTrace()
        }

        //register call event
//        this@CallSession.registerCallEvent()

        if(callType == CallType.OUTGOING_CALL){
            privateRoom?.onMemberJoinedHandler = { member ->
                if (member is LocalRoomMember){
                    sendCallEvent(CallEvent.Connected(member))
                    sendPushEvent(PushEvent.VoIP)
                }
            }
            privateRoom?.onMetadataUpdatedHandler = { data ->
                val metadata = Gson().fromJson(data, RoomMetadata::class.java)
                if(metadata.state == "in_call"){
                    registerCallEvent()
                    callScope.launch {
                        registerRemoteStream()
                    }
                }
            }
        }

        val metadata = Gson().toJson(RoomMetadata(mirror = isMirrorEnable))
        val member = RoomMember.Init(SkywayManager.selfId, metadata)
        localMember = privateRoom?.join(member)
        if(localMember != null && privateRoom != null){
            callState = CallState.CALLING
        }
    }

    private suspend fun tryCreateRoom(
        roomName: String,
        delay: Long = 0L,
        period: Long,
        numberOfAttempts: Int,
        onSuccess: (P2PRoom) -> Unit,
        onFailure: () -> Unit
    ){
        var totalCount = 0
        var p2PRoom : P2PRoom? = null
        delay(delay)
        while (p2PRoom == null && totalCount < numberOfAttempts) {
            p2PRoom = P2PRoom.findOrCreate(roomName)
            Log.e(this.javaClass.simpleName, "tryCreateRoom: ${p2PRoom?.name}")
            totalCount += 1
            if (p2PRoom != null){
                onSuccess(p2PRoom)
            } else if(totalCount == numberOfAttempts){
                onFailure()
            } else{
                delay(period)
            }
        }
    }

    fun isConnected(): Boolean{
        return localMember != null && localMember!!.state == Member.State.JOINED
    }

    private suspend fun subscribeTo(pub: RoomPublication){
        if(pub.publisher == null || pub.publisher?.id == localMember?.id) return
        val videoSubscribeOption = if (isLoadReductionEnable) RoomSubscription.Options(preferredEncodingId =  "low" ) else null
        val sub = localMember?.subscribe(pub, if (pub.contentType == Stream.ContentType.VIDEO) videoSubscribeOption else null)
        sub?.let {
//            subs.add(it)

            if(it.stream == null) return
            if(it.contentType == Stream.ContentType.VIDEO){
                sendCallEvent(CallEvent.RemoteCallParticipantEvent.VideoTrackUpdated(pub.publisher!!, it.stream as RemoteVideoStream))
            } else if(it.contentType == Stream.ContentType.DATA){
                (it.stream as? RemoteDataStream)?.let { dataStream ->
                    dataStream.onDataHandler = {message ->
                        val messageData = try {
                            Gson().fromJson(message, CallData::class.java)
                        } catch (ex : JsonSyntaxException){
                            null
                        }
                        messageData?.let {data ->
                            when(data.code){
                                MessageCode.Chat -> {
                                    sendCallEvent(CallEvent.Chat(pub.publisher!!, data.message))
                                }
                                MessageCode.BLUR -> {
                                    sendCallEvent(CallEvent.Blur(pub.publisher!!, data.message == "blur-enable"))
                                }
                                MessageCode.ScreenSharing -> {
                                    sendCallEvent(CallEvent.ScreenSharing(pub.publisher!!, data.message == "on"))
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

//    private suspend fun unsubscribe(memberId: String){
//        if(localMember != null) return
//        subs.forEach {
//            if(memberId == it.subscriber?.id){
//                val result = localMember!!.unsubscribe(it.id)
//                if(result){
//                    subs.remove(it)
//                }
//            }
//        }
//    }

    private fun leaveRoom(){
        callScope.launch {
//            unPublishAudio()
//            unPublishVideo()
            localMember?.leave()
            if (privateRoom != null){
                val metadata = Gson().toJson(RoomMetadata(state = "end"))
                privateRoom?.updateMetadata(metadata)
                privateRoom?.dispose()
            }
        }
    }

    fun startLocalStream(context: Context, videoEnable: Boolean, audioEnable: Boolean){
        callScope.launch {
            this@CallSession.cameraEnable = videoEnable
            this@CallSession.microphoneEnable = audioEnable

            publishVideo(context)
            publishAudio()
            publishDataStream()
        }
    }

    private suspend fun publishDataStream() {
        val localDataSource = DataSource()
        localDataStream = localDataSource.createStream()
        localDataStream?.let {
            dataPublication = localMember?.publish(it)
        }
    }

    private suspend fun unPublishDataStream() {
        withContext(Dispatchers.Main.immediate) {
            dataPublication?.let {
                localMember?.unpublish(it)
            }
            dataPublication = null
        }
    }

    fun setCameraEnable(enable: Boolean) {
        cameraEnable = enable
        callScope.launch {
            if(enable) videoPublication?.enable() else videoPublication?.disable()
        }
        if (isCustomCameraSource){
            UsbCameraSource.setCameraEnabled(enable)
        }
    }

    fun setMicrophoneEnable(enable: Boolean) {
        microphoneEnable = enable
        callScope.launch {
//            if (audioPublication == null){
//                publishAudio()
//            }
            if(enable) audioPublication?.enable() else audioPublication?.disable()
        }
    }

    fun switchCamera(context: Context) {
        val device = if (isFrontCamera) {
            CameraSource.getBackCameras(context).firstOrNull()
        } else {
            CameraSource.getFrontCameras(context).firstOrNull()
        }
        if (device.isNullOrEmpty()) return
        isFrontCamera = !isFrontCamera
        CameraSource.changeCamera(device)
    }

    private suspend fun publishVideo(context: Context){
        withContext(Dispatchers.Main.immediate) {
            val usbCameraSourceEnable = PrefUtils.getCameraSourceSetting(context) == CallSettingFragment.CAMERA_SOURCE_USB
            val videoResolution = PrefUtils.getVideoResolution(context)
            UsbCameraSource.initialize(context, videoResolution.width, videoResolution.height)
            if (usbCameraSourceEnable && UsbCameraSource.hasUsbCameraDevice()) {
                isCustomCameraSource = true
                
                localVideoStream = UsbCameraSource.createVideoStream()
                applyVideoProcessors(context, UsbCameraSource.source!!)
                
                val surfaceViewRenderer = SurfaceViewRenderer(context)
                surfaceViewRenderer.apply {
                    setup()
                    setBackgroundColor(Color.BLACK)
                    // Ensure local mirror is ON for natural selfie feeling
                    sink?.setMirror(true)
                    sink?.setZOrderMediaOverlay(true)
                }
                
                localVideoStream?.addRenderer(surfaceViewRenderer)
                localVideoView = surfaceViewRenderer
                
                // Start capture headless. 
                // Local view will be shown through SkyWay renderer (surfaceViewRenderer)
                UsbCameraSource.startHeadlessCapture()
            } else {
                isCustomCameraSource = false
                val captureOptionLow = CameraSource.CapturingOptions(640, 480, 15)
                val captureOptionNormal =
                    CameraSource.CapturingOptions(videoResolution.width, videoResolution.height)

                var deviceId = CameraSource.getFrontCameras(context).firstOrNull()
                if (deviceId == null) {
                    isFrontCamera = false
                    deviceId = CameraSource.getBackCameras(context).firstOrNull()
                        ?: CameraSource.getCameras(context).firstOrNull()
                }
                if (deviceId.isNullOrEmpty()) return@withContext
                CameraSource.startCapturing(
                    context,
                    deviceId,
                    if (isLoadReductionEnable) captureOptionLow else captureOptionNormal
                )
                localVideoStream = CameraSource.createStream()
                applyVideoProcessors(context, CameraSource)

                val surfaceViewRenderer = SurfaceViewRenderer(context)
                surfaceViewRenderer.apply {
                    setup()
                    setBackgroundColor(Color.BLACK)
                    // Ensure local mirror is ON for natural selfie feeling
                    sink?.setMirror(true)
                    sink?.setZOrderMediaOverlay(true)
                }
                
                localVideoStream?.addRenderer(surfaceViewRenderer)
                localVideoView = surfaceViewRenderer
            }
            sendCallEvent(
                CallEvent.LocalParticipantEvent.VideoTrackUpdated(
                    localVideoView,
                    localVideoStream
                )
            )

            val videoEncodings = listOf(Encoding("low", 100000, 4.0))
            videoPublication = localMember?.publish(
                localVideoStream!!,
                RoomPublication.Options(encodings = if (isLoadReductionEnable) videoEncodings else null)
            )
        }
    }

    private suspend fun unPublishVideo(){
        CameraSource.stopCapturing()
        withContext(Dispatchers.Main.immediate) {
            videoPublication?.let { localMember?.unpublish(it) }
            videoPublication = null
            cameraEnable = false
        }
    }

    private suspend fun publishAudio(){
        AudioSource.start()
        localAudioStream = AudioSource.createStream()
        localAudioStream?.let {
            audioPublication = localMember?.publish(it)
            if(microphoneEnable) audioPublication?.enable() else audioPublication?.disable()
        }
    }

    private suspend fun unPublishAudio(){
        withContext(Dispatchers.Main.immediate) {
            audioPublication?.let { localMember?.unpublish(it) }
            audioPublication = null
            microphoneEnable = false
            localAudioStream?.dispose()
            AudioSource.stop()
        }
    }

    private fun sendPushEvent(pushEvent: PushEvent){
        onPushEvent?.invoke(pushEvent)
    }

    fun makeCall(context: Context){
        callScope.launch {
            createOrJoinPrivateRoom(context)
        }
    }

    fun joinCall(context: Context){
        intervalJob?.cancel()
        callScope.launch {
            createOrJoinPrivateRoom(context)
        }
    }

    fun answerCall(){
        callScope.launch {
            val metadata = Gson().toJson(RoomMetadata(state = "in_call"))
            privateRoom?.updateMetadata(metadata)
            registerCallEvent()
            registerRemoteStream()
        }
    }

    private suspend fun registerRemoteStream(){
        if(privateRoom?.members.isNullOrEmpty().not()){
            privateRoom?.members?.forEach { member ->
                if(member is RemoteRoomMember && member.name == calleeId){
                    callState = CallState.IN_CALL
                    sendCallEvent(CallEvent.CallStarted(member))
                    member.publications.forEach {
                        subscribeTo(it)
                    }
                }
            }
        }
    }

    fun declineCall(){
        if(callState == CallState.WAITING || callState == CallState.CALLING){
            callScope.launch {
                val pushType = if(callType == CallType.INCOMING_CALL) PushEvent.Decline else
                    PushEvent.Cancel
                sendPushEvent(pushType)
            }
        }
        this.endCall(false)
    }

    fun sendDataEvent(callData: CallData){
        localDataStream?.write(Gson().toJson(callData))
    }

    fun endCall(sendEvent: Boolean, callEvent: CallEvent? = null){
        if(sendEvent){
            callScope.launch(Dispatchers.IO) {
                sendPushEvent(if (callState == CallState.CALLING || callState == CallState.WAITING)
                    PushEvent.Cancel else PushEvent.End)
            }
        }
        intervalJob?.cancel()
        sendCallEvent(callEvent ?: CallEvent.CallEnded)
        callState = CallState.END
        exitPrivateRoom()
    }

    private fun sendCallEvent(callEvent: CallEvent) {
        onCallEvent?.invoke(callEvent)
    }

    private fun releaseLocalTracks(){
        cpuVideoProcessor?.dispose()
        cpuVideoProcessor = null
        currentBackgroundBitmap?.recycle()
        currentBackgroundBitmap = null
        currentBackgroundPath = null
        
        localVideoStream?.removeAllRenderer()
        localVideoStream?.dispose()
        localDataStream?.dispose()
        localAudioStream?.dispose()
        localVideoStream = null
        localAudioStream = null
        localDataStream = null
        audioPublication = null
        videoPublication = null
        dataPublication = null
        AudioSource.stop()
        UsbCameraSource.stop()
        CameraSource.stopCapturing()
    }

    private fun exitPrivateRoom(){
        releaseLocalTracks()
        intervalJob?.cancel()
        intervalJob = null
        leaveRoom()
        CallManager.closeSession()
        callState = CallState.IDLE
    }

    fun inCall() : Boolean{
        return callState == CallState.CALLING || callState == CallState.IN_CALL
    }

    fun updateProcessors(context: Context) {
        val appContext = context.applicationContext
        val source = if (isCustomCameraSource) UsbCameraSource.source else CameraSource
        Log.d(TAG, "updateProcessors: source is ${if (isCustomCameraSource) "UsbCameraSource" else "CameraSource"}")
        source?.let { applyVideoProcessors(appContext, it) }
    }

    private fun applyVideoProcessors(context: Context, source: com.ntt.skyway.core.content.local.source.VideoSource) {
        val appContext = context.applicationContext
        val isBlur = PrefUtils.isBlurEnabled(appContext)
        val isVirtual = PrefUtils.isVirtualBackgroundEnabled(appContext)
        Log.e(TAG, "applyVideoProcessors starting (CPU)... Blur: $isBlur, Virtual: $isVirtual")
        
        if (cpuVideoProcessor != null) {
            // Incremental update to avoid re-initializing MediaPipe AI model
            try {
                cpuVideoProcessor?.blurStrength = if (isBlur) PrefUtils.getBlurRadius(appContext).coerceAtLeast(60) else 0
                
                if (isVirtual) {
                    var imagePath = PrefUtils.getVirtualBackgroundImage(appContext)
                    if (imagePath.isEmpty()) {
                        imagePath = "assets/backgrounds/bg_office.png"
                        PrefUtils.saveVirtualBackgroundImage(appContext, imagePath)
                    }
                    if (imagePath != currentBackgroundPath || currentBackgroundBitmap == null || currentBackgroundBitmap!!.isRecycled) {
                        currentBackgroundBitmap?.recycle()
                        currentBackgroundBitmap = try {
                            if (imagePath.startsWith("assets/")) {
                                appContext.assets.open(imagePath.removePrefix("assets/")).use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }
                            } else {
                                android.graphics.BitmapFactory.decodeFile(imagePath)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading background image $imagePath", e)
                            null
                        }
                        cpuVideoProcessor?.backgroundBitmap = currentBackgroundBitmap
                        currentBackgroundPath = imagePath
                    }
                    cpuVideoProcessor?.bgBlurStrength = if (PrefUtils.isVirtualBgBlurEnabled(appContext)) 60 else 0
                } else {
                    cpuVideoProcessor?.backgroundBitmap = null
                    currentBackgroundBitmap?.recycle()
                    currentBackgroundBitmap = null
                    currentBackgroundPath = null
                }
                
                // If both disabled, remove processor
                if (!isBlur && !isVirtual) {
                    source.removeVideoProcessor(cpuVideoProcessor!!)
                    cpuVideoProcessor?.dispose()
                    cpuVideoProcessor = null
                }
                Log.d(TAG, "CpuVideoProcessor updated incrementally")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error updating CPU video processor: ${e.message}", e)
                // Fallback: dispose and re-create if update fails
                source.removeVideoProcessor(cpuVideoProcessor!!)
                cpuVideoProcessor?.dispose()
                cpuVideoProcessor = null
            }
        }

        currentBackgroundBitmap?.recycle()
        currentBackgroundBitmap = null
        currentBackgroundPath = null
        cpuVideoProcessor = null

        if (isBlur || isVirtual) {
            try {
                if (isVirtual) {
                    var imagePath = PrefUtils.getVirtualBackgroundImage(appContext)
                    if (imagePath.isEmpty()) {
                        imagePath = "assets/backgrounds/bg_office.png"
                        PrefUtils.saveVirtualBackgroundImage(appContext, imagePath)
                    }
                    if (imagePath != currentBackgroundPath || currentBackgroundBitmap == null || currentBackgroundBitmap!!.isRecycled) {
                        currentBackgroundBitmap = try {
                            if (imagePath.startsWith("assets/")) {
                                appContext.assets.open(imagePath.removePrefix("assets/")).use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }
                            } else {
                                android.graphics.BitmapFactory.decodeFile(imagePath)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading background image $imagePath", e)
                            null
                        }
                        currentBackgroundPath = imagePath
                    }
                }

                val processor = CpuVideoProcessor(appContext)

                if (isBlur) {
                    processor.blurStrength = PrefUtils.getBlurRadius(appContext).coerceAtLeast(60)
                }

                if (isVirtual && currentBackgroundBitmap != null && !currentBackgroundBitmap!!.isRecycled) {
                    processor.backgroundBitmap = currentBackgroundBitmap
                    Log.d(
                        TAG,
                        "CpuVideoProcessor added with background: ${currentBackgroundBitmap?.width}x${currentBackgroundBitmap?.height}"
                    )
                    if (PrefUtils.isVirtualBgBlurEnabled(appContext)) {
                        processor.bgBlurStrength = 60
                    }
                }

                cpuVideoProcessor = processor
                source.addVideoProcessor(processor)
                Log.d(TAG, "CpuVideoProcessor added successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying CPU video processor: ${e.message}", e)
            }
        }
    }

}