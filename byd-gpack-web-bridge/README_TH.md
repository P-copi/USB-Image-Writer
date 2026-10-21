# V0.2.13 — Navigation Audio Diagnostics
Retains the validated EXIF GPS Photo -> Android host -> GPack Browser -> native Google Maps navigation path from versions 0.2.8.1–0.2.12. Installs separately, localhost port **8771**.

## New
- Collapsed **"ตรวจเสียง Google Maps / ลำโพงรถ"** section on GPack Browser includes a user-activated Web Audio sine-tone test (only tests Chrome audio output), optional speechSynthesis test (may be missing TTS voice), and buttons to record heard/not heard for both Chrome and Google Maps in local browser settings (never transmits these results to external services).
- Exact official Google Maps navigation sound troubleshooting: Maps > profile > Settings > Navigation settings > Play test sound; set Guidance volume=Louder; check Play voice over Bluetooth as appropriate; check Maps Sound vs Alerts only. Google official support: https://support.google.com/maps/answer/11523237 .
- No attempt to change GPack, Maps system volume, routing, microphone permission, or app audio settings.
- Still prioritizes navigation first and lazy-loads optional photo only when diagnostics expanded.
- Chrome may block automatic external-app launches without gesture; keep one-tap native button.

## Vehicle test (parked)
1. Install v13 next to v12; open it, check port 8771 in the advanced report.
2. GPack Chrome open http://127.0.0.1:8771 and confirm green.
3. Expand 'ตรวจเสียง Google Maps / ลำโพงรถ', click test Chrome audio. Report heard or not.
4. Independently open Google Maps settings via app UI and use 'Play test sound' to diagnose actual Maps voice. If voice test works, no reason to change Web Bridge. If Chrome tone works but Maps test not, examine Maps settings, offline voice pack, audio route.
5. For navigation, select a known verified GPS photo and confirm it can still open Google Maps when green button is tapped.
