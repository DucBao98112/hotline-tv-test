package com.gc.waravi.views.fragments

import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gc.waravi.*
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentDefaultMeetingBinding
import com.gc.waravi.databinding.LayoutChatBinding
import com.gc.waravi.databinding.LayoutRoomActionsBinding
import com.gc.waravi.models.ChatMessage
import com.gc.waravi.models.ParticipantViewState
import com.gc.waravi.models.buildParticipantViewState
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.skyway.room.RoomEvent
import com.gc.waravi.skyway.room.RoomEvent.LocalParticipantEvent
import com.gc.waravi.skyway.room.RoomManager
import com.gc.waravi.skyway.room.RoomSession
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.SimpleSwipeListener
import com.gc.waravi.utils.Utils
import com.gc.waravi.views.activities.HomeActivity
import com.gc.waravi.views.adapters.GroupChatAdapter
import com.gc.waravi.views.adapters.GroupVideoAdapter
import com.gc.waravi.views.adapters.GroupVideoViewHolder
import com.gc.waravi.views.adapters.OnVideoItemClickListener
import com.gc.waravi.views.dialogs.VolumeDialogFragment
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import android.graphics.RenderEffect
import android.graphics.Shader
import com.ntt.skyway.core.content.Stream
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.core.content.sink.SurfaceViewRenderer
import com.ntt.skyway.room.member.RemoteRoomMember
import kotlinx.coroutines.*

/**
 *　デフォルトのルームビデオ画面
 */
class DefaultRoomMeetingFragment : BaseFragment<FragmentDefaultMeetingBinding>() {
    companion object {
        const val ARG_GROUP_ID = "group-id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupId = it.getString(ARG_GROUP_ID) ?: ""
        }
    }

    private lateinit var roomSession: RoomSession
    private var groupId: String = ""
    private lateinit var videoAdapter: GroupVideoAdapter
    private lateinit var chatAdapter: GroupChatAdapter
    private lateinit var menuBinding: LayoutRoomActionsBinding
    private var unreadMessageCount: Int = 0
    private var isInFullScreenMode: Boolean = false
    private var fullScreenStreamId: String? = null
    private lateinit var chatBinding: LayoutChatBinding
    private var localVideoSize = Pair(0f, 0f)
    private var screenSize = Pair(0f, 0f)
    private var activeMode = false

    private val globalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            binding.myVideo.viewTreeObserver.removeOnGlobalLayoutListener(this)
            val width = binding.myVideo.width.toFloat()
            val height = binding.myVideo.height.toFloat()
            localVideoSize = Pair(width, height)
        }
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentDefaultMeetingBinding {
        val binding = FragmentDefaultMeetingBinding.inflate(inflater, container, false)
        menuBinding = binding.layoutMenu
        chatBinding = binding.layoutChat
        return binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (requireActivity() is HomeActivity) {
            (requireActivity() as HomeActivity).adjustCallVolume()
        }
        if (groupId.isNotEmpty()) {
            requestAudioFocus()
            binding.lblRoomId.text = String.format(getString(R.string.lbl_group_id), groupId)
//            val optionFragment = GroupOptionDialogFragment()
//            optionFragment.show(childFragmentManager, null)
//            optionFragment.setOnButtonListener(object : OnJoinButtonClickListener {
//                override fun onJoinButtonClick(roomMode : Topology) {
//                    initOptionState()
//                    lifecycleScope.launch {
//                        roomSession.connect(groupId)
//                    }
//                    optionFragment.dismiss()
//                }
//
//                override fun onCancel() {
//                    findNavController().popBackStack()
//                }
//            })
            this.initOptionState()
            this.createAndJoinRoom()
        }
    }

    private fun createAndJoinRoom() {
        lifecycleScope.launch {
            RoomManager.createP2pRoomSession(requireContext(), groupId) { session ->
                if (session != null) {
                    roomSession = session
                    roomSession.roomEvents.let { sharedFlow ->
                        lifecycleScope.launch {
                            sharedFlow.collect { observeRoomEvents(it) }
                        }
                    }
                    roomSession.joinRoom()
                    CallManager.roomSession = roomSession
                    CallManager.isInRoom = true
                } else {
                    showSnackBar(R.string.msg_something_wrong)
                    leaveRoom()
                }
            }
        }
    }

