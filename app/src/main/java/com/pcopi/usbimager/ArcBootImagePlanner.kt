package com.pcopi.usbimager

import java.io.IOException
import java.nio.ByteBuffer

/**
 * Read-only, fail-closed Arc Loader 4.2.2 compact image boundary detector.
 *
 * The compact image covers LBA 0 through the END of the last MBR partition,
 * inclusive. It preserves MBR boot code, pre-partition gap, FAT and EXT bytes.
 * It is not an EXT filesystem shrink and does not silently pad read failures.
 *
 * Restricted to the tested 3-partition ARC layout; never applies to GPT or
 * unrelated USB formats. Actual boot must still be tested on spare media.
 */
object ArcBootImagePlanner {
    data class Part(val number: Int, val type: Int, val first: Long, val last: Long)
    data class Plan(val usbBytes: Long, val imageBytes: Long, val lastLba: Long,
                    val partitions: List<Part>)

    @Throws(IOException::class)
    fun inspect(usb: UsbRawDevice): Plan {
        val blockSize = usb.blockSize
        if (blockSize != 512) throw IOException("ARC Compact รองรับ USB Block 512 B เท่านั้น")
        val blocks = usb.blockDevice.blocks
        val mbr = sector(usb, 0L)
        if (u16(mbr, 510) != 0xaa55) throw IOException("ไม่พบ MBR Signature 55 AA")

        val partitions = mutableListOf<Part>()
        for (index in 0 until 4) {
            val offset = 446 + index * 16
            val type = u8(mbr, offset + 4)
            val first = u32(mbr, offset + 8)
            val count = u32(mbr, offset + 12)
            if (type == 0xee) throw IOException("พบ GPT: ไม่รองรับ ARC Compact")
            if (type == 0 && first == 0L && count == 0L) continue
            if (type != 0x83 || first == 0L || count == 0L)
                throw IOException("MBR partition ${index + 1} ไม่ตรงกับ Arc Loader ที่รองรับ")
            val last = first + count - 1L
            if (last < first || last >= blocks)
                throw IOException("Partition ${index + 1} เกินขอบเขต USB")
            partitions.add(Part(index + 1, type, first, last))
        }

        if (partitions.size != 3 || partitions.map { it.number } != listOf(1, 2, 3))
            throw IOException("รองรับ Arc Loader ที่มี MBR Primary 3 partitions เท่านั้น")
        val p1 = partitions[0]
        val p2 = partitions[1]
        val p3 = partitions[2]

        if (p1.first != 2048L || p1.last != 104447L ||
            p2.first != 104448L || p2.last != 206847L ||
            p3.first != 206848L || p3.last <= p3.first)
            throw IOException("ตำแหน่ง ARC1/ARC2/ARC3 ไม่ตรงกับโครงสร้าง Arc 4.2.2")
        if (p3.last + 1L >= blocks)
            throw IOException("พาร์ทิชัน ARC3 ชิดท้าย USB: ต้องใช้ Full Raw แทน")

        val boot = sector(usb, p1.first)
        if (u16(boot, 510) != 0xaa55 ||
            String(boot, 82, 5, Charsets.US_ASCII) != "FAT32")
            throw IOException("ARC1 ไม่ใช่ FAT32 boot record ที่รองรับ")

        val ext2 = superblock(usb, p2.first, p2.last)
        val ext4 = superblock(usb, p3.first, p3.last)
        if ((ext2.incompat and 0x40L) != 0L || (ext2.compat and 0x4L) != 0L)
            throw IOException("ARC2 ไม่ตรงกับ EXT2 ที่คาดไว้")
        if ((ext4.incompat and 0x40L) == 0L)
            throw IOException("ARC3 ไม่ตรงกับ EXT4 ที่คาดไว้")
        if (!ext2.clean || !ext4.clean || ext2.needsRecovery || ext4.needsRecovery)
            throw IOException("พบ EXT ไม่ Clean / Journal ค้าง: หยุด ARC Compact")

        val imageBlocks = p3.last + 1L
        val imageBytes = imageBlocks * blockSize.toLong()
        // Guard unsupported sizes rather than making a dangerously broad copy.
        if (imageBytes <= 256L * 1024L * 1024L ||
            imageBytes > 16L * 1024L * 1024L * 1024L)
            throw IOException("ขนาด ARC Compact อยู่นอกขอบเขตที่รองรับ")

        return Plan(usb.capacityBytes, imageBytes, p3.last, partitions)
    }

    private data class Ext(val compat: Long, val incompat: Long, val clean: Boolean,
                           val needsRecovery: Boolean)

    private fun superblock(usb: UsbRawDevice, first: Long, last: Long): Ext {
        if (last - first < 4L)
            throw IOException("EXT partition เล็กเกินไป")
        val bytes = ByteArray(1024)
        sector(usb, first + 2L).copyInto(bytes, 0)
        sector(usb, first + 3L).copyInto(bytes, 512)
        if (u16(bytes, 56) != 0xef53)
            throw IOException("ไม่พบ EXT superblock")
        val logSize = u32(bytes, 24)
        if (logSize > 4L) throw IOException("EXT block size ไม่รองรับ")
        val bytesPerBlock = 1024L shl logSize.toInt()
        val incompat = u32(bytes, 96)
        val compat = u32(bytes, 92)
        val blocks = u32(bytes, 4) +
            (if ((incompat and 0x80L) != 0L) (u32(bytes, 0x150) shl 32) else 0L)
        val partBytes = (last - first + 1L) * 512L
        if (blocks <= 0L || blocks > partBytes / bytesPerBlock)
            throw IOException("EXT ขนาด filesystem เกิน partition")
        return Ext(compat, incompat, (u16(bytes, 58) and 1) == 1,
            (incompat and 4L) != 0L)
    }

    private fun sector(usb: UsbRawDevice, lba: Long): ByteArray {
        if (lba < 0L || lba >= usb.blockDevice.blocks)
            throw IOException("อ่าน LBA นอกขอบเขต USB: $lba")
        return ByteArray(512).also { usb.blockDevice.read(lba, ByteBuffer.wrap(it)) }
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int =
        u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u8(bytes, offset).toLong() or
            (u8(bytes, offset + 1).toLong() shl 8) or
            (u8(bytes, offset + 2).toLong() shl 16) or
            (u8(bytes, offset + 3).toLong() shl 24)
}
