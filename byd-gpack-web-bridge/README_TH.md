# BYD Web Bridge V0.2.8.1 — Separate port 8766

## Why
V0.2.7 starts on port 8765, but V0.2.8 gets EADDRINUSE even after reported stop of V0.2.7. Browser may show ERR_CONNECTION_REFUSED later. Rather than repeated user stop/restart steps, isolate the new EXIF test version.

## What changed
- Separate app ID com.pcopi.byd.webbridge.v0281, independently installable from previous versions.
- Foreground service binds 127.0.0.1:8766, not 8765.
- Application buttons, notifications, reports and GPack browser pages all display http://127.0.0.1:8766.
- EXIF original metadata permissions and image-selection confirmation from 0.2.8 retained; invalid GPS 0,0 is blocked.
- Existing GPack and original Phone Space unchanged.

## Test one step at a time
1. Install V0.2.8.1 (do not uninstall V0.2.7).
2. Open V0.2.8.1, tap Start Web Bridge and check the service reports listening 127.0.0.1:8766.
3. After success, visit http://127.0.0.1:8766 inside GPack Browser.
4. Only then choose same Flash Drive image, confirm, grant permission, and check actual original EXIF GPS.

If the isolated port also fails, inspect app state and permissions instead of blaming old 8765 collision.
