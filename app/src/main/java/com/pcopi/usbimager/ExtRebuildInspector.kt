package com.pcopi.usbimager

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale

/**
 * v0.2.7 READ-ONLY migration preflight.
 * Reads the actual MBR partitions, EXT superblocks and (optionally) highest
 * allocated block in the group bitmaps. NEVER repairs, shrinks or writes.
 *
 * A block-allocation high-water mark is NOT a minimum shrink size:
 * resize2fs/e2fsck must be run offline on a COPY with filesystem-aware
 * relocation, journal handling and metadata verification.
 */
object ExtRebuildInspector {
    data class Partition(
        val number: Int,
        val startLba: Long,
        val endLba: Long,
        val typeCode: String,
        val filesystem: String,
        val filesystemBytes: Long?,
        val allocatedBytes: Long?,
        val freeBytes: Long?,
        val uuid: String?,
        val volumeName: String?,
        val cleanState: Boolean?,
        val journalRecoveryRequired: Boolean?,
        val blockBytes: Int?,
        val groups: Long?,
        val highAllocatedBlock: Long?,
        val notes: List<String>
    }

    data class Report(
        val usbBytes: Long,
        val sectorBytes: Int,
        val layout: String,
        val partitions: List<Partition>,
        val issues: List<String>,
        val targetBytes: Long?
    )

