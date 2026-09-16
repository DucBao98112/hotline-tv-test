package com.gc.waravi.views.dialogs

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.gc.waravi.R
import com.gc.waravi.base.BaseDialogFragment
import com.gc.waravi.databinding.DialogVideoResolutionSelectionBinding
import com.gc.waravi.models.VideoResolution
import com.gc.waravi.skyway.UsbCameraSource
import com.gc.waravi.utils.PrefUtils
import com.gc.waravi.views.fragments.CallSettingFragment
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.local.source.CameraSource
import com.ntt.skyway.core.content.sink.SurfaceViewRenderer
import com.serenegiant.widget.AspectRatioSurfaceView
import com.serenegiant.widget.AspectRatioTextureView

class VideoResolutionDialogFragment : BaseDialogFragment() {
    private lateinit var binding : DialogVideoResolutionSelectionBinding
    private var videoStream : LocalVideoStream? = null
    private var videoView : View? = null
    private var dismissListener : DialogInterface.OnDismissListener? = null
    private var useUsbCamera = false
    private var currentResolution : VideoResolution? = null
    private var usbCameraStarted = false

    override fun onStart() {
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        super.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedResolution = PrefUtils.getVideoResolution(requireContext())
        val usbCameraSourceEnable = PrefUtils.getCameraSourceSetting(requireContext()) == CallSettingFragment.CAMERA_SOURCE_USB
        UsbCameraSource.initialize(requireActivity(), savedResolution.width, savedResolution.height)
        currentResolution = savedResolution
        useUsbCamera = usbCameraSourceEnable && UsbCameraSource.hasUsbCameraDevice()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogVideoResolutionSelectionBinding.inflate(inflater, container, false)
        initViews()
        return binding.root
    }

    private fun initViews() {
        if (!useUsbCamera){
            val surfaceViewRenderer = SurfaceViewRenderer(requireContext())
            surfaceViewRenderer.apply {
                setup()
                setBackgroundColor(Color.BLACK)
                sink?.setMirror(true)
                sink?.setZOrderOnTop(true)
                sink?.setZOrderMediaOverlay(true)
            }
            videoView = surfaceViewRenderer
            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            lp.gravity = Gravity.CENTER
            binding.previewVideo.addView(videoView, lp)
        }

        binding.btnOK.setOnClickListener {
            videoStream?.removeAllRenderer()
            UsbCameraSource.stop()
            dismiss()
        }
        binding.groupResolution.setOnCheckedChangeListener { _, checkedId ->
            when(checkedId){
                R.id.rd_vga -> applyVideoResolution(VideoResolution.VIDEO_VGA)
                R.id.rd_hd -> applyVideoResolution(VideoResolution.VIDEO_HD)
                R.id.rd_fhd -> applyVideoResolution(VideoResolution.VIDEO_FHD)
            }
        }
        when(currentResolution){
            VideoResolution.VIDEO_VGA -> {
                binding.rdVga.isChecked = true
                binding.rdVga.requestFocus()
            }
            VideoResolution.VIDEO_HD -> {
                binding.rdHd.isChecked = true
                binding.rdHd.requestFocus()
            }
            VideoResolution.VIDEO_FHD -> {
                binding.rdFhd.isChecked = true
                binding.rdFhd.requestFocus()
            }
        }
    }

    private fun createVideoView(){
        binding.previewVideo.removeAllViews()
        val surfaceView = AspectRatioTextureView(requireContext())
        surfaceView.setAspectRatio(16, 9)
        surfaceView.surfaceTextureListener = surfaceTextureListener
        videoView = surfaceView

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        lp.gravity = Gravity.CENTER
        binding.previewVideo.addView(videoView, lp)
    }

    private val surfaceTextureListener = object : SurfaceTextureListener{
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            UsbCameraSource.startCapture(surface, currentResolution!!.width, currentResolution!!.height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        }

    }

    private fun applyVideoResolution(videoResolution: VideoResolution){
        currentResolution = VideoResolution(videoResolution.width, videoResolution.height)
        videoStream?.removeAllRenderer()
        videoStream?.dispose()
        videoStream = null

        if (useUsbCamera){
            if (usbCameraStarted){
                this.stopUsbCamera()
            }
            usbCameraStarted = true
            this.createVideoView()
        } else{
            val deviceId = CameraSource.getBackCameras(requireContext()).firstOrNull()
                ?: CameraSource.getCameras(requireContext()).firstOrNull()
            if (deviceId.isNullOrEmpty()) return
            CameraSource.startCapturing(
                requireContext(),
                deviceId,
                CameraSource.CapturingOptions(videoResolution.width, videoResolution.height)
            )
            videoStream = CameraSource.createStream()
            videoStream?.apply {
                addRenderer(videoView as SurfaceViewRenderer)
            }
        }
        PrefUtils.saveVideoResolution(requireContext(), videoResolution)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener){
        this.dismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        dismissListener?.onDismiss(dialog)
        super.onDismiss(dialog)
    }

    private fun stopUsbCamera(){
        if (videoView is AspectRatioSurfaceView){
            (videoView as AspectRatioSurfaceView).getSurface().release()
            videoView = null
        }
        UsbCameraSource.close()
    }

    override fun onDestroyView() {
        videoStream?.removeAllRenderer()
        videoStream?.dispose()
        CameraSource.stopCapturing()
        stopUsbCamera()
        super.onDestroyView()
    }

}