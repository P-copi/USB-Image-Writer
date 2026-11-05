package com.pcopi.usbimager

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Read-only verification of the boot partition headers, never writes source USB. */
internal object CompactBootHeadersAudit {
    data class Report(val passed:Boolean,val text:String)
    private fun hex(data:ByteArray,start:Int,length:Int)=
        (start until start+length).joinToString("") { "%02X".format(java.util.Locale.US,data[it].toInt() and 255) }
    private fun sha(data:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString("") { "%02x".format(it) }
    fun run(manager:UsbManager,device:UsbDevice):Report {
        val out=StringBuilder("=== Compact Boot Headers Audit v0.2.33 ===\n")
        out.append("READ ONLY: USB sectors 0, 2048, 104450, 206850; no IMG created\n")
        try {
            UsbRawDevice.open(manager,device).use { usb ->
                usb.init()
                if(usb.blockSize!=512) return Report(false,out.append("BLOCKED: sector size != 512").toString())
                val lbas=longArrayOf(0L,2048L,104450L,206850L)
                val blocks=ArrayList<ByteArray>()
                for(lba in lbas) {
                    if(lba>=usb.capacityBytes/512L) return Report(false,out.append("BLOCKED: LBA out of range").toString())
                    val buf=ByteBuffer.allocate(512)
                    usb.blockDevice.read(lba,buf)
                    val bytes=buf.array()
                    blocks.add(bytes)
                    out.append("LBA ").append(lba).append(" SHA-256: ").append(sha(bytes)).append('\n')
                }
                val mbr=blocks[0]
                if(hex(mbr,510,2)!="55AA") return Report(false,out.append("BLOCKED: MBR signature").toString())
                val p1=blocks[1]
                out.append("P1 boot signature: ").append(hex(p1,510,2)).append('\n')
                out.append("P1 FAT marker: ").append(String(p1.copyOfRange(82,90),Charsets.US_ASCII)).append('\n')
                val p2=blocks[2]
                val p3=blocks[3]
                out.append("P2 EXT magic: ").append(hex(p2,56,2)).append('\n')
                out.append("P3 EXT magic: ").append(hex(p3,56,2)).append('\n')
                val ok=hex(p1,510,2)=="55AA" && hex(p2,56,2)=="53EF" && hex(p3,56,2)=="53EF"
                out.append(if(ok) "PASS: FAT boot signature and EXT superblock magic detected.\n"
                    else "BLOCKED: boot/filesystem signatures differ from expected.\n")
                out.append("EXPORT BLOCKED: full P1/P2 byte copy, bootloader compatibility and final IMG verification pending.")
                return Report(ok,out.toString())
            }
        } catch(e:Exception) {
            return Report(false,out.append("BLOCKED: "+(e.message?:e.javaClass.simpleName)).toString())
        }
    }
}
