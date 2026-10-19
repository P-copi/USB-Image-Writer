# USB Image Writer v0.2.7 — Smart Rebuild Preflight

This iteration is a **read-only** technical preflight for copying a real 30 GB Arc Loader 23.11.4a source onto a separate smaller 14 GB USB in a future controlled operation. It is not a filesystem resizer, USB cloner, or image generator.

## Source findings (from the user's successful EXT Explorer test)
- Original media: SanDisk USB, 28.67 GiB, MBR, 512-byte sectors.
- P1: FAT32 at LBA 2048–104447, ~50 MiB.
- P2: EXT2 at LBA 104448–206847, ~50 MiB, ~14 MiB allocated.
- P3: EXT4 at LBA 206848–60125183, ~28.57 GiB, ~781 MiB allocated by superblock, ~27.81 GiB free.
- EXT4 journal recovery warning was reported earlier. **Do not resize the original**. The 781 MB allocation size is not the safe minimum shrink size and does not mean blocks are physically limited to the first 781 MB.

## New in v0.2.7
- Main menu: **Smart Rebuild** (read-only).
- Select source and optionally distinct destination USB connected through a USB-C hub.
- Read each real MBR partition; detect FAT and EXT superblocks.
- For EXT2/3/4: filesystem UUID, volume name, clean/dirty state, journal recovery bit, incompatibility flags, filesystem size, used/free allocation, block size and block group count.
- Scan EXT block allocation bitmaps **from the end backwards**, where features allow, to report the highest allocated physical block. Note that this may be metadata near the end of the filesystem and is NOT a shrink boundary. Bitmap checksums and journal content are not validated; warnings remain.
- Show source and destination capacities and copyable result text. No write or format operations exist in this screen.

## One-time phone test
1. Install signed v0.2.7 over v0.2.6 with no uninstall.
2. Insert 30 GB Arc Loader source and, optionally, 14 GB destination with a suitable USB Hub.
3. Tap **Smart Rebuild**. Pick correct source and destination (or leave destination unset).
4. Tap **ตรวจ EXT และความพร้อมย้าย (อ่านอย่างเดียว)**.
5. Keep the screen active, wait for EXT scan and note UUID, journal/clean status, free/allocated block counts and maximum allocated physical block.
6. Send a screenshot of the report or copy its nonsecret contents. Do not reveal files containing configuration secrets, keys, tokens or serial numbers.
7. Keep original USB, encrypted backups and bootable media unchanged.

## What still needs to be built before a smaller USB can boot
A candidate pipeline must operate on a working **copy**, never the only bootable source:
- Produce a read-consistent copy or use a known-good Arc Loader base image;
- ensure ext4 journal is clean and validate with e2fsck (on the copy, not via ad-hoc Android writes);
- shrink EXT4 using filesystem-aware relocation and update MBR partition boundaries, or rebuild EXT file trees while preserving permissions, ownership, symlinks, attributes, and required identifiers;
- preserve proper BIOS/UEFI GRUB structure, boot code, UUID/PARTUUID references and Arc configs across all 3 partitions;
- write the candidate to a spare USB whose **measured** size is sufficient;
- reread and verify, then test boot on the target hardware.

App compile/sign success does not substitute for the physical USB boot test.
