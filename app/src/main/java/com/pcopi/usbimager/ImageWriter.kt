package com.pcopi.usbimager

import android.content.ContentResolver
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min

object ImageWriter {

    enum class Stage { WRITE, VERIFY }

    data class Progress(
        val stage: Stage,
        val completedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Double
    )

    data class Recovery(
        val stage: Stage,
        val completedBytes: Long,
        val totalBytes: Long,
        val attempt: Int,
        val maxAttempts: Int,
        val reason: String
    )

    data class Result(
        val bytesWritten: Long,
        val sourceSha256: String,
        val deviceSha256: String,
        val writeDurationMillis: Long,
        val verifyDurationMillis: Long,
        val writeAverageBytesPerSecond: Double,
        val verifyAverageBytesPerSecond: Double
    )

    @Throws(Exception::class)
    fun writeAndVerify(
        resolver: ContentResolver,
        imageUri: Uri,
        imageSize: Long,
        usbManager: UsbManager,
        usbDevice: UsbDevice,
        onProgress: (Progress) -> Unit,
        onRecovery: (Recovery) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        if (imageSize <= 0L) throw IOException("ไม่ทราบขนาดไฟล์ Image")

        var raw: UsbRawDevice? = null
        var expectedBlockSize = 0
        var expectedCapacity = 0L

        fun closeRaw() {
            val current = raw
            raw = null
            if (current != null) runCatching { current.close() }
        }

        fun openFresh(): UsbRawDevice {
            checkCancelled(isCancelled)
            val opened = UsbRawDevice.open(usbManager, usbDevice)
            try {
                opened.init()
                if (expectedBlockSize == 0) {
                    expectedBlockSize = opened.blockSize
                    expectedCapacity = opened.capacityBytes
                } else {
                    if (opened.blockSize != expectedBlockSize) {
                        throw IOException("USB block size เปลี่ยนหลัง reconnect")
                    }
                    if (opened.capacityBytes != expectedCapacity) {
                        throw IOException("ความจุ USB เปลี่ยนหลัง reconnect")
                    }
                }
                raw = opened
                return opened
            } catch (e: Exception) {
                runCatching { opened.close() }
                throw e
            }
        }

        fun ensureRaw(): UsbRawDevice = raw ?: openFresh()

        fun writeChunk(lba: Long, data: ByteArray, count: Int, completedBeforeChunk: Long) {
            var lastError: IOException? = null
            for (tryIndex in 0..MAX_SESSION_RECOVERY_ATTEMPTS) {
                checkCancelled(isCancelled)
                try {
                    ensureRaw().blockDevice.write(lba, ByteBuffer.wrap(data, 0, count))
                    return
                } catch (e: IOException) {
                    lastError = e
                    closeRaw()
                    if (tryIndex >= MAX_SESSION_RECOVERY_ATTEMPTS) break
                    onRecovery(
                        Recovery(
                            stage = Stage.WRITE,
                            completedBytes = completedBeforeChunk,
                            totalBytes = imageSize,
                            attempt = tryIndex + 1,
                            maxAttempts = MAX_SESSION_RECOVERY_ATTEMPTS,
                            reason = e.message ?: e.javaClass.simpleName
                        )
                    )
                    Thread.sleep(RECOVERY_BASE_DELAY_MS + tryIndex * RECOVERY_STEP_DELAY_MS)
                }
            }
            throw IOException(
                "USB สะดุดระหว่างเขียนและกู้การเชื่อมต่อไม่สำเร็จ กรุณาถอดแล้วเสียบ Flash Drive ใหม่",
                lastError
            )
        }

        fun readChunk(lba: Long, data: ByteArray, count: Int, completedBeforeChunk: Long) {
            var lastError: IOException? = null
            for (tryIndex in 0..MAX_SESSION_RECOVERY_ATTEMPTS) {
                checkCancelled(isCancelled)
                try {
                    val bb = ByteBuffer.wrap(data, 0, count)
                    ensureRaw().blockDevice.read(lba, bb)
                    return
                } catch (e: IOException) {
                    lastError = e
                    closeRaw()
                    if (tryIndex >= MAX_SESSION_RECOVERY_ATTEMPTS) break
                    onRecovery(
                        Recovery(
                            stage = Stage.VERIFY,
                            completedBytes = completedBeforeChunk,
                            totalBytes = imageSize,
                            attempt = tryIndex + 1,
                            maxAttempts = MAX_SESSION_RECOVERY_ATTEMPTS,
                            reason = e.message ?: e.javaClass.simpleName
                        )
                    )
                    Thread.sleep(RECOVERY_BASE_DELAY_MS + tryIndex * RECOVERY_STEP_DELAY_MS)
                }
            }
            throw IOException(
                "USB สะดุดระหว่าง Verify และกู้การเชื่อมต่อไม่สำเร็จ กรุณาถอดแล้วเสียบ Flash Drive ใหม่",
                lastError
            )
        }

        try {
            val first = ensureRaw()
            val blockSize = first.blockSize
            if (blockSize <= 0) throw IOException("USB block size ไม่ถูกต้อง")
            if (imageSize > first.capacityBytes) {
                throw IOException("Flash Drive เล็กกว่าไฟล์ Image")
            }
            if (imageSize % blockSize != 0L) {
                throw IOException("ขนาดไฟล์ Image ต้องหารด้วย USB block size ($blockSize) ลงตัว")
            }

            val chunkSize = alignedChunkSize(blockSize)
            val sourceDigest = MessageDigest.getInstance("SHA-256")
            val writeBuffer = ByteArray(chunkSize)

            val input = resolver.openInputStream(imageUri)
                ?: throw IOException("เปิดไฟล์ Image ไม่สำเร็จ")

            var written = 0L
            var writeChunks = 0L
            val writeStart = System.nanoTime()
            input.use { stream ->
                while (written < imageSize) {
                    checkCancelled(isCancelled)
                    val wanted = min(chunkSize.toLong(), imageSize - written).toInt()
                    val count = readExact(stream, writeBuffer, wanted)
                    if (count != wanted) throw EOFException("ไฟล์ Image จบก่อนขนาดที่รายงาน")
                    if (count % blockSize != 0) throw IOException("ข้อมูลไม่ตรงแนว block size")

                    val lba = written / blockSize
                    writeChunk(lba, writeBuffer, count, written)
                    sourceDigest.update(writeBuffer, 0, count)
                    written += count
                    writeChunks++

                    onProgress(
                        Progress(
                            Stage.WRITE,
                            written,
                            imageSize,
                            averageSpeed(written, writeStart)
                        )
                    )

                    if (writeChunks % 16L == 0L) {
                        Thread.sleep(2L)
                    }
                }
            }

            val writeDurationNanos = (System.nanoTime() - writeStart).coerceAtLeast(1L)
            val sourceHash = sourceDigest.digest().toHex()

            closeRaw()
            Thread.sleep(VERIFY_SETTLE_DELAY_MS)

            val verifyDigest = MessageDigest.getInstance("SHA-256")
            val verifyBuffer = ByteArray(chunkSize)
            var verified = 0L
            val verifyStart = System.nanoTime()

            while (verified < imageSize) {
                checkCancelled(isCancelled)
                val count = min(chunkSize.toLong(), imageSize - verified).toInt()
                val lba = verified / blockSize
                readChunk(lba, verifyBuffer, count, verified)
                verifyDigest.update(verifyBuffer, 0, count)
                verified += count

                onProgress(
                    Progress(
                        Stage.VERIFY,
                        verified,
                        imageSize,
                        averageSpeed(verified, verifyStart)
                    )
                )
            }

            val verifyDurationNanos = (System.nanoTime() - verifyStart).coerceAtLeast(1L)
            val deviceHash = verifyDigest.digest().toHex()
            if (!sourceHash.equals(deviceHash, ignoreCase = true)) {
                throw IOException("Verify ไม่ผ่าน: SHA-256 ของ Image และ Flash Drive ไม่ตรงกัน")
            }

            return Result(
                bytesWritten = written,
                sourceSha256 = sourceHash,
                deviceSha256 = deviceHash,
                writeDurationMillis = writeDurationNanos / 1_000_000L,
                verifyDurationMillis = verifyDurationNanos / 1_000_000L,
                writeAverageBytesPerSecond = written / (writeDurationNanos / 1_000_000_000.0),
                verifyAverageBytesPerSecond = verified / (verifyDurationNanos / 1_000_000_000.0)
            )
        } finally {
            closeRaw()
        }
    }

    private fun alignedChunkSize(blockSize: Int): Int {
        val preferred = 256 * 1024
        val blocks = (preferred / blockSize).coerceAtLeast(1)
        return blocks * blockSize
    }

    private fun readExact(input: InputStream, buffer: ByteArray, wanted: Int): Int {
        var total = 0
        while (total < wanted) {
            val n = input.read(buffer, total, wanted - total)
            if (n < 0) break
            if (n == 0) continue
            total += n
        }
        return total
    }

    private fun averageSpeed(bytes: Long, startNanos: Long): Double {
        val seconds = (System.nanoTime() - startNanos).coerceAtLeast(1L) / 1_000_000_000.0
        return bytes / seconds
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw IOException("USB ถูกถอดออกหรือการทำงานถูกยกเลิก")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // Keep recovery alive long enough for Samsung/Android to settle after task switches,
    // instead of declaring failure while the connected-device foreground service is alive.
    private const val MAX_SESSION_RECOVERY_ATTEMPTS = 12
    private const val RECOVERY_BASE_DELAY_MS = 700L
    private const val RECOVERY_STEP_DELAY_MS = 150L
    private const val VERIFY_SETTLE_DELAY_MS = 1200L
}
