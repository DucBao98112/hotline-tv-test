package com.gc.waravi.views.fragments

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentIncomingCallBinding
import com.gc.waravi.models.RecentType
import com.gc.waravi.skyway.call.ARG_CALL_ACTION
import com.gc.waravi.skyway.call.ARG_CALL_ID
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.skyway.call.CallEvent
import com.gc.waravi.skyway.call.CallType
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.gc.waravi.views.activities.InCallActivity
import kotlinx.coroutines.launch

/**
 * 着信画面
 */
class IncomingCallFragment : BaseFragment<FragmentIncomingCallBinding>() {
    private var callerId: String? = null
    private var isAutoAnsweringEnable = false
    private var autoAnswerTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            callerId = it.getString(ARG_CALL_ID)
        }
        CallManager.callEvents.let {
            lifecycleScope.launch {
                it.collect { callEvent ->
                    if (callEvent is CallEvent.CallCanceled || callEvent is CallEvent.CallEnded) {
                        onCallCanceled()
                    }
                }
            }
        }
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentIncomingCallBinding {
        return FragmentIncomingCallBinding.inflate(inflater, container, false)
    }

    private fun onCallCanceled() {
        this.autoAnswerTimer?.cancel()
        this.abandonAudioFocus()
        val contact = viewModel.getContact(callerId!!)
        val bundle =
            bundleOf(
                "message" to getString(
                    R.string.msg_missed_call,
                    contact?.name ?: callerId
                )
            )
        findNavController().navigate(R.id.PopUp, bundle, navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.Main, inclusive = false)
            .build())
    }

    override fun initViews() {
        isAutoAnsweringEnable = PrefUtils.isAutoAnsweringEnable(requireContext())
        if (isAutoAnsweringEnable) {
            binding.groupCallControl.visibility = View.INVISIBLE
            binding.txtMessage.visibility = View.VISIBLE
        }
        requireActivity().onBackPressedDispatcher.addCallback(this) {

        }
        //if running device is tablet, change button resource
        if (!Utils.isRunningOnTV(requireContext())) {
            binding.btnAcceptCall.setImageResource(R.drawable.button_accept_call_tablet)
            binding.btnDeclineCall.setImageResource(R.drawable.button_decline_call_tablet)
        }
        val contact = viewModel.getContact(callerId!!)
        binding.lblCallerId.text = contact?.name ?: callerId
        binding.btnAcceptCall.setOnClickListener {
            this.acceptCall()
        }

        binding.btnDeclineCall.setOnClickListener {
            this.declineCall()
        }

        binding.swToggleCamera.setOnCheckedChangeListener { _, isChecked ->
            CallManager.cameraEnable = isChecked
            setCameraState(isChecked)
        }

        binding.swToggleMic.setOnCheckedChangeListener { _, isChecked ->
            CallManager.microphoneEnable = isChecked
            setMicState(isChecked)
        }

        binding.btnCancel.setOnClickListener {
            this.autoAnswerTimer?.cancel()
            this.declineCall()
        }

        val cameraEnable = when (PrefUtils.getCameraSettingState(requireContext())) {
            0 -> PrefUtils.getCameraState(requireContext())
            1 -> true
            2 -> false
            else -> true
        }
        this.setMicState(true)
        this.setCameraState(cameraEnable)
    }

    private fun acceptCall() {
        stopSound()
        val bundle =
            bundleOf(
                ARG_CALL_ACTION to CallType.INCOMING_CALL,
                ARG_CALL_ID to callerId
            )
        findNavController().popBackStack()
        startActivity(Intent(requireContext(), InCallActivity::class.java).apply {
            putExtras(bundle)
        })
    }

    private fun declineCall() {
        stopSound()
        CallManager.declineCall()
        findNavController().navigateUp()
    }

    private fun setMicState(micOn: Boolean) {
        CallManager.microphoneEnable = micOn
        //init mic state
        binding.imvMicrophone.setImageResource(if (micOn) R.drawable.talk_icn_mic else R.drawable.talk_icn_mic_off)
        if (binding.swToggleMic.isChecked != micOn) {
            binding.swToggleMic.isChecked = micOn
        }
    }

    private fun setCameraState(cameraOn: Boolean) {
        CallManager.cameraEnable = cameraOn
        //init camera state
        binding.imvCamera.setImageResource(if (cameraOn) R.drawable.talk_icn_video else R.drawable.talk_icn_video_off)
        if (binding.swToggleCamera.isChecked != cameraOn) {
            binding.swToggleCamera.isChecked = cameraOn
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.requestAudioFocus()
        playSound(R.raw.ringtone, true)
        if (callerId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.saveCallRecent(callerId!!, RecentType.Incoming)
            }
        }

        if (isAutoAnsweringEnable) {
            binding.btnCancel.visibility = View.VISIBLE
            binding.btnCancel.requestFocus()
            binding.txtMessage.text = getString(R.string.msg_auto_answering, 3)
            autoAnswerTimer = object : CountDownTimer(4000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val tick = (millisUntilFinished / 1000).toInt()
                    binding.txtMessage.text = getString(R.string.msg_auto_answering, tick)
                }

                override fun onFinish() {
                    acceptCall()
                }
            }
            autoAnswerTimer?.start()
        }
    }

    override fun onLongKeyEvent(keyEvent: KeyEvent) {
        if(lifecycle.currentState == Lifecycle.State.RESUMED) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_MOVE_END -> {
                    this.declineCall()
                }
            }
        }
    }

    override fun onDestroyView() {
        this.stopSound()
        this.disposable.dispose()
        this.disposable.clear()
        this.autoAnswerTimer?.cancel()
        super.onDestroyView()
    }

    companion object {
        @JvmStatic
        fun newInstance(callerId: String, param2: String) =
            IncomingCallFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CALL_ID, callerId)
                }
            }
    }
}