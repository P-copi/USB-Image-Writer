package com.pcopi.usbimager

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * v0.2.22: non-destructive EXT4 preflight on the existing v0.2.21 working copy.
 * No USB handles, no repair, no resize, and no writes to any ARC3 image.
 */
internal object Arc3ReadOnlyPreflight {
    data class Report(val passed: Boolean, val text: String)
    private data class Run(val exit: Int, val output: String)
    private fun binary(context: Context, name: String): File {
        val f = File(context.applicationInfo.nativeLibraryDir, "lib" + name + ".so")
        if (!f.isFile || !f.canExecute()) throw IOException("Missing executable: " + name)
        return f
    }
    private fun execute(bin: File, args: List<String>, cwd: File,
                        cancelled: () -> Boolean): Run {
        val p = ProcessBuilder(listOf(bin.absolutePath) + args)
            .directory(cwd).redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = thread(isDaemon = true) {
            runCatching {
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(out) {
                            if (out.length < 12000) out.append(line.take(400)).append('\n')
                        }
                    }
                }
            }
        }
        try {
            val start = System.nanoTime()
            while (!p.waitFor(400, TimeUnit.MILLISECONDS)) {
                if (cancelled() || Thread.currentThread().isInterrupted)
                    throw IOException("Cancelled")
                if (TimeUnit.NANOSECONDS.toMinutes(System.nanoTime() - start) >= 12L)
                    throw IOException("EXT4 preflight timed out")
            }
            reader.join(1500)
            return Run(p.exitValue(), synchronized(out) { out.toString() })
        } finally {
            if (p.isAlive) p.destroyForcibly()
            reader.join(500)
        }
    }
    fun run(context: Context, cancelled: () -> Boolean = { false }): Report {
        val dir = File(context.filesDir, "arc3-workspace-v0221")
        val image = File(dir, "arc3-working.ext4")
        val original = File(context.filesDir,
            "legacy-arc3-mirror-v0220/arc3-mirror.ext4")
        val text = StringBuilder("=== ARC3 EXT4 Read-only Preflight v0.2.22 ===\n")
        text.append("USB: NOT OPENED; original ARC3: NOT MODIFIED\n")
        if (!image.isFile || !original.isFile)
            return Report(false, text.append("BLOCKED: Missing v0.2.21 workspace or v0.2.20 mirror").toString())
        if (image.length() != original.length() || image.length() < 1024L * 1024L)
            return Report(false, text.append("BLOCKED: Invalid logical length").toString())
        try {
            if (cancelled()) throw IOException("Cancelled")
            val fsck = execute(binary(context, "e2fsck"),
                listOf("-f", "-n", image.absolutePath), dir, cancelled)
            text.append("e2fsck -fn exit: ").append(fsck.exit).append('\n')
                .append(fsck.output.takeLast(2800)).append('\n')
            if (fsck.exit != 0)
                return Report(false, text.append("BLOCKED: e2fsck did not pass cleanly; NO RESIZE").toString())
            val estimate = execute(binary(context, "resize2fs"),
                listOf("-P", image.absolutePath), dir, cancelled)
            text.append("resize2fs -P exit: ").append(estimate.exit).append('\n')
                .append(estimate.output.takeLast(1400)).append('\n')
            val blocks = Regex("(?i)estimated minimum size of the filesystem:\\s*(\\d+)")
                .find(estimate.output)?.groupValues?.get(1)?.toLongOrNull()
            if (estimate.exit != 0 || blocks == null || blocks <= 0)
                return Report(false, text.append("BLOCKED: Cannot parse safe minimum; NO RESIZE").toString())
            text.append("Estimated minimum blocks: ").append(blocks).append('\n')
            text.append("PASS: Read-only preflight only. EXT4 NOT SHRUNK.\n")
            return Report(true, text.toString())
        } catch (e: Exception) {
            return Report(false, text.append("BLOCKED: ").append(e.message).toString())
        }
    }
}
