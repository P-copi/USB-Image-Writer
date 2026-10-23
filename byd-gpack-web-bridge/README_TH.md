# BYD Photo Navigation V0.2.16: route change without re-copying URL

Observed on user's car:
- GPack launcher can be started from BYD Android host, but the virtual Chrome is not exported as public Android activity (V0.2.15 'Chrome discovery: ไม่พบ Chrome launcher แบบ exported'); ordinary Android intents cannot launch GPack's isolated Chrome directly. Avoid promising this.
- Host reads original selected JPEG GPS via ACCESS_MEDIA_LOCATION; maps navigates with actual spoken guidance after user accepts opening Maps.
- Previously, while Google Maps is active, returning to Web Bridge required manually retyping http://127.0.0.1:<port>. Stop this with Chrome Bookmark / Create shortcut if supported.

New:
- Host stores **up to 10 verified GPS-image destinations** in private SharedPreferences as JSON (no cloud, no changes to original photo). Exposed locally in /api/state recent array.
- GPack Chrome Web Bridge now renders these as one-tap Google Maps direct Intent links. On returning from an active route to Chrome, choose another saved destination without switching to the Host/USB or typing URL.
- GPack Chrome supports typing an address/place name and using a Google Maps Intent. Optional Web Speech Recognition may fill text, but requires Chrome support and explicit user confirmation; not available on all GPack builds. Prevent auto-navigation to unconfirmed voice transcription.
- Collapsed one-time shortcut instructions: Chrome menu > Bookmark ★ or Create shortcut/Add to home screen (if available). These are user-driven; GPack may not export shortcut to BYD launcher. Bookmark inside Chrome should reduce repeated typing.
- Preserve original native Maps green button from 0.2.10–0.2.15, hidden photo and voice diagnostics, experimental auto link, GPack launcher fallback.
- New appId com.pcopi.byd.webbridge.v0216 and isolated port 8774.
- While Maps is foreground, Chrome tab polling may be paused. Native host app cannot invisibly replace Maps destination from separate GPack without a supported dispatcher. Switching to Chrome for a destination update is still needed. Google Maps itself supports voice via Gemini/Assistant if installed and enabled.

Parked-car tests:
1. Install v16, open, auto port 8774, open GPack Chrome once to http://127.0.0.1:8774, confirm green.
2. Create Chrome Bookmark (star) named BYD Photo Navigation, try optional Add to Home Screen only if GPack offers it.
3. Host choose and confirm 2 different GPS photos; Chrome receives both in "เปลี่ยนจุดหมายระหว่างนำทาง" list even after Maps is foregrounded. Tap a saved destination — report if Maps replaces old route or asks confirmation.
4. Optional test Chrome voice input and Maps Gemini only when parked. Do not operate touchscreen during driving.
