from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f"Cannot find patch target: {label}")
    return text.replace(old, new, 1)


main_path = ROOT / "app/src/main/java/com/pcopi/usbimager/MainActivity.kt"
main = main_path.read_text(encoding="utf-8")
main = main.replace(
    'text = "v0.1.0 • เขียน .IMG ลง Flash Drive ผ่าน USB-C OTG"',
    'text = "v0.1.1 • เขียน .IMG ลง Flash Drive ผ่าน USB-C OTG"',
)
main = replace_once(
    main,
    'text = "คำเตือน: การเขียน Image จะลบ Partition และข้อมูลเดิมใน Flash Drive ทั้งหมด"',
    'text = "คำเตือน: การเขียน Image จะลบ Partition และข้อมูลเดิมใน Flash Drive ทั้งหมด\\n" +\n'
    '                "หลัง Verify ผ่าน หาก Android/Samsung แจ้งให้ฟอร์แมต USB ให้กด ‘ยกเลิก’ ห้ามฟอร์แมต"',
    "format warning",
)

old_success = '''                            progressBar.progress = progressBar.max
                            progressTitle.text = "สำเร็จ • Verify ผ่าน"
                            progressDetail.text = "100% • ${FormatUtils.bytes(result.bytesWritten)}"
                            resultText.text =
                                "SHA-256 Image\\n${result.sourceSha256}\\n\\n" +
                                "SHA-256 Flash Drive\\n${result.deviceSha256}\\n\\n" +
                                "ผลตรวจสอบ: ตรงกัน 100%"
                            setBusyUi(false, "สำเร็จ • Verify ผ่าน")
                            showMessage("เขียน Image สำเร็จ", "เขียนและ Verify ผ่านแล้ว สามารถถอด Flash Drive ได้")
'''
new_success = '''                            progressBar.progress = progressBar.max
                            progressTitle.text = "สำเร็จ • Verify ผ่าน"
                            progressDetail.text = "100% • ${FormatUtils.bytes(result.bytesWritten)}"
                            resultText.text =
                                "ผลตรวจสอบ: ตรงกัน 100%\\n\\n" +
                                "เวลาเขียน: ${formatDuration(result.writeDurationMillis)} • ${FormatUtils.speed(result.writeAverageBytesPerSecond)}\\n" +
                                "เวลา Verify: ${formatDuration(result.verifyDurationMillis)} • ${FormatUtils.speed(result.verifyAverageBytesPerSecond)}\\n\\n" +
                                "SHA-256 Image\\n${result.sourceSha256}\\n\\n" +
                                "SHA-256 Flash Drive\\n${result.deviceSha256}"
                            setBusyUi(false, "สำเร็จ • Verify ผ่าน")
                            showSuccessMessage()
'''
main = replace_once(main, old_success, new_success, "success summary")

old_progress = '''        progressDetail.text = String.format(
            java.util.Locale.US,
            "%.1f%% • %s / %s • %s",
            percent,
            FormatUtils.bytes(p.completedBytes),
            FormatUtils.bytes(p.totalBytes),
            FormatUtils.speed(p.bytesPerSecond)
        )
'''
new_progress = '''        val eta = if (p.bytesPerSecond > 0.0 && p.completedBytes < p.totalBytes) {
            val remainSeconds = ((p.totalBytes - p.completedBytes) / p.bytesPerSecond).toLong().coerceAtLeast(0L)
            " • เหลือประมาณ ${formatDuration(remainSeconds * 1000L)}"
        } else {
            ""
        }
        progressDetail.text = String.format(
            java.util.Locale.US,
            "%.1f%% • %s / %s • %s%s",
            percent,
            FormatUtils.bytes(p.completedBytes),
            FormatUtils.bytes(p.totalBytes),
            FormatUtils.speed(p.bytesPerSecond),
            eta
        )
'''
main = replace_once(main, old_progress, new_progress, "ETA progress")

helpers = '''
    private fun showSuccessMessage() {
        AlertDialog.Builder(this)
            .setTitle("เขียน Image สำเร็จ")
            .setMessage(
                "เขียนและ Verify ผ่านแล้ว ข้อมูลบน Flash Drive ตรงกับไฟล์ Image 100%\\n\\n" +
                    "สำคัญ: Arc Loader หรือ Image แบบบูตบางชนิดอาจทำให้ Android/Samsung แจ้งว่า ‘ต้องฟอร์แมตไดรฟ์ USB’ ซึ่งเป็นอาการปกติ เพราะ Android อ่านบาง Partition ไม่ได้\\n\\n" +
                    "ให้กด ‘ยกเลิก’ และห้ามฟอร์แมต จากนั้นจึงถอด Flash Drive ไปใช้บูตได้"
            )
            .setPositiveButton("ตกลง", null)
            .show()
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0 -> String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
            else -> "${seconds} วินาที"
        }
    }
'''
if "private fun showSuccessMessage()" not in main:
    marker = '''    private fun showError(e: Throwable) {
        showMessage("เกิดข้อผิดพลาด", e.message ?: e.javaClass.simpleName)
    }
'''
    if marker not in main:
        raise RuntimeError("Cannot find helper insertion point")
    main = main.replace(marker, helpers + "\n" + marker, 1)
