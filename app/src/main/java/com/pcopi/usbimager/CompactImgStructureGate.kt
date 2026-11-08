package com.pcopi.usbimager

import java.io.File
import java.io.RandomAccessFile

/**
 * v0.2.49 offline, read-only image structure gate.
 * Detects malformed partitions and missing FAT/EXT signatures.
 * This is not a filesystem consistency check or physical boot test.
 */
internal object CompactImgStructureGate {
    private const val SECTOR = 512L
    private const val EXPECTED_BYTES = 2253389824L
    private val starts = longArrayOf(2048L, 104448L, 206848L)
    private val sizes = longArrayOf(102400L, 102400L, 4194304L)

    private fun u32(b: ByteArray, p: Int): Long {
        var value = 0L
        for (i in 0..3) value = value or ((b[p + i].toLong() and 255L) shl (8 * i))
        return value
    }

    fun validate(file: File): String {
        require(file.isFile && file.length() == EXPECTED_BYTES) { "Compact IMG missing or unexpected size" }
        RandomAccessFile(file, "r").use { input ->
            val mbr = ByteArray(512)
            input.readFully(mbr)
            require((mbr[510].toInt() and 255) == 0x55 && (mbr[511].toInt() and 255) == 0xAA) {
                "Invalid MBR signature"
            }
            require(mbr.copyOfRange(494, 510).all { it == 0.toByte() }) { "Unexpected fourth partition" }
            for (i in 0..2) {
                val p = 446 + i * 16
                val start = u32(mbr, p + 8)
                val count = u32(mbr, p + 12)
                require((mbr[p + 4].toInt() and 255) == 0x83 &&
                    start == starts[i] && count == sizes[i]) { "Unexpected partition P${i + 1}" }
                require(start > 0 && count > 0 && (start + count) * SECTOR <= EXPECTED_BYTES) {
                    "Partition P${i + 1} outside IMG"
                }
            }
            val fat = ByteArray(512)
            input.seek(starts[0] * SECTOR)
            input.readFully(fat)
            require((fat[510].toInt() and 255) == 0x55 && (fat[511].toInt() and 255) == 0xAA) {
                "P1 FAT boot sector signature missing"
            }
            val fat32 = String(fat, 82, 8, Charsets.US_ASCII)
            require(fat32.startsWith("FAT32")) { "P1 FAT32 marker missing" }
            for (i in 1..2) {
                val magic = ByteArray(2)
                input.seek(starts[i] * SECTOR + 1024L + 56L)
                input.readFully(magic)
                require((magic[0].toInt() and 255) == 0x53 &&
                    (magic[1].toInt() and 255) == 0xEF) { "P${i + 1} EXT superblock signature missing" }
            }
        }
        return "PASS: MBR geometry, P1 FAT32 and P2/P3 EXT signatures; NOT filesystem-checked or boot-tested"
    }
}
