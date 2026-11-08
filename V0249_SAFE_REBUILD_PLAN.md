# v0.2.49 — Smart Rebuild & Safe Retention (work in progress)

Baseline: v0.2.48-preview, commit 4f1a08a04aec445d71e034ef48e036294509174f.

## Safety and release gates
- The source ARC USB is strictly read-only; all shrinking and assembly run against verified offline copies.
- Preserve MBR partition starts, filesystem integrity, ARC configuration, and source backups. Reject unknown layouts and dirty EXT4 journals.
- Do not infer bootability from successful assembly, SHA-256, or CI build. Physical boot into ARC and DSM must be tested separately.
- Never automatically delete historical artifacts, workflow runs, releases, tags, source code, or protected HOLD IDs 13202169898, 13202911520, 13516347729.
- Preview signed APK artifacts expire after 3 days; debug artifacts after 1 day. A failed build should retain logs for diagnosis, and it must not upload a misleading signed APK.
- The signed APK is verified by apksigner and SHA-256 before uploading.
- Retention changes apply only to newly uploaded artifacts; they do not remove existing artifacts retroactively.
- Keep the last proven working signed APK and a rollback APK in Releases/backups before any further cleanup.
- Do not automatically publish a preview as a verified release.
- No production NAS disks or USB source writes in testing.

## Implementation checklist
- [x] Isolate v0.2.49 development in its own branch
- [x] Initialize preview build metadata
- [x] Shorten preview artifact retention; validate signing before upload
- [ ] Extend offline Smart Rebuild verification to reject unsupported metadata and incomplete copies
- [ ] Prove compact IMG filesystem integrity and cross-check partition boundaries
- [ ] Verify output written to a spare USB and record SHA-256
- [ ] User-confirmed physical ARC boot and DSM login
- [ ] Preserve original ARC USB as recovery baseline

No physical USB or NAS boot tests are claimed in this document.
