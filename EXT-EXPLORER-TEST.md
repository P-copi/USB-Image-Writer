# USB Image Writer v0.2.6 — EXT Explorer Test

Goal: on the original bootable 30 GB Arc Loader drive (previously detected as MBR + FAT32 P1 + EXT P2/P3), inspect the actual EXT filesystem safely and determine its allocated/free blocks without writing to source.

## Safety
- Read-only. No `blockDevice.write()`, filesystem repair, journal replay, format, partition resize, or flash cloning in this feature.
- Keep the source 30 GB USB intact and preserve existing verified IMG/IMG.GZ backups.
- If Android prompts to format, cancel.
- Do not share secrets/tokens/serial numbers from files such as `user-config.yml`.
- A low *allocated blocks* count does not prove that the same EXT filesystem can fit on a smaller drive without resizing metadata / moving allocated blocks.
- Incomplete or unsupported filesystems display a warning rather than incorrect data.

## Steps on S22 Ultra
1. Install permanent signed v0.2.6 over v0.2.5. App package/signing identity remains unchanged.
2. Plug the known-working Arc Loader original via USB-C OTG.
3. Open **สำรวจ USB Boot**, select USB and tap **สำรวจ Boot**.
4. Confirm identified Arc Loader version (23.11.4a on previously tested media), scheme MBR, FAT32 P1, EXT P2/P3.
5. Check used/allocated and free space from the EXT superblock (includes filesystem metadata).
6. Select P2, then P3 from the partition selector and browse root folders, then one harmless subfolder. Avoid opening/photographing any secret configs.
7. Share screenshot of partition layout + used/free measurements + sanitized folder names.
8. If a volume reports unsupported features or an error, copy the error wording; do not format or modify.

## Implementation scope
- Conventional EXT2/EXT3/EXT4: reads superblock, group descriptors, inodes, EXT4 extent trees or simple EXT2 direct/singly indirect blocks, and directory entries.
- Limited read-only text preview; binary files won't be displayed.
- Nested indirect mappings, some newer EXT features and encrypted/dirty/complex filesystems aren't fully supported; parser fails conservatively or warns.
- Used/free is based on superblock block allocation, not a recursive sum of file sizes.
- This is a diagnosis needed to choose a filesystem-aware 30 GB to 14 GB migration, NOT a resize/cloning implementation.
