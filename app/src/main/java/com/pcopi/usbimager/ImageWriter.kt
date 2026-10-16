package com.pcopi.usbimager

import android.content.ContentResolver
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
        raw: UsbRawDevice,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean
    ): Result {
        val blockSize = raw.blockSize
        if (blockSize <= 0) throw IOException("USB block size ไม่ถูกต้อง")
        if (imageSize <= 0L) throw IOException("ไม่ทราบขนาดไฟล์ Image")
        if (imageSize > raw.capacityBytes) {
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

                sourceDigest.update(writeBuffer, 0, count)
                val lba = written / blockSize
                raw.blockDevice.write(lba, ByteBuffer.wrap(writeBuffer, 0, count))
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

                // Give low-cost flash controllers a tiny breathing window after bursts.
                // This reduces long firmware stalls when their small write cache is exhausted.
                if (writeChunks % 16L == 0L) {
                    Thread.sleep(2L)
                }
            }
        }

        val writeDurationNanos = (System.nanoTime() - writeStart).coerceAtLeast(1L)
        val sourceHash = sourceDigest.digest().toHex()

        val verifyDigest = MessageDigest.getInstance("SHA-256")
        val verifyBuffer = ByteArray(chunkSize)
        var verified = 0L
        val verifyStart = System.nanoTime()

        while (verified < imageSize) {
            checkCancelled(isCancelled)
            val count = min(chunkSize.toLong(), imageSize - verified).toInt()
            val lba = verified / blockSize
            val bb = ByteBuffer.wrap(verifyBuffer, 0, count)
            raw.blockDevice.read(lba, bb)
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
}