//    private fun subscribeToRoomEvents() {
//        roomSession.roomEvents.let { sharedFlow ->
//            lifecycleScope.launch {
//                sharedFlow.collect { observeRoomEvents(it) }
//            }
//        }
//    }

    private fun observeRoomEvents(roomEvent: RoomEvent) {
        when (roomEvent) {
            is RoomEvent.ConnectFailure, RoomEvent.MaxParticipantFailure -> {
                showSnackBar(R.string.msg_something_wrong)
                leaveRoom()
            }

            is RoomEvent.Connecting -> {
//                showConnectingViewState()
            }

            is RoomEvent.Connected -> {
                Log.e("NQD", "onConnected...${roomEvent.room.name}")
                val cameraEnable = when (PrefUtils.getCameraSettingState(requireContext())) {
                    0 -> PrefUtils.getCameraState(requireContext())
                    1 -> true
                    2 -> false
                    else -> true
                }
                val cameraDrawable =
                    if (cameraEnable) R.drawable.talk_icn_video else R.drawable.talk_icn_video_off
                menuBinding.labelVideo.setDrawableStart(cameraDrawable, 58, 50)

                lifecycleScope.launch(Dispatchers.Main) {
                    delay(1500L)
                    roomSession.startLocalStream(cameraEnable, true)
                }
            }

            is RoomEvent.RemoteParticipantEvent -> handleRemoteParticipantEvent(roomEvent)
            is LocalParticipantEvent -> handleLocalParticipantEvent(roomEvent)
            else -> {}
        }
    }

    private fun handleLocalParticipantEvent(localParticipantEvent: LocalParticipantEvent) {
        when (localParticipantEvent) {
            is LocalParticipantEvent.VideoTrackUpdated -> {
                if (localParticipantEvent.videoTrack != null && localParticipantEvent.videoView != null) {
                    val lp = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    lp.gravity = Gravity.CENTER
                    binding.myVideo.addView(localParticipantEvent.videoView, lp)
                }
            }

            else -> {}
//            AudioOn -> updateState { currentState -> currentState.copy(isAudioMuted = false) }
//            AudioOff -> updateState { currentState -> currentState.copy(isAudioMuted = true) }
//            AudioEnabled -> updateState { currentState -> currentState.copy(isAudioEnabled = true) }
//            AudioDisabled -> updateState { currentState -> currentState.copy(isAudioEnabled = false) }
//            VideoEnabled -> updateState { currentState -> currentState.copy(isVideoEnabled = true) }
//            VideoDisabled -> updateState { currentState -> currentState.copy(isVideoEnabled = false) }
        }
    }

    private fun handleRemoteParticipantEvent(remoteParticipantEvent: RoomEvent.RemoteParticipantEvent) {
        when (remoteParticipantEvent) {
            is RoomEvent.RemoteParticipantEvent.RemoteParticipantConnected -> {
                val participant = remoteParticipantEvent.participant
                videoAdapter.addParticipant(
                    buildParticipantViewState(
                        participant,
                        participant.id, participant.name ?: participant.id
                    )
                )
            }

            is RoomEvent.RemoteParticipantEvent.VideoTrackUpdated -> {
                remoteParticipantEvent.videoTrack?.let {
                    videoAdapter.updateVideoTrack(remoteParticipantEvent.publisher.id, it)
                }
            }

            is RoomEvent.RemoteParticipantEvent.RemoteParticipantDisconnected -> {
                videoAdapter.removeParticipant(remoteParticipantEvent.participant.id)
            }

            is RoomEvent.RemoteParticipantEvent.Chat -> {
                val publisher = remoteParticipantEvent.publisher
                val message = ChatMessage(
                    message = remoteParticipantEvent.data, timeStamp = System.currentTimeMillis(),
                    sender = publisher.name ?: publisher.id, type = 0, isMine = false
                )
                this.addMessageChat(message)
                if (false == chatBinding.layoutMessage.tag) {
                    unreadMessageCount += 1
                    updateUnreadMessageCount()
                    Toast.makeText(
                        context,
                        String.format("%s: %s", publisher.name, message.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            is RoomEvent.RemoteParticipantEvent.Blur -> {
                toggleBlur(remoteParticipantEvent.publisher.id, remoteParticipantEvent.isEnable)
            }

            is RoomEvent.RemoteParticipantEvent.ScreenTrackUpdated -> {
                val id = "screen_${remoteParticipantEvent.publisher.id}"
                val name = "${remoteParticipantEvent.publisher.name}'s screen"
                val viewState = buildParticipantViewState(
                    remoteParticipantEvent.publisher as RemoteRoomMember,
                    id,
                    name,
                    true
                )
                videoAdapter.addParticipant(viewState)
                videoAdapter.updateVideoTrack(viewState.id, remoteParticipantEvent.screenTrack)
                enterFullScreenMode(viewState.id, viewState.isMirrored)
            }

            is RoomEvent.RemoteParticipantEvent.ScreenSharing -> {
                val participantId = remoteParticipantEvent.publisher.id
                if (!remoteParticipantEvent.isOn) {
                    videoAdapter.removeParticipant("screen_$participantId")
                    this.exitFullScreenMode()
                }
            }

            else -> {}
        }
    }

    private fun toggleBlur(identify: String, isApply: Boolean) {
        videoAdapter.enableBlurring(identify, isApply)
        if (isInFullScreenMode && identify == fullScreenStreamId) {
            if (isApply) {
                binding.fullView.alpha = 0.6f
                binding.fullViewMask.visibility = View.VISIBLE
            } else {
                binding.fullView.alpha = 1.0f
                binding.fullViewMask.visibility = View.GONE
            }
        }

        val viewHolder = getViewHolderByPeerId(identify) ?: return
        if (isApply) {
            viewHolder.binding.viewFrame.alpha = 0.6f
            viewHolder.binding.viewMask.visibility = View.VISIBLE
        } else {
            viewHolder.binding.viewFrame.alpha = 1.0f
            viewHolder.binding.viewMask.visibility = View.GONE
        }
    }

    private fun showVolumeSettingDialog() {
        val dialogFragment = VolumeDialogFragment()
        dialogFragment.show(childFragmentManager, "VolumeDialog")
    }

    private fun addMessageChat(message: ChatMessage) {
        chatAdapter.addMessage(message)
        chatBinding.rcvChat.smoothScrollToPosition(0)
    }

    override fun initViews() {
        menuBinding.btnMenu.tag = false
        binding.fullView.setup()
        binding.fullView.setScalingType(SurfaceViewRenderer.ScalingType.SCALE_ASPECT_FILL)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (true == chatBinding.layoutMessage.tag) {
                closeChatLayout()
            } else if (isInFullScreenMode) {
                exitFullScreenMode()
            } else {
                menuBinding.btnMenu.requestFocus()
            }
        }
        val metrics: DisplayMetrics = this.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()
        val layoutManager = object : FlexboxLayoutManager(context, FlexDirection.ROW) {
            override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
                if (direction == View.FOCUS_RIGHT || direction == View.FOCUS_DOWN) {
                    val pos = getPosition(focused)
                    if (pos == childCount - 1)
                        return menuBinding.btnMenu
                }
                return super.onInterceptFocusSearch(focused, direction)
            }
        }
        layoutManager.justifyContent = JustifyContent.CENTER
        videoAdapter = GroupVideoAdapter(Pair(screenWidth, screenHeight))
        videoAdapter.setHasStableIds(true)
        videoAdapter.setOnItemClickListener(object : OnVideoItemClickListener {
            override fun onClick(participantViewState: ParticipantViewState) {
                enterFullScreenMode(participantViewState.id, participantViewState.isMirrored)
            }
        })
        binding.rcvGroupVideo.layoutManager = layoutManager
        binding.rcvGroupVideo.adapter = videoAdapter
        binding.btnBack.setOnClickListener {
            exitFullScreenMode()
        }
        menuBinding.btnMenu.setOnClickListener {
            if (menuBinding.btnMenu.tag == true) {
                closeMenu()
            } else {
                showMenu()
            }
        }
        menuBinding.btnMenuEndCall.setOnClickListener {
            leaveRoom()
            val bundle =
                bundleOf(ARG_MESSAGE to getString(R.string.msg_call_end), ARG_AUTO_CLOSE to true)
            findNavController().navigate(R.id.leave_group, args = bundle)
        }

        menuBinding.labelVideo.setOnClickListener {
            toggleVideo()
        }

        menuBinding.labelMic.setOnClickListener {
            toggleMicrophone()
        }
        menuBinding.labelSwitch.setOnClickListener {
            showBackgroundSelection()
        }
        menuBinding.labelVolume.setOnClickListener {
            showVolumeSettingDialog()
        }
        menuBinding.labelChat.setOnClickListener {
            showChatLayout()
        }
        menuBinding.labelBackground.setOnClickListener {
            showBackgroundSelection()
        }
        initChatLayout()
        initLocalVideoFrame()
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
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                binding.layoutVideo.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
                return@setOnKeyListener false
            }

            if (event.action == KeyEvent.ACTION_UP) {
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
            return@setOnKeyListener true
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
        val localWidth = localVideoSize.first
        val localHeight = localVideoSize.second

        val position = binding.myVideo.tag as Int
        when (direction) {
            SimpleSwipeListener.Direction.up -> {
                if (position == 0 || position == 1) {
                    val animation = binding.layoutVideo.animate().y(-1f)
                    if (position == 0) {
                        animation.x(width - localWidth)
                    }
                    animation.start()
                    binding.myVideo.tag = if (position == 0) 3 else 2
                }
            }

            SimpleSwipeListener.Direction.down -> {
                if (position == 2 || position == 3) {
                    val animation = binding.layoutVideo.animate().y(height - localHeight)
                    if (position == 3) {
                        animation.x(width - localWidth - 180f)
                    }
                    animation.start()
                    binding.myVideo.tag = if (position == 2) 1 else 0
                }
            }

            SimpleSwipeListener.Direction.left -> {
                if (position == 0 || position == 3 || position == 5) {
                    val distance = when (position) {
                        0, 5 -> -1f
                        3 -> (width / 2) - (localWidth / 2)
                        else -> 0f
                    }
                    binding.layoutVideo.animate().x(distance).start()
                    binding.myVideo.tag = if (position == 0) 1 else if (position == 3) 5 else 2
                }
            }

            SimpleSwipeListener.Direction.right -> {
                if (position == 1 || position == 2 || position == 5) {
                    val buffer = if (position == 1) 180f else 0f
                    val distance = when (position) {
                        1 -> width - localWidth - buffer
                        2 -> (width / 2) - (localWidth / 2)
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

    private fun showBackgroundSelection() {
        val bottomSheet = com.gc.waravi.views.dialogs.BackgroundSelectionBottomSheet()
        bottomSheet.show(childFragmentManager, "background_selection")
    }

    private fun exitFullScreenMode() {
        if (isInFullScreenMode) {
            fullScreenStreamId?.let { id ->
                val stream = videoAdapter.getStreamById(id)
                stream?.removeRenderer(binding.fullView)

                //remove screen sharing owner screen
                val isScreenSharing = id.contains("screen")
                if (isScreenSharing) {
                    val participantId = id.substringAfter("screen_")
                    val participantStream = videoAdapter.getStreamById(participantId)
                    participantStream?.let {
                        Log.e("NQD", "remove participantStream: ${participantStream.id}")
                        participantStream.removeRenderer(binding.screenSharingOwnerView)
                    }
                }
            }
            binding.screenSharingOwnerView.visibility = View.GONE
            binding.screenSharingOwnerViewMask.visibility = View.GONE
            binding.fullViewMask.visibility = View.GONE
            binding.fullView.visibility = View.GONE
            binding.btnBack.visibility = View.GONE
            binding.rcvGroupVideo.visibility = View.VISIBLE
            binding.layoutVideo.visibility = View.VISIBLE
            binding.lblRoomId.visibility = View.VISIBLE
            isInFullScreenMode = false
        }
    }

    private fun enterFullScreenMode(identify: String, mirror: Boolean = false) {
        val stream = videoAdapter.getStreamById(identify)
        stream?.let { mediaStream ->
            binding.fullView.setMirror(mirror)
            isInFullScreenMode = true
            fullScreenStreamId = identify
            binding.rcvGroupVideo.visibility = View.GONE
            binding.layoutVideo.visibility = View.INVISIBLE
            binding.fullView.visibility = View.VISIBLE
            binding.btnBack.visibility = View.VISIBLE
            binding.lblRoomId.visibility = View.GONE

            //remove current canvas
            val viewHolder = getViewHolderByPeerId(identify)
            viewHolder?.let {
                binding.fullViewMask.visibility = it.binding.viewMask.visibility
            }
            mediaStream.addRenderer(binding.fullView)
        }

        val isScreenSharing = identify.contains("screen")
        if (isScreenSharing) {
            val participantId = identify.substringAfter("screen_")
            val participantStream = videoAdapter.getStreamById(participantId)
            participantStream?.let {
                Log.e("NQD", "participantStream: ${participantStream.id}")
                binding.screenSharingOwnerView.visibility = View.VISIBLE
                binding.screenSharingOwnerView.setup()
                binding.screenSharingOwnerView.setZOrderOnTop(true)
                binding.screenSharingOwnerView.setZOrderMediaOverlay(true)
                getViewHolderByPeerId(participantId)?.let { holder ->
                    binding.screenSharingOwnerViewMask.visibility =
                        holder.binding.viewMask.visibility
                }
                participantStream.addRenderer(binding.screenSharingOwnerView)
            }
        }
    }

    private fun getViewHolderByPeerId(peerId: String): GroupVideoViewHolder? {
        val position = videoAdapter.getParticipantPosition(peerId)
        return binding.rcvGroupVideo.findViewHolderForAdapterPosition(position) as? GroupVideoViewHolder
    }

    private fun initChatLayout() {
        this.enableChatLayout(false)
        chatBinding.layoutMessage.tag = false
        chatAdapter = GroupChatAdapter()
        chatBinding.rcvChat.layoutManager =
            object : LinearLayoutManager(context, RecyclerView.VERTICAL, true) {
                override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
                    if (direction == View.FOCUS_DOWN) {
                        val pos = getPosition(focused)
                        if (pos == 0)
                            return if (true == chatBinding.layoutMessage.tag) chatBinding.edtMessage else menuBinding.btnMenu
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
        chatBinding.btnBack.visibility =
            if (Utils.isRunningOnTV(requireContext())) View.GONE else View.VISIBLE
        chatBinding.btnBack.setOnClickListener {
            this.closeChatLayout()
        }
        chatBinding.layoutMessage.outlineProvider = object : ViewOutlineProvider() {
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, (view.width + 20f).toInt(), view.height, 20f)
            }
        }
        chatBinding.layoutMessage.clipToOutline = true
    }

    private fun sendMessage() {
        val msgText = chatBinding.edtMessage.text
        if (msgText.isNullOrEmpty()) {
            return
        }
        val messageStr = msgText.toString().trim()
        val message = ChatMessage(
            message = messageStr, timeStamp = System.currentTimeMillis(),
            sender = viewModel.currentPeerId.value ?: SkywayManager.selfId, type = 0, isMine = true
        )
        this.addMessageChat(message)
        chatBinding.edtMessage.text.clear()
        chatBinding.edtMessage.requestFocus()
        roomSession.sendRoomData(CallData(MessageCode.Chat.code, messageStr))
    }

    private fun showChatLayout() {
        this.enableChatLayout(true)
        chatBinding.layoutMessage.tag = true
        chatBinding.layoutMessage.animate().translationX(0f).start()
        chatBinding.edtMessage.requestFocus()
        unreadMessageCount = 0
        updateUnreadMessageCount()

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
        menuBinding.btnMenu.requestFocus()
    }

    private fun enableChatLayout(isEnable: Boolean) {
        (0 until chatBinding.layoutMessage.childCount).forEach { index ->
            chatBinding.layoutMessage.getChildAt(index).isEnabled = isEnable
        }
        chatBinding.chatFrame.descendantFocusability =
            if (isEnable) ViewGroup.FOCUS_BEFORE_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun updateUnreadMessageCount() {
        val unreadCount = if (unreadMessageCount <= 99) unreadMessageCount.toString() else "99+"
        menuBinding.tvChatCount.text = unreadCount
        menuBinding.tvChatCount.visibility =
            if (unreadMessageCount > 0 && menuBinding.btnMenu.tag == true) View.VISIBLE else View.INVISIBLE
        menuBinding.tvTotalCount.visibility =
            if (menuBinding.btnMenu.tag == true) View.GONE else View.VISIBLE
    }

    private fun initOptionState() {
        //init microphone state
        menuBinding.labelMic.setDrawableStart(R.drawable.talk_icn_mic, 50, 58)

        //init camera state
        menuBinding.labelVideo.setDrawableStart(R.drawable.talk_icn_video, 58, 50)

        menuBinding.labelBackground.setDrawableStart(R.drawable.talk_icn_blur)
        menuBinding.labelChat.setDrawableStart(R.drawable.talk_icn_chat)
        menuBinding.labelSwitch.setDrawableStart(R.drawable.talk_icn_blur)
        menuBinding.labelVolume.setDrawableStart(R.drawable.talk_icn_vol)
    }

    private fun toggleMicrophone() {
        val isEnable = roomSession.audioEnable
        if (isEnable) roomSession.disableLocalAudio() else roomSession.enableLocalAudio()
        val micDrawable = if (!isEnable) R.drawable.talk_icn_mic else R.drawable.talk_icn_mic_off
        menuBinding.labelMic.setDrawableStart(micDrawable, 50, 58)
    }

    private fun toggleVideo() {
        val isEnable = roomSession.cameraEnable
        if (isEnable) roomSession.disableLocalVideo() else roomSession.enableLocalVideo()
        val cameraDrawable =
            if (!isEnable) R.drawable.talk_icn_video else R.drawable.talk_icn_video_off
        menuBinding.labelVideo.setDrawableStart(cameraDrawable, 58, 50)
    }

    private fun leaveRoom() {
        videoAdapter.releaseVideoTracks()
        lifecycleScope.launch {
            roomSession.leaveRoom()
        }
        CallManager.roomSession = null
        CallManager.isInRoom = false
        this.abandonAudioFocus()
    }

    private val delayTime = 50L
    private fun showMenu() {
        menuBinding.btnMenu.tag = true

        menuBinding.container.showWithScaleAnimation(0 * delayTime)
        menuBinding.labelChat.showWithScaleAnimation(1 * delayTime)
        menuBinding.labelMic.showWithScaleAnimation(2 * delayTime)
        menuBinding.labelVideo.showWithScaleAnimation(3 * delayTime)
        menuBinding.labelBackground.showWithScaleAnimation(4 * delayTime)
        menuBinding.labelVolume.showWithScaleAnimation(5 * delayTime)
        menuBinding.labelSwitch.showWithScaleAnimation(6 * delayTime)

        menuBinding.tvTotalCount.visibility = View.INVISIBLE
        updateUnreadMessageCount()
    }

    private fun closeMenu() {
        menuBinding.btnMenu.tag = false

        menuBinding.labelSwitch.hideWithScaleAnimation(0 * delayTime)
        menuBinding.labelVolume.hideWithScaleAnimation(1 * delayTime)
        menuBinding.labelBackground.hideWithScaleAnimation(2 * delayTime)
        menuBinding.labelVideo.hideWithScaleAnimation(3 * delayTime)
        menuBinding.labelMic.hideWithScaleAnimation(4 * delayTime)
        menuBinding.labelChat.hideWithScaleAnimation(5 * delayTime)
        menuBinding.container.hideWithScaleAnimation(6 * delayTime)

        menuBinding.tvTotalCount.visibility = View.GONE
        menuBinding.tvChatCount.visibility = View.GONE
    }

    override fun onLongKeyEvent(keyEvent: KeyEvent) {
        if(lifecycle.currentState == Lifecycle.State.RESUMED) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_MOVE_END -> {
                    leaveRoom()
                    val bundle =
                        bundleOf(ARG_MESSAGE to getString(R.string.msg_call_end), ARG_AUTO_CLOSE to true)
                    findNavController().navigate(R.id.leave_group, args = bundle)
                }
            }
        }
    }

    override fun onDestroyView() {
        leaveRoom()
        disposable.clear()
        if (requireActivity() is HomeActivity) {
            (requireActivity() as HomeActivity).revertCallVolume()
        }
        super.onDestroyView()
    }
}

fun View.showWithScaleAnimation(duration: Long) {
    animate().scaleX(1f).scaleY(1f).setStartDelay(duration).withStartAction {
        isEnabled = true
        visibility = View.VISIBLE
    }
}

fun View.hideWithScaleAnimation(duration: Long) {
    animate().scaleX(0f).scaleY(0f).setStartDelay(duration).withEndAction {
        visibility = View.INVISIBLE
        isEnabled = false
    }
}

fun AppCompatTextView.setDrawableStart(
    @DrawableRes drawableRes: Int,
    width: Int = 50,
    height: Int = 50
) {
    val drawable = ContextCompat.getDrawable(context, drawableRes)
    drawable?.setBounds(0, 0, width, height)
    setCompoundDrawables(drawable, null, null, null)
}
