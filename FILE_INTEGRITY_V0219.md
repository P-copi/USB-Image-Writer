# USB Image Writer v0.2.19 Preview: verify user-file SHA-256 before and after EXT4 shrink

## Prior observed results from Galaxy S22 Ultra (v0.2.18)
- Bundled NDK ARM64 mke2fs, e2fsck, resize2fs execute without root.
- An app-owned sparse 96 MiB EXT4 image shrank to 32 MiB.
- e2fsck -fn passed both before and after shrink.
- Sparse file 96 MiB occupied only ~16 KiB BEFORE formatting.
- 25.27 GB free on device at test time.
- Crucially, v0.2.18 did not preserve/check test file contents because no file
  had been inserted; v0.2.19 adds a real content preservation test.

## v0.2.19 new experiment (no USB required)
- Build upstream e2fsprogs 1.47.2 debugfs for Android ARM64 in GitHub Actions.
- Package read-only-executable libdebugfs.so in APK native library directory.
- Create a 96 MiB sparse regular file in app-owned sandbox.
- Format test EXT4 with 64bit, metadata_csum, flex_bg.
- Generate 4 MiB deterministic binary data in app's sandbox.
- Use debugfs to WRITE binary into the EXT4 filesystem as /integrity.bin.
- Use debugfs to DUMP file before shrinking; verify SHA-256 and byte length.
- e2fsck -fn before shrinking; run resize2fs -P for minimum-size estimate.
- resize2fs offline shrinks EXT4 from 96 MiB to 32 MiB.
- e2fsck -fn after shrinking; dump file again and verify SHA-256 + byte length.
- Clean up test filesystem and payload files on success/cancellation/error.
- No USB is opened, no physical USB is formatted, and no original ARC3 is resized.

## STOP conditions and limits
- Any tool missing, failure, timeout, mismatch or unexpected result:
  label BLOCKED, keep Smart Compact shrink disabled.
- A correct synthetic file SHA-256 does not prove any real ARC3 backup correctness.
- A sparse logical EXT4 file can have physical storage allocation much smaller
  than logical capacity. But an accurate and complete copy of the 28.57 GiB
  legacy ARC3 is NOT yet implemented; this preview does NOT copy used blocks.
- Later versions must produce a validated offline ARC3 copy with complete
  metadata/backup-superblock handling and all file data preserved.
- User may have only 25.27 GB free; do NOT allocate a raw 28.67 GB physical
  file on the phone or claim a compact IMG without verified source reconstruction.
- Preserve the known-good production ARC 4.2.2 USB and v0.2.14 writer behavior.

## Later acceptance: 8 GB USB boot
- Audited ext4 used-block copy (including flex_bg, uninitialized groups,
  journal, checksums, and backup metadata), no silent padding for read errors.
- e2fsck -fn must pass on offline source copy; shrink only the copy.
- Validate UUID, 64bit metadata_csum, files, and MBR partition layout.
- Stream compact IMG, SHA-256 verify, write to spare 8 GB USB, read-back verify.
- Boot DSM using spare USB before marking complete.
