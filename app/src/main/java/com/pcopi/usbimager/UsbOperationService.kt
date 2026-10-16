package com.pcopi.usbimager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class UsbOperationService : Service() {

    data class Snapshot(
        val running: Boolean = false,
        val stage: String = "พร้อมใช้งาน",
        val completedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val bytesPerSecond: Double = 0.0,
        val imageName: String = "",
        val result: ImageWriter.Result? = null,
        val error: String? = null
    )

    companion object {
        const val ACTION_START_WRITE = "com.pcopi.usbimager.action.START_WRITE"
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_IMAGE_NAME = "image_name"
        const val EXTRA_IMAGE_SIZE = "image_size"
        const val EXTRA_USB_DEVICE = "usb_device"

        const val PREFS = "usb_operation_state"
        const val PREF_ACTIVE = "operation_active"
        const val PREF_IMAGE_NAME = "operation_image_name"

        private const val CHANNEL_ID = "usb_image_operation"
        private const val NOTIFICATION_ID = 4101

        @Volatile
        var snapshot: Snapshot = Snapshot()
            private set
    }

    private lateinit var usbManager: UsbManager
    private lateinit var notificationManager: NotificationManager
    private val executor = Executors.newSingleThreadExecutor()
    private val cancelFlag = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationAt = 0L
    private var receiverRegistered = false

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED && snapshot.running) {
                cancelFlag.set(true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        registerDetachReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START_WRITE) return START_NOT_STICKY

        startForegroundCompat(buildNotification("กำลังเตรียมเขียน Image...", 0, true))

        if (snapshot.running) return START_NOT_STICKY

        val uri = intent.getStringExtra(EXTRA_IMAGE_URI)?.let(Uri::parse)
        val imageName = intent.getStringExtra(EXTRA_IMAGE_NAME).orEmpty()
        val imageSize = intent.getLongExtra(EXTRA_IMAGE_SIZE, 0L)
        val device = getUsbDevice(intent)

        if (uri == null || imageSize <= 0L || device == null) {
            finishWithError("ข้อมูลสำหรับเริ่มงานไม่ครบ")
            return START_NOT_STICKY
        }

        cancelFlag.set(false)
        snapshot = Snapshot(
            running = true,
            stage = "กำลังเตรียมเขียน Image...",
            totalBytes = imageSize,
            imageName = imageName
        )
        markOperationActive(imageName)
        acquireWakeLock()

        executor.execute {
            try {
                UsbRawDevice.open(usbManager, device).use { raw ->
                    raw.init()
                    if (imageSize > raw.capacityBytes) {
                        throw IllegalStateException("Flash Drive เล็กกว่าไฟล์ Image")
                    }

                    val result = ImageWriter.writeAndVerify(
                        contentResolver,
                        uri,
                        imageSize,
                        raw,
                        onProgress = { p -> updateProgress(imageName, p) },
                        isCancelled = { cancelFlag.get() }
                    )

                    snapshot = Snapshot(
                        running = false,
                        stage = "สำเร็จ • Verify ผ่าน",
                        completedBytes = result.bytesWritten,
                        totalBytes = result.bytesWritten,
                        imageName = imageName,
                        result = result
                    )
                    clearOperationActive()
                    stopForegroundCompat()
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification("เขียนสำเร็จ • Verify ผ่าน", 100, false)
                    )
                    stopSelf()
                }
            } catch (e: Exception) {
                finishWithError(e.message ?: e.javaClass.simpleName)
            } finally {
                releaseWakeLock()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelFlag.set(true)
        if (snapshot.running) {
            snapshot = snapshot.copy(
                running = false,
                stage = "งานถูกหยุดก่อนเสร็จ",
                error = "งานอาจไม่สมบูรณ์ กรุณาเขียน Image ใหม่ตั้งแต่ต้น"
            )
        }
        releaseWakeLock()
        runCatching { if (receiverRegistered) unregisterReceiver(detachReceiver) }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateProgress(imageName: String, p: ImageWriter.Progress) {
        val stageText = when (p.stage) {
            ImageWriter.Stage.WRITE -> "กำลังเขียน Image..."
            ImageWriter.Stage.VERIFY -> "กำลัง Verify หลังเขียน..."
        }
        snapshot = Snapshot(
            running = true,
            stage = stageText,
            completedBytes = p.completedBytes,
            totalBytes = p.totalBytes,
            bytesPerSecond = p.bytesPerSecond,
            imageName = imageName
        )

        val now = System.currentTimeMillis()
        if (now - lastNotificationAt >= 750L || p.completedBytes >= p.totalBytes) {
            lastNotificationAt = now
            val percent = if (p.totalBytes <= 0L) 0 else
                ((p.completedBytes * 100L) / p.totalBytes).toInt().coerceIn(0, 100)
            val eta = FormatUtils.eta(p.completedBytes, p.totalBytes, p.bytesPerSecond)
            val text = "$stageText $percent% • ${FormatUtils.speed(p.bytesPerSecond)} • $eta"
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text, percent, true))
        }
    }

    private fun finishWithError(message: String) {
        snapshot = snapshot.copy(
            running = false,
            stage = "ไม่สำเร็จ",
            error = message,
            result = null
        )
        clearOperationActive()
        stopForegroundCompat()
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification("งานไม่สำเร็จ • $message", 0, false)
        )
        stopSelf()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "USBImageWriter::WriteVerify").apply {
            setReferenceCounted(false)
            acquire(6L * 60L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "USB Image Writer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "สถานะการเขียนและตรวจสอบ Flash Drive"
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, percent: Int, ongoing: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_usb)
            .setContentTitle(if (snapshot.imageName.isBlank()) "USB Image Writer" else snapshot.imageName)
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setAutoCancel(!ongoing)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun registerDetachReceiver() {
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(detachReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(detachReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun markOperationActive(imageName: String) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ACTIVE, true)
            .putString(PREF_IMAGE_NAME, imageName)
            .apply()
    }

    private fun clearOperationActive() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ACTIVE, false)
            .apply()
    }

    private fun getUsbDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_USB_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_USB_DEVICE)
        }
}