main_path.write_text(main, encoding="utf-8")


writer_path = ROOT / "app/src/main/java/com/pcopi/usbimager/ImageWriter.kt"
writer = writer_path.read_text(encoding="utf-8")
writer = replace_once(
    writer,
    '''    data class Result(
        val bytesWritten: Long,
        val sourceSha256: String,
        val deviceSha256: String
    )
''',
    '''    data class Result(
        val bytesWritten: Long,
        val sourceSha256: String,
        val deviceSha256: String,
        val writeDurationMillis: Long,
        val verifyDurationMillis: Long,
        val writeAverageBytesPerSecond: Double,
        val verifyAverageBytesPerSecond: Double
    )
''',
    "result metrics",
)
writer = replace_once(
    writer,
    '''        val sourceHash = sourceDigest.digest().toHex()

        val verifyDigest = MessageDigest.getInstance("SHA-256")
''',
    '''        val writeDurationNanos = (System.nanoTime() - writeStart).coerceAtLeast(1L)
        val sourceHash = sourceDigest.digest().toHex()

        val verifyDigest = MessageDigest.getInstance("SHA-256")
''',
    "write duration",
)
writer = replace_once(
    writer,
    '''        val deviceHash = verifyDigest.digest().toHex()
        if (!sourceHash.equals(deviceHash, ignoreCase = true)) {
            throw IOException("Verify ไม่ผ่าน: SHA-256 ของ Image และ Flash Drive ไม่ตรงกัน")
        }

        return Result(written, sourceHash, deviceHash)
''',
    '''        val verifyDurationNanos = (System.nanoTime() - verifyStart).coerceAtLeast(1L)
        val deviceHash = verifyDigest.digest().toHex()
        if (!sourceHash.equals(deviceHash, ignoreCase = true)) {
            throw IOException("Verify ไม่ผ่าน: SHA-256 ของ Image และ Flash Drive ไม่ตรงกัน")
        }

        return Result(
            bytesWritten = written,
            sourceSha256 = sourceHash,
            deviceSha256 = deviceHash,
            writeDurationMillis = writeDurationNanos / 1_000_000L,
            verifyDurationMillis = verifyDurationNanos / 1_000_000L,
            writeAverageBytesPerSecond = written / (writeDurationNanos / 1_000_000_000.0),
            verifyAverageBytesPerSecond = verified / (verifyDurationNanos / 1_000_000_000.0)
        )
''',
    "result return",
)
writer_path.write_text(writer, encoding="utf-8")


build_path = ROOT / "app/build.gradle.kts"
build = build_path.read_text(encoding="utf-8")
build = build.replace('versionCode = 1', 'versionCode = 2')
build = build.replace('versionName = "0.1.0"', 'versionName = "0.1.1"')
build_path.write_text(build, encoding="utf-8")


