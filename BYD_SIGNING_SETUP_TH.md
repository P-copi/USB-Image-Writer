# ตั้งค่าเซ็น BYD Phone Space แบบถาวร (V0.4.3 เป็นต้นไป)

## ทำไมต้องแก้
GitHub Actions เดิมใช้ Android debug signing key ที่ไม่คงที่ ใบรับรองของ APK หลายเวอร์ชันจึงไม่ตรงกัน Android ไม่ยอมติดตั้งทับแอปเดิม

## เก็บกุญแจอย่างปลอดภัย
- มีไฟล์ส่วนตัวจาก ChatGPT ชื่อ `BYD-Phone-Space-Signing-Backup.zip` โดยในไฟล์มี `.p12` และค่า GitHub Actions secrets สองตัว
- เก็บไฟล์สำรองนี้อย่างปลอดภัย ไม่อัปโหลดลง GitHub repository เพราะ repository เป็นสาธารณะ
- เปิด `https://github.com/P-copi/USB-Image-Writer/settings/secrets/actions` ขณะลงชื่อเข้าใช้ GitHub
- เพิ่ม Repository secret ชื่อ `BYD_SIGNING_KEYSTORE_BASE64` โดยใช้ข้อความทั้งหมดใน `BYD-SIGNING-KEYSTORE-BASE64.txt`
- เพิ่ม Repository secret ชื่อ `BYD_SIGNING_PASSWORD` โดยใช้ข้อความใน `BYD-SIGNING-PASSWORD.txt`
- อย่าใส่กุญแจลงใน repository, issue, commit หรือแชตสาธารณะ

## Build
หลังตั้ง Secrets ทั้งสองแล้ว เปิดแท็บ Actions → Build BYD Phone Space Stable-Signed APK → Run workflow บน branch `feature/byd-phone-space-v0.1`.
เมื่อสำเร็จให้ดาวน์โหลด artifact `BYDPhoneSpace-stable-signed`. Build จะตรวจใบรับรอง SHA-256 ทุกครั้งก่อนปล่อย APK

## ข้อจำกัดของการเปลี่ยนกุญแจ
APK ที่ติดตั้งด้วย debug certificate เก่าไม่สามารถรับการอัปเดตที่เซ็นด้วยกุญแจใหม่ได้โดยตรง จำเป็นต้องเก็บข้อมูลสำคัญจากแอปเดิมก่อน แล้วจึงลบ APK เดิมเพื่อเปลี่ยนไปใช้สายเซ็นถาวรหนึ่งครั้ง หลังจากนั้นใช้ APK ที่เซ็นด้วยกุญแจถาวรนี้ติดตั้งทับกันได้ (เมื่อ versionCode เพิ่มขึ้น)

**ห้ามลบเวอร์ชันเดิมก่อนสำรองข้อมูลรูป/เส้นทางสำคัญ**: ไฟล์รูปของแอปอยู่ใน Android app-specific external storage; การลบแอปอาจลบไฟล์เหล่านั้น และข้อมูลชุดเส้นทางใน SharedPreferences เป็นข้อมูลภายในแอป

หากกุญแจ `.p12` สูญหายและ GitHub Actions Secrets ถูกลบ จะไม่สามารถเซ็น APK ใหม่ให้เข้ากับแอปที่ติดตั้งด้วยใบรับรองนี้ได้อีก
