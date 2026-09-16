package com.gc.waravi.views.fragments

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gc.waravi.R
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentRegisterBinding
import com.gc.waravi.databinding.LayoutDialBinding
import com.gc.waravi.network.AuthRepository
import com.gc.waravi.skyway.DeviceManager
import com.gc.waravi.skyway.SkywayManager
import com.gc.waravi.skyway.call.CallManager
import com.gc.waravi.skyway.popservice.PopService
import com.gc.waravi.utils.Constant
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


/**
 * ID登録画面
 */
class RegisterFragment : BaseFragment<FragmentRegisterBinding>() {
    private lateinit var dialBinding: LayoutDialBinding
    private lateinit var numberKeys : List<TextView>
    private val authRepository by lazy {
        AuthRepository()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        dialBinding = binding.layoutDial
        initViews()
        return binding.root
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentRegisterBinding {
        return FragmentRegisterBinding.inflate(inflater, container, false)
    }

    override fun initViews() {
        binding.btnOk.setOnClickListener {
            registerId()
        }
        numberKeys = arrayListOf(dialBinding.btnKey0, dialBinding.btnKey1, dialBinding.btnKey2, dialBinding.btnKey3,
            dialBinding.btnKey4, dialBinding.btnKey5, dialBinding.btnKey6, dialBinding.btnKey7, dialBinding.btnKey8, dialBinding.btnKey9)
        numberKeys.forEach {
                view ->
            view.setOnClickListener {
            this.playKeySound(view.text.toString().toInt())
            val number = binding.txtInput.text.toString() + view.text.toString()
            binding.txtInput.text = number
        } }
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
    }

    private fun registerPeer(id : String) {
        lifecycleScope.launch(Dispatchers.IO) {
            SkywayManager.ensureSkywayInit(requireContext()){
                val deviceInfo = DeviceManager.getDeviceInfo(id)
                val isAvailable =
                    deviceInfo == null || deviceInfo.deviceId == Utils.getDeviceId(
                        requireContext()
                    ) || deviceInfo.authKey == null
                if (isAvailable) {
                    val registerRes = authRepository.register(id)
                    if(registerRes != null){
                        withContext(Dispatchers.Main) {
                            this@RegisterFragment.updateId(id, registerRes.authKey)
                        }
                    } else{
                        showSnackBar(R.string.msg_invalid_id)
                    }
                } else {
                    showSnackBar(R.string.msg_invalid_id)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.txtInput.text = SkywayManager.selfId
    }

    private fun registerId(){
        val number = binding.txtInput.text.toString()
        if(number.isNotEmpty()){
            registerPeer(number)
        } else{
            showSnackBar(R.string.msg_empty_number)
        }
    }

    private fun updateId(peerId: String, authKey: String){
        lifecycleScope.launch {
            withContext(Dispatchers.IO){
                val lastId = PrefUtils.getLastId(requireContext())
                if(lastId.isNotEmpty()){
                    DeviceManager.deleteDevice(lastId)
                }
                SkywayManager.selfId = peerId
                val token = FirebaseMessaging.getInstance().token.await()
                DeviceManager.updateDeviceInfo(requireContext(), peerId, token, Utils.getDeviceId(requireContext()), authKey)
                PrefUtils.saveLastId(requireContext(), peerId)
                PrefUtils.saveAuthKey(requireContext(), authKey)
                CallManager.registerForegroundCall(peerId)
                startAtPopService()

                //go back to home
                withContext(Dispatchers.Main) {
                    viewModel.setCurrentPeerId(peerId)
                    if (!findNavController().popBackStack(R.id.Main, false)) {
                        findNavController().navigate(R.id.goDial)
                    }
                }

            }
        }
    }

    private fun startAtPopService(){
        ContextCompat.startForegroundService(
            BaseApplication.get(),
            Intent(BaseApplication.get(), PopService::class.java)
        )
    }

    private fun playKeySound(key: Int){
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, Constant.KEYBOARD_SOUND_VOLUME)
        toneGen.startTone(key, 150)
    }

    override fun onKeyEvent(keyEvent: KeyEvent) {
        Log.d(this::class.simpleName, "${keyEvent.action} : ${keyEvent.keyCode}")
        if(isVisible && keyEvent.action == KeyEvent.ACTION_UP){
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

}