package com.pcopi.usbimager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** The four features are separate screens; do not present future tools as functional. */
class DashboardActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusLabel: TextView
    private val refresh = object : Runnable {
        override fun run() {
            val writer = UsbOperationService.snapshot
            val backup = UsbBackupService.snapshot
            val usb = getSystemService(USB_SERVICE) as UsbManager
            val count = usb.deviceList.values.count { UsbRawDevice.isSupportedMassStorage(it) }
            statusLabel.text = when {
                writer.running -> "กำลังเขียน / ตรวจสอบ Image • " + writer.stage
                backup.running -> "กำลังสำรอง Flash Drive • " + backup.stage
                count > 0 -> "พบ Flash Drive " + count + " ตัว • พร้อมตรวจสอบ"
                else -> "ยังไม่ได้เชื่อมต่อ Flash Drive"
            }
            handler.postDelayed(this, 1200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val prefs = getSharedPreferences(UsbBackupService.PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(UsbBackupService.PREF_ACTIVE, false) && !UsbBackupService.snapshot.running) {
            prefs.edit().putBoolean(UsbBackupService.PREF_ACTIVE, false).apply()
            AlertDialog.Builder(this)
                .setTitle("ตรวจพบการสำรองที่หยุดกะทันหัน")
                .setMessage("ไฟล์ .img ที่สร้างค้างอาจไม่สมบูรณ์ กรุณาลบไฟล์นั้นก่อนสร้าง Image ใหม่ อย่าใช้ไฟล์ที่ยังไม่ได้ Verify")
                .setPositiveButton("เข้าใจแล้ว", null).show()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ToolkitUi.BG)
            clipToPadding = false
            isFillViewport = true
        }
        ToolkitUi.applySafeInsets(scroll, this)
        val root = ToolkitUi.column(this)
        scroll.addView(root)

        root.addView(ToolkitUi.text(this, "USB TOOLKIT", 12f, ToolkitUi.BLUE, true))
        root.addView(ToolkitUi.text(this, "USB Image Writer", 29f, ToolkitUi.INK, true), ToolkitUi.margin(this, 3))
        root.addView(ToolkitUi.text(this, "จัดการ Flash Drive ได้จากมือถือเครื่องเดียว", 14f, ToolkitUi.SUB))
        val version = ToolkitUi.text(this, "v${packageManager.getPackageInfo(packageName, 0).versionName}  •  Flash Drive Manager", 12f, ToolkitUi.SUB)
        root.addView(version, ToolkitUi.margin(this, 5, 20))

        val status = ToolkitUi.card(this)
        val statusTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusTop.addView(ToolkitUi.text(this, "◉", 21f, ToolkitUi.GREEN, true))
        val statusTitle = ToolkitUi.text(this, "  สถานะ USB", 17f, ToolkitUi.INK, true)
        statusTop.addView(statusTitle)
        status.addView(statusTop)
        statusLabel = ToolkitUi.text(this, "กำลังตรวจสอบ...", 14f, ToolkitUi.SUB)
        status.addView(statusLabel, ToolkitUi.margin(this, 8))
        root.addView(status, ToolkitUi.margin(this, 0, 22))

        root.addView(ToolkitUi.text(this, "เครื่องมือของคุณ", 20f, ToolkitUi.INK, true), ToolkitUi.margin(this, 0, 10))

        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addTile(firstRow, "↧", "เขียน Image", "นำ .img ลง USB\nพร้อม Verify", ToolkitUi.BLUE, false) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        addTile(firstRow, "▣", "สร้าง Image", "USB → ไฟล์ .img\nFull Raw Backup", ToolkitUi.GREEN, false) {
            startActivity(Intent(this, BackupActivity::class.java))
        }
        root.addView(firstRow, ToolkitUi.margin(this, 0, 12))

        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addTile(secondRow, "⇄", "Smart Rebuild", "ตรวจ EXT4\nเตรียม USB 14 GB", ToolkitUi.VIOLET, false) {
            startActivity(Intent(this, SmartRebuildActivity::class.java))
        }
        addTile(secondRow, "⊞", "สำรวจ USB Boot", "ดูไฟล์ / Arc Loader\nไม่ต้อง Format", ToolkitUi.AMBER, false) {
            startActivity(Intent(this, BootExplorerActivity::class.java))
        }
        root.addView(secondRow)

        val note = ToolkitUi.card(this)
        note.addView(ToolkitUi.text(this, "ความปลอดภัยของข้อมูล", 16f, ToolkitUi.INK, true))
        note.addView(
            ToolkitUi.text(
                this,
                "• เขียน Image: ข้อมูลเดิมบน USB ปลายทางจะถูกแทนที่\n" +
                    "• สร้าง Image: อ่านจาก USB โดยไม่แก้ข้อมูลต้นฉบับ\n" +
                    "• เมื่อ Samsung ขอให้ฟอร์แมต USB ที่เขียน Arc Loader ให้เลือก “ยกเลิก”",
                13f, ToolkitUi.SUB
            ),
            ToolkitUi.margin(this, 8)
        )
        root.addView(note, ToolkitUi.margin(this, 22, 16))
        setContentView(scroll)
    }

    private fun addTile(
        row: LinearLayout, symbol: String, title: String, subtitle: String,
        tint: Int, upcoming: Boolean, click: () -> Unit
    ) {
        val card = ToolkitUi.card(this).apply {
            minimumHeight = ToolkitUi.dp(this@DashboardActivity, 169)
            setOnClickListener { click() }
            isClickable = true
        }
        val icon = ToolkitUi.text(this, symbol, 29f, tint, true)
        card.addView(icon)
        card.addView(ToolkitUi.text(this, title, 16f, ToolkitUi.INK, true), ToolkitUi.margin(this, 8))
        card.addView(ToolkitUi.text(this, subtitle, 12f, ToolkitUi.SUB), ToolkitUi.margin(this, 5))
        if (upcoming) {
            card.addView(ToolkitUi.text(this, "เร็ว ๆ นี้", 11f, tint, true), ToolkitUi.margin(this, 9))
        }
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.marginEnd = ToolkitUi.dp(this, 6)
        lp.marginStart = ToolkitUi.dp(this, 6)
        row.addView(card, lp)
    }

    private fun comingSoon(title: String, description: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(description)
            .setPositiveButton("ตกลง", null).show()
    }
}