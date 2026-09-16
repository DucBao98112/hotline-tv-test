package com.gc.waravi.skyway.room

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import android.graphics.Bitmap
import com.gc.waravi.CallData
import com.gc.waravi.MessageCode
import com.gc.waravi.skyway.UsbCameraSource
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.views.fragments.CallSettingFragment
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
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
import com.ntt.skyway.room.Room
import com.ntt.skyway.room.RoomPublication
import com.ntt.skyway.room.RoomSubscription
import com.ntt.skyway.room.member.LocalRoomMember
import com.ntt.skyway.room.member.RemoteRoomMember
import com.ntt.skyway.room.member.RoomMember
import com.ntt.skyway.room.p2p.P2PRoom
import com.ntt.skyway.room.sfu.SFURoom
import com.serenegiant.widget.AspectRatioSurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume

class RoomSession(private val context: Context, private val roomName: String,
                  private val member: RoomMember.Init, private val roomType: Room.Type){
    private val TAG = this.javaClass.simpleName
    var audioEnable : Boolean = true
        private set

    var cameraEnable : Boolean = true
        private set
    private var isLoadReductionEnable = false

    private val mutableRoomEvents: MutableSharedFlow<RoomEvent> = MutableSharedFlow()
    val roomEvents: SharedFlow<RoomEvent> = mutableRoomEvents

    private var _p2pRoom: P2PRoom? = null
    private var _sfuRoom: SFURoom? = null
    val room: Room
        get() = _p2pRoom ?: _sfuRoom
            ?: throw RuntimeException("Room could not be create")
    private val roomScope = CoroutineScope(Dispatchers.IO)
    private var localMember : LocalRoomMember? = null
    private var localAudioStream : LocalAudioStream? = null
    private var localDataStream : LocalDataStream? = null
    private var localVideoStream : LocalVideoStream? = null
//        set(value) {
//            field = value
//            sendEvent(RoomEvent.LocalParticipantEvent.VideoTrackUpdated(localVideoView, value))
//        }
    private var localVideoView: View? = null
    private var videoPublication: RoomPublication? = null
    private var audioPublication: RoomPublication? = null
    private var dataPublication: RoomPublication? = null
    private var subs : ArrayList<RoomSubscription> = arrayListOf()
    var isAudioPublished = false
    var isVideoPublished = false
    var isFrontCamera = true
    var isCustomCameraSource = false

    private var cpuVideoProcessor: CpuVideoProcessor? = null
    private var currentBackgroundPath: String? = null
    private var currentBackgroundBitmap: Bitmap? = null

    suspend fun createRoom(): Boolean{
        if(roomType == Room.Type.SFU){
            _sfuRoom = SFURoom.findOrCreate(roomName)
        } else {
            _p2pRoom = P2PRoom.findOrCreate(roomName)
        }
        if(_p2pRoom == null && _sfuRoom == null){
            val room = tryCreateRoomCoroutine()
            if (room != null){
                if (room.type == Room.Type.P2P){
                    _p2pRoom = room as P2PRoom?
                } else {
                    _sfuRoom = room as SFURoom?
                }
            }
        }
        Log.e("NQD", "ROOM: ${room.type}")
        return _p2pRoom != null || _sfuRoom != null
    }

    private suspend fun tryCreateRoomCoroutine() = suspendCancellableCoroutine{ continuation ->
        roomScope.launch {
            tryCreateRoom(delay = 500L, period = 1000L, numberOfAttempts = 10, onSuccess = { room ->
                continuation.resume(room)
            }, onFailure = {
                continuation.resume(null)
            })
        }
    }

    private suspend fun tryCreateRoom(
        delay: Long = 0L,
        period: Long,
        numberOfAttempts: Int,
        onSuccess: (Room) -> Unit,
        onFailure: () -> Unit
    ){
        var totalCount = 0
        var room : Room? = null
        delay(delay)
        while (room == null && totalCount < numberOfAttempts) {
            room = if(roomType == Room.Type.SFU){
                SFURoom.findOrCreate(roomName)
            } else{
                P2PRoom.findOrCreate(roomName)
            }
            Log.e(this.javaClass.simpleName, "tryCreateRoom: ${room?.name}")
            totalCount += 1
            if (room != null){
                onSuccess(room)
            } else if(totalCount == numberOfAttempts){
                onFailure()
            } else{
                delay(period)
            }
        }
    }

    fun joinRoom(forceLeave: Boolean = false){
        roomScope.launch {
            if (forceLeave) {
                val memberExist = room.members.find {
                    it.name == member.name
                }
                memberExist?.leave()
            }
            registerRoomEvent()
            localMember = room.join(member)

            if(room.members.isNotEmpty()){
                room.members.forEach { member ->
                    if(member is RemoteRoomMember){
                        addMember(member)
                        member.publications.forEach {
                            subscribeTo(it)
                        }
                    }
                }
            }
        }
    }

    private fun addMember(member: RemoteRoomMember){
        sendEvent(RoomEvent.RemoteParticipantEvent.RemoteParticipantConnected(member))
        member.onMetadataUpdatedHandler = { message ->
            handleDataMessage(member, message)
        }
    }

    private fun registerRoomEvent(){
        room.apply {
            onErrorHandler = {
                it.printStackTrace()
            }
            onMemberJoinedHandler = { member ->
                if(member is LocalRoomMember){
                    sendEvent(RoomEvent.Connected(room))
                } else if(member is RemoteRoomMember){
                    addMember(member)
                }
            }
            onMemberLeftHandler = {member ->
                if (member is RemoteRoomMember){
                    sendEvent(RoomEvent.RemoteParticipantEvent.RemoteParticipantDisconnected(member))
                }
            }
            onStreamPublishedHandler = { pub ->
                if (pub.publisher?.id != localMember?.id){
                    roomScope.launch {
                        subscribeTo(pub)
                    }
                }
            }
            onStreamUnpublishedHandler = {
                roomScope.launch{
                    it.publisher?.id?.let {
                        unsubscribe(it)
                    }
                }
            }
            onPublicationSubscribedHandler = { roomSubscription ->
            }
            onPublicationUnsubscribedHandler = { roomSubscription ->
            }
        }
    }

    private fun sendEvent(event: RoomEvent) {
        roomScope.launch {
            mutableRoomEvents.emit(event)
        }
    }

    fun isConnected(): Boolean{
        return localMember != null && localMember!!.state == Member.State.JOINED
    }

    private suspend fun subscribeTo(pub: RoomPublication){
        if(pub.publisher == null || pub.publisher?.id == localMember?.id) return
        val videoSubscribeOption = RoomSubscription.Options(preferredEncodingId = if (isLoadReductionEnable) "low" else "high")
        Log.d(this.javaClass.simpleName, "RoomSubscription: $isLoadReductionEnable / ${videoSubscribeOption.preferredEncodingId}")
        val sub = localMember?.subscribe(pub, if (pub.contentType == Stream.ContentType.VIDEO) videoSubscribeOption else null)
        sub?.let {
            subs.add(it)

            if(it.stream == null) return
            if(it.contentType == Stream.ContentType.VIDEO){
                if(pub.metadata.isNotEmpty()){
                    try {
                        val obj = JSONObject(pub.metadata)
                        val streamType = obj.get("streamType")
                        if (streamType == "SCREEN"){
                            sendEvent(RoomEvent.RemoteParticipantEvent.ScreenTrackUpdated(pub.publisher!!, it.stream as RemoteVideoStream))
                        } else {
                            sendEvent(RoomEvent.RemoteParticipantEvent.VideoTrackUpdated(pub.publisher!!, it.stream as? RemoteVideoStream))
                        }
                    } catch (ex: JSONException){
                        ex.printStackTrace()
                    }
                } else{
                    sendEvent(RoomEvent.RemoteParticipantEvent.VideoTrackUpdated(pub.publisher!!, it.stream as? RemoteVideoStream))
                }
            } else if (it.contentType == Stream.ContentType.DATA){
                (it.stream as? RemoteDataStream)?.onDataHandler = { message ->
                    handleDataMessage(pub.publisher!!, message)
                }
            }
        }
    }

    private fun handleDataMessage(publisher: RoomMember, message: String){
        val messageData = try {
            Gson().fromJson(message, CallData::class.java)
        } catch (ex: JsonSyntaxException) {
            null
        }
        messageData?.let { data ->
            when (data.code) {
                MessageCode.Chat -> {
                    sendEvent(RoomEvent.RemoteParticipantEvent.Chat(publisher, data.message))
                }

                MessageCode.BLUR -> {
                    sendEvent(RoomEvent.RemoteParticipantEvent.Blur(publisher,
                        data.message == "blur-enable"))
                }

                MessageCode.ScreenSharing -> {
                    sendEvent(RoomEvent.RemoteParticipantEvent.ScreenSharing(publisher, data.message == "on"))
                }

                else -> {}
            }
        }
    }

    private suspend fun unsubscribe(memberId: String){
        if(localMember != null) return
        subs.forEach {
            if(memberId == it.subscriber?.id){
                val result = localMember!!.unsubscribe(it.id)
                if(result){
                    subs.remove(it)
                }
            }
        }
    }

    private suspend fun unsubscribeAll(){
        if(localMember != null) return
        subs.forEach {
            localMember!!.unsubscribe(it.id)
        }
        subs.clear()
    }

    suspend fun leaveRoom() : Boolean{
        localVideoStream?.removeAllRenderer()
        localAudioStream?.dispose()
        localVideoStream?.dispose()
        localDataStream?.dispose()
        localAudioStream = null
        localVideoStream = null
        localDataStream = null
        CameraSource.stopCapturing()
        AudioSource.stop()
        UsbCameraSource.stop()
        
        cpuVideoProcessor?.dispose()
        cpuVideoProcessor = null
        currentBackgroundBitmap?.recycle()
        currentBackgroundBitmap = null
        currentBackgroundPath = null
        
        unsubscribeAll()
        return localMember?.leave() ?: false
    }

    fun startLocalStream(videoEnable: Boolean, audioEnable: Boolean){
        this.cameraEnable = videoEnable
        this.audioEnable = audioEnable
        this.isLoadReductionEnable = PrefUtils.getLoadReductionSetting(context)
        roomScope.launch {
            if(videoEnable){
                publishVideo()
            }
            if (audioEnable){
                publishAudio()
            }
            if (roomType == Room.Type.P2P){
                publishDataStream()
            }
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

    fun switchCamera() {
        if(isCustomCameraSource) return
        val deviceId = if (isFrontCamera) {
            CameraSource.getBackCameras(context).firstOrNull()
        } else {
            CameraSource.getFrontCameras(context).firstOrNull()
        }
        if (deviceId.isNullOrEmpty()) return
        isFrontCamera = !isFrontCamera
        CameraSource.changeCamera(deviceId)
    }

    fun enableLocalAudio() {
        this.audioEnable = true
        if (localMember == null) return
        roomScope.launch {
            audioPublication?.enable()
//            publishAudio()
        }
    }

    fun disableLocalAudio() {
        this.audioEnable = false
        if (localMember == null) return
        roomScope.launch {
            audioPublication?.disable()
//            unPublishAudio()
        }
    }

    fun enableLocalVideo() {
        this.cameraEnable = true
        if (localMember == null) return
        roomScope.launch {
            videoPublication?.enable()
            if (isCustomCameraSource){
                UsbCameraSource.setCameraEnabled(true)
            }
//            publishVideo()
        }
    }

    fun disableLocalVideo(){
        this.cameraEnable = false
        if (localMember == null) return
        roomScope.launch {
            videoPublication?.disable()
            if (isCustomCameraSource) {
                UsbCameraSource.setCameraEnabled(false)
            }
//            unPublishVideo()
        }
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

    private suspend fun publishVideo(){
        withContext(Dispatchers.Main.immediate) {
            val videoResolution = PrefUtils.getVideoResolution(context)
            val usbCameraSourceEnable = PrefUtils.getCameraSourceSetting(context) == CallSettingFragment.CAMERA_SOURCE_USB
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
                    sink?.setZOrderOnTop(true)
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
                    sink?.setMirror(true)
                    sink?.setZOrderOnTop(true)
                    sink?.setZOrderMediaOverlay(true)
//                clearImage()
                }
                
                localVideoStream?.addRenderer(surfaceViewRenderer)
                localVideoView = surfaceViewRenderer
            }
            sendEvent(
                RoomEvent.LocalParticipantEvent.VideoTrackUpdated(
                    localVideoView,
                    localVideoStream
                )
            )

            val videoEncodings = listOf(Encoding("low", 100000, 4.0))
            videoPublication = localMember?.publish(
                localVideoStream!!,
                RoomPublication.Options(encodings = if (isLoadReductionEnable) videoEncodings else null)
            )
            isVideoPublished = videoPublication != null
        }
    }

    private suspend fun unPublishVideo(){
        CameraSource.stopCapturing()
        withContext(Dispatchers.Main.immediate) {
            videoPublication?.let { localMember?.unpublish(it) }
            videoPublication = null
            isVideoPublished = false
        }
    }

    private suspend fun publishAudio(){
        AudioSource.start()
        localAudioStream = AudioSource.createStream()
        localAudioStream?.let { audioStream ->
            withContext(Dispatchers.Main.immediate) {
                audioPublication = localMember?.publish(audioStream)
            }
        }
        isAudioPublished = audioPublication != null
    }

    private suspend fun unPublishAudio(){
        AudioSource.stop()
        withContext(Dispatchers.Main.immediate) {
            audioPublication?.let { localMember?.unpublish(it) }
            audioPublication = null
            isAudioPublished = false
        }
    }

    fun sendRoomData(callData: CallData){
        val message = Gson().toJson(callData)
        if (roomType == Room.Type.P2P){
            localDataStream?.write(message)
        } else{
            roomScope.launch(Dispatchers.IO) {
                localMember?.updateMetadata(message)
            }
        }
    }
}