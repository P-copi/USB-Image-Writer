# USB Image Writer v0.2.20 Preview — EXPERIMENTAL ARC3 sparse mirror

## Confirmed on Samsung Galaxy S22 Ultra in v0.2.19
- ARM64 NDK mke2fs, e2fsck, resize2fs and debugfs run without root.
- 96 MiB synthetic EXT4, real 4 MiB file injected using debugfs.
- SHA-256 of file matched before and after shrinking to 32 MiB.
- e2fsck -fn exit 0 before/after; resize2fs -P estimated 2453 blocks.
- This does NOT yet prove that the old 28.57 GiB ARC3 is safely duplicable.

## New scope
An opt-in, read-only-source ARC3 sparse mirror to *app-private* storage, not
a compact bootable USB/IMG. The 28.57 GiB original filesystem has MBR start
LBA 206848; it uses 64-bit descriptors, metadata_csum, flex_bg and
BLOCK_UNINIT/INODE_UNINIT groups.

v0.2.20 performs:
1. Source MBR/EXT read-only preflight and existing EXT4 CRC32C metadata audit.
2. Parse primary Superblock and 229 descriptors, validated before use.
3. Mark known allocated blocks from initialized group block bitmaps only.
4. Also mark all descriptor-located block/inode bitmaps, all inode tables,
   and the Superblock/GDT/reserved GDT areas in sparse_super backup groups.
5. Copy only marked blocks to a *sparse* app-private regular file at exactly
   their original offsets. Unmarked regions are holes, not source reads.
6. Read-back SHA-256 for all selected blocks, strict byte count check.
7. Run e2fsck -fn and resize2fs -P against the OFFLINE copy only.
8. Keep a copy named arc3-mirror.ext4 only if all checks pass; otherwise
   delete the partial mirror and report the reason.

## Major limitations / risks
- Experimental. A copy that passes e2fsck is still NOT a full raw forensic
  clone, does not preserve stale unallocated sectors, and is not a tested
  bootable image. Not all kernel EXT4 features/layouts supported; unsupported
  flags or unexpected layout BLOCK the operation.
- The original source USB is opened exclusively for read operations via
  libaums; no source write/format operation is called.
- Reported bad source LBA near 8 million may still cause read failures.
  Fail closed; do NOT write zeroes for a failed required data block.
- Output logical size ~28.57 GiB; physical storage generally much smaller
  due to sparse holes, but actual size depends on allocated blocks and phone
  filesystem. Require >=3 GiB free space reserve and max 8 GiB selected data.
- Keep phone awake/app foreground. Disconnecting USB, call interruptions,
  Android app termination may leave partial mirror that the next run cleans.
- App-private completed mirror persists for future versions. Uninstalling the
  app may delete it. Check available storage. No export is implemented here.
- No automatic shrink, no MBR rewrite, no USB destination write in v0.2.20.
- Read-back SHA checks captured selected blocks only, NOT unallocated space,
  so it is NOT proof of raw-device equality.

## Test
1. Install signed v0.2.20 over prior signed APK.
2. Plug only the old SanDisk ARC 23.11.4a boot USB through reliable OTG.
3. Open Smart Rebuild, select source USB, tap ARC3 Sparse Mirror experiment.
4. Read the explicit confirmation dialog.
5. Do not switch apps during copy. The UI includes a cancel action.
6. Copy the final report, whether success or BLOCKED.
7. Do not use mirror as a backup until SHA, e2fsck and real bootable image
   verification are completed in later versions.

## Source references
- https://www.kernel.org/doc/html/latest/filesystems/ext4/blockgroup.html
- https://www.kernel.org/doc/html/latest/filesystems/ext4/group_descr.html
- https://github.com/tytso/e2fsprogs/tree/v1.47.2