    fun inspect(
        manager: UsbManager,
        usbDevice: UsbDevice,
        targetBytes: Long?,
        onProgress: (String) -> Unit,
        cancelled: () -> Boolean = { false }
    ): Report = UsbRawDevice.open(manager, usbDevice).use { disk ->
        disk.init()
        val bs=disk.blockSize
        val size=disk.capacityBytes
        if(bs!=512 && bs!=4096)throw IOException("USB Block Size $bs ยังไม่รองรับ")
        val whole = DiskReader(disk, bs, disk.blockDevice.blocks, cancelled)
        val mbr=whole.read(0,0L,bs.toLong(),512)
        if(u16(mbr,510)!=0xaa55)throw IOException("ไม่พบ MBR signature 55 AA")
        val entries=(0 until 4).mapNotNull { i ->
            val off=446+i*16
            val id=u8(mbr,off+4)
            val first=u32(mbr,off+8)
            val count=u32(mbr,off+12)
            if(id==0 || count==0L)null else Triple(i+1,id,first to count)
        }
        val issues=mutableListOf<String>()
        if(entries.any { it.second == 0xee }) {
            issues.add("ตรวจพบ GPT • รุ่นนี้รายงาน MBR โดยละเอียดเท่านั้น ยังไม่ประเมินการย่อ GPT")
            return@use Report(size,bs,"GPT",emptyList(),issues,targetBytes)
        }
        if(entries.isEmpty()) issues.add("ไม่มี Partition ใน MBR")
        val found=mutableListOf<Partition>()
        for ((number,typeCode,loc) in entries){
            if(cancelled())throw IOException("ยกเลิกการตรวจ")
            val first=loc.first
            val count=loc.second
            val end=first+count-1
            onProgress("ตรวจพาร์ทิชัน $number • LBA $first")
            val code="0x%02X".format(Locale.US,typeCode)
            if(first<=0 || end>=whole.blocks || end<first){
                issues.add("Partition $number อยู่นอกขอบเขต USB")
                continue
            }
            if(typeCode in listOf(0x05,0x0f,0x85)){
                issues.add("Partition $number เป็น Extended MBR ยังไม่รองรับ")
                continue
            }
            val notes=mutableListOf<String>()
            try {
                val sb=whole.read(first,1024L,count*bs.toLong(),1024)
                if(u16(sb,56)!=0xef53){
                    val vbr=whole.read(first,0L,bs.toLong(),512)
                    val fileSystem=when{
                        String(vbr,82,8,Charsets.US_ASCII).startsWith("FAT32")->"FAT32"
                        String(vbr,54,8,Charsets.US_ASCII).startsWith("FAT16")->"FAT16"
                        String(vbr,3,8,Charsets.US_ASCII)=="EXFAT   "->"exFAT"
                        else->"Unknown"
                    }
                    found.add(Partition(number,first,end,code,fileSystem,null,null,
                        null,null,null,null,null,null,null,null,
                        listOf("พาร์ทิชันนี้ไม่ใช่ EXT; ยังไม่สแกนบล็อกที่จัดสรร")))
                    continue
                }
                val log=u32(sb,24)
                if(log>4L)throw IOException("EXT block size ไม่รองรับ")
                val blockSize=1024 shl log.toInt()
                val incompat=u32(sb,96)
                val ro=u32(sb,100)
                val compat=u32(sb,92)
                val uses64=(incompat and 0x80L)!=0L
                val totalBlocks=u32(sb,4)+(if(uses64)u32(sb,0x150) shl 32 else 0L)
                val freeBlocks=u32(sb,12)+(if(uses64)u32(sb,0x158) shl 32 else 0L)
                val fsBytes=totalBlocks*blockSize.toLong()
                val partitionBytes=count*bs.toLong()
                if(totalBlocks<=0 || freeBlocks>totalBlocks || fsBytes>partitionBytes)
                    throw IOException("จำนวน EXT blocks ผิดปกติ")
                val allocated=(totalBlocks-freeBlocks)*blockSize.toLong()
                val free=freeBlocks*blockSize.toLong()
                val groupsBlocks=u32(sb,32)
                val firstData=u32(sb,20)
                if(groupsBlocks<=0 || firstData>=totalBlocks)
                    throw IOException("EXT group geometry ผิดปกติ")
                val groups=(totalBlocks-firstData+groupsBlocks-1)/groupsBlocks
                val state=u16(sb,58)
                val clean=(state and 1)==1
                val needsRecovery=(incompat and 4L)!=0L
                val label=String(sb,120,16,Charsets.UTF_8).trim('\u0000',' ')
                val uuid=uuid(sb,104)
                val fs=when {
                    (incompat and 0x40L)!=0L ->"EXT4"
                    (compat and 4L)!=0L ->"EXT3"
                    else ->"EXT2"
                }
                if(!clean)notes.add("EXT filesystem ไม่ได้อยู่ในสถานะ clean")
                if(needsRecovery)notes.add("EXT journal ต้อง replay บนสำเนาก่อนพิจารณาย่อ")
                if((ro and 0x400L)!=0L)
                    notes.add("ใช้ metadata checksums ซึ่งแอปยังไม่ได้ตรวจสอบ CRC ทั้งหมด")
                if((incompat and 0x10L)!=0L)
                    notes.add("ใช้ meta_bg; งดวิเคราะห์ Block Bitmap")
                if((ro and 0x200L)!=0L)
                    notes.add("ใช้ bigalloc; งดวิเคราะห์ Block Bitmap")
                if((incompat and 0x8000L)!=0L)
                    notes.add("ใช้ inline_data; งดวิเคราะห์ Block Bitmap")
                if(groups>512)
                    notes.add("จำนวนกลุ่มมากเกินขอบเขตสแกนที่ตั้งไว้")
                val supportedBitmap=(incompat and (0x10L or 0x8000L))==0L &&
                    (ro and 0x200L)==0L && groups<=512L && blockSize>=bs &&
                    groupsBlocks<=blockSize.toLong()*8L

                val highest=if(supportedBitmap) {
                    try {
                        scanHighestAllocated(whole, first, partitionBytes, sb,
                            blockSize,totalBlocks,firstData,groupsBlocks,groups,
                            uses64, cancelled, onProgress,number)
                    } catch(e:Exception) {
                        notes.add("อ่าน Block Bitmap ไม่ครบ: "+(e.message ?: e.javaClass.simpleName))
                        null
                    }
                }else null
                if(highest!=null)notes.add("บล็อกที่จัดสรรสูงสุด ≠ ขนาดย่อขั้นต่ำ เพราะต้องย้าย Metadata")
                notes.add("การสร้าง USB 14 GB ต้องทำบนสำเนาเท่านั้นและตรวจบูตจริง")
                found.add(Partition(number,first,end,code,fs,fsBytes,allocated,
                    free,uuid,label,clean,needsRecovery,blockSize,groups,
                    highest,notes))
            }catch(e:Exception) {
                found.add(Partition(number,first,end,code,"ตรวจไม่สำเร็จ",
                    null,null,null,null,null,null,null,null,null,null,
                    listOf(e.message ?: e.javaClass.simpleName)))
            }
        }
        if(found.any { it.journalRecoveryRequired==true || it.cleanState==false })
            issues.add("พบ EXT ที่ไม่ Clean หรือรอ Journal Recovery • ห้ามซ่อม/ย่อบนต้นฉบับ")
        issues.add("ยังไม่เปิดการเขียนหรือย่อพาร์ทิชัน • ผลนี้ใช้เพื่อวางแผนสร้างสำเนาบูตที่เล็กลง")
        Report(size,bs,"MBR",found,issues,targetBytes)
    }

