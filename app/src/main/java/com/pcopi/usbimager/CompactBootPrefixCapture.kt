package com.pcopi.usbimager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Copies source disk prefix (MBR, gap, P1, P2) into app-private staging; source is read-only. */
internal object CompactBootPrefixCapture {
    data class Report(val passed:Boolean,val text:String)
    private const val SECTORS=206848L
    private const val BLOCK=512
    private const val BYTES=SECTORS*BLOCK
    private fun digest()=MessageDigest.getInstance("SHA-256")
    private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
    fun run(context:Context,manager:UsbManager,device:UsbDevice,cancelled:()->Boolean):Report {
        val report=StringBuilder("=== Compact Boot Prefix Capture v0.2.34 ===\n")
        report.append("SOURCE USB READ ONLY; copies MBR + gap + P1 + P2\n")
        val folder=File(context.filesDir,"compact-boot-prefix-v0234")
        val temp=File(folder,"boot-prefix.partial")
        val output=File(folder,"boot-prefix.bin")
        try {
            if(!folder.exists() && !folder.mkdirs()) throw java.io.IOException("Cannot create staging folder")
            if(folder.usableSpace < BYTES+16L*1024*1024)
                throw java.io.IOException("Insufficient app-private free space")
            UsbRawDevice.open(manager,device).use { usb ->
                usb.init()
                if(usb.blockSize!=BLOCK || usb.capacityBytes<BYTES)
                    throw java.io.IOException("Unsupported source sector size/capacity")
                // libaums driver supports heap ByteBuffer; direct buffers can crash on array access.
                val bytes=ByteArray(128*BLOCK)
                val buf=ByteBuffer.wrap(bytes)
                val hash=digest()
                FileOutputStream(temp).use { out ->
                    var lba=0L
                    while(lba<SECTORS) {
                        if(cancelled()) throw java.io.InterruptedIOException("Cancelled")
                        val count=minOf(128L,SECTORS-lba).toInt()
                        buf.clear()
                        buf.limit(count*BLOCK)
                        usb.blockDevice.read(lba,buf)
                        buf.flip()
                        if(buf.remaining()!=count*BLOCK) throw java.io.IOException("USB short read at LBA $lba")
                        out.write(bytes,0,count*BLOCK)
                        hash.update(bytes,0,count*BLOCK)
                        lba+=count
                    }
                    out.fd.sync()
                }
                if(temp.length()!=BYTES) throw java.io.IOException("Captured byte count mismatch")
                val sourceHash=hex(hash.digest())
                val verify=digest()
                temp.inputStream().buffered().use { input ->
                    val bytes=ByteArray(65536)
                    while(true) {
                        if(cancelled()) throw java.io.InterruptedIOException("Cancelled")
                        val n=input.read(bytes)
                        if(n<0) break
                        verify.update(bytes,0,n)
                    }
                }
                val savedHash=hex(verify.digest())
                if(sourceHash!=savedHash) throw java.io.IOException("Saved prefix SHA-256 mismatch")
                if(output.exists()) throw java.io.IOException("Previous verified boot prefix exists; refusing overwrite")
                if(!temp.renameTo(output)) throw java.io.IOException("Cannot finalize staging file")
                report.append("Captured bytes: ").append(BYTES).append('\n')
                report.append("Source stream SHA-256: ").append(sourceHash).append('\n')
                report.append("Saved file SHA-256: ").append(savedHash).append('\n')
                report.append("PASS: boot prefix captured and verified locally.\n")
                report.append("EXPORT BLOCKED: IMG assembly and boot verification still pending.")
                return Report(true,report.toString())
            }
        } catch(e:Exception) {
            temp.delete()
            return Report(false,report.append("BLOCKED: ").append(e.message?:e.javaClass.simpleName).toString())
        }
    }
}
