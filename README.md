# USB Image Writer v0.1.1

Android app สำหรับเขียน raw disk image (`.img`) ลง USB Flash Drive ผ่าน USB-C OTG โดยไม่ Root

## เป้าหมาย V1

- เลือกไฟล์ `.img` ผ่าน Android Storage Access Framework
- ตรวจหา USB Mass Storage แบบ SCSI Transparent / Bulk-Only Transport
- ขอ USB permission ตามระบบ Android
- อ่านความจุจริงและ block size ของ Flash Drive
- ป้องกันการเขียนเมื่อ Image ใหญ่กว่า USB
- ยืนยันก่อนลบข้อมูล 2 ชั้น
- Raw write ตั้งแต่ LBA 0
- แสดง Progress / จำนวนข้อมูล / ความเร็วเฉลี่ย / เวลาที่เหลือโดยประมาณ / เวลาที่เหลือโดยประมาณ
- Verify หลังเขียนด้วย SHA-256 โดยอ่านข้อมูลกลับจาก USB
- ไม่ต้อง Root และไม่ต้องใช้คอมพิวเตอร์ในการใช้งาน APK

## ข้อจำกัด V1

- รองรับไฟล์ `.img` แบบไม่บีบอัดก่อน (`.img.gz` ยังไม่รองรับ)
- รองรับ USB Mass Storage ที่ใช้ Bulk-Only Transport (BOT). UAS/SSD enclosure บางรุ่นยังไม่รองรับ
- ขนาดไฟล์ Image ต้องตรงแนว block size ของ USB (โดยทั่วไป 512 หรือ 4096 ไบต์)
- ระหว่างเขียนห้ามถอด USB
- ยังไม่มี Backup USB -> Image ใน v0.1.1; ฟีเจอร์นี้วางไว้สำหรับ v0.2.0

## Build บน GitHub Actions

โปรเจกต์มี `.github/workflows/build-apk.yml` แล้ว เมื่อ push ขึ้น repo ใหม่ GitHub จะ build APK อัตโนมัติ

Artifact: `USB-Image-Writer-v0.1.1-debug`

APK: `app-debug.apk`

## การทดสอบครั้งแรกที่แนะนำ

1. ใช้ Flash Drive สำรองที่ไม่มีข้อมูลสำคัญ
2. ใช้ Image ขนาดเล็กก่อน เช่น 64-256 MB เพื่อวัดความเสถียร
3. ตรวจให้ Verify ผ่าน
4. นำ Flash Drive ไปเสียบเครื่องอื่นและตรวจ partition/boot data
5. จากนั้นจึงทดสอบกับ Arc Loader image จริง

## เทคโนโลยี

- Kotlin
- Android USB Host API
- libaums core 0.10.0 (Apache 2.0)
- SCSI raw block access ผ่าน USB Mass Storage Bulk-Only Transport
- SHA-256 verification

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

