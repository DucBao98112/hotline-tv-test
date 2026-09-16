# Implementation Plan - Fix Global Inversion and Mirroring in CpuVideoProcessor

The user reports that BOTH the virtual background and the person are still inverted (upside down) and the mirror effect is incorrect. This suggests a global inversion issue, likely occurring during the bitmap-to-video-frame conversion or within the renderer's coordinate system.

## User Review Required

> [!IMPORTANT]
> I am changing the orientation logic to apply a **Vertical Flip** to both the person and the background. This is a targeted fix for cases where the entire composite frame appears upside down on the screen.

## Proposed Changes

### [CpuVideoProcessor](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt)

#### [MODIFY] [CpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt)

1.  **Add Debug Logging**: Log `videoFrame.rotation` and dimensions to verify camera behavior.
2.  **Global Vertical Flip**: Apply `flipVertical = true` in `scaleCenterCrop` for both the input camera frame and the virtual background.
3.  **Correct Mirroring**: By combining the 180-degree camera rotation with a vertical flip, we effectively produce an upright, mirrored person. This should align with the user's expectation of a "selfie" mirror reflection.

## Verification Plan

### Manual Verification
1.  Deploy the app to the TV.
2.  Enable Virtual Background.
3.  Check if both the background and the person are upright.
4.  Verify the mirror effect: moving left should result in movement to the left on the screen (from your perspective).
