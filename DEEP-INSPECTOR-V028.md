# v0.2.8 Deep Inspector — implementation and safety gates

Source test: 28.67 GiB (512-byte sectors), MBR, target 14.50 GiB. This phase MUST be read-only. No filesystem repair, journal replay, partition edits, or raw writes to source.

## Confirm partition types
- Read all four MBR entries at byte 446, verify 0x55AA and CHS/LBA bounds, unsigned little-endian start and length; detect overlaps and out-of-device entries.
- MBR 0x83 is a Linux partition type, NOT FAT32. Identify actual FAT via boot sector BPB and signatures, ext via superblock magic at partition offset +1024.
- Record exact raw sector units and GiB/GB conventions.

## EXT2/3/4 superblock and descriptors
- Decode feature_compat, feature_incompat, feature_ro_compat, s_state, s_errors, s_last_orphan, s_blocks_count_hi, block size, 64bit, metadata_csum, flex_bg, sparse_super, uninit_bg, bigalloc.
- Decode needs_recovery from incompat feature bit 0x0004, independently of clean state. Mark resize BLOCKED when recovery needed.
- Handle descriptor size (32/64+), group descriptor table placement, flex_bg metadata location, bitmap pointers as 64-bit, blocks per group, final partial group.
- Uninitialized bitmap groups (EXT4_BG_BLOCK_UNINIT): do not read uninitialized bitmap as authoritative; account for reserved metadata using ext4 rules or report allocation scan incomplete. NEVER classify uninitialized as corruption automatically.
- Verify superblock/group descriptor CRC32C with correct ext4 seed/UUID rules when metadata_csum enabled. If unsupported, show 'checksum not verified', NOT 'valid'.
- Bound every read to partition/device, avoid integer overflow and arbitrary large allocations; stream bitmaps group by group.
- Highest allocated block is not minimum shrink size; metadata, inode tables, group boundaries, and movable blocks matter.

## FAT read-only diagnostics
- Validate BPB bytes/sector, sectors/cluster, reserved sectors, FAT count, FAT size, total sectors, root cluster; reject inconsistent sizes.
- Detect FAT12/16/32 from cluster count, not MBR type alone; scan FAT allocation read-only with bounds and reserved/bad cluster handling.
- Preserve partition boot record and MBR bootstrap in eventual cloning plan.

## User-facing output
- Show filesystem detected vs MBR partition type separately.
- Report statuses: VERIFIED, INCOMPLETE, UNSUPPORTED, NEEDS_RECOVERY, NOT_CLEAN, OUT_OF_BOUNDS.
- Block 'ready to shrink' if journal recovery, unverified metadata checksum, uninitialized bitmap handling unsupported, or incomplete allocation scan.
- Export sanitized diagnostic JSON (no USB identifiers unless user opts in), including offset, sector count, block size, features, checksum coverage, error reasons.

## Regression fixtures and acceptance
- MBR type 0x83 containing FAT32; ext2 unclean; ext4 clean + needs_recovery; ext4 group 227 BLOCK_UNINIT; ext4 metadata_csum; truncated device; overlapping MBR partitions; invalid superblock.
- Compare findings against e2fsck -fn, dumpe2fs -h, tune2fs -l on offline IMAGE COPIES only (do not run filesystem repair on source).
- Verify read-only: no USB bulk OUT write command, no write handle, no changed source hash.
- Build signed release and validate artifact in Actions before announcing installable APK.

## Later phase (NOT v0.2.8)
- Make an immutable source image first; on a copy replay journal and fsck, create target partition layout, copy files preserving uid/gid/modes/ACL/xattrs/symlinks/hardlinks, bootloader and required UUID/labels, then validate by booting target USB. Do not assume 781 MB allocated means a safe 781 MB image.
