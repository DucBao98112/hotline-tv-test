package com.gc.waravi.views.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gc.waravi.R
import com.gc.waravi.databinding.FragmentPopupBinding
import com.gc.waravi.skyway.call.ARG_SHOULD_FINISH
import java.util.*
import kotlin.concurrent.schedule

const val ARG_MESSAGE = "message"
const val ARG_AUTO_CLOSE = "auto-close"

/**
 * 通知またはエラーメッセージ表示する画面
 */
class PopupFragment : Fragment() {
    private lateinit var binding : FragmentPopupBinding
    private var message: String? = null
    private var shouldFinished: Boolean = false
    private var autoCloseable: Boolean = false
    private var autoCloseTimer: Timer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPopupBinding.inflate(inflater, container, false)
        message = arguments?.getString(ARG_MESSAGE)
        shouldFinished = arguments?.getBoolean(ARG_SHOULD_FINISH) ?: false
        autoCloseable = arguments?.getBoolean(ARG_AUTO_CLOSE) ?: false
        binding.lblContent.text = message ?: getString(R.string.msg_something_wrong)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener {
            autoCloseTimer?.cancel()
            this.close()
        }
        if (autoCloseable){
            autoCloseTimer = Timer()
            autoCloseTimer!!.schedule(3 * 1000L){
                requireActivity().runOnUiThread {
                    close()
                }
            }
        }
    }

    private fun close(){
        if(shouldFinished){
            requireActivity().finish()
        }else{
            requireActivity().onBackPressed()
        }
    }

    override fun onDestroyView() {
        autoCloseTimer?.cancel()
        super.onDestroyView()
    }
}