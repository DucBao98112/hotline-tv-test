package com.gc.waravi.skyway

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter.ImageSegmenterOptions
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import com.ntt.skyway.core.content.local.source.VideoFrame
import com.ntt.skyway.core.content.local.source.VideoProcessor
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * CPU VideoProcessor optimized for Android TV.
 *
 * TV Blueprint Architecture:
 * 1. AI Frame Interval = 2L (Runs AI every 2 frames for balanced performance).
 * 2. Strict Temporal Mask Lookup: floorEntry(timestamp) with MAX_MASK_DELAY = 3L.
 * 3. 3-Zone Mask with NaN/Infinity safeguards and smooth cubic curve interpolation.
 * 4. Thresholds: BODY_THRESHOLD = 0.42f, BACKGROUND_THRESHOLD = 0.16f.
 * 5. 3x3 Dilation (dilateMask) to prevent hands/hair from being clipped.
 * 6. Direct current frame usage: matchingFrame = normalizedBitmap!!.
 */
class CpuVideoProcessor(
    context: Context
) : VideoProcessor {

    private val TAG = "CpuVideoProcessor"
    private val diagnosticsDir = java.io.File(context.cacheDir, "background-diagnostics")

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private companion object {
        // The Android-compatible fallback model is trained on a square
        // 256x256 image. Keep that geometry and letterbox the camera frame;
        // stretching a 16:9 person into 256x144 was making the mask leak
        // through faces and shoulders.
        const val AI_INPUT_WIDTH = 256
        const val AI_INPUT_HEIGHT = 256

        // Balance face detail with smooth motion on the Android TV CPU.
        const val FRAME_DECODE_QUALITY = 85

        // Segment every output frame so the virtual background does not lag
        // behind a moving person.
        const val AI_FRAME_INTERVAL = 1L

        // Cap CPU bitmap conversion/compositing to a TV-friendly 16:9 size.
        const val MAX_PROCESS_HEIGHT = 176

        // Confidence thresholds for the person mask.
        const val BODY_THRESHOLD = 0.36f
        // Reject uncertain edge pixels from the real camera image. This removes
        // the bright/white halo around the person before compositing.
        const val BACKGROUND_THRESHOLD = 0.22f

        // Closing fills small gaps introduced while a person is moving, then
        // restores the outline to its original size. It protects the subject
        // without pulling real-camera pixels into the virtual background.
        const val PERSON_MASK_CLOSING_PASSES = 1

        // Keep a history for matching completed asynchronous inference to source frame.
        const val HISTORY_SIZE = 8

        // Maximum allowed age for a mask before falling back to clean camera frame (eliminates ghosting).
        const val MAX_MASK_DELAY = 5L

        // Maximum time to wait for processor lock (non-blocking for smooth video rendering).
        const val LOCK_TIMEOUT_MS = 10L
    }

    // ============================================================
    // THREAD SAFETY
    // ============================================================

    private val processorLock = ReentrantLock()
    private val maskLock = Any()
    private val stableOutputLock = Any()

    // ============================================================
    // MEDIA PIPE
    // ============================================================

    private var segmenter: ImageSegmenter? = null

    // ============================================================
    // FRAME / MASK STATE
    // ============================================================

    private data class FrameState(
        val frame: Bitmap,
        val timestamp: Long
    )

    private data class MaskState(
        val mask: Bitmap,
        val timestamp: Long
    )

    /**
     * Frames indexed by the same timestamp sent to MediaPipe.
     */
    private val frameHistory = TreeMap<Long, FrameState>()

    /**
     * Masks returned by MediaPipe.
     */
    private val maskHistory = TreeMap<Long, MaskState>()

    private var frameCounter = 0L

    /**
     * Last AI result timestamp.
     */
    private var lastAvailableMaskTimestamp: Long? = null

    // ============================================================
    // BACKGROUND
    // ============================================================

    @Volatile
    private var backgroundBitmapInternal: Bitmap? = null

    /**
     * Public background.
     *
     * IMPORTANT:
     * Do not recycle the bitmap from UI code after assigning it here.
     * This class owns the cached copy.
     */
    var backgroundBitmap: Bitmap?
        get() = backgroundBitmapInternal
        set(value) {
            Log.d(
                TAG,
                "BACKGROUND SET -> " +
                    "bitmap=${value?.width}x${value?.height}, " +
                    "null=${value == null}, " +
                    "recycled=${value?.isRecycled}"
            )

            processorLock.withLock {
                if (value === backgroundBitmapInternal) {
                    return
                }

                backgroundBitmapInternal = value

                // Force background cache rebuild on next frame.
                lastBackgroundSource = null
            }
        }

    private var lastBackgroundSource: Bitmap? = null
    private var cachedBackground: Bitmap? = null

    // ============================================================
    // PUBLIC SETTINGS
    // ============================================================

    /**
     * Reserved for future person blur.
     */
    @Volatile
    var blurStrength: Int = 0

    /**
     * Background blur.
     */
    @Volatile
    var bgBlurStrength: Int = 0

    /**
     * Debug mode:
     * show segmentation mask instead of normal composite.
     */
    @Volatile
    var showMaskOnly: Boolean = false

    /**
     * Additional rotation in degrees (0, 90, 180, 270).
     */
    @Volatile
    var rotationDegrees: Int = 0

    /**
     * Flip frame horizontally.
     * Default = false.
     */
    @Volatile
    var mirrorHorizontal: Boolean = false

    /**
     * Flip frame vertically.
     * Default = false.
     */
    @Volatile
    var flipVertical: Boolean = false

    // ============================================================
    // BITMAP POOL & PREALLOCATED BUFFERS
    // ============================================================

    private val framePool = mutableListOf<Bitmap>()

    private fun obtainFrameBitmap(
        width: Int,
        height: Int
    ): Bitmap {
        synchronized(framePool) {
            val iterator = framePool.iterator()
            while (iterator.hasNext()) {
                val bitmap = iterator.next()
                if (
                    !bitmap.isRecycled &&
                    bitmap.width == width &&
                    bitmap.height == height
                ) {
                    iterator.remove()
                    return bitmap
                }
            }
        }

        return Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        ).apply {
            density = Bitmap.DENSITY_NONE
        }
    }

    private fun releaseFrameBitmap(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) {
            return
        }

        synchronized(framePool) {
            if (framePool.size < HISTORY_SIZE) {
                framePool.add(bitmap)
            } else {
                bitmap.recycle()
            }
        }
    }

    // Reusable bitmaps
    private var normalizedBitmap: Bitmap? = null
    private var normalizedCanvas: Canvas? = null

    private var aiInputBitmap: Bitmap? = null
    private var aiInputCanvas: Canvas? = null
    // Content rectangle inside the square AI bitmap. The result mask uses the
    // same rectangle and must be cropped before it is mapped to the camera.
    private var aiContentLeft = 0
    private var aiContentTop = 0
    private var aiContentRight = AI_INPUT_WIDTH
    private var aiContentBottom = AI_INPUT_HEIGHT

    private var maskedPersonBitmap: Bitmap? = null
    private var maskedPersonCanvas: Canvas? = null

    private var compositeBitmap: Bitmap? = null
    private var compositeCanvas: Canvas? = null

    // SkyWay retains the rotation metadata of the source frame after a processor
    // returns a replacement frame. Keep a buffer in source-pixel orientation so
    // the renderer's one remaining rotation displays the final image upright.
    private var outputBitmap: Bitmap? = null
    private var outputCanvas: Canvas? = null

    // Last fully composited frame in the source orientation. It is safe to
    // display while the next segmentation pass is running.
    private var stableOutputBitmap: Bitmap? = null
    private var stableOutputCanvas: Canvas? = null

    // Dynamic mask pixels buffer allocated on demand according to actual mask size
    private var maskPixelsBuffer: IntArray? = null
    private var dilatedMaskPixels: IntArray? = null
    private var erodedMaskPixels: IntArray? = null

    // ============================================================
    // PAINTS & MATRIX
    // ============================================================

    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or
                Paint.FILTER_BITMAP_FLAG or
                Paint.DITHER_FLAG
    )

    private val personMaskPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or
                Paint.FILTER_BITMAP_FLAG
    ).apply {
        xfermode = PorterDuffXfermode(
            PorterDuff.Mode.DST_IN
        )
    }

    private val transformMatrix = Matrix()

    // ============================================================
    // INITIALIZATION
    // ============================================================

    init {
        try {
            val modelPath = try {
                context.applicationContext.assets.open("selfie_segmenter_landscape.tflite").close()
                "selfie_segmenter_landscape.tflite"
            } catch (_: Throwable) {
                "selfie_segmenter.tflite"
            }

            Log.d(TAG, "Loading MediaPipe asset model: $modelPath")

            // This TV firmware is not stable with MediaPipe's GPU delegate
            // during a live call, so always use the reliable CPU delegate.
            segmenter = createSegmenter(context, modelPath, Delegate.CPU)
            Log.i(TAG, "ImageSegmenter initialized with CPU delegate")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ImageSegmenter", e)
            segmenter = null
        }
    }

    private fun createSegmenter(
        context: Context,
        modelPath: String,
        delegate: Delegate
    ): ImageSegmenter {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelPath)
            .setDelegate(delegate)
            .build()

        val options = ImageSegmenterOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setOutputConfidenceMasks(true)
            .build()

        return ImageSegmenter.createFromOptions(
            context.applicationContext,
            options
        )
    }

    // ============================================================
    // NORMALIZE CAMERA FRAME
    // ============================================================

    private fun normalizeFrame(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        canvas: Canvas,
        customRotation: Int = 0
    ) {
        canvas.drawColor(
            Color.BLACK,
            PorterDuff.Mode.SRC
        )

        val totalRotation = ((rotationDegrees + customRotation) % 360 + 360) % 360
        val rotated = totalRotation == 90 || totalRotation == 270

        val sourceWidth = if (rotated) source.height else source.width
        val sourceHeight = if (rotated) source.width else source.height

        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return
        }

        val scale = maxOf(
            targetWidth.toFloat() / sourceWidth.toFloat(),
            targetHeight.toFloat() / sourceHeight.toFloat()
        )

        transformMatrix.reset()

        // Center source.
        transformMatrix.postTranslate(
            -source.width / 2f,
            -source.height / 2f
        )

        // Apply rotation.
        if (totalRotation != 0) {
            transformMatrix.postRotate(totalRotation.toFloat())
        }

        // Scale to fill target.
        transformMatrix.postScale(
            scale,
            scale
        )

        // Apply vertical / horizontal flipping if enabled.
        val sx = if (mirrorHorizontal) -1f else 1f
        val sy = if (flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            transformMatrix.postScale(sx, sy)
        }

        // Move to target center.
        transformMatrix.postTranslate(
            targetWidth / 2f,
            targetHeight / 2f
        )

        canvas.drawBitmap(
            source,
            transformMatrix,
            bitmapPaint
        )
    }

    // ============================================================
    // BACKGROUND NORMALIZATION
    // ============================================================

    private fun updateCachedBackground(
        targetWidth: Int,
        targetHeight: Int
    ) {
        val source = backgroundBitmapInternal

        if (source == null || source.isRecycled) {
            return
        }

        val needsUpdate =
            source !== lastBackgroundSource ||
                    cachedBackground == null ||
                    cachedBackground!!.isRecycled ||
                    cachedBackground!!.width != targetWidth ||
                    cachedBackground!!.height != targetHeight

        if (!needsUpdate) {
            return
        }

        val newBitmap = Bitmap.createBitmap(
            targetWidth,
            targetHeight,
            Bitmap.Config.ARGB_8888
        ).apply {
            density = Bitmap.DENSITY_NONE
        }

        val newCanvas = Canvas(newBitmap)

        drawCenterCrop(
            source = source,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            targetCanvas = newCanvas
        )

        val oldBitmap = cachedBackground

        cachedBackground = newBitmap
        lastBackgroundSource = source

        if (
            oldBitmap != null &&
            oldBitmap !== newBitmap &&
            !oldBitmap.isRecycled
        ) {
            oldBitmap.recycle()
        }
    }

    private fun drawCenterCrop(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        targetCanvas: Canvas
    ) {
        if (
            source.width <= 0 ||
            source.height <= 0 ||
            source.isRecycled
        ) {
            return
        }

        val scale = maxOf(
            targetWidth.toFloat() / source.width.toFloat(),
            targetHeight.toFloat() / source.height.toFloat()
        )

        val drawWidth = source.width * scale
        val drawHeight = source.height * scale

        val left = (targetWidth - drawWidth) / 2f
        val top = (targetHeight - drawHeight) / 2f

        val rect = android.graphics.RectF(
            left,
            top,
            left + drawWidth,
            top + drawHeight
        )

        // Background assets are upright. Camera rotation is applied before AI,
        // so the composite uses upright pixels throughout and needs no rotation.

        targetCanvas.drawBitmap(
            source,
            null,
            rect,
            bitmapPaint
        )

    }

    private fun drawMask(
        canvas: Canvas,
        mask: Bitmap,
        width: Int,
        height: Int,
        paint: Paint
    ) {
        transformMatrix.reset()
        transformMatrix.postScale(
            width.toFloat() / mask.width.toFloat(),
            height.toFloat() / mask.height.toFloat()
        )
        canvas.drawBitmap(
            mask,
            transformMatrix,
            paint
        )
    }

    // ============================================================
    // AI INPUT (256x256 letterboxed; no person distortion)
    // ============================================================

    private fun prepareAiInput(
        normalized: Bitmap
    ): Bitmap {
        if (
            aiInputBitmap == null ||
            aiInputBitmap!!.isRecycled
        ) {
            aiInputBitmap = Bitmap.createBitmap(
                AI_INPUT_WIDTH,
                AI_INPUT_HEIGHT,
                Bitmap.Config.ARGB_8888
            ).apply {
                density = Bitmap.DENSITY_NONE
            }

            aiInputCanvas = Canvas(aiInputBitmap!!)
        }

        val canvas = aiInputCanvas!!
        val scale = minOf(
            AI_INPUT_WIDTH.toFloat() / normalized.width.toFloat(),
            AI_INPUT_HEIGHT.toFloat() / normalized.height.toFloat()
        )
        val contentWidth = (normalized.width * scale).toInt().coerceAtLeast(1)
        val contentHeight = (normalized.height * scale).toInt().coerceAtLeast(1)
        aiContentLeft = (AI_INPUT_WIDTH - contentWidth) / 2
        aiContentTop = (AI_INPUT_HEIGHT - contentHeight) / 2
        aiContentRight = aiContentLeft + contentWidth
        aiContentBottom = aiContentTop + contentHeight

        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        canvas.drawBitmap(
            normalized,
            null,
            android.graphics.Rect(
                aiContentLeft,
                aiContentTop,
                aiContentRight,
                aiContentBottom
            ),
            bitmapPaint
        )

        return aiInputBitmap!!
    }

    /** Maps the letterboxed AI content rectangle to the returned mask size. */
    private fun maskContentRect(mask: Bitmap): android.graphics.Rect {
        val left = (aiContentLeft.toLong() * mask.width / AI_INPUT_WIDTH).toInt()
        val top = (aiContentTop.toLong() * mask.height / AI_INPUT_HEIGHT).toInt()
        val right = (aiContentRight.toLong() * mask.width / AI_INPUT_WIDTH).toInt()
        val bottom = (aiContentBottom.toLong() * mask.height / AI_INPUT_HEIGHT).toInt()

        return android.graphics.Rect(
            left.coerceIn(0, mask.width - 1),
            top.coerceIn(0, mask.height - 1),
            right.coerceIn(1, mask.width),
            bottom.coerceIn(1, mask.height)
        )
    }

    // ============================================================
    // MAIN VIDEO PROCESSOR
    // ============================================================

    override fun onFrameCaptured(
        videoFrame: VideoFrame
    ): VideoFrame {

        val locked = try {
            processorLock.tryLock(
                LOCK_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
        } catch (_: Throwable) {
            false
        }

        if (!locked) {
            return lastStableOutputFrame() ?: videoFrame
        }

        try {
            val timestamp = frameCounter++

            val currentSegmenter = segmenter ?: return lastStableOutputFrame() ?: videoFrame
            val frameHeight = videoFrame.height
            if (frameHeight <= 0) {
                return videoFrame
            }

            val processingHeight = minOf(frameHeight, MAX_PROCESS_HEIGHT)
            val targetWidth = (((processingHeight * 16 / 9) + 15) / 16) * 16
            val targetHeight = ((processingHeight + 15) / 16) * 16

            // 1. CAMERA FRAME -> NORMALIZED FRAME
            ensureNormalizedBitmap(
                targetWidth,
                targetHeight
            )

            val rawBitmap = VideoProcessor.videoFrameToBitmap(
                videoFrame,
                FRAME_DECODE_QUALITY
            )
            if (rawBitmap.width <= 0 || rawBitmap.height <= 0) {
                return videoFrame
            }

            normalizeFrame(
                source = rawBitmap,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                canvas = normalizedCanvas!!,
                customRotation = videoFrame.rotation
            )

            // 2. STORE FRAME WITH TIMESTAMP

            val frameCopy = obtainFrameBitmap(
                targetWidth,
                targetHeight
            )

            Canvas(frameCopy).drawBitmap(
                normalizedBitmap!!,
                0f,
                0f,
                bitmapPaint
            )

            frameHistory[timestamp] = FrameState(
                frame = frameCopy,
                timestamp = timestamp
            )

            // Clean up old frames immediately to keep RAM buffer at <= HISTORY_SIZE
            cleanupFrameHistory()

            // 3. SEND EVERY Nth FRAME TO AI
            if (timestamp % AI_FRAME_INTERVAL == 0L) {
                val aiBitmap = prepareAiInput(normalizedBitmap!!)

                try {
                    val mpImage = BitmapImageBuilder(aiBitmap).build()
                    try {
                        onAiResult(currentSegmenter.segmentForVideo(mpImage, timestamp))
                    } finally {
                        mpImage.close()
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Person segmentation failed", e)
                }
            }

            // 4. FIND BEST AVAILABLE MASK (Strict temporal delay check to prevent ghosting)
            val maskState = synchronized(maskLock) {
                maskHistory[timestamp]
            }

            val maskDelay = maskState?.let {
                timestamp - it.timestamp
            } ?: Long.MAX_VALUE

            val isMaskValid =
                maskState != null &&
                        !maskState.mask.isRecycled &&
                        maskDelay >= 0 &&
                        maskDelay <= MAX_MASK_DELAY

            if (com.gc.waravi.BuildConfig.DEBUG && timestamp == 30L) {
                try {
                    diagnosticsDir.mkdirs()
                    java.io.FileOutputStream(java.io.File(diagnosticsDir, "camera.png")).use {
                        normalizedBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    maskState?.mask?.let { mask ->
                        java.io.FileOutputStream(java.io.File(diagnosticsDir, "mask.png")).use {
                            mask.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    Log.i(TAG, "UPRIGHT_KEEP_PERSON frame=$timestamp appliedRotation=${videoFrame.rotation} outputRotation=0 mask=${maskState?.timestamp} valid=$isMaskValid")
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot save diagnostic frame", e)
                }
            }

            // 5. DEBUG MASK
            if (
                showMaskOnly &&
                isMaskValid
            ) {
                ensureCompositeBitmap(
                    targetWidth,
                    targetHeight
                )

                val canvas = compositeCanvas!!
                canvas.drawColor(
                    Color.BLACK,
                    PorterDuff.Mode.SRC
                )

                val debugMask = maskState!!.mask
                canvas.drawBitmap(
                    debugMask,
                    maskContentRect(debugMask),
                    android.graphics.Rect(0, 0, targetWidth, targetHeight),
                    bitmapPaint
                )

                return createOutputFrame(compositeBitmap!!, videoFrame.rotation)
            }

            // 6. NO BACKGROUND / STALE MASK -> FALLBACK TO ORIGINAL CAMERA FRAME (NEVER OUTPUT BLACK)
            val hasBackground = backgroundBitmapInternal != null &&
                    !backgroundBitmapInternal!!.isRecycled

            val needProcessing = isMaskValid &&
                    (hasBackground || blurStrength > 0 || bgBlurStrength > 0)

            if (!needProcessing) {
                return createOutputFrame(normalizedBitmap!!, videoFrame.rotation)
            }

            // 7. PREPARE COMPOSITE BUFFERS
            ensureCompositeBitmap(
                targetWidth,
                targetHeight
            )

            // 8. UPDATE BACKGROUND CACHE
            if (hasBackground) {
                updateCachedBackground(
                    targetWidth,
                    targetHeight
                )
            }

            val bg = cachedBackground

            if (bg == null || bg.isRecycled) {
                return createOutputFrame(normalizedBitmap!!, videoFrame.rotation)
            }

            // ============================================================
            // 10. COMPOSITE: BACKGROUND + MASKED PERSON
            // ============================================================

            val composite = compositeCanvas!!
            val person = maskedPersonBitmap!!
            val personCanvas = maskedPersonCanvas!!

            // A. Clear person layer
            personCanvas.drawColor(
                Color.TRANSPARENT,
                PorterDuff.Mode.CLEAR
            )

            // Use only the exact frame that produced this mask.
            val matchingFrame = frameHistory[maskState!!.timestamp]?.frame

            if (matchingFrame == null || matchingFrame.isRecycled) {
                return createOutputFrame(normalizedBitmap!!, videoFrame.rotation)
            }

            personCanvas.drawBitmap(
                matchingFrame,
                0f,
                0f,
                bitmapPaint
            )

            // C. Apply PERSON mask using DST_IN
            val personMask = maskState!!.mask
            personCanvas.drawBitmap(
                personMask,
                maskContentRect(personMask),
                android.graphics.RectF(
                    0f,
                    0f,
                    targetWidth.toFloat(),
                    targetHeight.toFloat()
                ),
                personMaskPaint
            )

            // D. Draw preloaded background
            composite.drawColor(
                Color.TRANSPARENT,
                PorterDuff.Mode.CLEAR
            )

            composite.drawBitmap(
                bg,
                0f,
                0f,
                bitmapPaint
            )

            // E. Draw masked person over background
            composite.drawBitmap(
                person,
                0f,
                0f,
                bitmapPaint
            )

            // F. Output
            return createOutputFrame(compositeBitmap!!, videoFrame.rotation)

        } catch (e: Throwable) {
            Log.e(TAG, "Video processing error", e)
            return videoFrame

        } finally {
            processorLock.unlock()
        }
    }

    // ============================================================
    // BITMAP INITIALIZATION
    // ============================================================

    private fun ensureNormalizedBitmap(
        width: Int,
        height: Int
    ) {
        if (
            normalizedBitmap == null ||
            normalizedBitmap!!.isRecycled ||
            normalizedBitmap!!.width != width ||
            normalizedBitmap!!.height != height
        ) {
            normalizedBitmap?.recycle()

            normalizedBitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            ).apply {
                density = Bitmap.DENSITY_NONE
            }

            normalizedCanvas = Canvas(normalizedBitmap!!)
        }
    }

    private fun ensureCompositeBitmap(
        width: Int,
        height: Int
    ) {
        if (
            compositeBitmap == null ||
            compositeBitmap!!.isRecycled ||
            compositeBitmap!!.width != width ||
            compositeBitmap!!.height != height
        ) {
            compositeBitmap?.recycle()
            maskedPersonBitmap?.recycle()

            compositeBitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            ).apply {
                density = Bitmap.DENSITY_NONE
            }

            compositeCanvas = Canvas(compositeBitmap!!)

            maskedPersonBitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            ).apply {
                density = Bitmap.DENSITY_NONE
            }

            maskedPersonCanvas = Canvas(maskedPersonBitmap!!)
        }
    }

    /**
     * The USB source tags frames with a 180° display rotation. The processor
     * works on upright pixels for segmentation, but the SkyWay renderer applies
     * that tag again after this method returns. Rotate the completed frame back
     * to source orientation so person and background are shown upright together.
     */
    private fun createOutputFrame(
        uprightBitmap: Bitmap,
        sourceRotation: Int
    ): VideoFrame {
        val normalizedRotation = ((sourceRotation % 360) + 360) % 360

        if (normalizedRotation != 180) {
            rememberStableOutput(uprightBitmap)
            return VideoProcessor.bitmapToVideoFrame(uprightBitmap, 0)
        }

        if (
            outputBitmap == null ||
            outputBitmap!!.isRecycled ||
            outputBitmap!!.width != uprightBitmap.width ||
            outputBitmap!!.height != uprightBitmap.height
        ) {
            outputBitmap?.recycle()
            outputBitmap = Bitmap.createBitmap(
                uprightBitmap.width,
                uprightBitmap.height,
                Bitmap.Config.ARGB_8888
            ).apply {
                density = Bitmap.DENSITY_NONE
            }
            outputCanvas = Canvas(outputBitmap!!)
        }

        outputCanvas!!.apply {
            drawColor(Color.BLACK, PorterDuff.Mode.SRC)
            save()
            rotate(180f, uprightBitmap.width / 2f, uprightBitmap.height / 2f)
            drawBitmap(uprightBitmap, 0f, 0f, bitmapPaint)
            restore()
        }

        rememberStableOutput(outputBitmap!!)
        return VideoProcessor.bitmapToVideoFrame(outputBitmap!!, 0)
    }

    private fun rememberStableOutput(source: Bitmap) {
        synchronized(stableOutputLock) {
            if (
                stableOutputBitmap == null ||
                stableOutputBitmap!!.isRecycled ||
                stableOutputBitmap!!.width != source.width ||
                stableOutputBitmap!!.height != source.height
            ) {
                stableOutputBitmap?.recycle()
                stableOutputBitmap = Bitmap.createBitmap(
                    source.width,
                    source.height,
                    Bitmap.Config.ARGB_8888
                ).apply {
                    density = Bitmap.DENSITY_NONE
                }
                stableOutputCanvas = Canvas(stableOutputBitmap!!)
            }

            stableOutputCanvas!!.drawBitmap(source, 0f, 0f, bitmapPaint)
        }
    }

    private fun lastStableOutputFrame(): VideoFrame? {
        synchronized(stableOutputLock) {
            val stable = stableOutputBitmap
            return if (stable != null && !stable.isRecycled) {
                VideoProcessor.bitmapToVideoFrame(stable, 0)
            } else {
                null
            }
        }
    }

    // ============================================================
    // AI RESULT & 3-ZONE MASK PROCESSING
    // ============================================================

    private fun onAiResult(
        result: ImageSegmenterResult
    ) {
        val timestamp = result.timestampMs()

        try {
            val masks = result.confidenceMasks()
            if (!masks.isPresent || masks.get().isEmpty()) {
                return
            }

            val confidenceMask = masks.get()[0]
            val width = confidenceMask.width
            val height = confidenceMask.height

            if (width <= 0 || height <= 0) {
                return
            }

            val byteBuffer = ByteBufferExtractor.extract(confidenceMask)
                .order(ByteOrder.nativeOrder())
            val floatBuffer = byteBuffer.asFloatBuffer()

            val total = width * height
            val pixels = if (maskPixelsBuffer != null && maskPixelsBuffer!!.size == total) {
                maskPixelsBuffer!!
            } else {
                val newArr = IntArray(total)
                maskPixelsBuffer = newArr
                newArr
            }

            processThreeZoneMask(floatBuffer, pixels, total)
            fillEnclosedMaskHoles(pixels, width, height)

            // Close tiny gaps in the current mask. Unlike a bare dilation,
            // this does not enlarge the person outline or retain a strip of
            // the real room around hair, hands and shoulders.
            var finalPixels = pixels
            repeat(PERSON_MASK_CLOSING_PASSES) {
                finalPixels = erodeMask(
                    dilateMask(finalPixels, width, height),
                    width,
                    height
                )
            }

            val maskBitmap = Bitmap.createBitmap(
                finalPixels,
                width,
                height,
                Bitmap.Config.ARGB_8888
            ).apply {
                density = Bitmap.DENSITY_NONE
            }

            // STORE MASK
            synchronized(maskLock) {
                maskHistory[timestamp] = MaskState(
                    mask = maskBitmap,
                    timestamp = timestamp
                )

                lastAvailableMaskTimestamp = timestamp

                // Remove old masks exceeding HISTORY_SIZE (3)
                while (maskHistory.size > HISTORY_SIZE) {
                    val oldestKey = maskHistory.firstKey()
                    val old = maskHistory.remove(oldestKey)
                    old?.mask?.let {
                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }
                }
            }

            masks.get().forEach {
                try {
                    it.close()
                } catch (_: Throwable) {
                }
            }

        } catch (e: Throwable) {
            Log.e(TAG, "AI result processing error", e)

        }
    }

    /**
     * TV-optimized 3-zone mask processing:
     * - Solid Body (confidence >= 0.42f) -> alpha 255.
     * - Feather Border (0.16f <= confidence < 0.42f) -> alpha 0..255.
     * - Background (confidence < 0.16f) -> alpha 0.
     */
    private fun processThreeZoneMask(
        floatBuffer: FloatBuffer,
        pixels: IntArray,
        total: Int
    ): IntArray {
        java.util.Arrays.fill(pixels, 0)
        floatBuffer.rewind()

        val available = minOf(
            total,
            floatBuffer.remaining()
        )

        for (i in 0 until available) {
            var confidence = floatBuffer.get()

            if (!confidence.isFinite()) {
                confidence = 0f
            }

            confidence = confidence.coerceIn(0f, 1f)

            val alpha = if (confidence >= BACKGROUND_THRESHOLD) 255 else 0

            pixels[i] =
                (alpha shl 24) or
                        0x00FFFFFF
        }

        return pixels
    }

    /**
     * Keeps small false-transparent patches inside the detected person opaque
     * (most noticeable on faces close to the camera).  Unlike dilation this
     * never expands the outline into the real background, so it cannot bring
     * back the light halo around hair and shoulders.
     */
    private fun fillEnclosedMaskHoles(
        pixels: IntArray,
        width: Int,
        height: Int
    ) {
        if (width < 3 || height < 3) return

        val total = width * height
        val outside = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        fun addIfTransparent(index: Int) {
            if (!outside[index] && (pixels[index] ushr 24) == 0) {
                outside[index] = true
                queue[tail++] = index
            }
        }

        for (x in 0 until width) {
            addIfTransparent(x)
            addIfTransparent((height - 1) * width + x)
        }
        for (y in 1 until height - 1) {
            addIfTransparent(y * width)
            addIfTransparent(y * width + width - 1)
        }

        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            if (x > 0) addIfTransparent(index - 1)
            if (x + 1 < width) addIfTransparent(index + 1)
            if (y > 0) addIfTransparent(index - width)
            if (y + 1 < height) addIfTransparent(index + width)
        }

        for (index in 0 until total) {
            if ((pixels[index] ushr 24) == 0 && !outside[index]) {
                pixels[index] = 0xFFFFFFFF.toInt()
            }
        }
    }

    private fun dilateMask(
        source: IntArray,
        width: Int,
        height: Int
    ): IntArray {
        val total = width * height

        var output = dilatedMaskPixels

        if (output == null || output.size != total) {
            output = IntArray(total)
            dilatedMaskPixels = output
        }

        java.util.Arrays.fill(output, 0)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxAlpha = 0

                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) {
                        continue
                    }

                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) {
                            continue
                        }

                        val index = ny * width + nx
                        val alpha = source[index] ushr 24

                        if (alpha > maxAlpha) {
                            maxAlpha = alpha
                        }
                    }
                }

                output[y * width + x] =
                    (maxAlpha shl 24) or
                            0x00FFFFFF
            }
        }

        return output
    }

    /**
     * Completes a morphological closing after [dilateMask]. Any border that
     * was temporarily expanded is removed again, while narrow transparent
     * cracks inside the moving subject remain filled.
     */
    private fun erodeMask(
        source: IntArray,
        width: Int,
        height: Int
    ): IntArray {
        val total = width * height
        var output = erodedMaskPixels

        if (output == null || output.size != total) {
            output = IntArray(total)
            erodedMaskPixels = output
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                var minAlpha = 255

                for (dy in -1..1) {
                    val ny = y + dy
                    for (dx in -1..1) {
                        val nx = x + dx
                        val alpha = if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                            0
                        } else {
                            source[ny * width + nx] ushr 24
                        }
                        if (alpha < minAlpha) minAlpha = alpha
                    }
                }

                output[y * width + x] =
                    (minAlpha shl 24) or 0x00FFFFFF
            }
        }

        return output
    }

    // ============================================================
    // CLEANUP FRAME HISTORY
    // ============================================================

    private fun cleanupFrameHistory() {
        while (frameHistory.size > HISTORY_SIZE) {
            val oldestKey = frameHistory.firstKey()
            val old = frameHistory.remove(oldestKey)
            releaseFrameBitmap(old?.frame)
        }
    }

    // ============================================================
    // DISPOSE
    // ============================================================

    fun dispose() {
        processorLock.withLock {
            try {
                segmenter?.close()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to close segmenter", e)
            }

            segmenter = null

            synchronized(maskLock) {
                maskHistory.values.forEach { state ->
                    if (!state.mask.isRecycled) {
                        state.mask.recycle()
                    }
                }
                maskHistory.clear()
            }

            frameHistory.values.forEach { state ->
                releaseFrameBitmap(state.frame)
            }
            frameHistory.clear()

            synchronized(framePool) {
                framePool.forEach {
                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }
                framePool.clear()
            }

            normalizedBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            aiInputBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            maskedPersonBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            compositeBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            outputBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            stableOutputBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            cachedBackground?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            normalizedBitmap = null
            normalizedCanvas = null

            aiInputBitmap = null
            aiInputCanvas = null

            maskedPersonBitmap = null
            maskedPersonCanvas = null

            compositeBitmap = null
            compositeCanvas = null

            outputBitmap = null
            outputCanvas = null

            stableOutputBitmap = null
            stableOutputCanvas = null

            cachedBackground = null
            lastBackgroundSource = null

            backgroundBitmapInternal = null

            lastAvailableMaskTimestamp = null

            frameCounter = 0L
        }

        Log.d(TAG, "CpuVideoProcessor disposed")
    }
}
