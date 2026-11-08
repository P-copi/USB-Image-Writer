package com.pcopi.usbimager

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Offline assembly only. Never opens or writes a USB device. */
internal object CompactImgAssembler {
 data class Report(val passed:Boolean,val text:String)
 private const val PREFIX=105906176L
 private const val ARC3=2147483648L
 private const val TOTAL=PREFIX+ARC3
 private fun u32(b:ByteArray,p:Int):Long=(0..3).fold(0L){a,i->a or ((b[p+i].toLong() and 255L) shl (8*i))}
 private fun put32(b:ByteArray,p:Int,v:Long){for(i in 0..3)b[p+i]=(v ushr (8*i)).toByte()}
 private fun sha()=MessageDigest.getInstance("SHA-256")
 private fun hex(b:ByteArray)=b.joinToString(""){"%02x".format(it)}
 fun run(context:Context,cancelled:()->Boolean,progress:(String)->Unit):Report {
  val log=StringBuilder("=== Compact IMG Assembly v0.2.37 ===\nOFFLINE: no USB access, originals read-only\n")
  val prefix=File(context.filesDir,"compact-boot-prefix-v0234/boot-prefix.bin")
  val arc3=File(context.filesDir,"arc3-workspace-v0221/arc3-working.ext4")
  val mirror=File(context.filesDir,"legacy-arc3-mirror-v0220/arc3-mirror.ext4")
  val dir=File(context.filesDir,"compact-img-v0237")
  val partial=File(dir,"arc-compact.partial")
  val final=File(dir,"arc-compact.img")
  try {
   if(!prefix.isFile||prefix.length()!=PREFIX||!arc3.isFile||arc3.length()!=ARC3||!mirror.isFile||mirror.length()<=ARC3)throw java.io.IOException("Preserved files missing or unexpected size")
   if(final.exists())throw java.io.IOException("Existing IMG preserved; no overwrite")
   if(!dir.exists()&&!dir.mkdirs())throw java.io.IOException("Cannot create output folder")
   if(dir.usableSpace<TOTAL+268435456L)throw java.io.IOException("Insufficient free space")
   if(partial.exists()&&!partial.delete())throw java.io.IOException("Old partial cannot be removed")
   val mbr=ByteArray(512)
   prefix.inputStream().use{if(it.read(mbr)!=512)throw java.io.IOException("MBR truncated")}
   if((mbr[510].toInt() and 255)!=85||(mbr[511].toInt() and 255)!=170)throw java.io.IOException("Invalid MBR")
   val starts=longArrayOf(2048,104448,206848)
   val sizes=longArrayOf(102400,102400,59918336)
   for(i in 0..2){val p=446+i*16;if((mbr[p+4].toInt() and 255)!=131||u32(mbr,p+8)!=starts[i]||u32(mbr,p+12)!=sizes[i])throw java.io.IOException("MBR P${i+1} mismatch")}
   if(mbr.sliceArray(494..509).any{it.toInt()!=0})throw java.io.IOException("Unexpected P4")
   put32(mbr,446+2*16+12,4194304L)
   val buf=ByteArray(65536)
   RandomAccessFile(partial,"rw").use{out->
    out.setLength(TOTAL)
    fun copy(file:File,size:Long){
     file.inputStream().buffered().use{input->
      var left=size
      while(left>0){
       if(cancelled())throw java.io.InterruptedIOException("Cancelled")
       val n=input.read(buf,0,minOf(left,buf.size.toLong()).toInt())
       if(n<=0)throw java.io.IOException("Truncated input")
       out.write(buf,0,n);left-=n
      }
     }
    }
    progress("1/3 Copying boot prefix");copy(prefix,PREFIX)
    out.seek(0);out.write(mbr);out.seek(PREFIX)
    progress("2/3 Copying ARC3");copy(arc3,ARC3)
    out.fd.sync()
   }
   progress("3/3 Checking IMG SHA-256 and partitions")
   val hash=sha()
   RandomAccessFile(partial,"r").use{img->
    if(img.length()!=TOTAL)throw java.io.IOException("Wrong IMG length")
    val first=ByteArray(512);img.readFully(first)
    if(!first.contentEquals(mbr)||u32(first,446+2*16+12)!=4194304L)throw java.io.IOException("MBR patch failed")
    img.seek(PREFIX)
    val a=sha();var left=ARC3
    while(left>0){if(cancelled())throw java.io.InterruptedIOException("Cancelled");val n=img.read(buf,0,minOf(left,buf.size.toLong()).toInt());if(n<=0)throw java.io.IOException("IMG truncated");a.update(buf,0,n);left-=n}
    val b=sha()
    arc3.inputStream().buffered().use{input->while(true){val n=input.read(buf);if(n<0)break;b.update(buf,0,n)}}
    if(!a.digest().contentEquals(b.digest()))throw java.io.IOException("ARC3 mismatch")
    img.seek(0)
    while(true){if(cancelled())throw java.io.InterruptedIOException("Cancelled");val n=img.read(buf);if(n<0)break;hash.update(buf,0,n)}
   }
   progress("Checking offline image structure")
   CompactImgStructureGate.validate(partial)
   if(!partial.renameTo(final))throw java.io.IOException("Could not finalize IMG")
   log.append("IMG bytes: ").append(TOTAL).append("\nSHA-256: ").append(hex(hash.digest())).append("\nPASS: IMG assembled and ARC3 verified. NOT BOOT TESTED.")
   return Report(true,log.toString())
  }catch(e:Exception){partial.delete();return Report(false,log.append("BLOCKED: ").append(e.message?:e.javaClass.simpleName).toString())}
 }
}
