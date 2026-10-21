# BYD Photo Navigation V0.2.12 — navigation-first interface

Validated bases kept: V0.2.8.1 EXIF latitude/longitude with Android ACCESS_MEDIA_LOCATION; V0.2.10 native Maps Android Intent triggered by physical tap in GPack Browser and started navigation without Maps secondary "Start"; V0.2.11 preview of user's selected GPS image on localhost.

Changes:
- Host launches Web Bridge foreground service when app opens; **one primary photo GPS chooser** front and center. Advanced diagnostics and demo coordinates collapsed.
- GPack Browser 127.0.0.1:8770 displays GPS and prominent **one-tap native Maps navigation button** without obstructing picture preview. The optional picture preview remains in collapsed diagnostics and is fetched lazily when opened; original file remains unmodified.
- New valid GPS image arrival triggers an **experimental automatic native intent attempt** once per recent revision (2-minute window), never a stale picture or 0,0. Redirect is attempted immediately without a timer, but Chrome may refuse external app launch without user gesture. That security rule CANNOT be bypassed by JavaScript.
- Keeps manual direct native Maps button that **actually worked on car**; web link fallback is diagnostic only. Does not auto-open google.com/maps and avoids its "Open Maps?" interstitial where possible.
- Host Android still cannot directly resolve Maps installed only within GPack; it may open GPack launcher instead of Chrome. No unsupported GPack API assumption; user might need to tap Chrome and the green button.
- Separate appId com.pcopi.byd.webbridge.v0212, server port 8770. Previous v11/v10 remain untouched for travel fallback.

Parked-car test:
1. Install v12, open; verify service listening on 8770 via diagnostics (should auto-start).
2. Open GPack Chrome to http://127.0.0.1:8770 once; verify green status and primary direct Maps button.
3. Return to host and choose previously verified JPEG on USB; tick confirmation.
4. See whether host opens GPack browser automatically or launcher, and whether Chrome auto opens native Maps. Do NOT misreport promise of bypassing Chromium user gesture controls.
5. If Chrome remains on page, tap giant green button once. Confirm route mode and correct GPS. Optional "แสดงภาพและเครื่องมือตรวจสอบ" opens hidden thumbnail only when needed.

Compliance: no root, no click automation, no bypasses of prompt permissions, only 127.0.0.1 local photo transfer.
