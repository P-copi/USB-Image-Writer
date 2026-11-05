package com.pcopi.usbimager

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * v0.2.23 experimental shrink: ONLY the disposable v0.2.21 workspace file.
 * The original v0.2.20 mirror remains untouched; no USB device is opened.
 * This experiment is not a compact IMG creator or a bootability test.
 */
internal object Arc3ShrinkExperiment {
    data class Report(val passed: Boolean, val text: String)
    private const val TARGET_BLOCKS = 524288L // 2 GiB with 4 KiB EXT4 blocks
    private data class Result(val exit: Int, val output: String)
    private fun command(context: Context, name: String): File {
        val f=File(context.applicationInfo.nativeLibraryDir, "lib" + name + ".so")
        if(!f.isFile || !f.canExecute()) throw IOException("Missing bundled tool: " + name)
        return f
    }
    private fun runTool(bin: File, args: List<String>, cwd: File, timeoutMinutes: Long): Result {
        val process=ProcessBuilder(listOf(bin.absolutePath)+args)
            .directory(cwd).redirectErrorStream(true).start()
        val log=StringBuilder()
        val drain=thread(isDaemon=true) {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(log) {
                            if(log.length < 16000) log.append(line.take(500)).append('\n')
                        }
                    }
                }
            }
        }
        try {
            val start=System.nanoTime()
            while(!process.waitFor(500,TimeUnit.MILLISECONDS)) {
                // Never forcibly interrupt a live filesystem mutation: leave it
                // to finish unless the timeout makes completion impossible.
                if(TimeUnit.NANOSECONDS.toMinutes(System.nanoTime()-start)>=timeoutMinutes)
                    throw IOException("Tool timeout: " + bin.name)
            }
            drain.join(1500)
            return Result(process.exitValue(),synchronized(log){log.toString()})
        } finally {
            if(process.isAlive) process.destroyForcibly()
            drain.join(500)
        }
    }
    fun run(context: Context): Report {
        val dir=File(context.filesDir,"arc3-workspace-v0221")
        val working=File(dir,"arc3-working.ext4")
        val original=File(context.filesDir,"legacy-arc3-mirror-v0220/arc3-mirror.ext4")
        val report=StringBuilder("=== ARC3 2 GiB Workspace Shrink v0.2.23 ===\n")
        report.append("USB NOT OPENED; original v0.2.20 mirror NOT MODIFIED\n")
        if(!working.isFile || !original.isFile || working.canonicalPath==original.canonicalPath)
            return Report(false,report.append("BLOCKED: Missing isolated workspace/original").toString())
        if(working.length()!=original.length() || working.length()<TARGET_BLOCKS*4096L)
            return Report(false,report.append("BLOCKED: Workspace length invalid or already resized").toString())
        try {
            val check=command(context,"e2fsck")
            val resize=command(context,"resize2fs")
            val before=runTool(check,listOf("-f","-n",working.absolutePath),dir,12)
            report.append("Pre-shrink e2fsck exit: ").append(before.exit).append('\n')
            if(before.exit!=0) return Report(false,report.append(before.output.takeLast(1800))
                .append("\nBLOCKED: Preflight not clean").toString())
            val estimate=runTool(resize,listOf("-P",working.absolutePath),dir,12)
            val minimum=Regex("(?i)estimated minimum size of the filesystem:\\s*(\\d+)")
                .find(estimate.output)?.groupValues?.get(1)?.toLongOrNull()
            report.append("Estimated minimum blocks: ").append(minimum).append('\n')
            if(estimate.exit!=0 || minimum==null || minimum<=0 || minimum*2>=TARGET_BLOCKS)
                return Report(false,report.append("BLOCKED: Minimum estimate lacks 2x safety margin").toString())
            if(dir.usableSpace < 512L*1024L*1024L)
                return Report(false,report.append("BLOCKED: Less than 512 MiB free storage").toString())
            report.append("Running resize2fs on workspace ONLY, target blocks: ")
                .append(TARGET_BLOCKS).append('\n')
            val shrink=runTool(resize,listOf(working.absolutePath,TARGET_BLOCKS.toString()),dir,25)
            report.append("resize2fs exit: ").append(shrink.exit).append('\n')
                .append(shrink.output.takeLast(2400)).append('\n')
            if(shrink.exit!=0) return Report(false,report.append(
                "BLOCKED: Resize failed. Do not reuse working file; original mirror remains.").toString())
            val after=runTool(check,listOf("-f","-n",working.absolutePath),dir,12)
            report.append("Post-shrink e2fsck exit: ").append(after.exit).append('\n')
                .append(after.output.takeLast(1800)).append('\n')
            report.append("Workspace file length: ").append(working.length()).append('\n')
            if(after.exit!=0 || working.length()!=TARGET_BLOCKS*4096L)
                return Report(false,report.append("BLOCKED: Post-shrink validation failed; original preserved").toString())
            report.append("PASS: EXT4 structure checked after 2 GiB shrink. ")
                .append("File-level hashes and USB boot NOT verified.\n")
            return Report(true,report.toString())
        } catch(e:Exception) {
            return Report(false,report.append("BLOCKED: ").append(e.message)
                .append("\nOriginal ARC3 mirror remains available.").toString())
        }
    }
}
