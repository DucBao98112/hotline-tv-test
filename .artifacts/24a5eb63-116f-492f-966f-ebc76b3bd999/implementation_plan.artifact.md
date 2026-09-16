# Implementation Plan - Fix Mirrored Orientation for USB Cameras

Correct the orientation logic for 180-degree USB cameras to ensure a natural "selfie" (mirrored) reflection on the TV screen.

## User Review Required

> [!IMPORTANT]
> I am changing the 180-degree rotation handling to a **Vertical Flip**. This is the key to preventing the "double flip" issue that was making your movements feel reversed.

## Proposed Changes

### Video Processing (Orientation Fix)

#### [MODIFY] [CpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt)

- **Step 1: Smart Orientation Normalization**:
    - Update `onFrameCaptured` to check the `rotation` value.
    - If `rotation == 180`, apply `postScale(1f, -1f, width / 2f, height / 2f)` (Vertical Flip) instead of `postRotate(180f)`.
    - This ensures the image is upright but **not** horizontally flipped in the processor.
    - The final mirror effect will be handled cleanly by the TV renderer.

## Verification Plan

### Manual Verification
- Deploy to TV.
- Enable Virtual Background.
- Confirm that physical movement to the left results in movement to the right on the screen (Selfie/Mirror view).
- Verify the image is upright.
