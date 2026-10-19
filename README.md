# USB Image Writer v0.2.1 — Flash Drive Manager

Android USB Toolkit for Samsung Galaxy S22 Ultra and other USB Host phones. No root or PC required at runtime.

## Available in v0.2.0

### 1. Write Image (IMG → USB)
- Pick an uncompressed raw \`.img\` using Android's document picker.
- Choose a USB mass-storage device, check capacity and block size.
- Confirm destructive write; raw write starts at LBA 0.
- Display progress, transfer speed, ETA and verify SHA-256 by reading the USB back.
- Connected-device foreground service, wake lock and patched libaums recovery inherited from v0.1.8.
- Warning: never accept Android/Samsung's format prompt for a bootable Arc Loader USB.

### 2. Create Image (USB → IMG)
- Open **สร้าง Image** from the new dashboard.
- Inspect the source USB and check its actual size.
- Choose a destination with Android \`ACTION_CREATE_DOCUMENT\`, preferably phone internal storage.
- Full-device backup: read every raw sector from LBA 0, never write to source USB.
- Foreground notification, progress, throughput, ETA and user cancellation.
- SHA-256 calculated while copying, then independently re-read the saved IMG to verify its stored content.
- On a recoverable failure/cancellation, attempt to delete the incomplete output document. If the process is killed, partial output might remain; do not use it.

Full RAW images are approximately as large as the entire **USB capacity**, not just used files. A 16 GB flash drive needs about 16 GB of free storage plus overhead.

### Coming later (visible but clearly marked unavailable)
- Direct USB → USB clone with a compatible powered hub and clear source/target confirmation.
- Compact/smart images (filesystem-specific partition analysis and compression).
- USB diagnostic and non-destructive testing; no claims of repairing damaged NAND chips.

## Limitations / safety
- USB mass storage using SCSI transparent subclass with Bulk-Only Transport (BOT); UAS devices may not work.
- Background USB stability depends on Android firmware/OTG hardware. First test with the app on screen.
- No concurrent raw write and backup operations.
- Verify for IMG creation checks the saved file against the data read on the first pass; it is not a hardware-health test or an independent re-scan of all source sectors.
- The destination picker must not be pointed at the source USB drive.
- Arc Loader source media with unreadable sectors may fail and require recovery outside this app.
- Debug APK signing may vary across GitHub Actions runs. You may need to uninstall a previous debug build before installing.

## Permanent APK signing

v0.2.1 introduces a new adaptive USB icon and a permanent signing identity for release APKs. The signing key is only committed in encrypted form and unlocked by GitHub Actions secret `USB_SIGNING_PASSPHRASE`. Setup details: [SIGNING-SETUP.md](SIGNING-SETUP.md).

The previously distributed CI Debug APKs cannot be upgraded to this new signer in place unless their original debug keystore can be recovered. One uninstall is required when switching to the first Release-signed APK. Later versions can update normally if signed with the same permanent key.

## Build
GitHub Actions: \`.github/workflows/build-apk.yml\`. Builds patched libaums, then app Debug APK. Artifact is \`USB-Image-Writer-v0.2.0-debug\`. The main source repository is public; never commit signing keys or passwords.

## Testing
See \`TEST-PLAN.md\`. First establish full raw USB backup and file verification on a spare USB; next restore that IMG to a *different* USB and confirm matching SHA-256. Test hub-based direct cloning only in a later release.
