package com.pcopi.usbimager

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale

/**
 * Read-only FAT16/FAT32 boot-volume explorer over Android USB Host. This does
 * not mount USB through Android and NEVER invokes blockDevice.write().
 * ext4/exFAT/NTFS are shown by type but not exposed as files in this version.
 */
class BootDiskReader private constructor(private val raw: UsbRawDevice) : Closeable {
    data class Entry(
        val name: String,
        val directory: Boolean,
        val cluster: Long,
        val size: Long
    )
    data class Volume(
        val number: Int, val startLba: Long, val endLba: Long,
        val fileSystem: String, val isReadable: Boolean,
        val label: String,
        val warning: String = ""
    )
    data class Identity(
        val title: String,
        val evidence: List<String>,
        val version: String?,
        val confidence: String
    )
    data class Scan(
        val style: String, val totalBytes: Long,
        val volumes: List<Volume>, val identity: Identity,
        val notes: List<String>
    )
    private data class Fat(
        val volume: Volume,
        val fat32: Boolean,
        val bytesPerSector: Int,
        val sectorsPerCluster: Int,
        val firstFat: Long,
        val fatSectors: Long,
        val dataStart: Long,
        val rootCluster: Long,
        val rootDirLba: Long,
        val rootDirSectors: Int,
        val maxClusters: Long
    )

    private val blockSize = raw.blockSize
    private val totalBlocks = raw.blockDevice.blocks
    private val fatCache = mutableMapOf<Int, Fat>()
    private val devices = mutableListOf<Volume>()
    private var scheme: String = "ไม่ทราบ"
    private val notes = mutableListOf<String>()
    private val cache = mutableMapOf<Long, ByteArray>()
    private var status: Identity = Identity("ยังระบุชนิด Bootloader ไม่ได้", emptyList(), null, "ไม่พบหลักฐาน")

    companion object {
        @Throws(IOException::class)
        fun open(manager: UsbManager, device: UsbDevice): BootDiskReader {
            val raw = UsbRawDevice.open(manager, device)
            try {
                raw.init()
                return BootDiskReader(raw)
            } catch (e: Exception) {
                raw.close()
                throw e
            }
        }
    }

