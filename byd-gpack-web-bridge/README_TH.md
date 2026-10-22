# BYD Photo Navigation V0.2.15 — GPack Chrome shortcut discovery

Goal: open the actual Chrome app inside the GPack virtual environment automatically when a GPS photo is selected, with the Web Bridge URL loaded, without root/ADB/accessibility or private API access. Based on user vehicle test V0.2.14: localhost Bridge autostarts, GPack launcher opens, and when user opens Chrome and accepts the Maps prompt, actual spoken driving navigation works. The missing part is launching GPack-internal Chrome and loading URL automatically.

Changes:
- New read-only launcher activity discovery: PackageManager.queryIntentActivities(ACTION_MAIN, CATEGORY_LAUNCHER, package=com.gpack.agent). Restrict possible shortcut candidates to exported Android activities whose label or class clearly contains Chrome/Browser.
- Offer a user-driven one-tap test choosing a candidate; use public explicit ACTION_MAIN LAUNCHER on GPack's Chrome-labeled exported alias, with URL hints. Never call internal nonexported activities or arbitrarily guess privileged GPack components.
- Only after a candidate has been manually identified can the user opt-in to auto-open it on future GPS photo confirmations; it is OFF by default. Revalidate exported Chrome-labelled launcher match before each launch and revert to the proven GPack launcher on failure.
- Diagnostic reports Chrome discovery, chosen shortcut class, auto-enabled and actual startActivity outcome (not proof Chrome loaded URL).
- User-driven host Chrome direct VIEW experiment provided separately. Host Chrome may be useless for reaching virtualized GPack Maps; not set as default.
- Service autostarts on app open, new isolated port http://127.0.0.1:8773, old v14 untouched. Existing EXIF safety, local photo preview, native Google Maps link, optional voice diagnostic preserved.

Security / realistic scope:
If GPack exposes no public Chrome/Browser exported launcher or URL entry point, host Android cannot reliably open the virtual Chrome or type into it using standard Android intents. Chrome browser also blocks external app intents not caused by a real user interaction; app confirmation must be user controlled. Do not pretend those flows succeeded. An optional bookmark / GPack Chrome home-screen shortcut can reduce repeated typing if supported.
When testing, park vehicle. Step 1: install v15 and open app -> automatic service port 8773. Step 2: tap "🔎 ค้นหาทางเปิด Chrome ใน GPack" and show result to assistant before test. If a Chrome alias appears, tap it for one-time test and report whether Chrome opened, and whether URL is loaded. Step 3 only after app confirms shortcut opens Chrome: opt-in auto-open on GPS photo confirmations. If none, fallback to regular launcher and manually launch bookmarked Web Bridge in Chrome.
