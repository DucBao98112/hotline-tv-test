package com.gc.waravi.views.fragments

import android.app.Dialog
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.view.setPadding
import com.gc.waravi.R
import com.gc.waravi.base.BaseApplication
import com.gc.waravi.base.BaseFragment
import com.gc.waravi.databinding.FragmentBaseSettingBinding
import com.gc.waravi.models.VideoResolution
import com.gc.waravi.skyway.AudioOutputManager
import com.gc.waravi.utils.MediaManager
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.views.activities.HomeActivity
import com.gc.waravi.views.dialogs.VideoResolutionDialogFragment


class BaseSettingFragment : BaseFragment<FragmentBaseSettingBinding>() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentBaseSettingBinding {
        return FragmentBaseSettingBinding.inflate(inflater, container, false)
    }

    private val onFocusChanged = View.OnFocusChangeListener { v, hasFocus ->
        if (hasFocus) {
            playPreviewSound(getVolume(v.id))
        } else {
            stopPreviewSound()
        }
    }

    override fun initViews() {
        binding.rdgSound.setOnCheckedChangeListener { group, checkedId ->
            val volume = getVolume(checkedId)
            PrefUtils.saveSoundVolume(requireContext(), volume)
//            binding.btnBaseSetting.nextFocusRightId = checkedId
        }

        binding.btnOk.setOnClickListener {
            binding.btnOk.clearFocus()
        }

        binding.rd0.onFocusChangeListener = onFocusChanged
        binding.rd1.onFocusChangeListener = onFocusChanged
        binding.rd2.onFocusChangeListener = onFocusChanged
        binding.rd3.onFocusChangeListener = onFocusChanged
        binding.rd4.onFocusChangeListener = onFocusChanged
        binding.rd5.onFocusChangeListener = onFocusChanged
        when (PrefUtils.getSoundVolume(requireContext())) {
            MediaManager.Volume.No -> binding.rd0.isChecked = true
            MediaManager.Volume.Level1 -> binding.rd1.isChecked = true
            MediaManager.Volume.Level2 -> binding.rd2.isChecked = true
            MediaManager.Volume.Level3 -> binding.rd3.isChecked = true
            MediaManager.Volume.Level4 -> binding.rd4.isChecked = true
            MediaManager.Volume.Level5 -> binding.rd5.isChecked = true
        }
        initVideoSolutionSetting()
        initVolumeSetting()
        initLanguageSetting()
        initAppearance()
    }

    private fun initVideoSolutionSetting() {
        binding.btnVideoSetting.setOnClickListener {
            val videoResolutionDialog = VideoResolutionDialogFragment()
            videoResolutionDialog.show(childFragmentManager, "resolution-dialog")
            videoResolutionDialog.setOnDismissListener {
                setVideoResolutionState()
            }
        }
        setVideoResolutionState()
    }

    private fun setVideoResolutionState(){
        val btnVideoStr = getString(
            when (PrefUtils.getVideoResolution(requireContext())) {
                VideoResolution.VIDEO_VGA -> R.string.lbl_video_vga
                VideoResolution.VIDEO_HD -> R.string.lbl_video_hd
                VideoResolution.VIDEO_FHD -> R.string.lbl_video_fhd
                else -> R.string.lbl_video_vga
            }
        )
        binding.btnVideoSetting.text = btnVideoStr
    }

    private fun setLocale(languageCode: String){
        if(requireActivity() is HomeActivity){
            (requireActivity() as HomeActivity).setLocale(languageCode)
        }
    }

    private fun initLanguageSetting() {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val localeStr = if (currentLocales.isEmpty)
            LocaleManagerCompat.getSystemLocales(requireActivity()).get(0)?.language
        else currentLocales.toLanguageTags()
        val languages = listOf(getString(R.string.lbl_en), getString(R.string.lbl_ja))
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.view_spinner_item,
            languages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spLanguage.adapter = adapter
        binding.spLanguage.setSelection(if (localeStr == "en") 0 else 1)
        binding.spLanguage.onItemSelectedListener = object : OnItemSelectedListener{
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                setLocale(if (position == 0) "en" else "ja")
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }

        }
    }

    private fun playPreviewSound(volume: MediaManager.Volume) {
        mediaPlayer = MediaPlayer.create(requireContext(), R.raw.ringtone)
        val stampVolume = when (volume) {
            MediaManager.Volume.No -> 0f
            MediaManager.Volume.Level1 -> 0.1f
            MediaManager.Volume.Level2 -> 0.2f
            MediaManager.Volume.Level3 -> 0.3f
            MediaManager.Volume.Level4 -> 0.4f
            MediaManager.Volume.Level5 -> 0.5f
        }
        mediaPlayer?.setVolume(stampVolume, stampVolume)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    private fun stopPreviewSound() {
        if (mediaPlayer == null) return
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.stop()
        }
        mediaPlayer!!.release()
        mediaPlayer = null
    }

    private fun initAppearance(){
        val checkButton = when(AppCompatDelegate.getDefaultNightMode()){
            AppCompatDelegate.MODE_NIGHT_NO -> binding.rdLight
            AppCompatDelegate.MODE_NIGHT_YES -> binding.rdDark
            else -> binding.rdAuto
        }
        binding.rdgTheme.check(checkButton.id)
        binding.rdgTheme.setOnCheckedChangeListener { _, checkedId ->
            when(checkedId){
                binding.rdAuto.id ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                binding.rdLight.id -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                binding.rdDark.id -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> return@setOnCheckedChangeListener
            }
        }
    }

    private fun getVolume(checkId: Int): MediaManager.Volume {
        return when (checkId) {
            binding.rd0.id -> MediaManager.Volume.No
            binding.rd1.id -> MediaManager.Volume.Level1
            binding.rd2.id -> MediaManager.Volume.Level2
            binding.rd3.id -> MediaManager.Volume.Level3
            binding.rd4.id -> MediaManager.Volume.Level4
            binding.rd5.id -> MediaManager.Volume.Level5
            else -> {
                MediaManager.Volume.Level3
            }
        }
    }

    private fun initVolumeSetting() {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val savedVolume = PrefUtils.getCallVolume(requireContext())
        binding.sbVolume.max = maxVolume
        binding.sbVolume.incrementProgressBy(1)
        binding.sbVolume.progress = if(savedVolume != -1) savedVolume else currentVolume
        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, p2: Boolean) {
                if(progress > 0){
                    PrefUtils.saveCallVolume(requireContext(), progress)
                } else{
                    Toast.makeText(requireContext(), getText(R.string.msg_volume_0_error), Toast.LENGTH_SHORT).show()
                    seekBar.progress = 1
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }

        })

        //audio output device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val audioDevices = audioManager.availableCommunicationDevices
            binding.groupAudioDevice.visibility = if(audioDevices.isEmpty()) View.GONE else View.VISIBLE
            val savedDeviceInfo = PrefUtils.getAudioOutputDevice(requireContext())
            var savedDeviceName : String? = null

            val currentDeviceId = audioManager.communicationDevice?.id
            if(savedDeviceInfo.isEmpty() && currentDeviceId != null){
                savedDeviceName = getDeviceName(audioDevices, currentDeviceId)
            } else if(savedDeviceInfo.isNotEmpty()){
                val selectedDeviceName = savedDeviceInfo.split("::").firstOrNull()
                val selectedDeviceType = savedDeviceInfo.split("::").lastOrNull()?.toInt()
                savedDeviceName = "$selectedDeviceName (${getDeviceTypeName(selectedDeviceType ?: 0)})"
            }
            binding.btnAudioDevice.text = savedDeviceName ?: getString(R.string.lbl_default_audio_device)

            binding.btnAudioDevice.setOnClickListener {
                val devicesList = audioDevices.map { "${it.productName} (${getDeviceTypeName(it.type)})" }
                val selectedIndex = devicesList.indexOfFirst { it == savedDeviceName }
                showAudioDeviceSelectionDialog(devicesList, selectedIndex){
                    val selectedDevice = audioDevices[it]
                    binding.btnAudioDevice.text = getDeviceName(audioDevices, selectedDevice.id)
                    BaseApplication.audioOutputManager().setAudioOutputDevice(selectedDevice.id)
                }
            }
        } else{
            binding.groupAudioDevice.visibility = View.GONE
        }
    }

    private fun getDeviceName(audioDevices: List<AudioDeviceInfo>, selectedDeviceId: Int): String{
        var deviceName = getString(R.string.lbl_default_audio_device)
        audioDevices.forEach {
            if (it.id == selectedDeviceId){
                deviceName = "${it.productName} (${getDeviceTypeName(it.type)})"
            }
        }
        return deviceName
    }

    private fun getDeviceTypeName(type: Int): String{
        return when(type){
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Device"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Speaker"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "HDMI"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Default speaker"
            else -> ""
        }
    }

    private fun showAudioDeviceSelectionDialog(devices: List<String>, selectedIndex: Int, onCheckedChanged: (Int) -> Unit){
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.layout_radiobutton_list)
        val rg = dialog.findViewById<View>(R.id.radio_group) as RadioGroup
        for (i in devices.indices) {
            val rb = RadioButton(requireContext())
            rb.id = i
            rb.text = devices[i]
            rb.setBackgroundResource(R.drawable.bg_box_normal_trans)
            rb.setPadding(5)
            rb.compoundDrawablePadding = 5
            rb.isChecked = if (i == 0 && selectedIndex == -1) true else i == selectedIndex
            rg.addView(rb)
        }

        rg.setOnCheckedChangeListener { group, checkedId ->
            onCheckedChanged(checkedId)
        }
        dialog.show()
    }

}