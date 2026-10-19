package com.pcopi.usbimager

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Stream-based, standard gzip disk images; no large temporary raw file.
 * Inspection consumes and validates the full gzip stream BEFORE USB writes.
 * GZIP's 32-bit ISIZE trailer cannot be trusted for >4 GiB images.
 */
object CompressedImage {
    data class Info(val rawBytes: Long, val sha256: String)

    fun isCompressed(fileName: String): Boolean =
        fileName.endsWith(".img.gz", ignoreCase = true)

    fun open(resolver: ContentResolver, uri: Uri, gzip: Boolean): InputStream {
        val input = resolver.openInputStream(uri)
            ?: throw IOException("ไม่สามารถเปิดไฟล์ Image ได้")
        val buffered = BufferedInputStream(input, 256 * 1024)
        return if (gzip) {
            try { GZIPInputStream(buffered, 256 * 1024) }
            catch (e: Exception) {
                buffered.close()
                throw e
            }
        } else buffered
    }

    fun inspect(
        resolver: ContentResolver,
        uri: Uri,
        maxRawBytes: Long,
        isCancelled: () -> Boolean,
        onBytesRead: (Long) -> Unit
    ): Info {
        if (maxRawBytes <= 0L) throw IOException("ไม่ทราบความจุ Flash Drive ปลายทาง")
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(256 * 1024)
        var rawBytes = 0L
        val stream = open(resolver, uri, true)
        stream.use { input ->
            while (true) {
                if (isCancelled()) throw IOException("ยกเลิกการตรวจสอบ Image")
                val n = input.read(buf)
                if (n < 0) break // reads GZIP CRC/trailer and rejects truncated gzip
                if (n == 0) continue
                if (n.toLong() > maxRawBytes - rawBytes)
                    throw IOException("Image หลังแตกไฟล์ใหญ่กว่า Flash Drive ปลายทาง")
                digest.update(buf, 0, n)
                rawBytes += n
                onBytesRead(rawBytes)
            }
        }
        if (rawBytes == 0L) throw IOException("ไฟล์ gzip ไม่มี Image")
        return Info(rawBytes, digest.digest().joinToString("") { "%02x".format(it) })
    }
}