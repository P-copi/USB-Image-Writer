# USB Image Writer v0.2.3 — Compressed Full-Device Images

## Why gzip first?
A 30 GB Arc Loader flash drive can have 2–3 GB of files while a full raw IMG still needs ~30 GB. A compressed raw disk image (.img.gz) preserves every byte of the partition table, EFI, Arc Loader and unallocated sectors, using standard gzip streaming. It does not need a temporary raw 30 GB file.

This is NOT partition resizing or copying only used data:
- Zero-filled unallocated sectors can compress very well.
- Unused sectors containing older incompressible data may make IMG.GZ almost as large as the USB.
- The restored target must have at least as many real disk sectors as the original. A smaller disk is not supported, even if the compressed image is only 2 GB.
- The source USB is never modified by backup.

## Formats
- .img: Full raw, uncompressed; size equals USB capacity.
- .img.gz: Full raw, gzip-compressed, lossless.

## Backup: USB to IMG.GZ
1. Connect a readable expendable USB by OTG and open สร้าง Image.
2. Inspect the USB and choose บีบอัดเต็มลูก (.img.gz).
3. Save to internal storage, not the original USB.
4. Keep the app visible on the first test; never unplug during work.
5. Wait through backup and Verify, which decompresses the saved file and compares its SHA-256 to the first-pass USB data.
6. Record reported saved size and SHA-256. Check that the filename ends with .img.gz.
7. Never use a partial or failed backup.

## Restore: IMG.GZ to USB
1. Use a different expendable USB whose real capacity is at least the original USB capacity.
2. Open เขียน Image, choose the .img.gz and inspect the target capacity.
3. Confirm the two destructive warnings.
4. Before any USB write, the app decompresses and validates the *entire* gzip file including CRC and computes the raw size and SHA-256. This may take several minutes for a 30 GB source.
5. If valid and it fits, decompressed blocks stream directly to LBA 0 on the USB.
6. After writing, read back USB and compare SHA-256.
7. Try booting the new Arc Loader USB. If Samsung asks to format the boot USB, select Cancel.

## Limits
- Compression ratio cannot be predicted just from used-file space.
- Does not shrink filesystems, remove free-space data, or relocate secondary GPT headers.
- Do not discard the original boot USB until the restored image boots reliably.
- A SHA-256 match does not certify physical NAND health.
