package com.pcopi.usbimager

import android.content.Context
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * v0.2.17: test the real ext4 shrink TOOLCHAIN using a temporary synthetic image
 * stored in app-private storage. Never passes a USB node or user file to a process.
 * Never executes filesystem repair or resize on the original ARC flash drive.
 *
 * If this test fails, the app MUST NOT attempt to resize the ARC3 filesystem.
 */
object Ext4ShrinkSandbox {
    private const val TEST_BYTES = 96L * 1024L * 1024L
    private const val RESIZED_4K_BLOCKS = 8192 // 32 MiB
    private const val LIMIT_OUTPUT = 2600
    private const val STEP_TIMEOUT_SECONDS = 45L

    data class Report(val success: Boolean, val text: String)

    private data class Run(val code: Int, val output: String)

    /**
     * Android 10+ prohibits executing files from writable app data directories.
     * The signed APK carries NDK-built PIE tools in its extracted native library
     * directory as libmke2fs.so, libe2fsck.so and libresize2fs.so.
     * They are executed as standalone files (not loaded with System.loadLibrary).
     */
    private fun locate(context: Context, name: String): File? {
        val bundled = File(context.applicationInfo.nativeLibraryDir, "lib" + name + ".so")
        if (bundled.isFile && bundled.canExecute()) return bundled
        return listOf("/system/bin/", "/vendor/bin/", "/product/bin/", "/system/xbin/")
            .map { File(it + name) }.firstOrNull { it.isFile && it.canExecute() }
    }

