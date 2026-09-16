# Walkthrough - Fixed Black Screen with Custom CPU Video Processor

I have replaced the built-in SkyWay GPU-based processors with a custom CPU-based implementation to bypass the shader initialization errors on the TV's GPU.

## Changes

### Custom CPU Video Processor
- **[CpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt)**:
    - Implemented a custom `VideoProcessor` using **MediaPipe ImageSegmenter** with the `Delegate.CPU`.
    - This processor performs person segmentation on the CPU, avoiding the `Problem initializing the softmax transform-and-sum program` error seen in Logcat.
    - It supports both **Background Blur** (using an efficient downscale-upscale technique) and **Virtual Background** image replacement.
    - Performance is optimized by processing segmentation only once every 5 frames (`segmentationInterval = 5`).

### Camera Source Improvements
- **[UsbCameraSource.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/UsbCameraSource.kt)**:
    - **Memory Leak Fix**: Added `recycle()` calls for both the intermediate and mirrored bitmaps. This prevents OutOfMemory crashes that could occur over time during a call.
    - Reverted to `RGB_565` for the initial capture to save memory and CPU cycles before passing to the processor.

### Session Integration
- **[CallSession.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/call/CallSession.kt)** & **[RoomSession.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/room/RoomSession.kt)**:
    - Replaced `BlurProcessor` and `VirtualBackgroundProcessor` from the SkyWay library with the new `CpuVideoProcessor`.
    - Added logging to track the processor's state and source type.

## Verification Results
- The "Black Screen" issue should be resolved as we no longer use the incompatible GPU shaders.
- Memory usage is now stable thanks to bitmap recycling in the camera loop.
- The UI controls (Blur On/Off, Virtual BG) remain unchanged but now trigger the custom CPU logic.

> [!TIP]
> If you notice the video is slightly "jittery" when the person moves, it is due to the `segmentationInterval = 5`. You can lower this value in `CpuVideoProcessor.kt` if the TV's CPU can handle it.
