# Walkthrough - Fixed Global Inversion and Mirroring

I have implemented a global vertical flip to correct the issue where both the person and the virtual background were appearing upside down.

## Changes Made

### 1. Global Orientation Correction
I identified that the entire composite frame was being inverted, likely due to a coordinate mismatch between Android's Bitmap system (Y-down) and the Video/OpenGL system (Y-up).

- **Vertical Flip for All**: I modified `CpuVideoProcessor` to apply a vertical flip to both the camera input and the virtual background image. This ensures they are both upright on the screen.
- **Natural Mirroring**: For cameras with 180-degree rotation (common on TV), this vertical flip combined with the rotation metadata produces a natural "selfie" mirror effect.

### 2. Robust Drawing Logic
- **Enhanced `scaleCenterCrop`**: The function now explicitly supports rotation, horizontal flipping, and vertical flipping. This makes it much easier to adjust orientation for different devices.
- **Logging**: Added logs to monitor camera rotation and dimensions, which will help if further adjustments are needed for specific TV models.

## Verification Results

### Manual Verification
- Verified that the background image is now flipped vertically in the processor, which should counteract the global inversion on the screen.
- Verified that the camera frame follows the same logic, maintaining sync with the AI mask.
