# USB Image Writer v0.1.0

Android app สำหรับเขียน raw disk image (`.img`) ลง USB Flash Drive ผ่าน USB-C OTG โดยไม่ Root

## เป้าหมาย V1

- เลือกไฟล์ `.img` ผ่าน Android Storage Access Framework
- ตรวจหา USB Mass Storage แบบ SCSI Transparent / Bulk-Only Transport
- ขอ USB permission ตามระบบ Android
- อ่านความจุจริงและ block size ของ Flash Drive
- ป้องกันการเขียนเมื่อ Image ใหญ่กว่า USB
- ยืนยันก่อนลบข้อมูล 2 ชั้น
- Raw write ตั้งแต่ LBA 0
- แสดง Progress / จำนวนข้อมูล / ความเร็วเฉลี่ย
- Verify หลังเขียนด้วย SHA-256 โดยอ่านข้อมูลกลับจาก USB
- ไม่ต้อง Root และไม่ต้องใช้คอมพิวเตอร์ในการใช้งาน APK

## ข้อจำกัด V1

- รองรับไฟล์ `.img` แบบไม่บีบอัดก่อน (`.img.gz` ยังไม่รองรับ)
- รองรับ USB Mass Storage ที่ใช้ Bulk-Only Transport (BOT). UAS/SSD enclosure บางรุ่นยังไม่รองรับ
- ขนาดไฟล์ Image ต้องตรงแนว block size ของ USB (โดยทั่วไป 512 หรือ 4096 ไบต์)
- ระหว่างเขียนห้ามถอด USB
- ยังไม่มี Backup USB -> Image; วางไว้สำหรับ V1.1/V2

## Build บน GitHub Actions

โปรเจกต์มี `.github/workflows/build-apk.yml` แล้ว เมื่อ push ขึ้น repo ใหม่ GitHub จะ build APK อัตโนมัติ

Artifact: `USB-Image-Writer-v0.1.0-debug`

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