    fun scan(): Scan {
        devices.clear()
        notes.clear()
        fatCache.clear()
        cache.clear()
        if (blockSize !in listOf(512, 4096))
            throw IOException("รองรับ USB Block Size 512/4096 B ในรุ่นนี้")

        val mbr = sector(0)
        if (u16(mbr,510) != 0xaa55)
            throw IOException("ไม่มี MBR signature อาจเป็น Superfloppy หรือระบบดิสก์พิเศษ")
        val mbrList = (0 until 4).mapNotNull { index ->
            val p = 446 + index * 16
            val type=u8(mbr,p+4)
            val start=u32(mbr,p+8)
            val count=u32(mbr,p+12)
            if (type==0 || count==0L) null else Triple(type,start,count)
        }
        if (mbrList.any { it.first==0xee }) {
            scheme="GPT"
            val hdr=sector(1)
            if (String(hdr.copyOfRange(0,8),Charsets.US_ASCII)!="EFI PART")
                throw IOException("Protective MBR แต่ GPT header อ่านไม่สำเร็จ")
            val table=u64(hdr,72)
            val count=u32(hdr,80).toInt()
            val size=u32(hdr,84).toInt()
            if (count !in 1..256 || size !in 128..256 || size % 8 != 0 ||
                table < 2 || table + (count.toLong()*size+blockSize-1)/blockSize >= totalBlocks)
                throw IOException("GPT partition table มีขนาดผิดปกติ")
            val bytes=ByteArray(count*size)
            var copied=0
            var next=table
            while (copied<bytes.size) {
                val s=sector(next++)
                val n=minOf(s.size,bytes.size-copied)
                s.copyInto(bytes,copied,0,n)
                copied+=n
            }
            for (i in 0 until count) {
                val p=i*size
                if ((0 until 16).all { bytes[p+it]==0.toByte() }) continue
                val first=u64(bytes,p+32)
                val last=u64(bytes,p+40)
                addVolume(i+1,first,last)
            }
        } else {
            scheme="MBR"
            mbrList.forEachIndexed { i, item ->
                if (item.first in listOf(5, 15, 133)) {
                    notes.add("Extended partitions ยังไม่รองรับการเปิดดูในรุ่นนี้")
                } else addVolume(i+1,item.second,item.second+item.third-1)
            }
        }

        val evidence=mutableListOf<String>()
        var arcVersion:String?=null
        var arc=false
        var grub=false
        var uefi=false
        for (v in devices) {
            if (!v.isReadable) continue
            try {
                val root=list(v.number, null)
                val version=root.firstOrNull { it.name.equals("ARC-VERSION",true) && !it.directory }
                if (version!=null) {
                    arc=true
                    arcVersion=readText(v.number,version,256).trim().lineSequence().firstOrNull()
                        ?.take(80)?.takeIf { it.isNotBlank() }
                    evidence.add("พาร์ทิชัน "+v.number+": พบ ARC-VERSION")
                }
                if (root.any{it.name.equals("ARC-BUILD",true)}) {
                    arc=true
                    evidence.add("พาร์ทิชัน "+v.number+": พบ ARC-BUILD")
                }
                val efi=root.firstOrNull{it.directory && it.name.equals("EFI",true)}
                if (efi!=null) {
                    uefi=true
                    evidence.add("พาร์ทิชัน "+v.number+": พบโฟลเดอร์ EFI")
                }
                val boot=root.firstOrNull { it.directory && it.name.equals("boot",true) }
                if (boot!=null) {
                    val grubFolder=list(v.number,boot).firstOrNull {
                        it.directory && it.name.equals("grub",true)
                    }
                    if(grubFolder!=null) {
                        val cfg=list(v.number,grubFolder).firstOrNull{
                            !it.directory && it.name.equals("grub.cfg",true)
                        }
                        if(cfg!=null){
                            grub=true
                            val snippet=readText(v.number,cfg,16384)
                            if(snippet.contains("Arc DSM Mode",true) ||
                                snippet.contains("dsm_arc",true) ||
                                snippet.contains("ARC_CMDLINE",true)) {
                                arc=true
                                evidence.add("พาร์ทิชัน "+v.number+": GRUB config มีสัญลักษณ์ Arc Loader")
                            } else evidence.add("พาร์ทิชัน "+v.number+": พบ boot/grub/grub.cfg")
                        }
                    }
                }
            } catch (e: Exception) {
                notes.add("อ่านไฟล์พาร์ทิชัน "+v.number+" ไม่ครบ: "+
                    (e.message ?: e.javaClass.simpleName))
            }
        }
        status=when{
            arc->Identity("Arc Loader (พบหลักฐานในไฟล์)", evidence,arcVersion,"สูง")
            grub->Identity("GRUB Bootloader (ยังไม่ระบุชุดระบบ)",evidence,null,"ปานกลาง")
            uefi->Identity("USB แบบ UEFI (ยังไม่ระบุระบบ)",evidence,null,"เบื้องต้น")
            else->Identity("ยังระบุชนิด Bootloader ไม่ได้",evidence,null,"ไม่ยืนยัน")
        }
        if (devices.isEmpty()) notes.add("ไม่พบพาร์ทิชันที่อ่านโครงสร้างได้")
        if (devices.none { it.isReadable })
            notes.add("ยังไม่รองรับการเปิดรายการไฟล์ exFAT/NTFS/EXT ในรุ่นนี้")
        return Scan(scheme,totalBlocks*blockSize.toLong(),devices.toList(),status,notes.toList())
    }

