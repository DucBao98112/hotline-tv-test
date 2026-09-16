package com.gc.waravi.views.fragments

import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.core.view.get
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gc.waravi.MessageCode
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentInCallBinding
import com.gc.waravi.databinding.LayoutChatBinding
import com.gc.waravi.models.ChatMessage
import com.gc.waravi.models.RecentType
import com.gc.waravi.models.RoomMetadata
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.call.ARG_CALL_ACTION
import com.gc.waravi.skyway.call.ARG_CALL_ID
import com.gc.waravi.skyway.call.ARG_SHOULD_FINISH
import com.gc.waravi.skyway.call.CallEvent
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.skyway.call.CallType
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.SimpleSwipeListener
import com.gc.waravi.utils.Utils
import com.gc.waravi.views.activities.InCallActivity
import com.gc.waravi.views.adapters.GroupChatAdapter
import com.gc.waravi.views.dialogs.AvatarDialogFragment
import com.gc.waravi.views.dialogs.VolumeDialogFragment
import com.gc.waravi.views.viewmodels.InCallViewModel
import com.google.gson.Gson
import android.graphics.RenderEffect
import android.graphics.Shader
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.core.content.sink.SurfaceViewRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InCallFragment : BaseFragment<FragmentInCallBinding>() {
    private lateinit var chatBinding: LayoutChatBinding
    private var callType: CallType = CallType.INCOMING_CALL
    private var callId: String? = null
    private var localVideoFrameWidth = 0f
    private var localVideoFrameHeight = 0f
    private var activeMode = false
    private var screenSize = Pair(0f, 0f)
    private lateinit var chatAdapter: GroupChatAdapter
    private lateinit var chatOverlayAdapter: GroupChatAdapter
    private var unreadMessageCount: Int = 0

    private val globalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            binding.myVideo.viewTreeObserver.removeOnGlobalLayoutListener(this)
            localVideoFrameWidth = binding.myVideo.width.toFloat()
            localVideoFrameHeight = binding.myVideo.height.toFloat()
        }
    }

    private val inCallViewModel: InCallViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            callType = it.get(ARG_CALL_ACTION) as CallType
            callId = it.getString(ARG_CALL_ID)
            callId?.let { id ->
                inCallViewModel.getContact(id)
            }
        }
        this.subscribeToCallEvents()
    }

    private fun subscribeToCallEvents() {
        CallManager.callEvents.let { sharedFlow ->
            lifecycleScope.launch {
                sharedFlow.collect { observeCallEvents(it) }
            }
        }
    }

    private fun observeCallEvents(callEvent: CallEvent) {
        when(callEvent){
            is CallEvent.Connected -> {
                if (callType == CallType.OUTGOING_CALL){
                    CallManager.startLocalStream(requireContext())
                }

//                lifecycleScope.launch(Dispatchers.Main) {
//                    val delay = if(callType == CallType.INCOMING_CALL) 2000L else 1000L
//                    delay(delay)
//                    CallManager.startLocalStream(requireContext())
//                }
            }
            is CallEvent.CallEnded, CallEvent.CallCanceled, CallEvent.CallRejected -> {
                stopSound()
                this.onCallCanceled(callEvent)
            }
            is CallEvent.CalleeBusy -> {
                stopSound()
                playSound(R.raw.busy)
                this.onCallCanceled(callEvent)
            }
            is CallEvent.CallStarted -> {
                stopSound()
                val metadata = if(callEvent.participant.metadata != null) Gson().fromJson(callEvent.participant.metadata, RoomMetadata::class.java)
                    else null
                binding.groupOutgoing.visibility = View.GONE
                binding.theirVideo.visibility = View.VISIBLE
                binding.lblTheirId.text = inCallViewModel.contact.value?.name ?: callEvent.participant.name
                (callEvent.participant.publications.firstOrNull()?.stream as? RemoteVideoStream)?.let {
                    addRenderVideo(it, metadata?.mirror == true)
                }
            }
            is CallEvent.RemoteCallParticipantEvent.VideoTrackUpdated -> {
                val metadata = if(callEvent.publisher.metadata != null) Gson().fromJson(callEvent.publisher.metadata, RoomMetadata::class.java)
                else null
                binding.groupOutgoing.visibility = View.GONE
                callEvent.videoTrack?.let { addRenderVideo(it, metadata?.mirror == true) }
            }
            is CallEvent.VideoTrackSwitched -> {
                binding.theirVideo.visibility = if (callEvent.isEnable) View.VISIBLE else View.INVISIBLE
            }

            is CallEvent.Chat -> {
                val msg = callEvent.data
                val message = ChatMessage(
                    message = msg, timeStamp = System.currentTimeMillis(),
                    sender = callId ?: "", type = 0, isMine = false
                )
                this.addMessageChat(message)
                if (false == chatBinding.layoutMessage.tag) {
                    unreadMessageCount += 1
                    updateUnreadMessageCount()
                }
            }

            is CallEvent.Blur -> {
                toggleBlur(callEvent.isEnable)
            }
            is CallEvent.LocalParticipantEvent.VideoTrackUpdated -> {
                if (callEvent.videoTrack != null && callEvent.videoView != null){
                    val videoView = callEvent.videoView as? SurfaceViewRenderer
                    videoView?.setZOrderMediaOverlay(true)
                    val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    lp.gravity = Gravity.CENTER
                    binding.myVideo.removeAllViews()
                    binding.myVideo.addView(callEvent.videoView, lp)
                }
            }
            is CallEvent.Error ->{
                stopSound()
                this.onCallCanceled(callEvent)
            }
            else -> {}
        }
    }

    private fun addRenderVideo(videoTrack: RemoteVideoStream, mirror: Boolean = false){
        binding.theirVideo.visibility = View.VISIBLE
        if (!binding.theirVideo.isSetup) {
            binding.theirVideo.setup()
            binding.theirVideo.setScalingType(SurfaceViewRenderer.ScalingType.SCALE_ASPECT_FIT)
        }
        binding.theirVideo.setMirror(mirror)
        binding.theirVideo.setZOrderMediaOverlay(false)
        videoTrack.addRenderer(binding.theirVideo)
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentInCallBinding {
        binding = FragmentInCallBinding.inflate(inflater, container, false)
        chatBinding = binding.layoutMessage
        return binding
    }

    override fun initViews() {
        binding.lblCallId.text = callId
        inCallViewModel.contact.observe(viewLifecycleOwner){ contact ->
            binding.lblCallId.text = contact.name
        }
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            closeChatLayout()
        }
        when (callType) {
            CallType.INCOMING_CALL -> {
                binding.groupOutgoing.visibility = View.GONE
            }
            CallType.OUTGOING_CALL -> {
                binding.groupOutgoing.visibility = View.VISIBLE
                binding.theirVideo.visibility = View.GONE
                requestAudioFocus()
                playSound(R.raw.dialing, true)
            }
        }

        binding.viewActions.btnEndCall.setOnClickListener {
            endCall()
        }
        //init microphone state
        setMicState(if(callType == CallType.OUTGOING_CALL) true else CallManager.microphoneEnable)

        //init camera state
        val cameraEnable = when(PrefUtils.getCameraSettingState(requireContext())){
            0 -> PrefUtils.getCameraState(requireContext())
            1 -> true
            2 -> false
            else -> true
        }
        setCameraState(if(callType == CallType.OUTGOING_CALL) cameraEnable else CallManager.cameraEnable)

        this.initViewActions()
        this.initChatLayout()
        this.initLocalVideoFrame()
    }

    private fun endCall(){
        CallManager.endCall(true)
        revertVolume()
        releaseMediaAndSound()
        showEndingPopup(getString(R.string.msg_call_end))
    }

    private fun onCallCanceled(callEvent: CallEvent){
        this.revertVolume()
        this.abandonAudioFocus()
        val message = getString(
            when (callEvent) {
                is CallEvent.CallCanceled, CallEvent.CallRejected -> R.string.msg_call_rejected
                is CallEvent.CalleeBusy -> R.string.msg_incoming_call_busy
                is CallEvent.Error -> R.string.msg_something_wrong
                else -> R.string.msg_call_end
            }
        )
        this.showEndingPopup(message)
    }

    private fun toggleBlur(isApply: Boolean){
        if (isApply) {
            binding.theirVideo.alpha = 0.6f
            // Chỉ scale nhẹ để tránh mất khung ảnh
            binding.theirVideo.scaleX = 1.1f
            binding.theirVideo.scaleY = 1.1f
            binding.theirVideoMask.visibility = View.VISIBLE
        } else {
            binding.theirVideo.alpha = 1.0f
            binding.theirVideo.scaleX = 1.0f
            binding.theirVideo.scaleY = 1.0f
            binding.theirVideoMask.visibility = View.GONE
        }
    }

    private fun initViewActions() {
        binding.viewActions.viewCamera.setOnClickListener {
            val isEnable = CallManager.cameraEnable
            setCameraState(!isEnable)
            CallManager.cameraEnable = !isEnable
        }
        binding.viewActions.viewMicrophone.setOnClickListener {
            val isEnable = CallManager.microphoneEnable
            setMicState(!isEnable)
            CallManager.microphoneEnable = !isEnable
        }
        binding.viewActions.viewSwitchCamera.setOnClickListener {
            showBackgroundSelection()
        }
        binding.viewActions.viewBackground.setOnClickListener {
            showBackgroundSelection()
        }

        binding.viewActions.viewVolume.setOnClickListener {
            showVolumeSettingDialog()
        }

        binding.viewActions.viewCamera.setOnLongClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return@setOnLongClickListener false
            if (binding.theirVideo.isSetup){
                binding.theirVideo.sink?.let { videoView ->
                    Utils.usePixelCopy(videoView){
                        it?.let {
                            lifecycleScope.launch(Dispatchers.Main){
                                val avatarDialog = AvatarDialogFragment.newInstance(callId ?: "", it)
                                avatarDialog.show(childFragmentManager, "dialog-avatar")
                            }
                        }
                    }
                }
            }
            return@setOnLongClickListener false
        }

        val childCount = binding.viewActions.root.childCount
        for (i in 0 until childCount){
            val view = binding.viewActions.root[i]
            view.nextFocusUpId = binding.layoutVideo.id
        }
    }

    private fun showBackgroundSelection() {
        val bottomSheet = com.gc.waravi.views.dialogs.BackgroundSelectionBottomSheet()
        bottomSheet.show(childFragmentManager, "background_selection")
    }

    private fun showVolumeSettingDialog() {
        val dialogFragment = VolumeDialogFragment()
        dialogFragment.show(childFragmentManager, "VolumeDialog")
    }

    private fun setCameraState(isEnable: Boolean) {
        binding.viewActions.btnToggleCamera.setImageLevel(if (isEnable) 0 else 1)
        binding.viewActions.btnToggleCamera.isActivated = !isEnable
        binding.viewActions.tvToggleCamera.text =
            getString(if (isEnable) R.string.lbl_camera_on else R.string.lbl_camera_off)
    }

    private fun setMicState(isEnable: Boolean) {
        binding.viewActions.btnToggleMic.setImageLevel(if (isEnable) 0 else 1)
        binding.viewActions.btnToggleMic.isActivated = !isEnable
        binding.viewActions.tvToggleMic.text =
            getString(if (isEnable) R.string.lbl_mic_on else R.string.lbl_mic_off)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if(requireActivity() is InCallActivity){
            (requireActivity() as InCallActivity).adjustCallVolume()
        }
        when (callType) {
            CallType.INCOMING_CALL -> {
                CallManager.acceptCall()
                CallManager.startLocalStream(requireContext())
            }
            CallType.OUTGOING_CALL -> {
                lifecycleScope.launch {
                    val isConnected = Utils.isOnline()
                    if (isConnected) {
                        callId?.let {
                            CallManager.makeCall(requireContext(), it)
                        }
                        lifecycleScope.launch {
                            inCallViewModel.saveCallRecent(
                                callId!!,
                                RecentType.Outgoing)
                        }
                    } else {
                        showSnackBar(R.string.msg_check_network)
                    }
                }

            }
        }
//        lifecycleScope.launch(Dispatchers.Main) {
//            CallManager.startLocalStream(requireContext())
//        }
    }

    private fun showEndingPopup(message: String){
        val bundle =
            bundleOf(ARG_MESSAGE to message, ARG_SHOULD_FINISH to true, ARG_AUTO_CLOSE to true)
        try {
            if (isAdded) {
                findNavController().navigate(R.id.PopUp, bundle)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun updateUnreadMessageCount() {
        binding.viewActions.tvChatCount.text =
            if (unreadMessageCount <= 99) unreadMessageCount.toString() else "99+"
        binding.viewActions.tvChatCount.visibility =
            if (unreadMessageCount > 0) View.VISIBLE else View.INVISIBLE
    }

    private fun initLocalVideoFrame() {
        val gestureDetector = GestureDetector(requireContext(), object : SimpleSwipeListener() {
            override fun onSwipe(direction: Direction): Boolean {
                moveVideoView(direction)
                return false
            }
        })
        val metrics: DisplayMetrics = this.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        screenSize = Pair(width, height)
        binding.myVideo.tag = 1
        binding.layoutVideo.setOnKeyListener { _, keyCode, event ->
            if (!activeMode) return@setOnKeyListener false

            if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER){
                binding.layoutVideo.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
                return@setOnKeyListener false
            }

            if(event.action == KeyEvent.ACTION_UP) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        moveVideoView(SimpleSwipeListener.Direction.up)
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        moveVideoView(SimpleSwipeListener.Direction.down)
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        moveVideoView(SimpleSwipeListener.Direction.left)
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        moveVideoView(SimpleSwipeListener.Direction.right)
                    }
                }
            }
            true
        }
        binding.layoutVideo.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                binding.layoutVideo.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }
        binding.layoutVideo.requestFocus()
        binding.layoutVideo.setOnClickListener {
            val isActivated = binding.myVideo.isActivated
            activeMovingMode(it, !isActivated)
        }
        binding.layoutVideo.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

        binding.myVideo.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    private fun moveVideoView(direction: SimpleSwipeListener.Direction) {
        val width = screenSize.first
        val height = screenSize.second
        val localWidth = localVideoFrameWidth
        val localHeight = localVideoFrameHeight
        val layoutActionHeight = resources.getDimension(R.dimen.view_menu_item_height) + 5f

        val position = binding.myVideo.tag as Int
        when (direction) {
            SimpleSwipeListener.Direction.up -> {
                if (position == 0 || position == 1) {
                    val animation = binding.layoutVideo.animate().y(-1f)
                    if(position == 0){
                        animation.x(width - localWidth)
                    }
                    animation.start()
                    binding.myVideo.tag = if (position == 0) 3 else 2
                }
            }
            SimpleSwipeListener.Direction.down -> {
                if (position == 2 || position == 3) {
                    val animation = binding.layoutVideo.animate().y(height - localHeight - layoutActionHeight)
                    if(position == 3){
                        animation.x(width - localWidth)
                    }
                    animation.start()
                    binding.myVideo.tag = if (position == 2) 1 else 0
                }
            }
            SimpleSwipeListener.Direction.left -> {
                if (position == 0 || position == 3 || position == 5) {
                    val distance = when(position){
                        0, 5 -> -1f
                        3 -> (width/2) - (localWidth/2)
                        else -> 0f
                    }
                    binding.layoutVideo.animate().x(distance).start()
                    binding.myVideo.tag = if (position == 0) 1 else if(position == 3) 5 else 2
                }
            }
            SimpleSwipeListener.Direction.right -> {
                if (position == 1 || position == 2 || position == 5) {
                    val distance = when(position){
                        1 -> width - localWidth
                        2 -> (width/2) - (localWidth/2)
                        5 -> width - localWidth
                        else -> 0f
                    }
                    binding.layoutVideo.animate().x(distance).start()
                    binding.myVideo.tag = if (position == 1) 0 else if (position == 2) 5 else 3
                }
            }
        }
        updateArrows(binding.myVideo.tag as Int)
    }

    private fun activeMovingMode(view: View, isActive: Boolean) {
        binding.viewFrame.isActivated = isActive
        view.isActivated = isActive
        activeMode = isActive
        updateArrows(binding.myVideo.tag as Int)
    }

    private fun updateArrows(position: Int) {
        binding.ivArrowLeft.visibility =
            if (activeMode && (position == 0 || position == 3 || position == 5)) View.VISIBLE else View.GONE
        binding.ivArrowRight.visibility =
            if (activeMode && (position == 1 || position == 2 || position == 5)) View.VISIBLE else View.GONE
        binding.ivArrowUp.visibility =
            if (activeMode && (position == 0 || position == 1)) View.VISIBLE else View.GONE
        binding.ivArrowDown.visibility =
            if (activeMode && (position == 2 || position == 3)) View.VISIBLE else View.GONE
    }

    private fun initChatLayout() {
        chatBinding.layoutMessage.tag = false
        chatAdapter = GroupChatAdapter()
        chatOverlayAdapter = GroupChatAdapter(isOverlay = true)

        binding.rcvChatOverlay.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, true)
        binding.rcvChatOverlay.adapter = chatOverlayAdapter

        chatBinding.rcvChat.layoutManager =
            object : LinearLayoutManager(context, RecyclerView.VERTICAL, true) {
                override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
                    if (direction == View.FOCUS_DOWN) {
                        val pos = getPosition(focused)
                        if (pos == 0)
                            return if (true == chatBinding.layoutMessage.tag) chatBinding.edtMessage else binding.viewActions.viewChat
                    }
                    return super.onInterceptFocusSearch(focused, direction)
                }
            }
        chatBinding.chatFrame.setOnFocusSearchListener { focused, direction ->
            return@setOnFocusSearchListener if (chatBinding.rcvChat.hasFocus())
                focused
            else chatBinding.edtMessage
        }

        chatBinding.rcvChat.adapter = chatAdapter

        chatBinding.edtMessage.setOnEditorActionListener { v, actionId, event ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEND -> {
                    sendMessage()
                    true
                }
                else -> false
            }
        }
        chatBinding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.viewActions.viewChat.setOnClickListener {
            val isShowing = true == chatBinding.layoutMessage.tag
            if (isShowing) {
                closeChatLayout()
            } else {
                showChatLayout()
            }
        }
        chatBinding.btnBack.setOnClickListener {
            this.closeChatLayout()
        }
        chatBinding.layoutMessage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, (view.width + 20f).toInt(), view.height, 20f)
            }
        }
        chatBinding.layoutMessage.clipToOutline = true

        enableChatLayout(false)

    }

    private fun showChatLayout() {
        this.enableChatLayout(true)
        chatBinding.layoutMessage.tag = true
        chatBinding.layoutMessage.animate().translationX(0f).start()
        binding.rcvChatOverlay.visibility = View.GONE
        chatBinding.edtMessage.requestFocus()
        unreadMessageCount = 0
        updateUnreadMessageCount()
        switchLayoutFocus()

        // Apply Glassmorphism effect on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            chatBinding.viewGlassBg.setRenderEffect(
                RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP)
            )
        }
    }

    private fun closeChatLayout() {
        this.enableChatLayout(false)
        val width = resources.getDimension(R.dimen.chat_layout_width)
        chatBinding.layoutMessage.tag = false
        chatBinding.layoutMessage.animate().translationX(width).start()
        // binding.rcvChatOverlay.visibility = View.VISIBLE
        binding.viewActions.viewChat.requestFocus()
        switchLayoutFocus()
    }

    private fun switchLayoutFocus() {
        val nextFocusUpId =
            if (chatBinding.layoutMessage.tag == true) chatBinding.edtMessage.id else binding.viewFrame.id
        binding.viewActions.viewChat.nextFocusUpId = nextFocusUpId
        binding.viewActions.btnEndCall.nextFocusUpId = nextFocusUpId
        binding.viewActions.viewMicrophone.nextFocusUpId = nextFocusUpId
        binding.viewActions.viewCamera.nextFocusUpId = nextFocusUpId
        binding.viewActions.viewSwitchCamera.nextFocusUpId = nextFocusUpId
        binding.viewActions.viewVolume.nextFocusUpId = nextFocusUpId
        binding.viewActions.viewBackground.nextFocusUpId = nextFocusUpId
    }

    private fun enableChatLayout(isEnable: Boolean) {
        (0 until chatBinding.layoutMessage.childCount).forEach { index ->
            chatBinding.layoutMessage.getChildAt(index).isEnabled = isEnable
        }
        chatBinding.chatFrame.descendantFocusability =
            if (isEnable) ViewGroup.FOCUS_BEFORE_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun sendMessage() {
        val msgText = chatBinding.edtMessage.text
        if (msgText.isNullOrEmpty()) {
            return
        }
        val messageStr = msgText.toString().trim()
        val message = ChatMessage(
            message = messageStr, timeStamp = System.currentTimeMillis(),
            sender = SkywayManager.selfId, type = 0, isMine = true
        )
        this.addMessageChat(message)
        chatBinding.edtMessage.text.clear()
        chatBinding.edtMessage.requestFocus()
        CallManager.sendMessage(messageStr)
    }

    private fun addMessageChat(message: ChatMessage) {
        chatAdapter.addMessage(message)
        chatOverlayAdapter.addMessage(message)
        chatBinding.rcvChat.smoothScrollToPosition(0)
        
        if (false == chatBinding.layoutMessage.tag) {
            showOverlayChat()
        }
    }

    private var hideOverlayJob: Job? = null
    private fun showOverlayChat() {
        binding.rcvChatOverlay.visibility = View.VISIBLE
        binding.rcvChatOverlay.smoothScrollToPosition(0)
        
        hideOverlayJob?.cancel()
        hideOverlayJob = lifecycleScope.launch {
            delay(10000L) // Show for 10 seconds
            binding.rcvChatOverlay.visibility = View.GONE
        }
    }

    private fun releaseMediaAndSound() {
        this.abandonAudioFocus()
        this.stopSound()
    }

    private fun revertVolume(){
        if(requireActivity() is InCallActivity){
            (requireActivity() as InCallActivity).revertCallVolume()
        }
    }

    override fun onLongKeyEvent(keyEvent: KeyEvent) {
        if(lifecycle.currentState == Lifecycle.State.RESUMED) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_MOVE_END -> {
                    this.endCall()
                }
            }
        }
    }

    override fun onDestroyView() {
//        CallManager.endCall()
        this.abandonAudioFocus()
        super.onDestroyView()
    }

    companion object {
        fun newInstance(bundle: Bundle?) = InCallFragment().apply {
            arguments = bundle
        }
    }

}
