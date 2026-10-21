# BYD Web Bridge V0.2.9 Photo Navigation
This builds on V0.2.8.1 confirmed working on BYD.
Uses separate Android package com.pcopi.byd.webbridge.v029 and loopback port 8767. Old 8766 version remains available.
Original EXIF permission, USB photo selection confirmation, SHA-256, and 0,0 blocking preserved.
After GPS read and send, try opening GPack automatically (checkbox enabled by default).
GPack browser auto-navigation tries an HTTPS Maps Intent directed at com.google.android.apps.maps, with proven Google Maps WEB directions fallback.
Manual large green one-tap button is a direct google.navigation:q=lat,lon&mode=d link with user activation. Second app-intent and original web-directions backup buttons remain.
Important: Chrome may block external app launches without a genuine click. This is untested on BYD/GPack; do not claim zero-tap native launch has passed.
Test parked: Install, start service 8767, open Browser http://127.0.0.1:8767 once, then pick image and confirm and report if GPack and native Maps launch. If browser maps route needs Start, return to bridge and try green native link on genuine tap.