    /** null directory = volume root, otherwise directory cluster Entry. */
    fun list(volumeNumber:Int,directory:Entry?):List<Entry> {
        val fat=fatCache[volumeNumber]
            ?:throw IOException("พาร์ทิชันนี้ไม่ใช่ FAT16/FAT32 ที่รองรับ")
        if(directory!=null && !directory.directory)throw IOException("ไม่ใช่โฟลเดอร์")
        val rawData=when{
            directory!=null->readChain(fat,directory.cluster,8*1024*1024)
            fat.fat32->readChain(fat,fat.rootCluster,8*1024*1024)
            else->{
                val output=ByteArrayOutputStream()
                for(i in 0 until fat.rootDirSectors){
                    output.write(sector(fat.rootDirLba+i))
                }
                output.toByteArray()
            }
        }
        val result=mutableListOf<Entry>()
        val longPieces=mutableMapOf<Int,String>()
        for(at in 0 until rawData.size/32) {
            val start=at*32
            val first=u8(rawData,start)
            if(first==0) break
            if(first==0xe5) { longPieces.clear(); continue }
            val attr=u8(rawData,start+11)
            if(attr==0x0f){
                val order=first and 31
                if((first and 0x40)!=0)longPieces.clear()
                if(order in 1..20)longPieces[order]=lfnPart(rawData,start)
                continue
            }
            if((attr and 0x08)!=0) {longPieces.clear();continue}
            val shortName=String(rawData.copyOfRange(start,start+8),Charsets.US_ASCII)
                .trim().replace('\u0005','\u00e5')
            val ext=String(rawData.copyOfRange(start+8,start+11),Charsets.US_ASCII).trim()
            val fallback=if(ext.isEmpty())shortName else shortName+"."+ext
            val longName=if(longPieces.isNotEmpty() &&
                (1..longPieces.size).all{longPieces.containsKey(it)})
                (1..longPieces.size).joinToString(""){longPieces[it].orEmpty()} else fallback
            longPieces.clear()
            val name=longName.trim()
            if(name.isEmpty() || name=="." || name=="..")continue
            val folder=(attr and 0x10)!=0
            val cluster=((u16(rawData,start+20).toLong() shl 16) or
                u16(rawData,start+26).toLong())
            val fileSize=u32(rawData,start+28)
            result.add(Entry(name,folder,cluster,fileSize))
            if(result.size>=600)break
        }
        return result.sortedWith(compareByDescending<Entry>{it.directory}.thenBy{it.name.lowercase(Locale.ROOT)})
    }

    fun readText(volumeNumber:Int,file:Entry,limit:Int=8192):String {
        if(file.directory)throw IOException("เลือกไฟล์ข้อความ ไม่ใช่โฟลเดอร์")
        if(file.size>128L*1024L && limit>128*1024)throw IOException("ไฟล์ใหญ่เกินขอบเขตพรีวิว")
        val fat=fatCache[volumeNumber]?:throw IOException("ระบบไฟล์ไม่รองรับ")
        if(file.size==0L)return ""
        val bytes=readChain(fat,file.cluster,minOf(file.size.toInt().coerceAtLeast(0),limit))
        val count=minOf(bytes.size,file.size.coerceAtMost(limit.toLong()).toInt())
        // Only return short readable text; do not render binary executables as text.
        if ((0 until count).count{bytes[it]==0.toByte()} > count/10)
            throw IOException("ไฟล์ไบนารี ไม่รองรับพรีวิวข้อความ")
        return String(bytes,0,count,Charsets.UTF_8)
    }

    private fun addVolume(number:Int,start:Long,end:Long) {
        if(start<=0 || end<start || end>=totalBlocks){
            notes.add("พาร์ทิชัน "+number+" อยู่นอกขอบเขต USB")
            return
        }
        val b=sector(start)
        val bytesPerSector=u16(b,11)
        val sectorsPerCluster=u8(b,13)
        val reserved=u16(b,14)
        val fats=u8(b,16)
        val fat16=u16(b,22)
        val fat32size=u32(b,36)
        val fat32=String(b.copyOfRange(82,90),Charsets.US_ASCII).startsWith("FAT32")
        val isFat16=String(b.copyOfRange(54,62),Charsets.US_ASCII).startsWith("FAT16")
        val label=when{
            fat32->String(b.copyOfRange(71,82),Charsets.US_ASCII).trim()
            isFat16->String(b.copyOfRange(43,54),Charsets.US_ASCII).trim()
            else->""
        }
        var name=when{
            fat32->"FAT32"
            isFat16->"FAT16"
            String(b.copyOfRange(3,11),Charsets.US_ASCII)=="EXFAT   "->"exFAT"
            String(b.copyOfRange(3,11),Charsets.US_ASCII)=="NTFS    "->"NTFS"
            else->"Unknown"
        }
        if(name=="Unknown") {
            try {
                val superblock=sector(start+(1024/blockSize))
                val at=if(blockSize==4096)1024 else 0
                if(u16(superblock,at+56)==0xef53)name="EXT2/3/4"
            }catch(_:Exception){}
        }
        val validFat=(fat32||isFat16) && bytesPerSector==blockSize &&
            sectorsPerCluster in 1..128 && (sectorsPerCluster and (sectorsPerCluster-1))==0 &&
            reserved>0 && fats in 1..2 &&
            (if(fat32)fat32size>0 else fat16>0)
        if(validFat){
            val fatSectors=if(fat32)fat32size else fat16.toLong()
            val rootEntries=u16(b,17)
            val rootSectors=if(fat32)0 else (rootEntries*32+blockSize-1)/blockSize
            val rootLba=start+reserved+fats.toLong()*fatSectors
            val dataStart=rootLba+rootSectors
            val available=end-dataStart+1
            val clusters=available/sectorsPerCluster
            if(dataStart<=end && clusters>0 &&
                (!fat32 || u32(b,44) in 2..clusters+1)){
                val volume=Volume(number,start,end,name,true,label)
                devices.add(volume)
                fatCache[number]=Fat(volume,fat32,blockSize,sectorsPerCluster,
                    start+reserved,fatSectors,dataStart,
                    if(fat32)u32(b,44) else 0L,rootLba,rootSectors,clusters+2)
                return
            }
        }
        devices.add(Volume(number,start,end,name,false,label,
            if(name.startsWith("FAT")) "FAT boot sector ไม่สมบูรณ์" else "อ่านชื่อระบบไฟล์ได้ แต่ยังเปิดไฟล์ไม่ได้"))
    }

