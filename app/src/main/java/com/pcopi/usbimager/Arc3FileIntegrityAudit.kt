package com.pcopi.usbimager

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Read-only EXT4 regular-file content audit; fails closed on unsupported entries. */
internal object Arc3FileIntegrityAudit {
    data class Report(val passed:Boolean,val text:String)
    private data class Entry(val mode:Int,val size:Long)
    private fun debugfs(bin:File,img:File,command:String,dir:File):String {
        val p=ProcessBuilder(bin.absolutePath,"-R",command,img.absolutePath)
            .directory(dir).redirectErrorStream(true).start()
        val output=StringBuilder()
        val reader=Thread {
            p.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if(output.length<1000000) output.append(line).append('\n')
                }
            }
        }
        reader.start()
        try {
            if(!p.waitFor(10,TimeUnit.MINUTES)) throw IOException("debugfs timeout")
            reader.join(1000)
            if(p.exitValue()!=0) throw IOException("debugfs failed: "+command.take(50))
            return output.toString()
        } finally {
            if(p.isAlive) p.destroyForcibly()
            reader.join(500)
        }
    }
    private fun manifest(bin:File,img:File,dir:File):Map<String,Entry> {
        val result=linkedMapOf<String,Entry>()
        val queue=ArrayDeque<Pair<String,Int>>()
        queue.add("/" to 0)
        while(queue.isNotEmpty()) {
            val (folder,depth)=queue.removeFirst()
            if(depth>32) throw IOException("Directory depth limit")
            val output=debugfs(bin,img,"ls -p "+folder,dir)
            for(line in output.lineSequence()) {
                if(!line.startsWith("/")) continue
                val fields=line.split('/')
                if(fields.size<7) throw IOException("Malformed listing")
                val mode=fields[2].toIntOrNull(8) ?: throw IOException("Bad file mode")
                val name=fields[5]
                if(name=="." || name=="..") continue
                if(name.isBlank() || name.any { it=='/' || it=='\n' || it=='\r' || it=='"' || it=='\\' || it==' ' })
                    throw IOException("Unsupported filename")
                val type=mode and 61440
                if(type!=32768 && type!=16384)
                    throw IOException("Unsupported special file or symlink")
                val path=if(folder=="/") "/"+name else folder+"/"+name
                // debugfs ls -p may omit the size field for directories.
                // Only regular files require a numeric size; fail closed otherwise.
                val size=if(type==16384) 0L else
                    fields.getOrNull(6)?.trim()?.toLongOrNull()
                        ?: throw IOException("Bad file size at "+path+
                            " (field="+fields.getOrNull(6)?.take(32)+")")
                if(size<0L) throw IOException("Negative file size at "+path)
                if(result.put(path,Entry(type,size))!=null) throw IOException("Duplicate path")
                if(result.size>20000) throw IOException("Too many files")
                if(type==16384) queue.add(path to (depth+1))
            }
        }
        if(result.isEmpty()) throw IOException("Empty manifest")
        return result
    }
    private fun digest(file:File):String {
        val md=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf=ByteArray(65536)
            while(true) {
                val n=input.read(buf)
                if(n<0) break
                md.update(buf,0,n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    fun run(context:Context):Report {
        val dir=File(context.filesDir,"arc3-workspace-v0221")
        val mirror=File(context.filesDir,"legacy-arc3-mirror-v0220/arc3-mirror.ext4")
        val working=File(dir,"arc3-working.ext4")
        val temp=File(dir,"audit-v0227-temp.bin")
        val out=StringBuilder("=== ARC3 Recursive SHA-256 Audit v0.2.27 ===\n")
        out.append("READ ONLY: ARC3 images; USB NOT OPENED\n")
        if(!mirror.isFile || !working.isFile || working.length()!=2147483648L ||
            mirror.length()<=working.length() || temp.exists())
            return Report(false,out.append("BLOCKED: image sizes or audit workspace invalid").toString())
        try {
            val bin=File(context.applicationInfo.nativeLibraryDir,"libdebugfs.so")
            if(!bin.isFile || !bin.canExecute()) throw IOException("debugfs unavailable")
            val a=manifest(bin,mirror,dir)
            val b=manifest(bin,working,dir)
            out.append("Manifest entries: ").append(a.size).append(" / ").append(b.size).append('\n')
            if(a!=b) throw IOException("Recursive directory/file manifest mismatch")
            var checked=0
            var total=0L
            for((path,entry) in a.toSortedMap()) {
                if(entry.mode!=32768) continue
                var expected=""
                for(img in listOf(mirror,working)) {
                    if(temp.exists()) throw IOException("Temp file exists")
                    if(dir.usableSpace<entry.size+268435456L)
                        throw IOException("Insufficient space for file extraction")
                    debugfs(bin,img,"dump "+path+" "+temp.absolutePath,dir)
                    if(!temp.isFile || temp.length()!=entry.size)
                        throw IOException("File extraction failed")
                    val sha=digest(temp)
                    if(img==mirror) expected=sha
                    else if(sha!=expected) throw IOException("SHA-256 mismatch at "+path)
                    if(!temp.delete()) throw IOException("Cannot remove temp")
                }
                checked++
                total+=entry.size
            }
            out.append("Regular files SHA-256 MATCH: ").append(checked).append('\n')
            out.append("File bytes per image: ").append(total).append('\n')
            out.append("PASS: supported regular files and recursive directory manifest match\n")
            return Report(true,out.toString())
        } catch(e:Exception) {
            return Report(false,out.append("BLOCKED: ").append(e.message).toString())
        } finally {
            if(temp.exists()) temp.delete()
        }
    }
}
