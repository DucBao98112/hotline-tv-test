package com.gc.waravi.skyway

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.local.source.CustomVideoFrameSource
import com.serenegiant.usb.DeviceFilter
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.utils.ThreadPool
import java.lang.UnsupportedOperationException

@SuppressLint("StaticFieldLeak")
object UsbCameraSource {
    private val TAG = this::class.simpleName
    
    private const val DEFAULT_WIDTH = 640
    private const val DEFAULT_HEIGHT = 480

    private var videoFrameSource : CustomVideoFrameSource? = null
    val source: CustomVideoFrameSource?
        get() = videoFrameSource
    private var width = DEFAULT_WIDTH
    private var height = DEFAULT_HEIGHT

    private var mSync = Any()

    private var mUSBMonitor: USBMonitor? = null
    private var mUVCCamera: UVCCamera? = null
//    private var videoView: IAspectRatio? = null
    private var mPreviewSurface : Surface? = null
    private var isCapturing = false
    private var videoStream : LocalVideoStream? = null

    fun initialize(context: Context, width: Int = DEFAULT_WIDTH, height: Int = DEFAULT_HEIGHT){
        this.width = width
        this.height = height
        mUSBMonitor = USBMonitor(context, onDeviceConnectListener)
        mUSBMonitor?.setDeviceFilter(DeviceFilter.getDeviceFilters(context, com.serenegiant.uvccamera.R.xml.device_filter))
        mUSBMonitor?.register()
    }

    private var dummySurfaceTexture: SurfaceTexture? = null
    private var dummySurface: Surface? = null

    fun startHeadlessCapture() {
        if (isCapturing) return
        
        // Create a dummy surface to satisfy UVCCamera requirements
        dummySurfaceTexture = SurfaceTexture(10).apply {
            setDefaultBufferSize(width, height)
        }
        dummySurface = Surface(dummySurfaceTexture)
        startCameraCapture(dummySurface!!)
    }

    fun stop(){
        mUSBMonitor?.unregister()
        releaseCamera()
        if (mPreviewSurface != null) {
            mPreviewSurface!!.release()
            mPreviewSurface = null
        }
        dummySurface?.release()
        dummySurface = null
        dummySurfaceTexture?.release()
        dummySurfaceTexture = null
        videoStream = null
    }

    fun createVideoStream() : LocalVideoStream {
        videoFrameSource = CustomVideoFrameSource(width, height)
        if (videoStream == null){
            videoStream = videoFrameSource?.createStream()
        }
        return videoStream!!
    }

    private var reusableInputBitmap: Bitmap? = null
    private var reusableMirrorBitmap: Bitmap? = null
    private val mirrorMatrix = Matrix().apply { preScale(-1f, 1f) }
    private val mirrorLock = Any()

    private val mIFrameCallback = IFrameCallback { frame ->
        synchronized(mirrorLock) {
            if (reusableInputBitmap == null || reusableInputBitmap!!.width != width || reusableInputBitmap!!.height != height) {
                reusableInputBitmap?.recycle()
                reusableInputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            }
            
            frame.clear()
            reusableInputBitmap!!.copyPixelsFromBuffer(frame)
            
            // Send the raw frame with rotation tag.
            // Rendering and mirroring are handled by SkyWay renderers.
            videoFrameSource?.updateFrame(reusableInputBitmap!!, 180)
        }
    }

