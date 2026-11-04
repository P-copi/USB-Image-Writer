package com.pcopi.usbimager

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Read-only, bounded primary EXT4 CRC32C auditor (v0.2.16).
 * Checks superblock, group descriptors, and initialized allocation bitmaps.
 * Does not verify inodes, journals, directory entries, data or backup superblocks.
 * Passing this audit NEVER authorizes shrinking/truncating the USB.
 */
object Ext4MetadataAudit {
    data class Result(
        val groups: Int,
        val descriptors: Int,
        val blockBitmaps: Int,
        val inodeBitmaps: Int,
        val skippedUninit: Int,
        val errors: List<String>
    ) {
        fun format(): String = buildString {
            append("\n=== EXT4 CRC32C Audit v0.2.16 (Read-only) ===\n")
            append("Group descriptors ผ่าน: ").append(descriptors).append("/").append(groups).append('\n')
            append("Block Bitmap CRC32C ผ่าน: ").append(blockBitmaps).append('\n')
            append("Inode Bitmap CRC32C ผ่าน: ").append(inodeBitmaps).append('\n')
            append("Bitmaps ที่ไม่ตรวจ (UNINIT): ").append(skippedUninit).append('\n')
            append("สถานะ CRC เฉพาะส่วนที่รองรับ: ")
                .append(if (errors.isEmpty() && descriptors == groups) "PASS" else "FAILED / INCOMPLETE")
                .append('\n')
            for (error in errors) append("• ").append(error).append('\n')
            append("ยังไม่ตรวจ Inodes, Extents, Directories, Journal, Backup Superblocks และข้อมูลไฟล์\n")
            append("Smart Shrink: BLOCKED — ยังต้องมี e2fsck/resize2fs บนสำเนา\n")
            append("ไม่ได้แก้ไขข้อมูล USB ต้นฉบับ\n")
        }
    }

    private val table = IntArray(256) { initial ->
        var crc = initial
        repeat(8) {
            crc = if ((crc and 1) != 0) (crc ushr 1) xor 0x82F63B78.toInt()
                  else crc ushr 1
        }
        crc
    }

    /** Reflected Castagnoli CRC32C, raw Linux register (no final XOR). */
    fun crcRaw(seed: Int, bytes: ByteArray, from: Int = 0, length: Int = bytes.size - from): Int {
        require(from >= 0 && length >= 0 && from <= bytes.size - length)
        var crc = seed
        for (pos in from until from + length)
            crc = (crc ushr 8) xor table[(crc xor bytes[pos].toInt()) and 255]
        return crc
    }