    private fun readChain(fat:Fat,first:Long,limitBytes:Int):ByteArray {
        if(first<2 || first>=fat.maxClusters)throw IOException("หมายเลข Cluster ไม่ถูกต้อง")
        val bytes=ByteArrayOutputStream()
        val visited=mutableSetOf<Long>()
        var current=first
        val maxBytes=limitBytes.coerceAtMost(8*1024*1024)
        val unit=fat.sectorsPerCluster*blockSize
        while (bytes.size()<maxBytes) {
            if(current<2 || current>=fat.maxClusters || !visited.add(current))
                throw IOException("FAT Cluster chain ผิดปกติ")
            val lba=fat.dataStart+(current-2)*fat.sectorsPerCluster
            for(i in 0 until fat.sectorsPerCluster){
                if(bytes.size()>=maxBytes)break
                val sec=sector(lba+i)
                bytes.write(sec,0,minOf(sec.size,maxBytes-bytes.size()))
            }
            if(bytes.size()>=maxBytes)break
            val next=nextCluster(fat,current)
            if((fat.fat32 && next>=0x0ffffff8L) ||
                (!fat.fat32 && next>=0xfff8L))break
            if(next==0L || next==1L)throw IOException("FAT chain มี Cluster ว่างกลางไฟล์")
            current=next
        }
        return bytes.toByteArray()
    }

    private fun nextCluster(fat:Fat,cluster:Long):Long {
        val at=cluster*(if(fat.fat32)4 else 2)
        val lba=fat.firstFat+at/blockSize
        if(lba>=fat.firstFat+fat.fatSectors)throw IOException("FAT index นอกขอบเขต")
        val bytes=sector(lba)
        val off=(at%blockSize).toInt()
        if(off+(if(fat.fat32)4 else 2)>blockSize)throw IOException("FAT entry ข้าม Sector")
        return if(fat.fat32)u32(bytes,off) and 0x0fffffff else u16(bytes,off).toLong()
    }

    private fun lfnPart(data:ByteArray,start:Int):String {
        val chars=StringBuilder()
        for(offset in listOf(1,3,5,7,9,14,16,18,20,22,24,28,30)){
            val u=u16(data,start+offset)
            if(u==0 || u==0xffff)break
            chars.append(u.toChar())
        }
        return chars.toString()
    }

    private fun sector(lba:Long):ByteArray {
        if(lba<0 || lba>=totalBlocks)throw IOException("อ่าน LBA นอกขอบเขต "+lba)
        cache[lba]?.let{return it}
        val data=ByteArray(blockSize)
        var error:Exception?=null
        for (attempt in 0..2) {
            try {
                raw.blockDevice.read(lba,ByteBuffer.wrap(data))
                if(cache.size>=192)cache.clear()
                cache[lba]=data
                return data
            } catch(e:Exception){error=e; if(attempt<2)Thread.sleep(300L)}
        }
        throw IOException("อ่าน USB LBA "+lba+" ล้มเหลว",error)
    }

    override fun close(){ raw.close() }
    private fun u8(b:ByteArray,p:Int)=b[p].toInt() and 0xff
    private fun u16(b:ByteArray,p:Int)=u8(b,p) or (u8(b,p+1) shl 8)
    private fun u32(b:ByteArray,p:Int):Long=
        (u8(b,p).toLong() or (u8(b,p+1).toLong() shl 8) or
            (u8(b,p+2).toLong() shl 16) or (u8(b,p+3).toLong() shl 24)) and 0xffffffffL
    private fun u64(b:ByteArray,p:Int)=u32(b,p) or (u32(b,p+4) shl 32)
}