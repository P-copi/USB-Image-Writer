# v0.2.50 — ARC Recovery & Boot Validation (advance plan)

This is a planning document, not a claim of implemented features or successful builds.

## Dependencies / gates
1. v0.2.49 must compile, produce a signed APK with a verified signature, and pass offline image structure and integrity tests.
2. Keep original ARC USB read-only and physically separate from spare target USB.
3. No automatic writes to source USB, NAS data disks, or DSM volumes.
4. Physical ARC/DSM boot confirmation must come from a real-device test; a GitHub Actions success is insufficient.

## Planned capabilities
- Read-only ARC configuration inventory with explicit presence/absence status for model, serial, MAC, addons, and boot settings.
- Export configuration backups with SHA-256 and an explicit user-selected destination.
- Import and validate backups offline; reject missing or incompatible fields before restore.
- Provide an opt-in restore preview/diff, never silently overwrite a working configuration.
- Guided spare-USB write and read-back verification, with source/destination identity confirmation.
- Record separate states: BUILD PASS, APK VERIFIED, IMG VERIFIED, USB VERIFIED, ARC BOOT PASS, DSM LOGIN PASS.
- Clear temporary working files only after positive user confirmation; do not delete the last known good image or rollback backup.

## Artifact retention
- Preview signed APK: 3 days; debug APK: 1 day; release APKs remain separate and are not automatically deleted.
- No retrospective cleanup; preserve historical HOLD artifact IDs 13202169898, 13202911520, 13516347729.
- Never delete logs immediately on failure. Retention of workflow logs follows repository settings.

## Implementation order
A. Configuration inventory and read-only export.
B. Offline backup verification and restore preview.
C. Spare-USB read-back verification and user-guided boot checklist.
D. Restore execution only after explicit safety review and user consent.