    fun inspect(
        usbManager: UsbManager,
        device: UsbDevice,
        partition: ExtRebuildInspector.Partition,
        progress: (String) -> Unit,
        cancelled: () -> Boolean = { false }
    ): Result = UsbRawDevice.open(usbManager, device).use { usb ->
        usb.init()
        if (usb.blockSize != 512) throw IOException("ตรวจ CRC รองรับ USB 512 B/sector เท่านั้น")
        val startBytes = partition.startLba * 512L
        val partBytes = (partition.endLba - partition.startLba + 1L) * 512L
        if (startBytes <= 0 || partBytes < 4096L || startBytes + partBytes > usb.capacityBytes)
            throw IOException("ขอบเขต EXT4 บน USB ไม่ถูกต้อง")
        val reader = Reader(usb, startBytes, partBytes, cancelled)
        val sb = reader.read(1024L, 1024)
        if (u16(sb, 0x38) != 0xef53) throw IOException("ไม่พบ EXT superblock")
        val incompat = u32(sb, 0x60)
        val ro = u32(sb, 0x64)
        if ((ro and 0x400L) == 0L) throw IOException("EXT4 นี้ไม่มี metadata_csum")
        if ((incompat and 0x10L) != 0L || (ro and 0x200L) != 0L)
            throw IOException("ยังไม่รองรับ meta_bg หรือ bigalloc")
        if (u8(sb, 0x175) != 1) throw IOException("checksum type ไม่ใช่ CRC32C")

        val superChecksum = u32(sb, 0x3fc)
        val computed = crcRaw(-1, sb, 0, 0x3fc).toLong() and 0xffffffffL
        if (superChecksum != computed)
            return@use Result(0, 0, 0, 0, 0, listOf("Superblock checksum ไม่ตรงกัน"))

        val uuid = sb.copyOfRange(0x68, 0x78)
        val seed = if ((incompat and 0x2000L) != 0L) u32(sb, 0x270).toInt()
                   else crcRaw(-1, uuid)
        val log = u32(sb, 0x18)
        if (log > 2L) throw IOException("EXT block size ไม่รองรับ")
        val blockBytes = 1024 shl log.toInt()
        val blockCount = u32(sb, 4) +
            (if ((incompat and 0x80L) != 0L) (u32(sb, 0x150) shl 32) else 0L)
        val firstData = u32(sb, 0x14)
        val blocksPerGroup = u32(sb, 0x20)
        val inodesPerGroup = u32(sb, 0x28)
        val descBytes = if ((incompat and 0x80L) != 0L) u16(sb, 0xfe) else 32
        if (blockCount <= firstData || blockCount > partBytes / blockBytes ||
            blocksPerGroup < 8L || blocksPerGroup > blockBytes.toLong() * 8L ||
            inodesPerGroup < 8L || inodesPerGroup > blockBytes.toLong() * 8L ||
            blocksPerGroup % 8L != 0L || inodesPerGroup % 8L != 0L ||
            descBytes !in 32..256 || descBytes > blockBytes || descBytes % 4 != 0)
            throw IOException("EXT geometry หรือ descriptor size ไม่ถูกต้อง")
        val groupCount = (blockCount - firstData + blocksPerGroup - 1L) / blocksPerGroup
        if (groupCount <= 0L || groupCount > 512L)
            throw IOException("จำนวน Groups เกินขอบเขตการตรวจ (512)")
        val count = groupCount.toInt()
        val descStart = if (blockBytes == 1024) 2048L else blockBytes.toLong()
        if (descStart + groupCount * descBytes > partBytes)
            throw IOException("Group Descriptor Table เกินพาร์ทิชัน")

        val errors = mutableListOf<String>()
        var descPass = 0
        var blockPass = 0
        var inodePass = 0
        var uninit = 0
        for (group in 0 until count) {
            if (cancelled()) throw IOException("ยกเลิกการตรวจ CRC")
            if (group % 16 == 0) progress("ตรวจ EXT4 checksum group " + (group + 1) + "/" + count)
            val desc = reader.read(descStart + group.toLong() * descBytes, descBytes)
            val recorded = u16(desc, 0x1e)
            desc[0x1e] = 0
            desc[0x1f] = 0
            val groupBytes = byteArrayOf(group.toByte(), (group ushr 8).toByte(),
                (group ushr 16).toByte(), (group ushr 24).toByte())
            val checksum = crcRaw(crcRaw(seed, groupBytes), desc) and 0xffff
            if (recorded != checksum) {
                if (errors.size < 16) errors.add("Group " + group + ": descriptor CRC mismatch")
                continue
            }
            descPass++
            val flags = u16(desc, 0x12)
            // On 64bit descriptors, high halves of bitmap block pointers live at 0x20/0x24.
            val bitmapBlock = u32(desc, 0) +
                (if (descBytes >= 64) (u32(desc, 0x20) shl 32) else 0L)
            val bitmapInode = u32(desc, 4) +
                (if (descBytes >= 64) (u32(desc, 0x24) shl 32) else 0L)
            if ((flags and 2) != 0) uninit++
            else if (bitmapBlock == 0L || bitmapBlock >= blockCount) {
                if (errors.size < 16) errors.add("Group " + group + ": block bitmap pointer ไม่ถูกต้อง")
            } else {
                val bitmap = reader.read(bitmapBlock * blockBytes, (blocksPerGroup / 8).toInt())
                val recordedCrc = u16(desc, 0x18).toLong() +
                    (if (descBytes >= 64) (u16(desc, 0x38).toLong() shl 16) else 0L)
                val actualCrc = crcRaw(seed, bitmap).toLong() and
                    (if (descBytes >= 64) 0xffffffffL else 0xffffL)
                if (recordedCrc == actualCrc) blockPass++
                else if (errors.size < 16) errors.add("Group " + group + ": block bitmap CRC mismatch")
            }
            if ((flags and 1) != 0) uninit++
            else if (bitmapInode == 0L || bitmapInode >= blockCount) {
                if (errors.size < 16) errors.add("Group " + group + ": inode bitmap pointer ไม่ถูกต้อง")
            } else {
                val bitmap = reader.read(bitmapInode * blockBytes, (inodesPerGroup / 8).toInt())
                val recordedCrc = u16(desc, 0x1a).toLong() +
                    (if (descBytes >= 64) (u16(desc, 0x3a).toLong() shl 16) else 0L)
                val actualCrc = crcRaw(seed, bitmap).toLong() and
                    (if (descBytes >= 64) 0xffffffffL else 0xffffL)
                if (recordedCrc == actualCrc) inodePass++
                else if (errors.size < 16) errors.add("Group " + group + ": inode bitmap CRC mismatch")
            }
        }
        Result(count, descPass, blockPass, inodePass, uninit, errors)
    }

    private class Reader(
        private val usb: UsbRawDevice,
        private val start: Long,
        private val sizeBytes: Long,
        private val cancelled: () -> Boolean
    ) {
        private val cache = object : LinkedHashMap<Long, ByteArray>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?) =
                size > 96
        }
        fun read(offset: Long, length: Int): ByteArray {
            if (offset < 0L || length < 0 || offset > sizeBytes - length)
                throw IOException("EXT4 checksum read ออกนอกพาร์ทิชัน")
            val out = ByteArray(length)
            var written = 0
            while (written < length) {
                if (cancelled()) throw IOException("ยกเลิกการตรวจ")
                val absolute = start + offset + written
                val lba = absolute / 512L
                val within = (absolute % 512L).toInt()
                val bytes = cache[lba] ?: ByteArray(512).also {
                    usb.blockDevice.read(lba, ByteBuffer.wrap(it))
                    cache[lba] = it
                }
                val copy = minOf(512 - within, length - written)
                bytes.copyInto(out, written, within, within + copy)
                written += copy
            }
            return out
        }
    }

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 255
    private fun u16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
    private fun u32(b: ByteArray, i: Int): Long =
        u8(b, i).toLong() or (u8(b, i + 1).toLong() shl 8) or
            (u8(b, i + 2).toLong() shl 16) or (u8(b, i + 3).toLong() shl 24)
}
