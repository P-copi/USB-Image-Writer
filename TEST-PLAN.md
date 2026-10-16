# Test Plan — v0.1.1

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
