# v0.2.17: Offline EXT4 shrink sandbox and architecture for actual compact ARC IMG

## User-visible goal
Source Arc Loader 23.11.4a: 28.67 GiB SanDisk USB, MBR P1 FAT32,
P2 EXT2, P3 EXT4 (229 groups, 781.23 MiB allocated, 27.81 GiB free).
Target: a verified bootable IMG small enough for a genuine 8 GB USB.
Keep v0.2.14 Arc Loader 4.2.2 compact writer and SHA-256 verify unchanged.

## Why this milestone is necessary
Android USB host in this project uses libaums SCSI bulk I/O. e2fsprogs
accepts regular files and Linux block-device paths, NOT a Java libaums
block driver. Do not pass Android's /dev/bus/usb node to e2fsck.
First prove the local, rootless subprocess and sparse-workspace path.
This milestone creates a disposable 96 MiB EXT4, runs e2fsck read-only,
runs resize2fs to 32 MiB, re-checks with e2fsck, then deletes the file.
No source USB read/write occurs in that sandbox.

## Acceptance required before REAL legacy compact shrink
1. On Samsung S22 Ultra Android, confirm mke2fs + e2fsck + resize2fs
   really run as the app UID and the synthetic image fsck passes.
   Missing system binaries or SELinux denial requires an independently
   packaged, audited Android-native e2fsprogs toolchain.
2. Validate sparse file support on the phone's app-private filesystem
   (large logical size need not imply large physical allocation).
3. Build a complete OFFLINE ARC3 copy, not just a manifest:
   - Check primary superblock/group descriptor and initialized bitmap CRC.
   - Copy allocated blocks from initialized group bitmaps, preserving exact
     LBA/byte offsets; copy superblock/GDT backup blocks and any required
     metadata in UNINIT groups rather than assuming they're all zero.
   - Handle flex_bg's bitmap/inode table pointers outside their own group.
   - Fail on medium errors in any needed metadata or used block, NEVER
     silently zero-fill read failures.
   - Preserve EXT4 UUID, metadata_csum, journal, permissions, hardlinks,
     inodes/extents, all files, and the loader configuration.
   - Treat the temporary image as disposable; source USB never writable.
4. Run e2fsck -fn on the COMPLETE offline copy. If it reports corruption,
   halt. If required, e2fsck repairs only the offline copy, then re-checks.
5. Query resize2fs -P for minimum blocks and choose a safe >minimum size.
   Do not equate 781.23 MiB allocated with the resize minimum.
6. Offline shrink with resize2fs on the validated copy. Re-run e2fsck;
   if any step fails, delete partial output and keep source untouched.
7. Prepare MBR with original boot code, same P1/P2 byte contents and
   start LBAs, updated P3 sector count, correct EXT4 size and alignment.
8. Stream raw IMG or gzip with SHA-256 and read-back verify; image
   length must fit measured destination (not just advertised "8 GB").
9. Burn onto a spare 8 GB USB with the existing writer/verify, then
   boot real NAS to DSM. Until verified: label 'EXPERIMENTAL / BLOCKED'.
10. Do not touch the production Arc Loader 4.2.2 boot or backup USB.

## v0.2.17 test procedure
- Install signed preview over v0.2.16 on Samsung S22 Ultra.
- No USB is necessary for this synthetic-only test.
- Open Smart Rebuild > v0.2.17 test EXT4 shrink sandbox.
- Read and copy the full report, including discovered tool paths and
  exit codes. A PASS proves only that system tools can shrink a
  disposable regular-file filesystem; it does NOT prove real ARC3 safety.