    private fun scanHighestAllocated(
        reader: DiskReader,
        part:Long,
        partitionBytes:Long,
        sb:ByteArray,
        blockSize:Int,
        totalBlocks:Long,
        firstData:Long,
        groupBlocks:Long,
        groups:Long,
        uses64:Boolean,
        cancelled:()->Boolean,
        progress:(String)->Unit,
        partitionNumber:Int
    ):Long? {
        val descriptorSize=if(uses64)u16(sb,254).coerceAtLeast(64) else 32
        if(descriptorSize !in 32..256 || descriptorSize>blockSize)
            throw IOException("EXT group descriptor format ไม่รองรับ")
        val descStart=if(blockSize==1024)2048L else blockSize.toLong()
        for(group in groups-1 downTo 0L){
            if(cancelled())throw IOException("ยกเลิกการตรวจ")
            if(group%32L==0L)progress("EXT P$partitionNumber • ตรวจบิตแมปกลุ่ม "+(group+1)+"/"+groups)
            val descriptor=reader.read(part,descStart+group*descriptorSize,
                partitionBytes,descriptorSize)
            val flags=u16(descriptor,18)
            if((flags and 0x2)!=0)
                throw IOException("พบ Block Bitmap กลุ่ม $group ยังไม่ Initialize")
            val bitmapBlock=u32(descriptor,0)+
                (if(uses64 && descriptorSize>=64)u32(descriptor,32) shl 32 else 0L)
            if(bitmapBlock<=0 || bitmapBlock>=totalBlocks)
                throw IOException("Block Bitmap pointer ผิดปกติ (กลุ่ม $group)")
            val b=reader.read(part,bitmapBlock*blockSize.toLong(),
                partitionBytes,blockSize)
            val groupStart=firstData+group*groupBlocks
            val validBits=minOf(groupBlocks,totalBlocks-groupStart).toInt()
            for(bit in validBits-1 downTo 0){
                if((u8(b,bit/8) and (1 shl (bit%8)))!=0)
                    return groupStart+bit.toLong()
            }
        }
        return null
    }

    private class DiskReader(
        val disk:UsbRawDevice,val bs:Int,val blocks:Long,val cancelled:()->Boolean
    ) {
        private val cache=LinkedHashMap<Long,ByteArray>(64,0.75f,true)
        fun read(partLba:Long, offset:Long, partitionBytes:Long, length:Int):ByteArray {
            if(offset<0 || length<0 || offset>partitionBytes-length)
                throw IOException("พยายามอ่านนอกพาร์ทิชัน")
            val output=ByteArray(length)
            var at=0
            while(at<length){
                if(cancelled())throw IOException("ยกเลิกการตรวจ")
                val byteOffset=partLba*bs.toLong()+offset+at
                val lba=byteOffset/bs
                if(lba<0 || lba>=blocks)throw IOException("LBA นอกขอบเขต")
                val sector=sector(lba)
                val within=(byteOffset%bs).toInt()
                val n=minOf(bs-within,length-at)
                sector.copyInto(output,at,within,within+n)
                at+=n
            }
            return output
        }
        private fun sector(lba:Long):ByteArray {
            cache[lba]?.let{return it}
            val result=ByteArray(bs)
            var failure:Exception?=null
            for(attempt in 1..3){
                try{
                    disk.blockDevice.read(lba,ByteBuffer.wrap(result))
                    if(cache.size>=128){
                        val i=cache.entries.iterator()
                        if(i.hasNext()){i.next();i.remove()}
                    }
                    cache[lba]=result
                    return result
                }catch(e:Exception) {
                    failure=e
                    if(attempt<3)Thread.sleep(250L)
                }
            }
            throw IOException("อ่าน USB LBA $lba ไม่สำเร็จ",failure)
        }
    }

    private fun uuid(b:ByteArray,start:Int):String {
        val h=(0 until 16).joinToString("") { "%02x".format(Locale.US,u8(b,start+it)) }
        return h.substring(0,8)+"-"+h.substring(8,12)+"-"+h.substring(12,16)+
            "-"+h.substring(16,20)+"-"+h.substring(20)
    }
    private fun u8(b:ByteArray,i:Int)=b[i].toInt() and 0xff
    private fun u16(b:ByteArray,i:Int)=u8(b,i) or (u8(b,i+1) shl 8)
    private fun u32(b:ByteArray,i:Int):Long=
        u8(b,i).toLong() or (u8(b,i+1).toLong() shl 8) or
            (u8(b,i+2).toLong() shl 16) or (u8(b,i+3).toLong() shl 24)
}