# Implementation Plan - Ultimate GPU Performance & Stability

The application continues to crash and experience ANRs because the current video pipeline performs heavy pixel manipulation on the CPU. I will refactor the system to a **Zero-CPU conversion architecture**, moving all pixel work to the GPU and implementing strict memory pooling.

## User Review Required

> [!IMPORTANT]
> - **Zero-CPU Pipeline**: I will eliminate the Kotlin loop for YUV-to-RGB conversion. This work will now happen inside a **GPU Shader**, which is ~100x faster and frees up the CPU for AI tasks.
> - **Double Buffering**: I will implement a double-buffering pattern in the camera source to prevent race conditions where the SDK reads a frame while we are updating it.
> - **Resource Pooling**: All textures and bitmaps will be pooled to reach a zero-allocation state in the main rendering loop.

## Proposed Changes

### 1. Rendering Engine (OpenGL ES 2.0)
#### [MODIFY] [EffectsShader.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/rendering/EffectsShader.kt)
- **Add `YUV_TO_RGB_FRAGMENT_SHADER`**: A high-performance shader that samples from 3 separate textures (Y, U, V) and outputs RGB in a single GPU pass.

#### [MODIFY] [GlRenderer.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/rendering/GlRenderer.kt)
- **Implement `yuvToRgbTexture()`**: Accepts raw YUV `ByteBuffer`s from the `VideoFrame` and converts them to an RGB Texture ID on the GPU.
- **Texture Pooling**: Maintain a pool of reusable textures to avoid constant `glGenTextures` calls.

### 2. Video Orchestration
#### [MODIFY] [GpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/GpuVideoProcessor.kt)
- **Remove `fastYuvToBitmap`**: This CPU-heavy function will be deleted.
- **Refactor Pipeline**:
    1. Upload YUV planes to GPU.
    2. Convert to RGB Texture.
    3. Run AI Segmenter (using a pooled Bitmap).
    4. Apply Blur/Background effects on GPU.
    5. Read back final frame once.

### 3. Camera Source Handling
#### [MODIFY] [UsbCameraSource.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/UsbCameraSource.kt)
- **Implement Double Buffering**: Use two mirror bitmaps. While the SDK is processing Buffer A, we update Buffer B. This eliminates the "bitmap is recycled" and "buffer null" inconsistencies.

## Verification Plan

### Automated Tests
- Full build and resource verification.

### Manual Verification
1. **CPU Usage**: Monitor CPU using `top`. Expect a drop from >100% to <40%.
2. **Stress Test**: Long-running call (15+ mins) with background effects.
3. **Latency Check**: Verify that video movement is fluid with no noticeable lag behind real-world movement.
