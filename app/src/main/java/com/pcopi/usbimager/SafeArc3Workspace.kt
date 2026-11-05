package com.pcopi.usbimager

import java.io.File
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
     * The storage check is conservative and deliberately assumes a fully
     * allocated destination: an underestimated free-space check is unsafe.
     * This means large ARC3 mirrors may be rejected until an allocated-block
     * preflight is available. Never fall back to an unsafe full copy.
     */
    fun copyReadOnly(source: File, destination: File, cancelled: () -> Boolean = { false }): Result {
        require(source.isFile && source.canRead()) { "ARC3 mirror missing or unreadable" }
        require(!source.canonicalFile.equals(destination.canonicalFile)) { "Source and destination identical" }
        require(!destination.exists()) { "Working destination already exists" }
        require(destination.parentFile?.isDirectory == true) { "Working directory missing" }
        val logical = source.length()
        require(logical in 1..MAX_LOGICAL_BYTES) { "Invalid ARC3 mirror size" }
        require(destination.parentFile!!.usableSpace > logical + RESERVE_BYTES) {
            "Insufficient verified free space; sparse allocation preflight required"
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
