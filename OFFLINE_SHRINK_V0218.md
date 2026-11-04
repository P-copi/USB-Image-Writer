# USB Image Writer v0.2.18 Preview — Bundled Android e2fsprogs (ARM64)

## Confirmed on Samsung Galaxy S22 Ultra in v0.2.17
- Android system dirs did not contain accessible mke2fs/e2fsck/resize2fs.
- The synthetic sandbox stopped before creating an EXT4 test image.
- This does NOT indicate corrupted ARC USB, nor require rooting the phone.

## v0.2.18 implementation
- Build aarch64 PIE tools from the unmodified upstream e2fsprogs v1.47.2 source
  during GitHub Actions, with Android NDK 25.2, API 26.
- Package tools as native extracted executables:
  - lib/arm64-v8a/libmke2fs.so
  - lib/arm64-v8a/libe2fsck.so
  - lib/arm64-v8a/libresize2fs.so
- Execute from APK's read-only nativeLibraryDir, not from writable filesDir.
- Native extraction: android:extractNativeLibs=true and useLegacyPackaging=true.
- Test ONLY an app-created temporary EXT4 image, 96MiB -> 32MiB.
- Availability + test result must be demonstrated on user's real phone.
- Do not execute anything against /dev/block, /dev/bus/usb, or a USB file.
- On any nonzero return code, timeout, SELinux denial, or checksum failure:
  stop and show diagnostic report. Clean the disposable test image.
- v0.2.14 working Arc Loader 4.2.2 IMG writer and SHA-256 verify unchanged.
- The production NAS boot flash drive is never a test source.

## Open source / license
This version builds external e2fsprogs by Theodore Ts'o and contributors.
- Source: https://github.com/tytso/e2fsprogs/tree/v1.47.2
- Source license and notices: https://github.com/tytso/e2fsprogs/blob/v1.47.2/NOTICE
- e2fsprogs comprises components under GPL and LGPL; downstream distribution
  must satisfy the applicable obligations for all bundled components.
- Build config and upstream ref are in .github/workflows/build-apk.yml.
  Build logs record exact source commit and hashes of built executables.
- No precompiled third-party binaries are downloaded.

## Next milestone (NOT implemented by v0.2.18)
Actual offline ARC3 shrink on a verified, block-faithful disposable COPY:
- Preserve backup superblocks, flex_bg out-of-group metadata, 64bit descriptors,
  metadata_csum, initialized and uninitialized bitmap semantics.
- Detect I/O errors; never claim success if used blocks or metadata fail reading.
- Run offline e2fsck/resize2fs only after an audited copy exists.
- Rewrite MBR PART3 count with proper alignment; preserve P1/P2 and UUID.
- Verify saved IMG and test spare 8 GB USB boot on NAS.
- Until all gates pass, keep Smart Compact Shrink BLOCKED.
