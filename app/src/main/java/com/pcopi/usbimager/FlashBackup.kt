package com.pcopi.usbimager

import android.content.ContentResolver
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min

/** Full-device raw backup. This reads the source, never writes to the USB drive. */
object FlashBackup {
    enum class Stage { READ_USB, VERIFY_FILE }
    data class Progress(val stage: Stage, val completed: Long, val total: Long, val speed: Double)
    data class Result(
        val bytes: Long,
        val sha256: String,
        val readMillis: Long,
        val verifyMillis: Long,
        val outputUri: Uri
    )

    @Throws(Exception::class)
    fun createImage(
        resolver: ContentResolver,
        targetUri: Uri,
        manager: UsbManager,
        device: UsbDevice,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        var completed = false
        try {
            val result = UsbRawDevice.open(manager, device).use { usb ->
                usb.init()
                val total = usb.capacityBytes
                val blockSize = usb.blockSize
                if (total <= 0 || blockSize <= 0 || total % blockSize != 0L) {
                    throw IOException("ความจุหรือ Block Size ของ USB ไม่ถูกต้อง")
                }
                val chunk = (262144 / blockSize).coerceAtLeast(1) * blockSize
                val buffer = ByteArray(chunk)
                val digest = MessageDigest.getInstance("SHA-256")
                var position = 0L
                val readStart = System.nanoTime()
                val stream = resolver.openOutputStream(targetUri, "wt")
                    ?: throw IOException("ไม่สามารถเปิดไฟล์ปลายทางเพื่อเขียนได้")
                BufferedOutputStream(stream, chunk).use { out ->
                    while (position < total) {
                        if (isCancelled()) throw IOException("ยกเลิกการสร้าง Image")
                        val count = min(chunk.toLong(), total - position).toInt()
                        var success = false
                        var lastError: Exception? = null
                        for (attempt in 0..5) {
                            if (isCancelled()) throw IOException("ยกเลิกการสร้าง Image")
                            try {
                                usb.blockDevice.read(position / blockSize, ByteBuffer.wrap(buffer, 0, count))
                                success = true
                                break
                            } catch (e: IOException) {
                                lastError = e
                                if (attempt < 5) Thread.sleep(400L)
                            }
                        }
                        if (!success) throw IOException("อ่าน USB ไม่สำเร็จที่ LBA " + (position / blockSize), lastError)
                        out.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        position += count
                        onProgress(Progress(Stage.READ_USB, position, total, speed(position, readStart)))
                    }
                    out.flush()
                }
                val readMillis = nanosMillis(readStart)
                val originalHash = digest.digest().toHex()

                // Verifies bytes saved in the image file, not source media health.
                val verifyStart = System.nanoTime()
                val savedDigest = MessageDigest.getInstance("SHA-256")
                var checked = 0L
                val input = resolver.openInputStream(targetUri)
                    ?: throw IOException("เปิด Image ที่บันทึกแล้วเพื่อตรวจสอบไม่ได้")
                input.use {
                    while (true) {
                        if (isCancelled()) throw IOException("ยกเลิกการตรวจสอบ Image")
                        val len = it.read(buffer)
                        if (len < 0) break
                        if (len == 0) continue
                        checked += len
                        if (checked > total) throw IOException("Image ที่บันทึกมีขนาดมากกว่าต้นทาง")
                        savedDigest.update(buffer, 0, len)
                        onProgress(Progress(Stage.VERIFY_FILE, checked, total, speed(checked, verifyStart)))
                    }
                }
                if (checked != total || !originalHash.equals(savedDigest.digest().toHex(), ignoreCase = true)) {
                    throw IOException("Verify ไม่ผ่าน: ไฟล์ Image ที่บันทึกไม่ตรงกับข้อมูลที่อ่านจาก USB")
                }
                Result(total, originalHash, readMillis, nanosMillis(verifyStart), targetUri)
            }
            completed = true
            return result
        } finally {
            // Never leave a partial .img that might be mistaken for a valid backup.
            if (!completed) {
                runCatching { DocumentsContract.deleteDocument(resolver, targetUri) }
            }
        }
    }

    private fun speed(bytes: Long, since: Long): Double =
        bytes / ((System.nanoTime() - since).coerceAtLeast(1L) / 1_000_000_000.0)

    private fun nanosMillis(since: Long): Long =
        (System.nanoTime() - since).coerceAtLeast(1L) / 1_000_000L

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}