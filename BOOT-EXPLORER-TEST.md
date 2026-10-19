# USB Image Writer v0.2.5 — USB Boot Explorer

This module can inspect bootable USB media that Android/Samsung My Files cannot mount. If Android asks to format the flash drive, press Cancel. The app directly reads USB blocks through USB Host and does not write to or format the drive.

## Supported
- USB Mass Storage Bulk-Only Transport with SCSI Transparent protocol.
- MBR and GPT partitions (read-only).
- FAT16 and FAT32 root folders and subfolders (read-only, including simple LFN).
- Small text file previews in supported FAT partitions.
- Arc Loader evidence: ARC-VERSION, ARC-BUILD, EFI folder, and boot/grub/grub.cfg indicators.
- Displays non-FAT partition types when detectable. Does not browse their files yet.

## Unsupported
- exFAT, NTFS, EXT2/EXT4 file browsing (type detection only).
- Recovering missing files from filenames alone.
- Editing/rebuilding boot partitions and boot sectors.
- Exhaustive 3rd-party bootloader fingerprinting.
- Proving a USB will boot merely from the detected filenames.

## Samsung S22 Ultra test
1. Install the same permanent-key signed Release APK over v0.2.4.
2. Plug the known-working Arc Loader USB into OTG. If Samsung offers Format, **Cancel**.
3. Open the new **สำรวจ USB Boot** tile and tap **สำรวจ Boot**.
4. Grant USB access if prompted. Record the identified bootloader evidence and partition list.
5. Select the readable FAT32 partition and open folders EFI, boot and grub.
6. Tap ARC-VERSION / ARC-BUILD if shown to preview text.
7. Share screenshots of the partition report and file/folder list; do not share sensitive config values or identity settings.
8. Do not run Write Image on the original bootable flash drive.

If you previously copied actual contents and not just a list of filenames, a properly bootable smaller Arc Loader base image may be used to reconstruct a target, with backups of settings copied into the correct volumes. File copy into a blank/fresh FAT drive alone is not sufficient to guarantee boot.
