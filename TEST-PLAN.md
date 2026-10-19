# USB Image Writer v0.2.0 — Test Plan

Use expendable test media. Do NOT overwrite your only working Arc Loader boot drive.

## A. Dashboard and basic navigation
1. Install Debug APK; uninstall previous debug build if signing conflict appears.
2. Open the app. Check that the new dashboard fits under system status/navigation bars.
3. Verify four main tiles: Write Image, Create Image, Clone USB (coming soon), Diagnose/Repair (coming soon).
4. Open each enabled screen; navigate back; make sure the app remains responsive.
5. Without USB inserted, confirm the app handles "no supported device" normally.

## B. Existing Write Image regression
1. Use a spare USB at least as large as the chosen raw \`.img\`.
2. Inspect USB capacity and block size.
3. Confirm both destructive warnings and write to the spare USB.
4. Observe Write 100%, Verify 100%, matching SHA-256.
5. Remove/reinsert and test boot separately where appropriate.
6. Background app switching is still an open compatibility test for certain Samsung OTG combinations.

## C. New Full Raw Backup
1. Insert a **spare, readable** Flash Drive, ideally small (2–8 GB).
2. Open "สร้าง Image" and inspect the source capacity; note the reported bytes.
3. Confirm **internal phone storage has enough free space for full USB capacity plus headroom**.
4. Tap "สร้างไฟล์ .img" and choose a folder in internal phone storage.
5. Leave Backup in the foreground for the first test. Observe USB-read progress to 100%.
6. Then observe file-Verify progress to 100%, and success message with SHA-256.
7. In My Files, confirm the IMG exists and has approximately exactly the source USB's full capacity.
8. For an end-to-end restore test, write that IMG to a **different expendable USB** with sufficient actual capacity, Verify, and test the partition structure/boot if applicable.

## D. Backup cancellation / reliability
1. On a spare source, start Backup and cancel while progress is underway.
2. Confirm the original USB remains readable and unchanged.
3. Confirm a partial output is not used. The app attempts to delete it; manually remove it if still present.
4. Repeat on low free storage and USB detach scenarios, always using noncritical test media.
5. Background test: only after a successful foreground test, switch apps briefly, return and check progress. Report error text, percent and USB model if it stops.

## Not implemented in v0.2.0
- Direct USB-to-USB clone with hub.
- Smart/compact image.
- Filesystem repair, full-media destructive test, or NAND repair.
