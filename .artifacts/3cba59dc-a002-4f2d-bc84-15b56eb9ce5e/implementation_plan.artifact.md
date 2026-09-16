# Implementation Plan - Atomic Background Swapping & Diagnostic Mask Mode

Eliminate black screen flashes during background image switches using atomic double-buffered background caching, thread-safe background setters, and implement functional `showMaskOnly` mode in `CpuVideoProcessor.kt` for AI mask diagnostics on Android TV.

## User Review Required

> [!IMPORTANT]
> - **Atomic Double-Buffered Background Cache**: Pre-renders the new background bitmap into a temporary canvas before swapping `cachedBackground` and recycling the old background. This guarantees 0 black frames during background switches.
> - **Thread Safety**: `@Volatile` setter for `backgroundBitmap` guarded with `processorLock` to prevent race conditions during UI-triggered background changes.
> - **Diagnostic Mask Overlay (`showMaskOnly`)**: When `showMaskOnly = true`, the processor renders the raw AI mask directly onto the screen (White = Person, Black = Background) so developers can verify mask alignment and threshold accuracy on TV.

## Proposed Changes

### Background Caching & Diagnostic Pipeline

#### [MODIFY] [CpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt)
1. **Thread-Safe Setter**: Add `@Volatile` and `processorLock` synchronization to `backgroundBitmap` setter.
2. **Atomic Background Swap**:
   - Create `newCachedBackground`, render `scaleCenterCrop(newBackground)`, then swap `cachedBackground = newCachedBackground`.
   - Safely recycle `oldCachedBackground` after swap.
3. **Canvas Clear Mode**: Replace `drawColor(Color.BLACK)` with `drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)`.
4. **Diagnostic Mode (`showMaskOnly`)**: If `showMaskOnly = true`, render `state.mask` scaled directly to composite output for live TV mask debugging.

## Verification Plan

### Automated Tests
- Run Gradle build checks: `./gradlew :app:assembleDebug`.

### Manual Verification
1. Launch the app on TV and join a call / room.
2. Rapidly change background images in the selection sheet:
   - **Expectation**: Background transitions smoothly without any black flash.
3. Enable `showMaskOnly = true` (or toggle debug mask):
   - **Expectation**: Mapped AI mask appears clearly on TV screen (White = Person, Black = Background).
