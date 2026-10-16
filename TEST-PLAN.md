# Test Plan — v0.1.0

## A. USB discovery
- [ ] เสียบ Flash Drive ผ่าน USB-C OTG แล้วรายการขึ้น
- [ ] ถอดแล้วรายการหาย
- [ ] กดรีเฟรชแล้วรายการถูกต้อง
- [ ] Android ขอสิทธิ์ USB และแอปทำงานต่อหลังอนุญาต

## B. Capacity
- [ ] แสดงความจุ USB ใกล้เคียงค่าจริง
- [ ] แสดง block size 512/4096 ตามอุปกรณ์
- [ ] Image ใหญ่กว่า USB ต้องห้ามเขียน

## C. Write
- [ ] เลือก .img ได้
- [ ] มีคำเตือน 2 ชั้น
- [ ] Progress 0-100%
- [ ] แสดง MB/s
- [ ] หน้าจอไม่ดับระหว่างทำงาน
- [ ] ถอด USB ระหว่างทำงานแล้วหยุดพร้อมแจ้ง error

## D. Verify
- [ ] หลังเขียนเสร็จเข้า Verify อัตโนมัติ
- [ ] SHA-256 Image == SHA-256 USB
- [ ] แสดง “Verify ผ่าน” เฉพาะเมื่อ hash ตรงกัน

## E. Arc Loader
- [ ] Flash Drive บูต Arc Loader ได้
- [ ] Partition/ไฟล์ภายในตรงกับต้นฉบับ
- [ ] ทดสอบบน Flash Drive ลูกใหม่ที่ความจุ >= Image
