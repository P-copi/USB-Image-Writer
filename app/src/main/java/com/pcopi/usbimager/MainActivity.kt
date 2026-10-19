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
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.pcopi.usbimager.USB_PERMISSION"
        private const val REQ_OPEN_IMAGE = 1001
        private const val REQ_NOTIFICATION = 1002
    }

    private lateinit var usbManager: UsbManager
    private val executor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var imageUri: Uri? = null
    private var imageName: String = ""
    private var imageSize: Long = 0L

    private var usbDevices: List<UsbDevice> = emptyList()
    private var selectedUsb: UsbDevice? = null
    private var inspectedCapacity: Long = 0L
    private var inspectedBlockSize: Int = 0
    private var pendingAfterPermission: (() -> Unit)? = null
    private var localBusy = false
    private var serviceStartRequested = false

    private lateinit var imageInfo: TextView
    private lateinit var usbSpinner: Spinner
    private lateinit var usbInfo: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTitle: TextView
    private lateinit var progressDetail: TextView
    private lateinit var resultText: TextView
    private lateinit var chooseImageButton: Button
    private lateinit var refreshUsbButton: Button
    private lateinit var inspectUsbButton: Button
    private lateinit var writeButton: Button

    private val progressPoller = object : Runnable {
        override fun run() {
            applyServiceSnapshot()
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val device = getUsbDeviceExtra(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        val action = pendingAfterPermission
                        pendingAfterPermission = null
                        action?.invoke()
                    } else {
                        pendingAfterPermission = null
                        toast("ไม่ได้รับสิทธิ์เข้าถึง USB")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (!operationRunning()) refreshUsbList()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val removed = getUsbDeviceExtra(intent)
                    if (removed != null && selectedUsb?.deviceId == removed.deviceId) {
                        if (!operationRunning()) {
                            selectedUsb = null
                            inspectedCapacity = 0L
                            inspectedBlockSize = 0
                            refreshUsbList()
                        }
                        toast("Flash Drive ถูกถอดออก")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        buildUi()
        registerUsbReceiver()
        requestNotificationPermissionIfNeeded()
        refreshUsbList()
        showInterruptedWarningIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.removeCallbacks(progressPoller)
        uiHandler.post(progressPoller)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(progressPoller)
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        executor.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (operationRunning()) {
            toast("งานเขียน/Verify จะทำต่อในเบื้องหลัง")
        }
        super.onBackPressed()
    }

    private fun buildUi() {
        val bg = Color.rgb(246, 247, 249)
        val primary = Color.rgb(29, 78, 216)
        val warning = Color.rgb(180, 83, 9)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
            setBackgroundColor(bg)
        }

        root.addView(TextView(this).apply {
            text = "USB Image Writer"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(17, 24, 39))
        })
        root.addView(TextView(this).apply {
            text = "v0.2.4 • Image Writer"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(4), 0, dp(18))
        })

        root.addView(sectionTitle("1. เลือกไฟล์ Image (.img หรือ .img.gz)"))
        chooseImageButton = actionButton("เลือกไฟล์ .img / .img.gz", primary) { chooseImage() }
        root.addView(chooseImageButton)
        imageInfo = infoText("ยังไม่ได้เลือกไฟล์")
        root.addView(imageInfo)

        root.addView(sectionTitle("2. เลือก Flash Drive"))
        usbSpinner = Spinner(this)
        root.addView(usbSpinner, matchWrap())

        val usbButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        refreshUsbButton = actionButton("รีเฟรช USB", Color.rgb(75, 85, 99)) { refreshUsbList() }
        inspectUsbButton = actionButton("ตรวจสอบ USB", primary) { inspectSelectedUsb() }
        usbButtons.addView(refreshUsbButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        usbButtons.addView(inspectUsbButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(usbButtons)
        usbInfo = infoText("ยังไม่ได้ตรวจสอบความจุ")
        root.addView(usbInfo)

        root.addView(sectionTitle("3. เขียนและตรวจสอบ"))
        writeButton = actionButton("เขียน Image ลง Flash Drive", warning) { confirmWriteStep1() }
        root.addView(writeButton)

        progressTitle = TextView(this).apply {
            text = "พร้อมใช้งาน"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(6))
        }
        root.addView(progressTitle)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
        }
        root.addView(progressBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)))

        progressDetail = infoText("0%")
        root.addView(progressDetail)

        resultText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(31, 41, 55))
            setPadding(0, dp(10), 0, 0)
            setTextIsSelectable(true)
        }
        root.addView(resultText)

        root.addView(TextView(this).apply {
            text = "ระหว่างเขียนสามารถกด Home เปิดแอปอื่น รับสาย หรือดับหน้าจอได้ งานจะทำต่อในเบื้องหลัง\n\n" +
                "คำเตือน: ห้ามถอด Flash Drive จนกว่า Verify จะผ่าน และถ้า Android/Samsung แจ้งให้ฟอร์แมต Arc Loader ให้กด ‘ยกเลิก’"
            textSize = 13f
            setTextColor(Color.rgb(153, 27, 27))
            setPadding(0, dp(22), 0, dp(10))
        })

        val scroll = ScrollView(this).apply {
            addView(root)
            clipToPadding = false
        }
        ToolkitUi.applySafeInsets(scroll, this, side = 0, top = 0, bottom = 0)
        setContentView(scroll)
    }

    private fun chooseImage() {
        if (controlsLocked()) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQ_OPEN_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_OPEN_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val meta = queryImageMeta(uri)
        if (!meta.first.lowercase().endsWith(".img") && !CompressedImage.isCompressed(meta.first)) {
            showMessage("ไฟล์ไม่รองรับ", "เลือกไฟล์ .img หรือ .img.gz เท่านั้น")
            return
        }
        if (meta.second <= 0L) {
            toast("ไม่สามารถอ่านขนาดไฟล์ Image ได้")
            return
        }

        imageUri = uri
        imageName = meta.first
        imageSize = meta.second
         imageInfo.text = "$imageName\nขนาดไฟล์ ${FormatUtils.bytes(imageSize)}" +
            (if (CompressedImage.isCompressed(imageName)) "\nจะตรวจสอบขนาดหลังคลายบีบอัดก่อนเขียนจริง" else "")
        resultText.text = ""
    }

    private fun queryImageMeta(uri: Uri): Pair<String, Long> {
        var name = "image.img"
        var size = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        if (size <= 0L) {
            size = runCatching { contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        }
        return name to size
    }

    private fun refreshUsbList() {
        if (controlsLocked()) return
        usbDevices = usbManager.deviceList.values
            .filter { UsbRawDevice.isSupportedMassStorage(it) }
            .sortedBy { it.deviceId }

        val labels = if (usbDevices.isEmpty()) {
            listOf("ไม่พบ Flash Drive ที่รองรับ")
        } else {
            usbDevices.mapIndexed { index, d ->
                val product = runCatching { d.productName }.getOrNull().orEmpty().ifBlank { "USB Mass Storage" }
                "${index + 1}. $product  [${hex(d.vendorId)}:${hex(d.productId)}]"
            }
        }

        usbSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        usbSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedUsb = usbDevices.getOrNull(position)
                inspectedCapacity = 0L
                inspectedBlockSize = 0
                usbInfo.text = if (selectedUsb == null) "ยังไม่ได้ตรวจสอบความจุ" else "เลือกแล้ว • กด “ตรวจสอบ USB”"
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        if (usbDevices.isEmpty()) selectedUsb = null
        updateControls()
    }

    private fun inspectSelectedUsb() {
        if (controlsLocked()) return
        val device = selectedUsb ?: run {
            toast("ยังไม่พบ Flash Drive")
            return
        }
        withUsbPermission(device) {
            setLocalBusy(true, "กำลังตรวจสอบ USB...")
            executor.execute {
                try {
                    UsbRawDevice.open(usbManager, device).use { raw ->
                        raw.init()
                        val capacity = raw.capacityBytes
                        val blockSize = raw.blockSize
                        runOnUiThread {
                            inspectedCapacity = capacity
                            inspectedBlockSize = blockSize
                            usbInfo.text = "ความจุ ${FormatUtils.bytes(capacity)} • Block $blockSize B\n${usbIdentity(device)}"
                            setLocalBusy(false, "พร้อมใช้งาน")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        setLocalBusy(false, "ตรวจสอบ USB ไม่สำเร็จ")
                        showError(e)
                    }
                }
            }
        }
    }

    private fun confirmWriteStep1() {
        if (controlsLocked()) return
        val uri = imageUri ?: run {
            toast("กรุณาเลือกไฟล์ .img หรือ .img.gz ก่อน")
            return
        }
        val device = selectedUsb ?: run {
            toast("กรุณาเลือก Flash Drive ก่อน")
            return
        }
        if (inspectedCapacity <= 0L) {
            toast("กรุณากด “ตรวจสอบ USB” ก่อน")
            return
        }
        if (!CompressedImage.isCompressed(imageName) && imageSize > inspectedCapacity) {
            showMessage("ความจุไม่พอ", "Image ${FormatUtils.bytes(imageSize)} ใหญ่กว่า Flash Drive ${FormatUtils.bytes(inspectedCapacity)}")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("ตรวจสอบก่อนเขียน")
            .setMessage(
                "ไฟล์: $imageName\n" +
                    "ขนาดไฟล์: ${FormatUtils.bytes(imageSize)}\n" +
                    (if (CompressedImage.isCompressed(imageName)) "จะตรวจขนาดจริงและความถูกต้องของ .img.gz ก่อนเริ่มเขียน\n" else "") + "\n" +
                    "ปลายทาง: ${usbIdentity(device)}\n" +
                    "ความจุ: ${FormatUtils.bytes(inspectedCapacity)}\n\n" +
                    "ข้อมูลเดิมใน Flash Drive จะถูกลบทั้งหมด"
            )
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("ดำเนินการต่อ") { _, _ -> confirmWriteStep2(uri, device) }
            .show()
    }

    private fun confirmWriteStep2(uri: Uri, device: UsbDevice) {
        AlertDialog.Builder(this)
            .setTitle("ยืนยันครั้งสุดท้าย")
            .setMessage(
                "หลังเริ่มเขียน ห้ามถอด Flash Drive จนกว่าจะ Verify ผ่าน\n\n" +
                    "สามารถกด Home เปิดแอปอื่น ดับหน้าจอ หรือรับสายโทรศัพท์ได้ งานจะทำต่อในเบื้องหลัง"
            )
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("เขียน Image") { _, _ -> startWriteService(uri, device) }
            .show()
    }

    private fun startWriteService(uri: Uri, device: UsbDevice) {
        withUsbPermission(device) {
            resultText.text = ""
            serviceStartRequested = true
            progressTitle.text = "กำลังเริ่ม Background Service..."
            updateControls()

            val serviceIntent = Intent(this, UsbOperationService::class.java).apply {
                action = UsbOperationService.ACTION_START_WRITE
                putExtra(UsbOperationService.EXTRA_IMAGE_URI, uri.toString())
                putExtra(UsbOperationService.EXTRA_IMAGE_NAME, imageName)
                putExtra(UsbOperationService.EXTRA_IMAGE_SIZE, imageSize)
                putExtra(UsbOperationService.EXTRA_USB_CAPACITY, inspectedCapacity)
                putExtra(UsbOperationService.EXTRA_USB_DEVICE, device)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startForegroundService(serviceIntent)
                uiHandler.postDelayed({
                    if (serviceStartRequested && !UsbOperationService.snapshot.running) {
                        serviceStartRequested = false
                        updateControls()
                        showMessage("เริ่มงานไม่สำเร็จ", "Background Service ไม่เริ่มทำงาน กรุณาลองใหม่")
                    }
                }, 4000L)
            } catch (e: Exception) {
                serviceStartRequested = false
                updateControls()
                showError(e)
            }
        }
    }

    private fun applyServiceSnapshot() {
        val s = UsbOperationService.snapshot
        if (s.running) serviceStartRequested = false

        if (s.running || s.result != null || s.error != null) {
            progressTitle.text = s.stage
            if (s.totalBytes > 0L) {
                val percent = (s.completedBytes * 100.0 / s.totalBytes).coerceIn(0.0, 100.0)
                progressBar.progress = ((percent / 100.0) * progressBar.max).toInt()
                progressDetail.text = if (s.running) {
                    String.format(
                        Locale.US,
                        "%.1f%% • %s / %s • %s • %s",
                        percent,
                        FormatUtils.bytes(s.completedBytes),
                        FormatUtils.bytes(s.totalBytes),
                        FormatUtils.speed(s.bytesPerSecond),
                        FormatUtils.eta(s.completedBytes, s.totalBytes, s.bytesPerSecond)
                    )
                } else {
                    String.format(Locale.US, "%.1f%% • %s", percent, FormatUtils.bytes(s.completedBytes))
                }
            }

            s.result?.let { r ->
                progressBar.progress = progressBar.max
                progressDetail.text = "100% • ${FormatUtils.bytes(r.bytesWritten)}"
                resultText.text =
                    "SHA-256 Image\n${r.sourceSha256}\n\n" +
                    "SHA-256 Flash Drive\n${r.deviceSha256}\n\n" +
                    "ผลตรวจสอบ: ตรงกัน 100%\n\n" +
                    "เวลาเขียน ${FormatUtils.duration(r.writeDurationMillis)} • ${FormatUtils.speed(r.writeAverageBytesPerSecond)}\n" +
                    "เวลา Verify ${FormatUtils.duration(r.verifyDurationMillis)} • ${FormatUtils.speed(r.verifyAverageBytesPerSecond)}\n\n" +
                    "หาก Samsung ถามให้ฟอร์แมต Arc Loader ให้กด ‘ยกเลิก’"
            }
            if (s.error != null) {
                resultText.text = "งานไม่สำเร็จ\n${s.error}\n\nFlash Drive มักไม่ได้เสีย แต่ Image อาจไม่สมบูรณ์ กรุณาเขียนใหม่ตั้งแต่ต้น"
            }
        }
        updateControls()
    }

    private fun setLocalBusy(value: Boolean, title: String) {
        localBusy = value
        if (!operationRunning()) progressTitle.text = title
        if (!value && title == "พร้อมใช้งาน" && UsbOperationService.snapshot.result == null) {
            progressBar.progress = 0
            progressDetail.text = "0%"
        }
        updateControls()
    }

    private fun controlsLocked(): Boolean = localBusy || operationRunning() || serviceStartRequested

    private fun operationRunning(): Boolean = UsbOperationService.snapshot.running || UsbBackupService.snapshot.running

    private fun updateControls() {
        val locked = controlsLocked()
        chooseImageButton.isEnabled = !locked
        refreshUsbButton.isEnabled = !locked
        inspectUsbButton.isEnabled = !locked
        writeButton.isEnabled = !locked
        usbSpinner.isEnabled = !locked
    }

    private fun withUsbPermission(device: UsbDevice, action: () -> Unit) {
        if (usbManager.hasPermission(device)) {
            action()
            return
        }
        pendingAfterPermission = action
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        val pi = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, pi)
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
        }
    }

    private fun showInterruptedWarningIfNeeded() {
        val prefs = getSharedPreferences(UsbOperationService.PREFS, Context.MODE_PRIVATE)
        val wasActive = prefs.getBoolean(UsbOperationService.PREF_ACTIVE, false)
        if (wasActive && !UsbOperationService.snapshot.running) {
            val previousName = prefs.getString(UsbOperationService.PREF_IMAGE_NAME, "Image").orEmpty()
            prefs.edit().putBoolean(UsbOperationService.PREF_ACTIVE, false).apply()
            AlertDialog.Builder(this)
                .setTitle("พบงานก่อนหน้าที่หยุดกะทันหัน")
                .setMessage(
                    "$previousName อาจเขียนไม่ครบ\n\n" +
                        "Flash Drive โดยทั่วไปไม่ได้เสีย แต่ข้อมูลบนไดรฟ์อาจไม่สมบูรณ์ กรุณาเขียน Image ใหม่ตั้งแต่ต้นและรอจน Verify ผ่าน"
                )
                .setPositiveButton("ตกลง", null)
                .show()
        }
    }

    private fun usbIdentity(d: UsbDevice): String {
        val maker = runCatching { d.manufacturerName }.getOrNull().orEmpty()
        val product = runCatching { d.productName }.getOrNull().orEmpty()
        val pretty = listOf(maker, product).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "USB Mass Storage" }
        return "$pretty [${hex(d.vendorId)}:${hex(d.productId)}]"
    }

    private fun getUsbDeviceExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(31, 41, 55))
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun infoText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(75, 85, 99))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun actionButton(text: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        setOnClickListener { onClick() }
        isAllCaps = false
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun hex(v: Int): String = String.format("%04X", v)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun showError(e: Throwable) {
        showMessage("เกิดข้อผิดพลาด", e.message ?: e.javaClass.simpleName)
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("ตกลง", null)
            .show()
    }
}