readme_path = ROOT / "README.md"
readme = readme_path.read_text(encoding="utf-8")
readme = readme.replace('# USB Image Writer v0.1.0', '# USB Image Writer v0.1.1')
readme = readme.replace('- แสดง Progress / จำนวนข้อมูล / ความเร็วเฉลี่ย', '- แสดง Progress / จำนวนข้อมูล / ความเร็วเฉลี่ย / เวลาที่เหลือโดยประมาณ')
readme = readme.replace('- ยังไม่มี Backup USB -> Image; วางไว้สำหรับ V1.1/V2', '- ยังไม่มี Backup USB -> Image ใน v0.1.1; ฟีเจอร์นี้วางไว้สำหรับ v0.2.0')
readme = readme.replace('Artifact: `USB-Image-Writer-v0.1.0-debug`', 'Artifact: `USB-Image-Writer-v0.1.1-debug`')
roadmap = '''

## สิ่งที่เพิ่มใน v0.1.1

- แสดงเวลาเขียนและเวลา Verify หลังทำงานเสร็จ
- แสดงความเร็วเฉลี่ยของการเขียนและ Verify
- แสดงเวลาที่เหลือโดยประมาณระหว่างทำงาน
- เพิ่มคำเตือนกรณี Arc Loader/boot image ที่ Android อาจเสนอให้ฟอร์แมต USB หลังเขียนสำเร็จ
- ย้ำให้กด **ยกเลิก** และห้ามฟอร์แมตเมื่อ Verify ผ่านแล้ว

## Roadmap v0.2.0 — Backup / Clone

ทำได้โดยไม่ Root เช่นเดียวกัน เพราะแอปสามารถอ่าน raw block จาก Flash Drive ผ่าน USB Host ได้อยู่แล้ว

แนวทางที่ปลอดภัยที่สุดบนมือถือที่มี USB-C เพียงพอร์ตเดียวคือ:

1. **Flash Drive → Image**: อ่าน raw block จาก USB แล้วบันทึกเป็น `.img` ลงหน่วยความจำโทรศัพท์ผ่าน Storage Access Framework
2. **Image → Flash Drive**: ใช้ฟังก์ชัน Write + Verify ที่มีอยู่เพื่อเขียนกลับลง Flash Drive ลูกใหม่
3. กระบวนการสองขั้นนี้เท่ากับการ **Clone Flash Drive** โดยไม่ต้องต่อ USB สองลูกพร้อมกัน

ภายหลังสามารถเพิ่ม **USB → USB Direct Clone** สำหรับผู้ใช้ที่มี USB-C hub (แนะนำ powered hub) และเสียบ Flash Drive ต้นทาง/ปลายทางสองลูกพร้อมกันได้

หมายเหตุ: Full Device Backup จะมีขนาดใกล้เคียงความจุจริงของ Flash Drive เช่น USB 32 GB จะได้ Image ใกล้เคียง 32 GB แม้ข้อมูลใช้งานจริงน้อยกว่า จึงควรเริ่มจาก Full Backup ที่แม่นยำก่อน แล้วค่อยพัฒนา Compact/Smart Backup ภายหลัง
'''
if '## สิ่งที่เพิ่มใน v0.1.1' not in readme:
    readme = readme.rstrip() + roadmap + "\n"
readme_path.write_text(readme, encoding="utf-8")


test_plan = '''# Test Plan — v0.1.1

## A. USB discovery
- [x] เสียบ Flash Drive ผ่าน USB-C OTG แล้วรายการขึ้น
- [x] กดรีเฟรชแล้วรายการถูกต้อง
- [x] Android ขอสิทธิ์ USB และแอปทำงานต่อหลังอนุญาต
- [ ] ถอดแล้วรายการหายและแอปไม่ค้าง

## B. Capacity
- [x] แสดงความจุ USB ใกล้เคียงค่าจริง
- [x] แสดง block size ตามอุปกรณ์
- [ ] Image ใหญ่กว่า USB ต้องห้ามเขียน

## C. Write
- [x] เลือก .img ได้
- [x] มีคำเตือน 2 ชั้น
- [x] Progress 0-100%
- [x] แสดง MB/s
- [x] หน้าจอไม่ดับระหว่างทำงาน
- [ ] ถอด USB ระหว่างทำงานแล้วหยุดพร้อมแจ้ง error
- [ ] ตรวจเวลาที่เหลือโดยประมาณใน v0.1.1

## D. Verify
- [x] หลังเขียนเสร็จเข้า Verify อัตโนมัติ
- [x] SHA-256 Image == SHA-256 USB
- [x] แสดง “Verify ผ่าน” เฉพาะเมื่อ hash ตรงกัน
- [ ] ตรวจเวลาและความเร็วเฉลี่ย Write/Verify ในหน้าสรุป v0.1.1

## E. Arc Loader
- [x] เขียน arc.img 2.00 GB สำเร็จและ Verify ผ่าน
- [x] Samsung อาจแจ้งให้ฟอร์แมตหลังเขียน boot image; ต้องกด “ยกเลิก” และห้ามฟอร์แมต
- [ ] Flash Drive บูต Arc Loader ได้
- [ ] Partition/ไฟล์ภายในตรงกับต้นฉบับ
- [ ] ทดสอบบน Flash Drive ลูกใหม่ที่ความจุ >= Image

## F. Roadmap Backup / Clone
- [ ] v0.2.0: อ่าน Flash Drive แบบ raw แล้วบันทึกเป็น .img ผ่าน Storage Access Framework
- [ ] ตรวจพื้นที่ว่าง/ขนาดปลายทางก่อน Backup
- [ ] แสดง Progress / MB/s / ETA ระหว่าง Backup
- [ ] สร้าง SHA-256 ของไฟล์ Backup
- [ ] ใช้ Backup Image เขียนกลับไป Flash Drive ลูกใหม่และ Verify
- [ ] ภายหลัง: Direct USB → USB Clone ผ่าน USB-C hub/powered hub
'''
(ROOT / "TEST-PLAN.md").write_text(test_plan, encoding="utf-8")

print("v0.1.1 source upgrade complete")
