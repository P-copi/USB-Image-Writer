package com.pcopi.usbimager

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.CRC32

/** Read-only GPT/MBR preflight. Never claims filesystem shrink is safe. */
object SmartDiskInspector {
    data class Partition(
        val number: Int, val first: Long, val last: Long,
        val type: String, val fs: String, val volumeBytes: Long?
    )
    data class Report(
        val blockSize: Int, val totalBlocks: Long, val scheme: String,
        val partitions: List<Partition>, val warnings: List<String>,
        val healthyLayout: Boolean, val minTargetBlocks: Long, val detail: String
    ) {
        val totalBytes get() = totalBlocks * blockSize.toLong()
        fun fits(targetBytes: Long): Boolean = healthyLayout &&
            targetBytes > 0 && targetBytes % blockSize == 0L &&
            targetBytes / blockSize >= minTargetBlocks &&
            targetBytes <= totalBytes
    }

    fun inspect(manager: UsbManager, device: UsbDevice): Report =
        UsbRawDevice.open(manager,device).use { raw ->
            raw.init()
            val bs=raw.blockSize
            val blocks=raw.blockDevice.blocks
            if (bs != 512 && bs != 4096) throw IOException("ยังไม่รองรับ block size "+bs)
            if (blocks < 100) throw IOException("USB capacity ไม่ถูกต้อง")
            fun read(lba:Long):ByteArray {
                if (lba < 0 || lba >= blocks) throw IOException("LBA นอกช่วง "+lba)
                val out=ByteArray(bs)
                var last:Exception?=null
                for(attempt in 1..3) {
                    try { raw.blockDevice.read(lba,ByteBuffer.wrap(out)); return out }
                    catch(e:Exception){last=e; if(attempt<3) Thread.sleep(250L)}
                }
                throw IOException("อ่าน LBA "+lba+" ไม่สำเร็จ",last)
            }
            fun detect(first:Long,last:Long):Pair<String,Long?> {
                if (first<=0 || first>=blocks || last>=blocks) return "Invalid" to null
                return try {
                    val b=read(first)
                    val name=String(b.copyOfRange(3,11),Charsets.US_ASCII)
                    when {
                        name=="EXFAT   " -> "exFAT" to u64(b,72)*bs.toLong()
                        name=="NTFS    " -> "NTFS" to u64(b,40)*bs.toLong()
                        String(b.copyOfRange(82,90),Charsets.US_ASCII).startsWith("FAT32") ->
                            "FAT32" to u32(b,32)*bs.toLong()
                        String(b.copyOfRange(54,59),Charsets.US_ASCII)=="FAT16" ->
                            "FAT16" to ((if(u16(b,19)>0)u16(b,19).toLong() else u32(b,32))*bs)
                        else -> {
                            val sbLba=first+1024/bs
                            val sb=read(sbLba)
                            val off=if(bs==4096)1024 else 0
                            if(u16(sb,off+56)==0xef53) {
                                val size=1024L shl u32(sb,off+24).toInt().coerceIn(0,6)
                                "EXT2/3/4" to u32(sb,off+4)*size
                            } else "Unknown" to null
                        }
                    }
                } catch(e:Exception) { "Unreadable header" to null }
            }
            val warnings=mutableListOf<String>()
            val mbr=read(0)
            if(u8(mbr,510)!=0x55 || u8(mbr,511)!=0xaa) {
                return@use Report(bs,blocks,"Unknown",emptyList(),
                    listOf("ไม่พบ MBR signature; ยังไม่รองรับการย่อ"),false,
                    blocks,"MBR signature not found")
            }
            val entries=(0 until 4).map { i ->
                val o=446+i*16
                Triple(u8(mbr,o+4),u32(mbr,o+8),u32(mbr,o+12))
            }.filter{it.first!=0 && it.third>0}
            if(entries.any{it.first==0xee}) {
                val h=read(1)
                if(String(h.copyOfRange(0,8),Charsets.US_ASCII)!="EFI PART")
                    throw IOException("Protective MBR แต่ GPT Header เสีย")
                val headerSize=u32(h,12).toInt()
                if(headerSize !in 92..bs)throw IOException("GPT header size ไม่ถูกต้อง")
                val headCopy=h.copyOfRange(0,headerSize)
                headCopy.fill(0,16,20)
                val headerOk=crc(headCopy)==u32(h,16)
                val entryLba=u64(h,72)
                val entryCount=u32(h,80).toInt()
                val entrySize=u32(h,84).toInt()
                if(entryCount !in 1..256 || entrySize !in 128..256 || entrySize%8!=0)
                    throw IOException("GPT entry format ยังไม่รองรับ")
                val tableBytes=entryCount.toLong()*entrySize
                val sectors=((tableBytes+bs-1)/bs).toInt()
                if(entryLba<2 || entryLba+sectors>=blocks)throw IOException("GPT table นอกช่วง")
                val table=ByteArray(sectors*bs)
                for(i in 0 until sectors)read(entryLba+i).copyInto(table,i*bs)
                val tableOk=crc(table.copyOfRange(0,tableBytes.toInt()))==u32(h,88)
                val partitions=mutableListOf<Partition>()
                for(i in 0 until entryCount){
                    val at=i*entrySize
                    if((0 until 16).all{table[at+it]==0.toByte()})continue
                    val start=u64(table,at+32)
                    val end=u64(table,at+40)
                    if(start<2 || end<start || end>=blocks) {
                        warnings.add("GPT partition "+(i+1)+" invalid range")
                        continue
                    }
                    val fs=detect(start,end)
                    partitions.add(Partition(i+1,start,end,"GPT type "+u32(table,at).toString(16),fs.first,fs.second))
                }
                if(!headerOk)warnings.add("GPT primary CRC ไม่ถูกต้อง")
                if(!tableOk)warnings.add("GPT table CRC ไม่ถูกต้อง")
                if(u64(h,32)!=blocks-1)warnings.add("GPT backup header ไม่อยู่ท้ายดิสก์")
                if(partitions.isEmpty())warnings.add("ไม่พบ GPT partitions")
                val sorted=partitions.sortedBy{it.first}
                if(sorted.zipWithNext().any{it.first.last>=it.second.first})warnings.add("Partition overlap")
                val last=partitions.maxOfOrNull{it.last}?:blocks-1
                // Space for relocated secondary GPT array + header, with one gap sector.
                val minimum=last+sectors.toLong()+3L
                if(minimum>blocks)warnings.add("Partition กินพื้นที่ถึงส่วนท้าย GPT")
                Report(bs,blocks,"GPT",partitions,warnings,warnings.isEmpty(),
                    minimum,"GPT header CRC="+headerOk+
                        "; table CRC="+tableOk+
                        "; source backup LBA="+u64(h,32)+
                        "; lastUsable="+u64(h,48)+
                        "; backup GPT sectors="+sectors)
            } else {
                val partitions=entries.mapIndexed{i,e->
                    val (id,first,count)=e
                    val last=first+count-1L
                    if(last>=blocks)warnings.add("MBR partition "+(i+1)+" out of bounds")
                    if(id in listOf(0x05,0x0f,0x85))warnings.add("Extended MBR ยังไม่รองรับ")
                    val fs=detect(first,last)
                    Partition(i+1,first,last,"MBR type 0x"+id.toString(16),fs.first,fs.second)
                }
                if(partitions.isEmpty())warnings.add("ไม่พบ MBR partition")
                val sorted=partitions.sortedBy{it.first}
                if(sorted.zipWithNext().any{it.first.last>=it.second.first})warnings.add("Partition overlap")
                val last=partitions.maxOfOrNull{it.last}?:blocks-1
                Report(bs,blocks,"MBR",partitions,warnings,warnings.isEmpty(),last+1,
                    "MBR primary partitions="+partitions.size)
            }
        }

    private fun u8(b:ByteArray,i:Int)=b[i].toInt() and 0xff
    private fun u16(b:ByteArray,i:Int)=u8(b,i) or (u8(b,i+1) shl 8)
    private fun u32(b:ByteArray,i:Int)=
        (u8(b,i).toLong() or (u8(b,i+1).toLong() shl 8) or
         (u8(b,i+2).toLong() shl 16) or (u8(b,i+3).toLong() shl 24)) and 0xffffffffL
    private fun u64(b:ByteArray,i:Int)=u32(b,i) or (u32(b,i+4) shl 32)
    private fun crc(b:ByteArray)=CRC32().apply{update(b)}.value
}