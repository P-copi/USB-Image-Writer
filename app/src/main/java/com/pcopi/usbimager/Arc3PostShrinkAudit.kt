package com.pcopi.usbimager

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Read-only metadata audit after shrinking ARC3. Not a full file-content hash audit. */
internal object Arc3PostShrinkAudit {
    data class Report(val passed: Boolean, val text: String)
    private data class Output(val exit: Int, val body: String)
    private fun run(bin: File, args: List<String>, dir: File): Output {
        if (!bin.isFile || !bin.canExecute()) throw IOException("Missing " + bin.name)
        val process=ProcessBuilder(listOf(bin.absolutePath)+args)
            .directory(dir).redirectErrorStream(true).start()
        val output=StringBuilder()
        val drain=thread(isDaemon=true) {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if(output.length<12000) output.append(line.take(400)).append('\n')
                        }
                    }
                }
            }
        }
        try {
            if(!process.waitFor(12,TimeUnit.MINUTES))
                throw IOException("Audit timed out")
            drain.join(1000)
            return Output(process.exitValue(),synchronized(output){output.toString()})
        } finally {
            if(process.isAlive) process.destroyForcibly()
            drain.join(500)
        }
    }
    fun run(context: Context): Report {
        val dir=File(context.filesDir,"arc3-workspace-v0221")
        val workspace=File(dir,"arc3-working.ext4")
        val mirror=File(context.filesDir,"legacy-arc3-mirror-v0220/arc3-mirror.ext4")
        val out=StringBuilder("=== ARC3 Post-shrink Metadata Audit v0.2.25 ===\n")
        out.append("READ ONLY: no USB, no resize, no file modification\n")
        if(!workspace.isFile || !mirror.isFile || workspace.canonicalPath==mirror.canonicalPath)
            return Report(false,out.append("BLOCKED: source or workspace missing").toString())
        if(workspace.length()!=2147483648L || mirror.length()<=workspace.length())
            return Report(false,out.append("BLOCKED: unexpected workspace/mirror size").toString())
        try {
            val libs=context.applicationInfo.nativeLibraryDir
            val fsck=File(libs,"libe2fsck.so")
            val debug=File(libs,"libdebugfs.so")
            val originalCheck=run(fsck,listOf("-f","-n",mirror.absolutePath),dir)
            val workingCheck=run(fsck,listOf("-f","-n",workspace.absolutePath),dir)
            out.append("Original fsck exit: ").append(originalCheck.exit).append('\n')
            out.append("Workspace fsck exit: ").append(workingCheck.exit).append('\n')
            if(originalCheck.exit!=0 || workingCheck.exit!=0)
                return Report(false,out.append("BLOCKED: fsck not clean").toString())
            val sourceStats=run(debug,listOf("-R","stats",mirror.absolutePath),dir)
            val targetStats=run(debug,listOf("-R","stats",workspace.absolutePath),dir)
            if(sourceStats.exit!=0 || targetStats.exit!=0)
                return Report(false,out.append("BLOCKED: debugfs stats failed").toString())
            fun field(body:String, name:String):String? =
                Regex("(?m)^"+Regex.escape(name)+":\\s*(.+)$")
                    .find(body)?.groupValues?.get(1)?.trim()
            val names=listOf("Filesystem UUID","Filesystem volume name",
                "Filesystem features")
            for(name in names) {
                val a=field(sourceStats.body,name)
                val b=field(targetStats.body,name)
                out.append(name).append(": source=").append(a)
                    .append(" | workspace=").append(b).append('\n')
                if(a==null || b==null || a!=b)
                    return Report(false,out.append("BLOCKED: metadata mismatch or missing").toString())
            }
            val sourceRoot=run(debug,listOf("-R","ls -p /",mirror.absolutePath),dir)
            val targetRoot=run(debug,listOf("-R","ls -p /",workspace.absolutePath),dir)
            if(sourceRoot.exit!=0 || targetRoot.exit!=0)
                return Report(false,out.append("BLOCKED: root listing failed").toString())
            // debugfs ls -p records: /inode/mode/uid/gid/name/size/
            // Inode IDs, UID/GID, file sizes and physical allocation are not
            // appropriate to compare as a raw line after filesystem resize.
            // Compare only the exact filename and file-type bits here.
            fun entries(body: String): Map<String,String> {
                val parsed=linkedMapOf<String,String>()
                for(line in body.lineSequence()) {
                    if(!line.startsWith("/")) continue
                    val parts=line.split('/')
                    if(parts.size<6) throw IOException("Malformed debugfs ls -p record")
                    val mode=parts[2].toIntOrNull(8)
                        ?: throw IOException("Invalid mode in debugfs listing")
                    val name=parts[5]
                    if(name.isBlank()) throw IOException("Blank filename in listing")
                    if(parsed.put(name,(mode and 61440).toString())!=null)
                        throw IOException("Duplicate root entry: " + name)
                }
                if(parsed.isEmpty()) throw IOException("No parsed root entries")
                return parsed
            }
            val sourceEntries=entries(sourceRoot.body)
            val targetEntries=entries(targetRoot.body)
            val missing=sourceEntries.keys-targetEntries.keys
            val extra=targetEntries.keys-sourceEntries.keys
            val changed=sourceEntries.keys.intersect(targetEntries.keys)
                .filter { sourceEntries[it]!=targetEntries[it] }
            out.append("Root entries: source=").append(sourceEntries.size)
                .append(" workspace=").append(targetEntries.size).append('\n')
            if(missing.isNotEmpty() || extra.isNotEmpty() || changed.isNotEmpty()) {
                out.append("Missing names: ").append(missing.take(15)).append('\n')
                out.append("Extra names: ").append(extra.take(15)).append('\n')
                out.append("Changed types: ").append(changed.take(15)).append('\n')
                return Report(false,out.append("BLOCKED: root names/types mismatch").toString())
            }
            out.append("PASS: fsck, selected EXT4 metadata and root entries match.\n")
            out.append("LIMITATION: file contents and nested directory SHA-256 NOT verified.\n")
            return Report(true,out.toString())
        } catch(e:Exception) {
            return Report(false,out.append("BLOCKED: ").append(e.message).toString())
        }
    }
}
