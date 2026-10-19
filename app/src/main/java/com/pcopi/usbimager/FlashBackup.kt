package com.pcopi.usbimager

import android.content.ContentResolver
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Deflater
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min

/**
 * Full raw USB -> IMG. No writes to source USB, no bad-sector substitution.
 * If USB read fails, adaptively retry smaller *aligned* read requests on the
 * SAME claimed USB session. A partially read chunk is never written to output.
 */
object FlashBackup {
    enum class Stage { READ_USB, RECOVER_USB, VERIFY_FILE }
    data class Progress(
        val stage: Stage,
        val completed: Long,
        val total: Long,
        val speed: Double,
        val detail: String = ""
    )
    data class Result(
        val bytes: Long,
        val sha256: String,
        val readMillis: Long,
        val verifyMillis: Long,
        val outputUri: Uri,
        val storedBytes: Long,
        val compressed: Boolean
    )

    @Throws(Exception::class)
    fun createImage(
        resolver: ContentResolver,
        targetUri: Uri,
        manager: UsbManager,
        device: UsbDevice,
        compressed: Boolean,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        var completed = false
        try {
            val result = UsbRawDevice.open(manager, device).use { usb ->
                usb.init()
                val total = usb.capacityBytes
                val blockSize = usb.blockSize
                if (total <= 0L || blockSize <= 0 || total % blockSize != 0L)
                    throw IOException("ความจุหรือ Block Size ของ USB ไม่ถูกต้อง")

                val chunk = (256 * 1024 / blockSize).coerceAtLeast(1) * blockSize
                val reader = AdaptiveReader(usb, manager, device, blockSize, isCancelled)
                val buffer = ByteArray(chunk)
                val digest = MessageDigest.getInstance("SHA-256")
                var position = 0L
                val readStart = System.nanoTime()

                val stream = resolver.openOutputStream(targetUri, "wt")
                    ?: throw IOException("ไม่สามารถเปิดไฟล์ปลายทางเพื่อเขียนได้")
                val rawOut = BufferedOutputStream(stream, chunk)
                val output: OutputStream = if (compressed) {
                    object : GZIPOutputStream(rawOut, chunk) {
                        init { def.setLevel(Deflater.BEST_SPEED) }
                    }
                } else rawOut
                output.use { out ->
                    while (position < total) {
                        checkCancelled(isCancelled)
                        val count = min(chunk.toLong(), total - position).toInt()

                        reader.readFully(position, buffer, count) { currentLba, currentWindow, reason ->
                            onProgress(
                                Progress(
                                    Stage.RECOVER_USB, position, total, 0.0,
                                    "LBA " + currentLba + " • ลองอ่าน " +
                                        FormatUtils.bytes(currentWindow.toLong()) + " • " + reason
                                )
                            )
                        }
                        // Only commit a chunk after every byte was read successfully.
                        out.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        position += count
                        onProgress(Progress(Stage.READ_USB, position, total, speed(position, readStart)))
                    }
                    out.flush()
                }

                val readMillis = nanosMillis(readStart)
                val originalHash = digest.digest().toHex()

                // File verification: re-read saved IMG and compare its hash with the
                // first-pass USB read hash. This does not prove source NAND health.
                val verifyStart = System.nanoTime()
                val savedDigest = MessageDigest.getInstance("SHA-256")
                var checked = 0L
                CompressedImage.open(resolver, targetUri, compressed).use { incoming ->
                    while (true) {
                        checkCancelled(isCancelled)
                        val len = incoming.read(buffer)
                        if (len < 0) break
                        if (len == 0) continue
                        checked += len
                        if (checked > total)
                            throw IOException("Image ที่บันทึกมีขนาดมากกว่าต้นทาง")
                        savedDigest.update(buffer, 0, len)
                        onProgress(Progress(Stage.VERIFY_FILE, checked, total, speed(checked, verifyStart)))
                    }
                }

                if (checked != total || !originalHash.equals(savedDigest.digest().toHex(), ignoreCase = true))
                    throw IOException("Verify ไม่ผ่าน: ไฟล์ Image ที่บันทึกไม่ตรงกับข้อมูลที่อ่านจาก USB")

                val savedBytes = resolver.query(
                    targetUri, arrayOf(OpenableColumns.SIZE), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (col >= 0 && !cursor.isNull(col)) cursor.getLong(col) else -1L
                    } else -1L
                } ?: -1L
                Result(total, originalHash, readMillis, nanosMillis(verifyStart),
                    targetUri, savedBytes, compressed)
            }
            completed = true
            return result
        } finally {
            if (!completed) {
                // Best effort. Some document providers may not permit deleting a partial file.
                runCatching { DocumentsContract.deleteDocument(resolver, targetUri) }
            }
        }
    }

    private class AdaptiveReader(
        private val usb: UsbRawDevice,
        private val manager: UsbManager,
        private val device: UsbDevice,
        private val blockSize: Int,
        private val isCancelled: () -> Boolean
    ) {
        private val normal = alignedSize(64 * 1024)
        private var window = normal
        private var goodReads = 0

        /** Fills buffer[0,count), never skips a sector and never changes USB source. */
        fun readFully(
            offset: Long,
            buffer: ByteArray,
            count: Int,
            onRecovery: (lba: Long, bytes: Int, reason: String) -> Unit
        ) {
            var copied = 0
            while (copied < count) {
                checkCancelled(isCancelled)
                val absolute = offset + copied
                val lba = absolute / blockSize
                val size = min(window, count - copied)
                var error: IOException? = null
                var ok = false
                for (attempt in 1..2) {
                    checkCancelled(isCancelled)
                    if (!manager.deviceList.values.any { it.deviceId == device.deviceId } ||
                        !manager.hasPermission(device)) {
                        throw IOException("USB ถูกถอดหรือสิทธิ์การเข้าถึงหายที่ LBA " + lba)
                    }
                    try {
                        // Fresh buffer view every attempt; no advancing/reusing a failed ByteBuffer.
                        usb.blockDevice.read(lba, ByteBuffer.wrap(buffer, copied, size))
                        ok = true
                        break
                    } catch (e: IOException) {
                        error = e
                        onRecovery(lba, size, "ครั้ง " + attempt + "/2 • " + briefError(e))
                        if (attempt < 2) Thread.sleep(250L)
                    }
                }
                if (ok) {
                    copied += size
                    goodReads++
                    // Gradually recover throughput after USB becomes healthy again.
                    if (goodReads >= 32 && window < normal) {
                        window = (window * 4).coerceAtMost(normal)
                        goodReads = 0
                    }
                } else {
                    goodReads = 0
                    if (window > blockSize) {
                        window = alignedSize((window / 4).coerceAtLeast(blockSize))
                        onRecovery(lba, window, "กำลังลดขนาดการอ่านเพื่อกู้ข้อมูล")
                    } else {
                        throw IOException(
                            "อ่าน USB ไม่สำเร็จที่ LBA " + lba +
                                " แม้ลองอ่านทีละ " + blockSize +
                                " B แล้ว • " + briefError(error),
                            error
                        )
                    }
                }
            }
        }

        private fun alignedSize(wanted: Int): Int =
            (wanted / blockSize).coerceAtLeast(1) * blockSize
    }

    private fun briefError(error: Throwable?): String {
        if (error == null) return "ไม่ทราบสาเหตุ"
        val chain = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 3) {
            val message = current.message.orEmpty().replace("\n", " ").take(140)
            chain.add(current.javaClass.simpleName + (if (message.isBlank()) "" else ": " + message))
            current = current.cause
            depth++
        }
        return chain.joinToString(" / ")
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw IOException("งานสำรองถูกยกเลิกหรือ USB ถูกถอด")
    }

    private fun speed(bytes: Long, since: Long): Double =
        bytes / ((System.nanoTime() - since).coerceAtLeast(1L) / 1_000_000_000.0)

    private fun nanosMillis(since: Long): Long =
        (System.nanoTime() - since).coerceAtLeast(1L) / 1_000_000L

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}