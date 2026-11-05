package com.pcopi.usbimager

import java.io.File
import android.system.Os
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * v0.2.21 stage 1: preserve the original ARC3 sparse mirror while preparing a
 * separate working copy. This class never opens a USB device and never resizes
 * or modifies the source mirror.
 *
 * Deliberately not connected to the UI until the end-to-end resize verification
 * and cancellation gates have been implemented and tested.
 */
internal object SafeArc3Workspace {
    private const val BLOCK = 4096
    private const val MAX_LOGICAL_BYTES = 128L * 1024 * 1024 * 1024
    private const val RESERVE_BYTES = 512L * 1024 * 1024

    data class Result(
        val workingFile: File,
        val logicalBytes: Long,
        val nonZeroBytesWritten: Long,
        val sourceDigest: String,
        val workingDigest: String
    )

    /**
     * Copy zero-filled ranges as holes. The source must be a regular file in
     * private app storage. Destination must not exist. No USB writes occur.
     * SHA-256 is computed over every logical byte, including holes.
     *
     * Estimate physical source allocation using stat.st_blocks (512-byte units).
     * Require a 2x allocation budget plus a 512 MiB reserve and continuously
     * enforce the reserve while copying. Abort and delete on any failure.
     */
    fun copyReadOnly(source: File, destination: File, cancelled: () -> Boolean = { false }): Result {
        require(source.isFile && source.canRead()) { "ARC3 mirror missing or unreadable" }
        require(!source.canonicalFile.equals(destination.canonicalFile)) { "Source and destination identical" }
        require(!destination.exists()) { "Working destination already exists" }
        require(destination.parentFile?.isDirectory == true) { "Working directory missing" }
        val logical = source.length()
        require(logical in 1..MAX_LOGICAL_BYTES) { "Invalid ARC3 mirror size" }
        val physical = Math.multiplyExact(Os.stat(source.absolutePath).st_blocks, 512L)
        require(physical > 0L && physical <= logical) { "Invalid source allocation" }
        val budget = Math.addExact(Math.multiplyExact(physical, 2L), RESERVE_BYTES)
        require(destination.parentFile!!.usableSpace > budget) {
            "Insufficient space for safe sparse copy and reserve"
        }
        val sourceHash = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(BLOCK)
        var nonZero = 0L
        var complete = false
        try {
            RandomAccessFile(source, "r").use { input ->
                RandomAccessFile(destination, "rw").use { output ->
                    output.setLength(logical)
                    var offset = 0L
                    while (offset < logical) {
                        if (cancelled() || Thread.currentThread().isInterrupted)
                            throw InterruptedException("ARC3 copy cancelled")
                        val size = minOf(BLOCK.toLong(), logical - offset).toInt()
                        input.readFully(bytes, 0, size)
                        sourceHash.update(bytes, 0, size)
                        if (destination.parentFile!!.usableSpace <= RESERVE_BYTES)
                            throw IllegalStateException("Storage reserve exhausted")
                        if ((0 until size).any { bytes[it].toInt() != 0 }) {
                            output.seek(offset)
                            output.write(bytes, 0, size)
                            nonZero += size
                        }
                        offset += size
                    }
                    output.fd.sync()
                }
            }
            val a = sourceHash.digest().joinToString("") { "%02x".format(it) }
            val verifyHash = MessageDigest.getInstance("SHA-256")
            RandomAccessFile(destination, "r").use { verify ->
                var remaining = logical
                while (remaining > 0L) {
                    if (cancelled() || Thread.currentThread().isInterrupted)
                        throw InterruptedException("ARC3 verification cancelled")
                    val n = minOf(BLOCK.toLong(), remaining).toInt()
                    verify.readFully(bytes, 0, n)
                    verifyHash.update(bytes, 0, n)
                    remaining -= n
                }
            }
            val b = verifyHash.digest().joinToString("") { "%02x".format(it) }
            check(a == b) { "Logical copy digest mismatch" }
            complete = true
            return Result(destination, logical, nonZero, a, b)
        } finally {
            if (!complete) destination.delete()
        }
    }
}
