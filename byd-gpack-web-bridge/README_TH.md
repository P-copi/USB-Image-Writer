# BYD Photo Navigation V0.2.11 — photo preview + auto launch experiment

## Goal
Send confirmed GPS JPEG from USB in BYD host into loopback Web Bridge. GPack Chrome at http://127.0.0.1:8769 shows **thumbnail + filename + actual GPS**, and tries to open native Google Maps navigation through the **same Android intent URL** that user confirmed working with a physical click in V0.2.10.

## Safety and limitations
- USB photo remains read-only. Only the confirmed 800px-or-less JPEG thumbnail is cached privately and served at `127.0.0.1:8769/api/photo?rev=N` (requires current revision, no CORS). Never uploads original photo to Google Maps, only GPS.
- EXIF ACCESS_MEDIA_LOCATION permission and invalid/0,0 blocking retained from V0.2.8.1.
- Auto launch ON by default only for *confirmed GPS images* sent during the past 5 minutes. Each revision attempted at most once, preventing repeated loops when returning from Maps.
- In GPack Browser, Chromium might block an `intent:` external app navigation not triggered by user gesture. There is **no supported way to bypass that security** in JavaScript. If auto launch is blocked, manually tap the big green 'เปิด Google Maps นำทางทันที' button — V0.2.10 established that this button enters actual spoken-navigation UI without secondary Start or web interstitial.
- The host Android environment cannot open virtualized Google Maps directly; host-side native launch is OFF by default. Tries GPack agent BROWSABLE link forwarding only if supported, otherwise opens GPack launcher.
- V0.2.11 separate appId and port 8769; prior tested V0.2.10 retained for fallback.

## Safe car testing (parked)
1. Install 0.2.11, start bridge and confirm 'กำลังฟัง 127.0.0.1:8769'.
2. Inside GPack Browser open http://127.0.0.1:8769 (green 'เชื่อมต่อสำเร็จ'). Auto ON by default.
3. In Android V0.2.11 select actual JPEG with GPS from USB; tick confirmation. App sends EXIF GPS and a thumbnail; tries to launch GPack, possibly launcher.
4. Observe GPack browser whether new photo appears and Google Maps starts navigation without extra user tap; record what happens.
5. If Browser doesn't auto-start Maps, click main green button once to open native Maps; this is supported fallback.
