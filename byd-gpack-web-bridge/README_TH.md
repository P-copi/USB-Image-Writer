# Web Bridge V0.2.7 — confirm selected image
- Android DocumentsUI tap selects one picture by design. The app now pauses to show filename, file size and real photo preview, with a checkbox required before EXIF read.
- Includes SHA256 of the exact bytes delivered by USB provider to compare to phone original. No change to image data.
- Does not send invalid / 0,0 coordinates. Detailed Android and AndroidX Exif diagnostic retained.
- Does not modify Phone Space or Dome Camera APK. GPack Browser manual routing remains unchanged.
- Auto navigation is experimental and not yet confirmed to work with virtualized Browser when returning from BYD Android.
- When testing, stop the old Bridge service first to release localhost:8765, then choose ONE picture, confirm after seeing the thumbnail, and screenshot the diagnostics below.
