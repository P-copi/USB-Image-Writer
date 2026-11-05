package com.pcopi.usbimager
import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

internal object CompactImgExport {
 data class Report(val passed:Boolean,val text:String)
 const val SIZE=2253389824L
 const val EXPECTED="1a5320c9f0811c61d024d662700d1ff3c960f4ce5181b1461a83d4007d29fcee"
 fun file(c:Context)=File(c.filesDir,"compact-img-v0237/arc-compact.img")
 fun hash(input:java.io.InputStream):Pair<Long,String>{
  val md=MessageDigest.getInstance("SHA-256");val buf=ByteArray(65536);var total=0L
  input.use{while(true){val n=it.read(buf);if(n<0)break;md.update(buf,0,n);total+=n}}
  return total to md.digest().joinToString(""){"%02x".format(it)}
 }
 fun verify(c:Context):Report{
  val b=StringBuilder("=== Compact IMG Verify v0.2.38 ===\nOFFLINE ONLY\n")
  try{
   val f=file(c)
   if(!f.isFile||f.length()!=SIZE)throw java.io.IOException("IMG missing or wrong size")
   val m=ByteArray(512)
   RandomAccessFile(f,"r").use{it.readFully(m)}
   fun u32(p:Int)=(0..3).fold(0L){v,i->v or ((m[p+i].toLong() and 255L) shl (i*8))}
   if((m[510].toInt() and 255)!=85||(m[511].toInt() and 255)!=170)throw java.io.IOException("Invalid MBR")
   val starts=longArrayOf(2048,104448,206848)
   val sizes=longArrayOf(102400,102400,4194304)
   for(i in 0..2){val p=446+i*16;if((m[p+4].toInt() and 255)!=131||u32(p+8)!=starts[i]||u32(p+12)!=sizes[i])throw java.io.IOException("P${i+1} mismatch")}
   if(m.sliceArray(494..509).any{it.toInt()!=0})throw java.io.IOException("Unexpected P4")
   val (size,digest)=hash(f.inputStream().buffered())
   if(size!=SIZE||digest!=EXPECTED)throw java.io.IOException("IMG checksum mismatch")
   b.append("IMG bytes: ").append(size).append("\nSHA-256: ").append(digest).append("\nPASS: geometry and SHA-256 verified. NOT BOOT TESTED.")
   return Report(true,b.toString())
  }catch(e:Exception){return Report(false,b.append("BLOCKED: ").append(e.message).toString())}
 }
}