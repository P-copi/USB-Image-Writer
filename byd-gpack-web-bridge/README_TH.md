# BYD Web Bridge Test 0.2.5
### Goal
- Choose an actual local photo from BYD Android using ACTION_OPEN_DOCUMENT
- Read GPS coordinates from image EXIF
- Send lat/lon to the already confirmed loopback Web Bridge endpoint
- In the GPack Browser, keep the successful manual start-navigation control
- Add optional opt-in attempt to auto-launch Google Maps directions for NEW destinations only when Browser page is visible

### Setup
1. Install 0.2.5 as a separate app so previous 0.2.4 remains intact.
2. STOP the old V0.2.4 Bridge service. Both use localhost port 8765, so only one should run at once.
3. Open V0.2.5 and start the Bridge service.
4. In GPack Browser revisit/bookmark http://127.0.0.1:8765.
5. In the browser tap "เปิดโหมดนำทางอัตโนมัติ (ทดลอง)" once to arm; navigate only when NEW data arrives.
6. In V0.2.5 tap "เลือกภาพถ่ายจริง ...", select a local image with EXIF GPS.
7. Browser should update with its lat/lon. The browser will *attempt* to auto-open Google Maps directions if page is visible; if it doesn't, click green "เริ่มนำทาง".
8. You may also share an image from Photo Space/Gallery to the "BYD Web Bridge Test 0.2.5" share target (if available).

### Important technical limitations
- The actual V0.7.4 Phone Space APK source repository is not connected. This APK is a bridge companion, not a modified Phone Space build.
- Browser/virtualization may forbid background tab redirects, auto launching Google Maps, or popup navigation without an explicit tap.
- Every auto-navigation trial needs user observation of the exact destination and no assumptions of success from the Android startActivity result.
- If image EXIF has no GPS, nothing is sent. No device GPS substitute; user privacy protected.
- Do not test while driving. No root or modifications to GPack.
