# BYD GPack Installer Probe 0.2.3

แอปทดสอบการเปิด AppInstallerActivity จากภายนอกของ GPack 2.20260321.01

**ไม่ใช่การติดตั้งอัตโนมัติ** ต้องยืนยันผู้ใช้ ไม่แก้ไฟล์ GPack หรือระบบรถ

1. ติดตั้งไฟล์ probe-debug.apk บนจอรถ Android หลัก
2. เมื่อจอดรถ กด 1 ตรวจ GPack installer
3. กด 2 ลองเปิด AppInstaller โดยส่ง APK ขนาดเล็กที่ฝังมาภายในตัวทดสอบ
4. ดูหน้าที่ปรากฏว่าอยู่ภายใน GPack หรือ Android หลัก (ห้ามยืนยันหากมีข้อความแก้ไข GPack หรือสิทธิ์ระดับระบบ)
5. ถ้าแอปติดตั้งภายใน GPack จริง เปิดไอคอน BYD Mini Bridge TEST แล้วทดลองกด A/B/C/D เพื่อเปิด Maps พร้อมพิกัด
6. คัดลอกรายงานจาก Probe ส่งมาให้วิเคราะห์

**ควรหยุดทดสอบหากมีหน้าต่างให้สิทธิ์ระบบที่ผิดปกติหรือขอรูท**

Action VIEW กับ INSTALL_PACKAGE อาศัย exported=true ของ AppInstallerActivity จาก Manifest แต่ยังไม่ยืนยันว่าติดตั้งแอปภายใน VirtualApp สำเร็จ
