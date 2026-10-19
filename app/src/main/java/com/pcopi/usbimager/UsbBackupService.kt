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
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class UsbBackupService : Service() {
    data class Snapshot(
        val running: Boolean = false,
        val stage: String = "พร้อมใช้งาน",
        val completed: Long = 0,
        val total: Long = 0,
        val bytesPerSecond: Double = 0.0,
        val result: FlashBackup.Result? = null,
        val error: String? = null
    )

    companion object {
        const val ACTION_START = "com.pcopi.usbimager.BACKUP_START"
        const val ACTION_CANCEL = "com.pcopi.usbimager.BACKUP_CANCEL"
        const val EXTRA_OUTPUT_URI = "backup_output_uri"
        const val EXTRA_USB = "backup_usb"
        const val EXTRA_COMPRESSED = "backup_compressed"
        const val PREFS = "usb_backup_state"
        const val PREF_ACTIVE = "active"
        const val PREF_URI = "uri"

        @Volatile var snapshot = Snapshot()
            private set
        private const val CHANNEL = "usb_raw_backup"
        private const val NOTIFICATION_ID = 4102
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private lateinit var manager: UsbManager
    private lateinit var notifications: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var usbId: Int? = null
    private var lastNotification = 0L

    private val detached = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            }
            if (device?.deviceId == usbId && snapshot.running) cancelled.set(true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(Context.USB_SERVICE) as UsbManager
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "สำรอง Flash Drive", NotificationManager.IMPORTANCE_LOW)
        )
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(detached, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(detached, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled.set(true)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || snapshot.running) return START_NOT_STICKY
        startForegroundConnected(notification("กำลังเตรียมสำรอง USB...", 0, true))
        if (UsbOperationService.snapshot.running) {
            fail("มีงานเขียน USB กำลังทำงานอยู่")
            return START_NOT_STICKY
        }
        val uri = intent.getStringExtra(EXTRA_OUTPUT_URI)?.let(Uri::parse)
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_USB, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<UsbDevice>(EXTRA_USB)
        }
        if (uri == null || device == null || !manager.hasPermission(device)) {
            fail("สิทธิ์ USB หรือไฟล์ปลายทางไม่พร้อม")
            return START_NOT_STICKY
        }
        val compressed = intent.getBooleanExtra(EXTRA_COMPRESSED, false)
        usbId = device.deviceId
        cancelled.set(false)
        snapshot = Snapshot(running = true, stage = "กำลังอ่าน Flash Drive...")
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(PREF_ACTIVE, true).putString(PREF_URI, uri.toString()).apply()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "USBImageWriter::Backup").apply {
                setReferenceCounted(false)
                acquire(6L * 60L * 60L * 1000L)
            }
        executor.execute {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND) }
            try {
                val result = FlashBackup.createImage(
                    contentResolver, uri, manager, device, compressed,
                    onProgress = { updateProgress(it) },
                    isCancelled = { cancelled.get() }
                )
                snapshot = Snapshot(
                    running = false, stage = "สร้าง Image สำเร็จ • Verify ผ่าน",
                    completed = result.bytes, total = result.bytes, result = result
                )
                endOperation()
                notifications.notify(NOTIFICATION_ID, notification("Backup สำเร็จ • Verify ผ่าน", 100, false))
                stopSelf()
            } catch (e: Exception) {
                fail(e.message ?: e.javaClass.simpleName)
            } finally {
                releaseWake()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateProgress(p: FlashBackup.Progress) {
        val name = when (p.stage) {
            FlashBackup.Stage.READ_USB -> "กำลังอ่าน USB เพื่อสร้าง Image..."
            FlashBackup.Stage.RECOVER_USB -> "USB สะดุด • กำลังลองอ่านใหม่..."
            FlashBackup.Stage.VERIFY_FILE -> "กำลัง Verify ไฟล์ Image..."
        }
        snapshot = Snapshot(
            running = true, stage = if (p.detail.isNotBlank()) name + "\n" + p.detail else name,
            completed = p.completed, total = p.total,
            bytesPerSecond = p.speed
        )
        val now = System.currentTimeMillis()
        if (now - lastNotification > 900L || p.completed == p.total) {
            lastNotification = now
            val percent = if (p.total > 0) (p.completed * 100 / p.total).toInt().coerceIn(0, 100) else 0
            notifications.notify(
                NOTIFICATION_ID,
                notification(name + " " + percent + "% • " +
                    (if (p.detail.isNotBlank()) p.detail.take(65) else FormatUtils.speed(p.speed)),
                    percent, true)
            )
        }
    }

    private fun fail(message: String) {
        snapshot = snapshot.copy(running = false, stage = "สำรองไม่สำเร็จ", error = message, result = null)
        endOperation()
        notifications.notify(NOTIFICATION_ID, notification("Backup ไม่สำเร็จ: " + message, 0, false))
        stopSelf()
    }

    private fun endOperation() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(PREF_ACTIVE, false).remove(PREF_URI).apply()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun notification(text: String, progress: Int, ongoing: Boolean): Notification {
        val intent = Intent(this, BackupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_usb_notification)
            .setContentTitle("USB Image Writer • Backup")
            .setContentText(text).setContentIntent(pending)
            .setOnlyAlertOnce(true).setOngoing(ongoing)
            .setProgress(100, progress, false).build()
    }

    private fun startForegroundConnected(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun releaseWake() {
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    override fun onDestroy() {
        cancelled.set(true)
        if (snapshot.running) snapshot = snapshot.copy(running = false, stage = "งานสำรองถูกขัดจังหวะ", error = "Image อาจไม่สมบูรณ์")
        releaseWake()
        runCatching { unregisterReceiver(detached) }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}