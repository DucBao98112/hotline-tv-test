package com.gc.waravi.views.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.gc.waravi.R
import com.gc.waravi.base.BaseDialogFragment
import com.gc.waravi.databinding.DialogFragmentAuthBinding
import com.gc.waravi.databinding.LayoutDialBinding
import com.gc.waravi.network.AuthRepository
import com.gc.waravi.utils.Constant
import com.gc.waravi.utils.MediaManager
import com.gc.waravi.utils.PrefUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AuthDialogFragment : BaseDialogFragment() {
    private lateinit var binding : DialogFragmentAuthBinding
    private lateinit var numberKeys : List<TextView>
    private lateinit var textBoxes : List<TextView>
    private lateinit var dialBinding : LayoutDialBinding
    private var callback : OnAuthResponseListener? = null
    private var inputCode = ""
    private var isLoading = false
    private val authRepository by lazy {
        AuthRepository()
    }

    override fun onStart() {
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        super.onStart()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogFragmentAuthBinding.inflate(inflater, container, false)
        dialBinding = binding.layoutDial
        initViews()
        return binding.root
    }

    fun setOnResultListener(listener: OnAuthResponseListener){
        callback = listener
    }

    fun initViews() {
        binding.btnOk.setOnClickListener {
            if (inputCode.length == 10){
                checkAuthCode(inputCode){
                    onCheckAuthSuccess(inputCode)
                }
            } else{
                showMessage(getString(R.string.msg_code_invalid))
            }
        }
        initKeypad()
    }

    private fun onCheckAuthSuccess(code: String){
        PrefUtils.saveAuthCode(requireContext(), code)
        callback?.onSuccess()
        dismiss()
    }

    private fun initKeypad() {
        numberKeys = arrayListOf(dialBinding.btnKey0, dialBinding.btnKey1, dialBinding.btnKey2, dialBinding.btnKey3,
            dialBinding.btnKey4, dialBinding.btnKey5, dialBinding.btnKey6, dialBinding.btnKey7, dialBinding.btnKey8, dialBinding.btnKey9)
        textBoxes = arrayListOf(binding.textBox1, binding.textBox2, binding.textBox3, binding.textBox4, binding.textBox5, binding.textBox6,
            binding.textBox7, binding.textBox8, binding.textBox9, binding.textBox10)

        textBoxes[0].isActivated = true
        numberKeys.forEach {
                view ->
            view.setOnClickListener {
                if (inputCode.length >= textBoxes.size) return@setOnClickListener
                val number = view.text.toString()
                this.playKeySound(number.toInt())
                this.setInputText(inputCode.length, number)
                inputCode += number
            } }
        dialBinding.btnKeyBackspace.setOnClickListener {
            playKeySound(ToneGenerator.TONE_PROP_BEEP)
            val length = inputCode.length
            inputCode = if(length > 0){
                this.setInputText(length - 1, "")
                val number = inputCode.substring(0 until length - 1)
                number
            } else {
                ""
            }
        }

        dialBinding.btnKeyBackspace.setOnLongClickListener {
            playKeySound(ToneGenerator.TONE_PROP_BEEP)
            clearInputText()
            return@setOnLongClickListener false
        }
    }

    private fun setInputText(index: Int, number: String){
        if (index >= 0 && index < textBoxes.size){
            textBoxes[index].text = number
            setInputActivated(if(number.isEmpty()) index else index + 1)
        }
    }

    private fun clearInputText(){
        inputCode = ""
        for (i in textBoxes.indices){
            textBoxes[i].text = ""
            textBoxes[i].isActivated = i == 0
        }
    }

    private fun setInputActivated(index: Int){
        if (index >= 0 && index < textBoxes.size){
            for (i in textBoxes.indices){
                textBoxes[i].isActivated = i == index
            }
        }
    }

    private fun playKeySound(key: Int){
        val playKeySound = PrefUtils.getSoundVolume(requireContext()) != MediaManager.Volume.No
        if(!playKeySound) return
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, Constant.KEYBOARD_SOUND_VOLUME)
            toneGenerator.startTone(key, 150)
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(Runnable {
                toneGenerator.release()
            }, 150)
        } catch (e: Exception) {
            Log.d(this::class.simpleName, "Exception while playing sound:$e")
        }
    }

    private fun checkAuthCode(code: String, onSuccess: () -> Unit){
        if (isLoading || code.length < 10) return
        setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            authRepository.checkVerifyCode(code, null, onSuccess = {
                lifecycleScope.launch(Dispatchers.Main){
                    setLoading(false)
                    onSuccess()
                }
            }, onFailure = {error ->
                lifecycleScope.launch(Dispatchers.Main){
                    setLoading(false)
                    val message = getString(when(error?.message){
                        "EXPIRED" -> R.string.msg_code_expired
                        "IN USE" -> R.string.msg_code_in_use
                        else -> R.string.msg_code_invalid
                    })
                    showMessage(message)
                }
            })
        }
    }

    private fun showMessage(message: String){
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun setLoading(isShow: Boolean){
        isLoading = isShow
        binding.pbLoading.visibility = if(isShow) View.VISIBLE else View.GONE
        binding.btnOk.visibility = if(isShow) View.INVISIBLE else View.VISIBLE
    }

}

interface OnAuthResponseListener{
    fun onSuccess()
    fun onFailure(error: String)
}