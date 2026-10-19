package com.pcopi.usbimager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class BackupActivity : Activity() {
    companion object {
        private const val ACTION_USB_PERMISSION = "com.pcopi.usbimager.USB_BACKUP_PERMISSION"
        private const val REQ_CREATE_IMAGE = 2201
        private const val REQ_NOTIFICATIONS = 2202
    }

    private lateinit var manager: UsbManager
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var devices: List<UsbDevice> = emptyList()
    private var selected: UsbDevice? = null
    private var capacity: Long = 0
    private var blockSize: Int = 0
    private var busy = false
    private var startRequested = false
    private var pendingPermission: (() -> Unit)? = null
    private lateinit var spinner: Spinner
    private lateinit var details: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var result: TextView
    private lateinit var refreshButton: android.widget.Button
    private lateinit var inspectButton: android.widget.Button
    private lateinit var backupButton: android.widget.Button
    private lateinit var cancelButton: android.widget.Button

    private val poll = object : Runnable {
        override fun run() {
            applySnapshot()
            handler.postDelayed(this, 500L)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val action = pendingPermission
                    pendingPermission = null
                    if (granted) action?.invoke() else toast("ไม่ได้รับสิทธิ์ USB")
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (!locked()) refreshDevices()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.USB_SERVICE) as UsbManager
        buildUi()
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, filter)
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
        refreshDevices()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(usbReceiver) }
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(ToolkitUi.BG)
            clipToPadding = false
        }
        ToolkitUi.applySafeInsets(scroll, this)
        val root = ToolkitUi.column(this)
        scroll.addView(root)
        root.addView(ToolkitUi.text(this, "‹  กลับหน้าแรก", 15f, ToolkitUi.BLUE, true).apply {
            setOnClickListener { finish() }
        })
        root.addView(ToolkitUi.text(this, "สร้าง Image", 28f, ToolkitUi.INK, true), ToolkitUi.margin(this, 16))
        root.addView(ToolkitUi.text(this, "สำรอง Flash Drive ทั้งลูกเป็นไฟล์ .img โดยไม่แก้ไข USB ต้นฉบับ", 14f, ToolkitUi.SUB),
            ToolkitUi.margin(this, 5, 17))

        val deviceCard = ToolkitUi.card(this)
        deviceCard.addView(ToolkitUi.text(this, "1   เลือก Flash Drive ต้นฉบับ", 17f, ToolkitUi.INK, true))
        spinner = Spinner(this)
        deviceCard.addView(spinner, ToolkitUi.margin(this, 12, 8))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        refreshButton = ToolkitUi.button(this, "รีเฟรช", ToolkitUi.SUB) { refreshDevices() }
        inspectButton = ToolkitUi.button(this, "ตรวจสอบ USB", ToolkitUi.BLUE) { inspectDevice() }
        buttons.addView(refreshButton, LinearLayout.LayoutParams(0, ToolkitUi.dp(this, 52), 1f))
        buttons.addView(inspectButton, LinearLayout.LayoutParams(0, ToolkitUi.dp(this, 52), 1.25f))
        deviceCard.addView(buttons)
        details = ToolkitUi.text(this, "ยังไม่ได้ตรวจความจุ", 14f, ToolkitUi.SUB)
        deviceCard.addView(details, ToolkitUi.margin(this, 12))
        root.addView(deviceCard, ToolkitUi.margin(this, 0, 15))

        val backupCard = ToolkitUi.card(this)
        backupCard.addView(ToolkitUi.text(this, "2   เลือกที่เก็บไฟล์ Image", 17f, ToolkitUi.INK, true))
        backupCard.addView(ToolkitUi.text(
            this, "Full Raw Backup จะสร้างไฟล์เท่าความจุจริงของ USB เช่น USB 16 GB จะได้ Image ประมาณ 16 GB กรุณาเลือกพื้นที่โทรศัพท์ที่ว่างเพียงพอ ไม่ใช่ USB ต้นฉบับ",
            13f, ToolkitUi.SUB
        ), ToolkitUi.margin(this, 8, 12))
        backupButton = ToolkitUi.button(this, "สร้างไฟล์ .img", ToolkitUi.GREEN) { confirmBackup() }
        backupCard.addView(backupButton)
        cancelButton = ToolkitUi.button(this, "ยกเลิกงานสำรอง", ToolkitUi.AMBER) { cancelBackup() }
        backupCard.addView(cancelButton, ToolkitUi.margin(this, 8))
        root.addView(backupCard)

        val stateCard = ToolkitUi.card(this)
        status = ToolkitUi.text(this, "พร้อมเริ่มสำรองข้อมูล", 16f, ToolkitUi.INK, true)
        stateCard.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        stateCard.addView(progress, ToolkitUi.margin(this, 12))
        progressText = ToolkitUi.text(this, "0%", 13f, ToolkitUi.SUB)
        stateCard.addView(progressText)
        result = ToolkitUi.text(this, "", 13f, ToolkitUi.INK)
        result.setTextIsSelectable(true)
        stateCard.addView(result, ToolkitUi.margin(this, 10))
        root.addView(stateCard, ToolkitUi.margin(this, 15))
        root.addView(ToolkitUi.text(
            this,
            "ระหว่าง Backup ห้ามถอด Flash Drive • สามารถกลับหน้าแรกได้ โดยงานจะทำต่อผ่านการแจ้งเตือน • ถ้าสำรองไม่ครบ ห้ามนำ Image ที่ค้างไปเขียน USB",
            13f, ToolkitUi.SUB
        ), ToolkitUi.margin(this, 16))
        setContentView(scroll)
    }

    private fun refreshDevices() {
        if (locked()) return
        devices = manager.deviceList.values.filter { UsbRawDevice.isSupportedMassStorage(it) }.sortedBy { it.deviceId }
        val labels = if (devices.isEmpty()) listOf("ไม่พบ USB Mass Storage") else devices.mapIndexed { i, d ->
            val n = runCatching { d.productName }.getOrNull().orEmpty().ifBlank { "Flash Drive" }
            (i + 1).toString() + ". " + n + " [" + String.format("%04X:%04X", d.vendorId, d.productId) + "]"
        }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selected = devices.getOrNull(position)
                capacity = 0
                blockSize = 0
                details.text = if (selected == null) "ไม่พบ USB" else "เลือกแล้ว • โปรดกดตรวจสอบ USB"
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        updateButtons()
    }

    private fun inspectDevice() {
        if (locked()) return
        val device = selected ?: return toast("กรุณาเลือก Flash Drive")
        withUsbPermission(device) {
            busy = true
            updateButtons()
            status.text = "กำลังตรวจสอบ USB..."
            worker.execute {
                try {
                    UsbRawDevice.open(manager, device).use {
                        it.init()
                        val size = it.capacityBytes
                        val block = it.blockSize
                        runOnUiThread {
                            capacity = size
                            blockSize = block
                            details.text = "ความจุ " + FormatUtils.bytes(size) + " • Block " + block + " B\n" +
                                "Full Image จะมีขนาดประมาณ " + FormatUtils.bytes(size)
                            busy = false
                            status.text = "พร้อมสร้าง Image"
                            updateButtons()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        busy = false
                        updateButtons()
                        AlertDialog.Builder(this).setTitle("ตรวจ USB ไม่สำเร็จ")
                            .setMessage(e.message ?: e.javaClass.simpleName)
                            .setPositiveButton("ตกลง", null).show()
                    }
                }
            }
        }
    }

    private fun confirmBackup() {
        if (locked()) return
        if (selected == null || capacity <= 0) return toast("กรุณาตรวจสอบ Flash Drive ก่อน")
        AlertDialog.Builder(this)
            .setTitle("ยืนยันสร้าง Full Raw Image")
            .setMessage(
                "USB ต้นฉบับ: " + FormatUtils.bytes(capacity) +
                    "\nไฟล์ .img จะมีขนาดใกล้เคียงกันแม้ใช้พื้นที่จริงน้อย\n\n" +
                    "ต้องมีพื้นที่ปลายทางว่างเพียงพอ และไม่ควรบันทึกลง USB ต้นฉบับ\n\n" +
                    "การสำรองนี้อ่านอย่างเดียว ไม่ฟอร์แมตต้นฉบับ"
            )
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("เลือกตำแหน่งบันทึก") { _, _ ->
                val name = "flash_backup_" +
                    SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".img"
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, name)
                }
                startActivityForResult(intent, REQ_CREATE_IMAGE)
            }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CREATE_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (isRemovableOutput(uri)) {
            AlertDialog.Builder(this)
                .setTitle("ตำแหน่งบันทึกไม่ปลอดภัย")
                .setMessage("กรุณาเลือกหน่วยความจำภายในของโทรศัพท์ ไม่ใช่ Flash Drive ที่เสียบอยู่")
                .setPositiveButton("ตกลง", null).show()
            return
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val device = selected ?: return
        withUsbPermission(device) { startBackup(uri, device) }
    }

    private fun isRemovableOutput(uri: Uri): Boolean {
        if (uri.authority != "com.android.externalstorage.documents") return false
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return true
        return !(documentId.startsWith("primary:", true) || documentId.startsWith("home:", true))
    }

    private fun startBackup(uri: Uri, device: UsbDevice) {
        if (locked()) return
        startRequested = true
        status.text = "กำลังเริ่มบริการสำรองข้อมูล..."
        result.text = ""
        updateButtons()
        val serviceIntent = Intent(this, UsbBackupService::class.java).apply {
            action = UsbBackupService.ACTION_START
            putExtra(UsbBackupService.EXTRA_OUTPUT_URI, uri.toString())
            putExtra(UsbBackupService.EXTRA_USB, device)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        try {
            startForegroundService(serviceIntent)
            handler.postDelayed({
                if (startRequested && !UsbBackupService.snapshot.running) {
                    startRequested = false
                    status.text = "บริการสำรองข้อมูลยังไม่เริ่ม"
                    updateButtons()
                }
            }, 4500L)
        } catch (e: Exception) {
            startRequested = false
            status.text = "เริ่มงานไม่สำเร็จ"
            result.text = e.message
            updateButtons()
        }
    }

    private fun cancelBackup() {
        AlertDialog.Builder(this).setTitle("ยกเลิกการสำรอง?")
            .setMessage("ไฟล์ที่เขียนค้างจะถูกลบเมื่อระบบยกเลิกสำเร็จ USB ต้นฉบับจะไม่ถูกแก้ไข")
            .setNegativeButton("ทำต่อ", null)
            .setPositiveButton("ยกเลิกงาน") { _, _ ->
                startService(Intent(this, UsbBackupService::class.java).apply {
                    action = UsbBackupService.ACTION_CANCEL
                })
            }.show()
    }

    private fun applySnapshot() {
        val snap = UsbBackupService.snapshot
        if (snap.running) startRequested = false
        if (snap.running || snap.result != null || snap.error != null) {
            status.text = snap.stage
            if (snap.total > 0) {
                val pct = (snap.completed * 100.0 / snap.total).coerceIn(0.0, 100.0)
                progress.progress = (pct * 10).toInt()
                progressText.text = String.format(Locale.US, "%.1f%% • %s / %s • %s • %s", pct,
                    FormatUtils.bytes(snap.completed), FormatUtils.bytes(snap.total),
                    FormatUtils.speed(snap.bytesPerSecond),
                    FormatUtils.eta(snap.completed, snap.total, snap.bytesPerSecond))
            }
            snap.result?.let {
                progress.progress = 1000
                progressText.text = "100% • " + FormatUtils.bytes(it.bytes)
                result.text = "บันทึก Image สำเร็จ\n\nSHA-256\n" + it.sha256 +
                    "\n\nเวลาอ่าน USB: " + FormatUtils.duration(it.readMillis) +
                    "\nเวลาตรวจสอบไฟล์: " + FormatUtils.duration(it.verifyMillis) +
                    "\n\nVerify ผ่าน: ไฟล์ที่บันทึกตรงกับข้อมูลที่อ่านจาก USB"
            }
            snap.error?.let { result.text = "สำรองไม่สำเร็จ: " + it + "\nห้ามใช้ Image ที่เขียนไม่ครบ" }
        }
        updateButtons()
    }

    private fun locked(): Boolean = busy || startRequested ||
        UsbBackupService.snapshot.running || UsbOperationService.snapshot.running

    private fun updateButtons() {
        val lock = locked()
        spinner.isEnabled = !lock
        refreshButton.isEnabled = !lock
        inspectButton.isEnabled = !lock
        backupButton.isEnabled = !lock
        cancelButton.isEnabled = UsbBackupService.snapshot.running
    }

    private fun withUsbPermission(device: UsbDevice, action: () -> Unit) {
        if (manager.hasPermission(device)) return action()
        pendingPermission = action
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        val pending = PendingIntent.getBroadcast(
            this, 13, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        manager.requestPermission(device, pending)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}