    private fun mirrorBitmap(source: Bitmap): Bitmap {
        synchronized(mirrorLock) {
            val mirrored = if (reusableMirrorBitmap != null && 
                reusableMirrorBitmap!!.width == source.width && 
                reusableMirrorBitmap!!.height == source.height) {
                reusableMirrorBitmap!!
            } else {
                reusableMirrorBitmap?.recycle()
                val newBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.RGB_565)
                reusableMirrorBitmap = newBitmap
                newBitmap
            }
            
            val canvas = Canvas(mirrored)
            canvas.drawBitmap(source, mirrorMatrix, null)
            return mirrored
        }
    }

    private val onSurfaceHolderCallback = object : SurfaceHolder.Callback{
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "surfaceCreated...${holder.surface}")
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if ((width == 0 || height == 0) || isCapturing) return
            Log.d(TAG, "surfaceChanged...$width/$height")
            startCameraCapture(holder.surface)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
        }

    }

    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) {
            Log.d(TAG, "DEVICE: ${device.deviceName} / ${device.productName}/ ${device.deviceClass}/ ${device.vendorId}")
            if(isCapturing){
                if(mUSBMonitor!!.hasPermission(device).not()){
                    mUSBMonitor!!.requestPermission(device)
                } else{
                    mUSBMonitor!!.connect(device)
                }
            }
        }

        override fun onDettach(device: UsbDevice) {
        }

        override fun onConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) {
            releaseCamera()
            ThreadPool.queueEvent() {
                val camera = UVCCamera()
                try {
                    camera.open(ctrlBlock)
                } catch (ex: UnsupportedOperationException){
                    Log.d(TAG, "can not open camera: ${ex.message}")
                    ex.printStackTrace()
                    isCapturing = false
                }
                try {
                    camera.setPreviewSize(
                        width,
                        height,
                        UVCCamera.FRAME_FORMAT_MJPEG
                    )
                } catch (e: IllegalArgumentException) {
                    // fallback to YUV mode
                    try {
                        camera.setPreviewSize(
                            width,
                            height,
                            UVCCamera.DEFAULT_PREVIEW_MODE
                        )
                    } catch (e1: IllegalArgumentException) {
                        camera.destroy()
                    }
                }
                Log.d(TAG, "onConnect: $width/$height")
                if (mPreviewSurface != null) {
                    camera.setPreviewDisplay(mPreviewSurface)
                    camera.setFrameCallback(
                        mIFrameCallback,
                        UVCCamera.PIXEL_FORMAT_RGB565 /*UVCCamera.PIXEL_FORMAT_NV21*/
                    )
                    camera.startPreview()
                    camera.updateCameraParams()
                    isCapturing = true
                }
                synchronized(mSync) { mUVCCamera = camera }
            }
        }

        override fun onDisconnect(device: UsbDevice, p1: USBMonitor.UsbControlBlock) {
            releaseCamera()
        }

        override fun onCancel(device: UsbDevice) {
        }

    }

    fun hasUsbCameraDevice() : Boolean{
        return getDevice() != null
    }

    fun getDevice(): UsbDevice?{
        return mUSBMonitor?.deviceList?.firstOrNull()
    }

    @Synchronized
    private fun releaseCamera() {
        isCapturing = false
        synchronized(mSync) {
            if (mUVCCamera != null) {
                try {
                    mUVCCamera!!.setStatusCallback(null)
                    mUVCCamera!!.setButtonCallback(null)
                    mUVCCamera!!.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(TAG, "Error when stop camera: ${e.localizedMessage}")
                }
                mUVCCamera = null
            }
        }
    }

    fun setCameraEnabled(isEnable: Boolean){
        val device = getDevice() ?: return
        if (isEnable){
            mUSBMonitor?.connect(device)
        } else{
            mUVCCamera?.destroy()
        }
        isCapturing = isEnable
    }

    fun startCapture(surfaceView: SurfaceView){
        surfaceView.holder.addCallback(onSurfaceHolderCallback)
    }

    fun startCapture(surface: Surface, width: Int? = null, height: Int? = null){
        this.mPreviewSurface = surface
        if (width != null) {
            this.width = width
        }
        if (height != null) {
            this.height = height
        }
        getDevice()?.let { device ->
            isCapturing = true
            if (mUSBMonitor!!.hasPermission(device)){
                mUSBMonitor!!.connect(device)
            } else{
                mUSBMonitor!!.requestPermission(device)
            }
        }
    }

    fun startCapture(surfaceTexture: SurfaceTexture, width: Int? = null, height: Int? = null){
        if (width != null) {
            this.width = width
        }
        if (height != null) {
            this.height = height
        }
        this.startCameraCapture(Surface(surfaceTexture))
    }

    private fun startCameraCapture(surface: Surface){
        this.mPreviewSurface = surface
        getDevice()?.let { device ->
            isCapturing = true
            if (mUSBMonitor!!.hasPermission(device)){
                mUSBMonitor!!.connect(device)
            } else{
                mUSBMonitor!!.requestPermission(device)
            }
        }
    }

    fun close(){
        releaseCamera()
    }

    fun isCameraOpened() : Boolean{
        return mUVCCamera != null
    }
}