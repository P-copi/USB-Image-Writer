# BYD Web Bridge V0.2.6 – EXIF USB diagnostic

## Why
The same Dome Camera JPEG reportedly shows 16.823692,100.262038 in the phone navigation app but 0,0 when selected directly from USB in BYD Web Bridge V0.2.5. This is not yet proof that either USB copy or Android parser changed GPS. Both are plausible.

## What changed
- Read EXIF twice (Android framework and AndroidX ExifInterface 1.3.7) from two fresh InputStreams of the original selected content URI.
- Capture raw GPS coordinates (rational degrees/minutes/seconds) and hemisphere indicators.
- Parse EXIF DMS rational tags manually as fallback.
- Report document provider details, size and outcomes in "Image GPS" report.
- NEVER queue navigation if GPS is missing, NaN, outside range, or exactly 0,0.
- Browser auto-navigation now queues a new destination while its tab is hidden and attempts the launch when the tab returns to foreground.
- Manual green button is always retained as an option; browser restrictions may still prevent automatic redirects.

## Steps (one step at a time)
1. STOP old V0.2.5 Web Bridge to free localhost port 8765.
2. Install/open V0.2.6.
3. Pick ONE JPEG from original USB flash drive.
4. If 0,0, screenshot "รายงานสภาพการเชื่อมต่อ" or copy report and send it. Do not tap Maps.
5. If valid coords, open GPack browser and test auto navigation.

Test while car parked, not in motion. V0.2.6 installs separately from V0.2.5.
