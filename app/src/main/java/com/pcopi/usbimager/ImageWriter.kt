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

    enum class Stage { PREPARE, WRITE, VERIFY }

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
        imageName: String,
        maxUsbBytes: Long,
        usbManager: UsbManager,
        usbDevice: UsbDevice,
        onProgress: (Progress) -> Unit,
        onRecovery: (Recovery) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        if (imageSize <= 0L) throw IOException("ไม่ทราบขนาดไฟล์ Image")
        val compressed = CompressedImage.isCompressed(imageName)
        val prepareStart = System.nanoTime()
        // For GZIP, verify the complete compressed stream, including checksum and
        // real 64-bit output length, before opening or modifying any USB blocks.
        val inspected = if (compressed) {
            CompressedImage.inspect(
                resolver, imageUri, maxUsbBytes, isCancelled
            ) { readBytes ->
                onProgress(Progress(Stage.PREPARE, readBytes, maxUsbBytes, averageSpeed(readBytes, prepareStart)))
            }
        } else null
        val rawImageSize = inspected?.rawBytes ?: imageSize

        var raw: UsbRawDevice? = null

        fun closeRaw() {
            val current = raw
            raw = null
            if (current != null) runCatching { current.close() }
        }

        fun openInitial(): UsbRawDevice {
            checkCancelled(isCancelled)
            val opened = UsbRawDevice.open(usbManager, usbDevice)
            try {
                opened.init()
                raw = opened
                return opened
            } catch (e: Exception) {
                runCatching { opened.close() }
                throw e
            }
        }

        fun ensureRaw(): UsbRawDevice = raw ?: openInitial()

        // Important for Samsung background behavior:
        // once the app has claimed the USB mass-storage interface, keep that exact
        // connection claimed until the whole Write + Verify operation is finished.
        // Releasing/reopening after a transient error lets Android's storage stack
        // compete for the interface and recovery may never succeed until replugged.
        // libaums performs BOT reset/clear-halt on the SAME claimed connection;
        // this outer loop simply retries the same block without closing the session.
        // Persist the reduced window across file chunks rather than immediately
        // returning to large transfers after every 256 KiB stream read.
        var writeWindowBytes = 0
        var verifyWindowBytes = 0
        var writeGoodSegments = 0
        var verifyGoodSegments = 0

        // Keep one claimed USB session throughout Write and Verify.
        // Every confirmed segment has an aligned size and an exact LBA.
        // On SCSI error retry only the failed range; never skip a sector.
        fun transferChunk(
            stage: Stage,
            startLba: Long,
            data: ByteArray,
            count: Int,
            confirmedBeforeChunk: Long
        ) {
            val deviceBlockSize = ensureRaw().blockSize
            val normalWindow = alignedSize(PREFERRED_TRANSFER_BYTES, deviceBlockSize)
            var window = (if (stage == Stage.WRITE) writeWindowBytes else verifyWindowBytes)
                .takeIf { it > 0 } ?: normalWindow
            var completed = 0
            var consecutiveGood = if (stage == Stage.WRITE) writeGoodSegments else verifyGoodSegments

            while (completed < count) {
                checkCancelled(isCancelled)
                val segmentSize = min(window, count - completed)
                val segmentLba = startLba + completed / deviceBlockSize
                var lastError: IOException? = null
                var ok = false

                for (attempt in 1..MAX_SEGMENT_ATTEMPTS) {
                    checkCancelled(isCancelled)
                    try {
                        // Fresh ByteBuffer slice for every retry; no stale position.
                        val bb = ByteBuffer.wrap(data, completed, segmentSize)
                        if (stage == Stage.WRITE)
                            ensureRaw().blockDevice.write(segmentLba, bb)
                        else
                            ensureRaw().blockDevice.read(segmentLba, bb)
                        ok = true
                        break
                    } catch (e: IOException) {
                        lastError = e
                        onRecovery(
                            Recovery(
                                stage = stage,
                                completedBytes = confirmedBeforeChunk + completed,
                                totalBytes = rawImageSize,
                                attempt = attempt,
                                maxAttempts = MAX_SEGMENT_ATTEMPTS,
                                reason = "LBA " + segmentLba + " • " +
                                    (segmentSize / 1024) + " KiB • " + shortReason(e)
                            )
                        )
                        if (attempt < MAX_SEGMENT_ATTEMPTS) Thread.sleep(350L)
                    }
                }

                if (ok) {
                    completed += segmentSize
                    consecutiveGood++
                    // Only enlarge transfers after sustained clean communication.
                    if (consecutiveGood >= 128 && window < normalWindow) {
                        window = min(normalWindow, window * 2)
                        consecutiveGood = 0
                    }
                } else {
                    consecutiveGood = 0
                    if (window > deviceBlockSize) {
                        window = alignedSize((window / 4).coerceAtLeast(deviceBlockSize), deviceBlockSize)
                        onRecovery(
                            Recovery(
                                stage = stage,
                                completedBytes = confirmedBeforeChunk + completed,
                                totalBytes = rawImageSize,
                                attempt = 1,
                                maxAttempts = MAX_SEGMENT_ATTEMPTS,
                                reason = "LBA " + segmentLba +
                                    " • ลดขนาดคำสั่งเหลือ " + window +
                                    " B • " + shortReason(lastError)
                            )
                        )
                        Thread.sleep(400L)
                    } else {
                        val phase = if (stage == Stage.WRITE) "เขียน" else "Verify"
                        throw IOException(
                            phase + " USB ไม่สำเร็จที่ LBA " + segmentLba +
                                " (ตำแหน่ง " + ((confirmedBeforeChunk + completed) / (1024 * 1024)) +
                                " MiB) แม้ลดขนาดคำสั่งเหลือ " + deviceBlockSize + " B • " +
                                shortReason(lastError) +
                                " • USB ปลายทางอาจเขียนไม่ครบ ห้ามใช้บูตจนกว่าจะเขียนใหม่และ Verify ผ่าน",
                            lastError
                        )
                    }
                }
                if (stage == Stage.WRITE) {
                    writeWindowBytes = window
                    writeGoodSegments = consecutiveGood
                } else {
                    verifyWindowBytes = window
                    verifyGoodSegments = consecutiveGood
                }
            }
        }

        fun writeChunk(lba: Long, data: ByteArray, count: Int, completedBeforeChunk: Long) =
            transferChunk(Stage.WRITE, lba, data, count, completedBeforeChunk)

        fun readChunk(lba: Long, data: ByteArray, count: Int, completedBeforeChunk: Long) =
            transferChunk(Stage.VERIFY, lba, data, count, completedBeforeChunk)

        try {
            val first = ensureRaw()
            val blockSize = first.blockSize
            if (blockSize <= 0) throw IOException("USB block size ไม่ถูกต้อง")
            if (rawImageSize > first.capacityBytes) {
                throw IOException("Flash Drive เล็กกว่าไฟล์ Image")
            }
            if (rawImageSize % blockSize != 0L) {
                throw IOException("ขนาดไฟล์ Image ต้องหารด้วย USB block size ($blockSize) ลงตัว")
            }

            val chunkSize = alignedChunkSize(blockSize)
            val sourceDigest = MessageDigest.getInstance("SHA-256")
            val writeBuffer = ByteArray(chunkSize)

            val input = CompressedImage.open(resolver, imageUri, compressed)

            var written = 0L
            var writeChunks = 0L
            val writeStart = System.nanoTime()
            input.use { stream ->
                while (written < rawImageSize) {
                    checkCancelled(isCancelled)
                    val wanted = min(chunkSize.toLong(), rawImageSize - written).toInt()
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
                            rawImageSize,
                            averageSpeed(written, writeStart)
                        )
                    )

                    if (writeChunks % 16L == 0L) {
                        Thread.sleep(2L)
                    }
                }
                // Drain gzip to its end and validate trailer; no unexpected extra raw bytes.
                if (stream.read() != -1)
                    throw IOException("ไฟล์ Image มีข้อมูลหลังขนาดที่ตรวจสอบแล้ว")
            }

            val writeDurationNanos = (System.nanoTime() - writeStart).coerceAtLeast(1L)
            val sourceHash = sourceDigest.digest().toHex()
            if (inspected != null && !sourceHash.equals(inspected.sha256, ignoreCase = true))
                throw IOException("ไฟล์ .img.gz เปลี่ยนแปลงหลังตรวจสอบ • หยุดงานเพราะ SHA-256 ไม่ตรง")

            // Keep the same USB interface claimed across the phase boundary.
            // Only give the flash controller a short settle window before read-back.
            Thread.sleep(VERIFY_SETTLE_DELAY_MS)

            val verifyDigest = MessageDigest.getInstance("SHA-256")
            val verifyBuffer = ByteArray(chunkSize)
            var verified = 0L
            val verifyStart = System.nanoTime()

            while (verified < rawImageSize) {
                checkCancelled(isCancelled)
                val count = min(chunkSize.toLong(), rawImageSize - verified).toInt()
                val lba = verified / blockSize
                readChunk(lba, verifyBuffer, count, verified)
                verifyDigest.update(verifyBuffer, 0, count)
                verified += count

                onProgress(
                    Progress(
                        Stage.VERIFY,
                        verified,
                        rawImageSize,
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

    private fun alignedSize(bytes: Int, blockSize: Int): Int =
        (bytes / blockSize).coerceAtLeast(1) * blockSize

    private fun shortReason(error: Throwable?): String {
        if (error == null) return "unknown USB error"
        val message = error.message.orEmpty().replace('\n', ' ').take(150)
        val cause = error.cause?.message.orEmpty().replace('\n', ' ').take(90)
        return error.javaClass.simpleName + ": " + message +
            (if (cause.isBlank()) "" else " / " + cause)
    }

    private const val PREFERRED_TRANSFER_BYTES = 32 * 1024
    private const val MAX_SEGMENT_ATTEMPTS = 2
    private const val VERIFY_SETTLE_DELAY_MS = 1500L
}
