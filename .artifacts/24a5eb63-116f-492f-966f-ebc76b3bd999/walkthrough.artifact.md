# Walkthrough - Corrected Mirroring by Centralizing Logic

I have removed the redundant manual mirroring from the video processor. This ensures that the orientation is handled correctly by the TV renderer, maintaining consistency between you and the virtual background.

## Changes

### Video Processing (Mirroring Fix)

#### [CpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt)

I identified that applying a horizontal flip in the processor while the SkyWay renderer was also mirroring resulted in a "reversed" or incorrect orientation.

1.  **Removed Person Flip**: Removed the manual `postScale(-1f, 1f)` in the normalization step.
2.  **Removed Background Flip**: Removed the manual `postScale(-1f, 1f)` in the background drawing logic.

Now, the entire 16:9 composite frame is delivered in its "true" orientation to the renderer, which applies the single, final mirror for the local user.

## Verification Results

### Logic Check
- **Consistency**: Both the person and the background are now part of the same coordinate space. When the TV renderer mirrors the frame, both will move correctly together.
- **Orientation**: 180-degree USB camera rotation is still handled, ensuring the image is upright.
- **Tracking**: AI mask tracking remains synchronized thanks to the `frameHistory` buffer.
