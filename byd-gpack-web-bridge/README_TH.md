# BYD Web Bridge V0.2.8 — Original EXIF Investigation
## Why
The phone Phone Space/Dome Camera read GPS 16.823692,100.262039 but BYD Bridge 0.2.7 sees redacted-looking 0/1 GPS EXIF and missing hemisphere refs when directly opening the selected USB file. This suggests location metadata redaction (not yet proven). The user's phone screenshot does not prove that the flash-drive copy is byte-identical; compare hashes if the mismatch persists.

## Changes
- Request runtime ACCESS_MEDIA_LOCATION after user has explicitly chosen and confirmed a photo. Explain privacy before asking; never upload the photo.
- Try MediaStore.getMediaUri to convert SAF URI to MediaStore where supported, then request original bytes using MediaStore.setRequireOriginal; also try original flag directly on SAF URI as fallback.
- Read AndroidX EXIF from original byte stream and include raw coordinate/tag readings alongside previously recorded redacted readings and checksum.
- Keep checkbox and preview UI. Do not send 0,0 GPS. Keep successful manual GPack navigation bridge untouched.

## Manual test
1. STOP previous Web Bridge version first (all use port 8765).
2. Install and launch 0.2.8, then tap Start Bridge.
3. Select and confirm the SAME Buddha image directly from USB on BYD.
4. Grant the location metadata permission when prompted.
5. Check "รายงานสภาพการเชื่อมต่อ". If valid coords, return to GPack Browser. If 0,0, send the ORIGINAL EXIF diagnostics screenshot.
6. Android USB document providers may not implement original access; in that case the app records a specific permission/provider error rather than inventing coordinates.

Vehicle must remain parked during tests.
