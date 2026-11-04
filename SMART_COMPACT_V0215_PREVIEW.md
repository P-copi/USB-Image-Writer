# USB Image Writer v0.2.15 Preview — Smart Compact V1

## User-tested baseline, do not regress
- v0.2.14 writes and SHA-256 verifies the Arc Loader 4.2.2 compact image (3,879,731,200 bytes).
- Original 4.2.2 boot USB and known-good backups are **not** test targets.
- Read-only ARC4.2.2 compact backup is already tested. Keep its code unchanged.

## Legacy source observations (Arc Loader 23.11.4a)
- SanDisk USB 28.67 GiB, 512-byte logical blocks, primary MBR.
- P1 FAT32 (MBR 0x83), LBA 2048–104447.
- P2 EXT2 (MBR 0x83), LBA 104448–206847, 50 MB, clean.
- P3 EXT4 (MBR 0x83), LBA 206848–60125183; filesystem ~28.57 GB,
  allocated ~781.23 MB; clean; 229 groups; 64bit + flex_bg + metadata_csum.
- The EXT4 partition fills the USB. It cannot be truncated safely to 4 GB.
- Folder inventory does not prove data CRC correctness or show all allocated extents.

## First safe implementation milestone (this branch)
- Expose a quick **read-only** Smart Compact V1 scan via Smart Rebuild.
- Analyze MBR/EXT structure and potential target sizes. Skip recursive file inventory
  in quick mode to limit time and USB reads.
- Report what is known and unknown, including missing EXT4 metadata CRC support.
- Fail closed when the layout is invalid / filesystem is unclean.
- Clearly label smaller USB feasibility as a capacity estimate, not a successful shrink.
- Do not enable a button to produce a small bootable IMG until filesystem-aware
  rebuild and checksum verification are implemented and tested.

## Full compact shrink acceptance criteria (NOT IMPLEMENTED YET)
1. Use e2fsck and resize2fs (with EXT4 64bit, flex_bg, metadata_csum, sparse_super)
   built for Android without root, or another carefully audited equivalent.
2. Shrink only an **offline copy**. Treat the original USB as read-only.
3. Preserve boot sectors, partition starts, UUIDs, files, Linux permissions, boot config.
4. Update MBR sector counts only after success. Ensure ARC3 filesystem bytes fit.
5. Validate EXT4 metadata checksums and full filesystem, not just apparent file sizes.
6. Verify a written image and boot DSM 7.2 on a disposable smaller USB.
7. Reject I/O errors and never silently replace data with zero blocks.

## Test procedure for this preview
1. Install over v0.2.14 only if the signed APK build succeeds.
2. Plug the **old Arc 23.11.4a** USB into Android OTG. Do not connect the working NAS boot USB.
3. Go to Smart Rebuild > Smart Compact V1 quick read-only.
4. Copy report. Confirm ARC3 last LBA 60125183 and that shrink remains blocked.
5. Check that existing image write and verify remains unchanged.
