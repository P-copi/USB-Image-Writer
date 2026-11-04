# USB Image Writer v0.2.16 Preview: EXT4 CRC32C audit

## Status
This is a SAFETY milestone toward a compact 8 GB-bootable legacy ARC IMG.
- ✅ Reuses v0.2.14 proven IMG writer + Verify without touching its code.
- ✅ Reuses v0.2.15 quick legacy ARC preflight.
- ✅ Adds opt-in read-only EXT4 checksum inspection.
- ❌ No EXT4 shrink, no 8 GB boot IMG builder yet.

## Why the legacy ARC 23.11.4a still needs a resizer
The test USB is 28.67 GB with MBR partitions:
1. FAT32 2048–104447
2. EXT2 104448–206847
3. EXT4 206848–60125183 (~28.57 GB filesystem, ~781.23 MB allocated).

Because partition 3 reaches the USB end, raw truncation to 4 or 8 GB
would cut off filesystem metadata. Metadata includes backup superblocks,
group descriptors, bitmaps, inode tables, and extents. The allocated
781 MB is not a safe lower bound for a valid shrunk file system.

## What EXT4 CRC32C Audit actually verifies
- The primary EXT4 superblock CRC32C.
- All primary group descriptors' CRC32C.
- Initialized block bitmap CRC32C.
- Initialized inode bitmap CRC32C.
- Intentionally skip UNINIT bitmaps, report the count.
- Fail closed on unsupported meta_bg or bigalloc, invalid geometry, or I/O error.

Does NOT validate inode checksum, extent checksum, directory checksum,
data blocks, journal, backup metadata, nor bootability. A PASS does NOT
mean the drive can be shrunk or that all data is readable.

Implementation follows Linux EXT4 checksum docs and fs/ext4 code:
https://www.kernel.org/doc/html/latest/filesystems/ext4/checksums.html
https://origin.kernel.org/doc/html/latest/filesystems/ext4/group_descr.html
https://github.com/torvalds/linux/blob/master/fs/ext4/bitmap.c

## Test on Android
1. Leave the working ARC 4.2.2 NAS boot USB untouched.
2. Install the SIGNED v0.2.16 Preview over prior signed build.
3. Plug legacy ARC 23.11.4a test USB into USB-C OTG.
4. Open Smart Rebuild.
5. Select SOURCE only; do not select any destination.
6. Tap `EXT4 CRC32C • ตรวจ Superblock / Groups / Bitmaps`.
7. Copy the result, including errors or mismatch information.
8. If a checksum mismatches, do not attempt shrink or a clone.

## Next implementation criteria: actual compact IMG
- Build and validate e2fsck+resize2fs for Android (no root), or a proven
  filesystem-aware equivalent, and run on an offline copy only.
- Provide storage plan: even a full 28.67 GB original copy is not required
  to be materialized if a block-safe sparse copy can be proven; until then
  the app must NOT imply no workspace is needed.
- Preserve bootloader MBR, FAT32, EXT2, EXT4 UUIDs and files.
- Update MBR partition 3 size only after resize2fs verifies new filesystem.
- Read-back verify checksum and boot spare USB on real DSM NAS.
- Never pad a medium read error or overwrite the source USB.
