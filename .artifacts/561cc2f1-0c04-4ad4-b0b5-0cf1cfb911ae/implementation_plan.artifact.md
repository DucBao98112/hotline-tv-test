# Implementation Plan - Fix GPU Shader Incompatibility on Android TV

The logcat shows that the TV's GPU is unable to compile the shaders required by MediaPipe's GPU Delegate (`Problem initializing the softmax transform-and-sum program`). This causes the built-in `BlurProcessor` to return null frames, leading to a black screen.

I will implement a custom **CPU-based Video Processor** using MediaPipe's CPU delegate to bypass the buggy GPU drivers on the TV.

## User Review Required

> [!IMPORTANT]
> - I will implement a custom `CpuVideoProcessor` class. This processor performs image segmentation on the CPU.
> - While more stable, this may increase CPU usage. I will set the segmentation interval to every 5 frames by default to maintain performance on the TV.
> - I will fix a memory leak in `UsbCameraSource.kt` where mirrored bitmaps were not being recycled.

## Proposed Changes

### Custom Video Processor

#### [NEW] [CpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt)
- Implements `com.ntt.skyway.core.content.local.source.VideoProcessor`.
- Uses MediaPipe `ImageSegmenter` with `Delegate.CPU`.
- Handles both Background Blur and Virtual Background image replacement.
- Optimizes performance by reusing buffers and skipping frames for segmentation.

### Camera Source

#### [MODIFY] [UsbCameraSource.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/UsbCameraSource.kt)
- Revert back to `RGB_565` for efficiency (since we'll handle conversion in the processor if needed).
- **CRITICAL FIX**: Recycle the bitmap returned by `mirrorBitmap` to prevent OutOfMemory crashes.

### Core Logic (Skyway Sessions)

#### [MODIFY] [CallSession.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/call/CallSession.kt) & [RoomSession.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/room/RoomSession.kt)
- Switch from `com.ntt.skyway.videoprocessors.BlurProcessor` to my custom `CpuVideoProcessor`.
- This ensures the video frames are processed on the CPU, avoiding the OpenGL shader initialization error.

## Verification Plan

### Manual Verification
1. Start a call.
2. Toggle Blur/Virtual Background.
3. Verify that the screen is **NOT** black and the effect is applied (remote side should see it).
4. Monitor CPU usage to ensure it doesn't cause lag.
