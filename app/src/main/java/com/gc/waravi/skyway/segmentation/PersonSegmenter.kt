package com.gc.waravi.skyway.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter.ImageSegmenterOptions
import java.nio.ByteOrder

/**
 * Encapsulates MediaPipe Image Segmenter for person detection.
 * Optimized for Android TV with frame skipping and downsampling.
 */
class PersonSegmenter(context: Context) {
    private val TAG = "PersonSegmenter"
    private var segmenter: ImageSegmenter? = null
    
    private val segmentationInterval = 3 // Process every 3rd frame
    private var frameCount = 0
    private var lastMask: Bitmap? = null
    
    private val aiInputSize = 256
    private var aiInputBitmap: Bitmap? = null

    init {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("selfie_segmenter.tflite")
                .setDelegate(Delegate.CPU) // CPU is more stable on various TV chipsets
            
            val options = ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .build()
            
            segmenter = ImageSegmenter.createFromOptions(context.applicationContext, options)
            Log.d(TAG, "ImageSegmenter initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ImageSegmenter: ${e.message}")
        }
    }

    /**
     * Processes the input bitmap and returns a segmentation mask.
     * Uses downsampling and frame skipping for performance.
     */
    @Synchronized
    fun getMask(input: Bitmap): Bitmap? {
        val segmenter = segmenter ?: return null
        
        if (frameCount % segmentationInterval == 0 || lastMask == null) {
            try {
                // 1. Prepare downsampled input for AI
                val scaledInput = if (aiInputBitmap != null && aiInputBitmap!!.width == aiInputSize) {
                    val canvas = android.graphics.Canvas(aiInputBitmap!!)
                    canvas.drawBitmap(input, null, android.graphics.Rect(0, 0, aiInputSize, aiInputSize), null)
                    aiInputBitmap!!
                } else {
                    aiInputBitmap?.recycle()
                    val newAI = Bitmap.createScaledBitmap(input, aiInputSize, aiInputSize, true)
                    aiInputBitmap = newAI
                    newAI
                }

                // 2. Run Inference
                val mpImage = BitmapImageBuilder(scaledInput).build()
                val result = segmenter.segment(mpImage)
                val masks = result.confidenceMasks()
                
                if (masks.isPresent && masks.get().isNotEmpty()) {
                    val maskProxy = masks.get()[0]
                    val byteBuffer = ByteBufferExtractor.extract(maskProxy).order(ByteOrder.nativeOrder())
                    val floatBuffer = byteBuffer.asFloatBuffer()
                    
                    val mWidth = maskProxy.width
                    val mHeight = maskProxy.height
                    val maskPixels = IntArray(mWidth * mHeight)
                    
                    for (i in 0 until mWidth * mHeight) {
                        val confidence = floatBuffer.get()
                        // Store confidence in alpha channel, RGB as white
                        val alpha = (confidence * 255).toInt().coerceIn(0, 255)
                        maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
                    }
                    
                    val newMask = Bitmap.createBitmap(maskPixels, mWidth, mHeight, Bitmap.Config.ARGB_8888)
                    
                    // Create high-res mask for GPU blending
                    val smoothedMask = Bitmap.createScaledBitmap(newMask, input.width, input.height, true)
                    
                    // Critical section for updating lastMask
                    val oldMask = lastMask
                    lastMask = smoothedMask
                    oldMask?.recycle()
                    
                    newMask.recycle()
                    masks.get().forEach { it.close() }
                }
                mpImage.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error during segmentation: ${e.message}")
            }
        }
        
        frameCount++
        return lastMask
    }

    @Synchronized
    fun close() {
        segmenter?.close()
        lastMask?.recycle()
        aiInputBitmap?.recycle()
        lastMask = null
        aiInputBitmap = null
        segmenter = null
    }
}
