package com.gc.waravi.views.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.gc.waravi.R
import com.gc.waravi.databinding.LayoutVolumeSettingDialogBinding
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.utils.Utils

class VolumeDialogFragment : DialogFragment() {
    private lateinit var binding : LayoutVolumeSettingDialogBinding
    private var streamType : Int = AudioManager.STREAM_VOICE_CALL

    override fun onStart() {
        dialog?.window?.setLayout(600, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        super.onStart()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = LayoutVolumeSettingDialogBinding.inflate(inflater, container, false)
        initViews()
        return binding.root
    }

    private fun initViews() {
        streamType = if(Utils.isRunningOnTV(requireContext())) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
        binding.btnClose.setOnClickListener {
            this.dismiss()
        }
        initVolumeSetting()
    }

    private fun initVolumeSetting() {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(streamType)
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val savedVolume = PrefUtils.getCallVolume(requireContext())
        binding.sbVolume.max = maxVolume
        binding.sbVolume.incrementProgressBy(1)
        binding.sbVolume.progress = if (savedVolume != -1) savedVolume else currentVolume
        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, p2: Boolean) {
                if(progress > 0){
                    PrefUtils.saveCallVolume(requireContext(), progress)
                    setSystemVolume(progress)
                } else{
                    Toast.makeText(requireContext(), getText(R.string.msg_volume_0_error), Toast.LENGTH_SHORT).show()
                    seekBar.progress = 1
                    setSystemVolume(1)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {

            }

        })
    }

    private fun setSystemVolume(volumeValue: Int){
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(streamType, volumeValue, 0)
    }
}