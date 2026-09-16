package com.gc.waravi.views.fragments

import android.content.DialogInterface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import com.gc.waravi.R
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentKeypadBinding
import com.gc.waravi.utils.Constant
import com.gc.waravi.utils.MediaManager
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils

val ARG_KEYPAD_TYPE = "keypad-type"
enum class KeypadType {
    P2PCall,
    ROOM;
}
class KeypadFragment : BaseFragment<FragmentKeypadBinding>() {
    private lateinit var numberKeys : List<TextView>
    private lateinit var type : KeypadType
    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable(ARG_KEYPAD_TYPE, KeypadType::class.java) as KeypadType
            } else{
                it.get(ARG_KEYPAD_TYPE) as KeypadType
            }
        }
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentKeypadBinding {
        return FragmentKeypadBinding.inflate(inflater, container, false)
    }

    override fun initViews() {
        if (type == KeypadType.P2PCall){
            viewModel.setCallInput(binding.txtInput.text.toString())
            viewModel.clearCallInput.observe(viewLifecycleOwner){
                if (it){
                    binding.txtInput.text = ""
                }
            }
        } else if(type == KeypadType.ROOM){
            viewModel.setRoomInput(binding.txtInput.text.toString())
        }
        initKeypad()
        binding.txtInput.doOnTextChanged { text, start, before, count ->
            if (type == KeypadType.P2PCall){
                viewModel.setCallInput(text.toString())
            } else if (type == KeypadType.ROOM){
                viewModel.setRoomInput(text.toString())
            }
        }
    }

    private fun initKeypad(){
        val dialBinding = binding.layoutDial
        numberKeys = arrayListOf(dialBinding.btnKey0, dialBinding.btnKey1, dialBinding.btnKey2, dialBinding.btnKey3,
            dialBinding.btnKey4, dialBinding.btnKey5, dialBinding.btnKey6, dialBinding.btnKey7, dialBinding.btnKey8, dialBinding.btnKey9)
        numberKeys.forEach { view ->
            view.setOnClickListener {
                playKeySound(view.text.toString().toInt())
                val number = binding.txtInput.text.toString() + view.text.toString()
                binding.txtInput.text = number
            }
            //implement long click
            view.setOnLongClickListener {
                if(binding.txtInput.text.isNotEmpty()){
                    val id = binding.txtInput.text.toString()
                    showConfirmDialogWithId(getString(R.string.msg_call_by_id, id))
                }else{
                    this.makeCallFromShort(view.text.toString().toInt())
                }
                return@setOnLongClickListener true
            }
        }
        dialBinding.btnKeyBackspace.setOnClickListener {
            playKeySound(ToneGenerator.TONE_PROP_BEEP)
            val length = binding.txtInput.text.length
            if(length > 0){
                val number = binding.txtInput.text.toString().substring(0 until length - 1)
                binding.txtInput.text = number
            } else {
                binding.txtInput.text = ""
            }
        }

        dialBinding.btnKeyBackspace.setOnLongClickListener {
            playKeySound(ToneGenerator.TONE_PROP_BEEP)
            binding.txtInput.text = ""
            return@setOnLongClickListener false
        }

        binding.btnKeypadCall.setOnClickListener {
            if (binding.txtInput.text.isNotEmpty()) {
                val id = binding.txtInput.text.toString()
                showConfirmDialogWithId(getString(if (type == KeypadType.P2PCall)
                    R.string.msg_call_by_id else R.string.msg_join_room, id))
            }
        }
        
        /* if (type == KeypadType.ROOM) {
            binding.btnKeypadCall.setImageResource(R.drawable.call_group_icn)
        } */
    }

    private fun playKeySound(key: Int){
        val playKeySound = PrefUtils.getSoundVolume(requireContext()) != MediaManager.Volume.No
        if(!playKeySound) return
        Utils.playKeyTone(key)
    }

    private fun showConfirmDialogWithId(title: String){
        if(isDialogShowing) return
        isDialogShowing = true
        val dialog = Utils.createDialog(requireContext(), title){
            if (type == KeypadType.P2PCall){
                viewModel.makeCallByCurrentId()
            } else{
                viewModel.joinRoomByCurrentId()
            }
        }
        dialog.setOnDismissListener {
            isDialogShowing = false
        }
        dialog.show().getButton(DialogInterface.BUTTON_POSITIVE).requestFocus()
    }

    private fun makeCallFromShort(shortId: Int){
        if (type != KeypadType.P2PCall) return
        val contact = viewModel.getContactByShort(shortId)
        if(contact != null){
            binding.txtInput.text = contact.contactId
            showConfirmDialogWithId(
                getString(R.string.msg_call_by_short, shortId.toString(), contact.name))
        }
    }

    override fun onKeyEvent(keyEvent: KeyEvent) {
        if(lifecycle.currentState == Lifecycle.State.RESUMED) {
            when(keyEvent.keyCode){
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
                KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9 -> {
                    val number = binding.txtInput.text.toString() + (keyEvent.keyCode - 7).toString()
                    binding.txtInput.text = number
                }
            }
        }
    }

    override fun onLongKeyEvent(keyEvent: KeyEvent) {
        if(lifecycle.currentState == Lifecycle.State.RESUMED) {
            Log.e("NQD", "onLongKeyEvent: ${KeyEvent.keyCodeToString(keyEvent.keyCode)}")
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
                KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9 -> {
                    if (binding.txtInput.text.isNotEmpty()) {
                        val id = binding.txtInput.text.toString()
                        showConfirmDialogWithId(getString(if (type == KeypadType.P2PCall)
                            R.string.msg_call_by_id else R.string.msg_join_room, id))
                    } else {
                        this.makeCallFromShort((keyEvent.keyCode - 7))
                    }
                }
                KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_CALL -> {
                    if (binding.txtInput.text.isNotEmpty()) {
                        val id = binding.txtInput.text.toString()
                        showConfirmDialogWithId(getString(if (type == KeypadType.P2PCall)
                            R.string.msg_call_by_id else R.string.msg_join_room, id))
                    }
                }
            }
        }
    }

    companion object{
        @JvmStatic
        fun newInstance(type: KeypadType) =
            KeypadFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_KEYPAD_TYPE, type)
                }
            }
    }

}