    /** Execute an allowlisted SYSTEM binary against an app-owned test image only. */
    private fun execute(
        binary: File, args: List<String>, directory: File, cancelled: () -> Boolean
    ): Run {
        if (cancelled()) throw IOException("ยกเลิกโดยผู้ใช้")
        val proc = ProcessBuilder(listOf(binary.absolutePath) + args)
            .directory(directory)
            .redirectErrorStream(true)
            .start()
        val buffer = ByteArrayOutputStream()
        val drain = thread(isDaemon = true, name = "ext4-tool-output") {
            runCatching {
                proc.inputStream.use { input ->
                    val bytes = ByteArray(1024)
                    while (true) {
                        val len = input.read(bytes)
                        if (len < 0) break
                        synchronized(buffer) {
                            if (buffer.size() < LIMIT_OUTPUT) {
                                buffer.write(bytes, 0, minOf(len, LIMIT_OUTPUT - buffer.size()))
                            }
                        }
                    }
                }
            }
        }
        try {
            val start = System.nanoTime()
            while (true) {
                if (cancelled()) throw IOException("ยกเลิกโดยผู้ใช้")
                if (proc.waitFor(300, TimeUnit.MILLISECONDS)) break
                if (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) > STEP_TIMEOUT_SECONDS)
                    throw IOException("คำสั่ง " + binary.name + " ใช้เวลานานเกิน " +
                        STEP_TIMEOUT_SECONDS + " วินาที")
            }
            drain.join(1200L)
            val output = synchronized(buffer) { buffer.toString("UTF-8") }
            return Run(proc.exitValue(), output)
        } finally {
            if (proc.isAlive) proc.destroyForcibly()
            runCatching { proc.inputStream.close() }
            drain.join(500L)
        }
    }

    /** Run on a worker thread, not on the Android UI thread. */
    fun run(
        context: Context,
        progress: (String) -> Unit,
        cancelled: () -> Boolean = { false }
    ): Report {
        val out = StringBuilder("=== EXT4 Offline Shrink Sandbox • v0.2.18 ===\n")
        out.append("ทดสอบกับ EXT4 สร้างใหม่ในพื้นที่ส่วนตัวของแอปเท่านั้น\n")
        out.append("USB ต้นฉบับ / USB ปลายทาง: ไม่ถูกเปิด ไม่ถูกแก้ไข\n")
        val names = listOf("mke2fs", "e2fsck", "resize2fs")
        val commands = names.map { it to locate(context, it) }.toMap()
        for (name in names) {
            val bin = commands[name]
            out.append(name).append(": ")
                .append(bin?.absolutePath ?: "ไม่พบในเส้นทางระบบที่ตรวจ")
                .append('\n')
        }
        val missing = names.filter { commands[it] == null }
        if (missing.isNotEmpty()) {
            out.append("BLOCKED: เครื่องมือที่ต้องใช้ไม่ครบ: ")
                .append(missing.joinToString(", ")).append('\n')
            out.append("แนวทางต่อ: ตรวจสอบการแตกไฟล์ native libs ของ APK และสิทธิ์ SELinux\n")
            return Report(false, out.toString())
        }

        val baseDir = File(context.filesDir, "ext4-sandbox-v0217")
        if (!baseDir.exists() && !baseDir.mkdirs())
            return Report(false, out.append("ไม่สามารถสร้างพื้นที่ทดสอบของแอป\n").toString())
        val testFile = File(baseDir, "sandbox-test-only.ext4")
        // A stale sandbox file is never reused for arbitrary external input.
        runCatching { testFile.delete() }
        if (testFile.exists())
            return Report(false, out.append("ลบไฟล์ทดสอบเดิมไม่ได้ — ยกเลิกเพื่อความปลอดภัย\n").toString())

        try {
            progress("สร้าง EXT4 จำลองขนาด 96 MiB ในแอป")
            out.append("พื้นที่ว่างก่อนทดสอบ: ")
                .append(FormatUtils.bytes(baseDir.usableSpace)).append('\n')
            RandomAccessFile(testFile, "rw").use { raf ->
                raf.setLength(TEST_BYTES)
                // Only 2 sectors are physically written, letting us probe sparse support.
                raf.seek(0L)
                raf.write(ByteArray(4096))
                raf.seek(TEST_BYTES - 4096L)
                raf.write(ByteArray(4096))
            }
            val blocks = Os.stat(testFile.absolutePath).st_blocks
            val allocated = blocks * 512L
            out.append("ไฟล์จำลอง: ").append(FormatUtils.bytes(testFile.length()))
                .append(" • ใช้พื้นที่จริงก่อน format ≈ ")
                .append(FormatUtils.bytes(allocated)).append('\n')
            if (allocated > 32L * 1024L * 1024L)
                return Report(false, out.append(
                    "BLOCKED: ระบบจัดเก็บไม่รองรับ Sparse File ตามที่ต้องการ\n").toString())

            fun step(title: String, bin: String, args: List<String>): Run {
                if (cancelled()) throw IOException("ยกเลิกโดยผู้ใช้")
                progress(title)
                val selected = commands[bin] ?: throw IOException("ไม่พบ " + bin)
                val result = execute(selected, args, baseDir, cancelled)
                out.append(title).append(" • Exit ").append(result.code).append('\n')
                if (result.output.isNotBlank())
                    out.append(result.output.trim().take(850).replace('\r', ' ')).append('\n')
                if (result.code != 0)
                    throw IOException(title + " ไม่ผ่าน (exit " + result.code + ")")
                return result
            }

            // All commands below target the app-created disposable image only.
            step("1/4 สร้าง EXT4 จำลอง", "mke2fs",
                listOf("-q", "-F", "-t", "ext4", "-b", "4096",
                    "-O", "64bit,metadata_csum,flex_bg", testFile.absolutePath))
            step("2/4 ตรวจ e2fsck แบบไม่แก้ไข", "e2fsck",
                listOf("-f", "-n", testFile.absolutePath))
            step("3/4 ย่อจริงบนไฟล์จำลองเป็น 32 MiB", "resize2fs",
                listOf(testFile.absolutePath, RESIZED_4K_BLOCKS.toString()))
            step("4/4 ตรวจ e2fsck หลังย่อ", "e2fsck",
                listOf("-f", "-n", testFile.absolutePath))
            out.append("SUCCESS: โทรศัพท์เรียกเครื่องมือย่อ EXT4 บนไฟล์จำลองได้\n")
            out.append("หมายเหตุ: ไม่ได้ทดสอบ ARC3 28.57 GB และยังไม่ได้สร้าง Compact Image\n")
            out.append("ถัดไปต้องพิสูจน์การสร้างสำเนา ARC3 แบบ Sparse และ Verify บนสำเนาจริง\n")
            return Report(true, out.toString())
        } catch (error: Exception) {
            out.append("BLOCKED: ")
                .append(error.message ?: error.javaClass.simpleName).append('\n')
            out.append("หากแอปไม่ได้รับสิทธิ์เรียกเครื่องมือระบบ ต้องใช้ e2fsprogs ที่แพ็กมาเอง\n")
            out.append("ยังไม่อนุญาตให้ย่อ ARC3 หรือสร้าง Compact Image รุ่นเก่า\n")
            return Report(false, out.toString())
        } finally {
            // Remove all sandbox bytes even on cancellation and failure.
            runCatching { testFile.delete() }
            runCatching { baseDir.delete() }
        }
    }
}
