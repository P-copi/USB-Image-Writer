package com.pcopi.usbimager

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** USB source MBR inspection only. No writes and no changes to saved ARC3 images. */
internal object CompactSourceMbrAudit {
    data class Report(val passed:Boolean,val text:String)
    private fun u32(b:ByteBuffer,at:Int)=b.getInt(at).toLong() and 0xffffffffL
    fun run(manager:UsbManager,device:UsbDevice):Report {
        val out=StringBuilder("=== Compact Source MBR Audit v0.2.32 ===\n")
        out.append("SOURCE USB READ ONLY: sector 0 only; no writes\n")
        try {
            UsbRawDevice.open(manager,device).use { usb ->
                usb.init()
                if(usb.blockSize!=512) return Report(false,out.append("BLOCKED: requires 512-byte sectors; got "+usb.blockSize).toString())
                val sector=ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
                usb.blockDevice.read(0L,sector)
                if((sector.get(510).toInt() and 255)!=0x55 ||
                   (sector.get(511).toInt() and 255)!=0xaa)
                    return Report(false,out.append("BLOCKED: MBR signature invalid").toString())
                out.append("MBR signature: 55 AA\n")
                val starts=longArrayOf(2048L,104448L,206848L)
                val lengths=longArrayOf(102400L,102400L,0L)
                var valid=true
                for(i in 0..3) {
                    val base=446+i*16
                    val type=sector.get(base+4).toInt() and 255
                    val start=u32(sector,base+8)
                    val count=u32(sector,base+12)
                    out.append("P").append(i+1).append(": type=0x")
                        .append("%02X".format(java.util.Locale.US,type))
                        .append(" start=").append(start).append(" sectors=").append(count).append('\n')
                    if(i<3 && (start!=starts[i] || count==0L)) valid=false
                    if(i<2 && count!=lengths[i]) valid=false
                    if(i==2 && count<4194304L) valid=false
                    if(i==3 && (type!=0 || start!=0L || count!=0L)) valid=false
                }
                out.append("Disk signature bytes: ")
                for(i in 440..443) out.append("%02X".format(java.util.Locale.US,sector.get(i).toInt() and 255))
                out.append('\n')
                out.append(if(valid) "PASS: recorded partition starts match source MBR.\n" else "BLOCKED: recorded partition layout differs from source MBR.\n")
                out.append("EXPORT STILL BLOCKED: P1/P2 data, boot code and ARC3 placement not yet validated.")
                return Report(valid,out.toString())
            }
        } catch(e:Exception) {
            return Report(false,out.append("BLOCKED: "+(e.message?:e.javaClass.simpleName)).toString())
        }
    }
}
