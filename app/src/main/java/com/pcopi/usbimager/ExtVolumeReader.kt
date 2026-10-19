package com.pcopi.usbimager

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale

/**
 * Small, bounded, READ-ONLY EXT2/3/4 reader for boot-media inspection.
 * Supports conventional group descriptors, direct/single-indirect block maps
 * and EXT4 extent trees. It never replays journals or writes to USB.
 * Unsupported/inconsistent metadata fails closed instead of guessing.
 */
class ExtVolumeReader(
    private val raw: UsbRawDevice,
    private val firstLba: Long,
    private val lastLba: Long
) {
    data class Entry(val name: String, val inode: Long, val directory: Boolean, val size: Long)
    data class Usage(
        val fs: String, val sizeBytes: Long, val usedBytes: Long,
        val freeBytes: Long, val blockSize: Int, val totalBlocks: Long,
        val freeBlocks: Long, val label: String, val warning: String
    )

    private data class Inode(
        val number: Long, val mode: Int, val size: Long,
        val flags: Long, val blockBytes: ByteArray
    )

    private val sectorBytes = raw.blockSize
    private val partitionBytes = (lastLba-firstLba+1L)*sectorBytes.toLong()
    private val diskCache = LinkedHashMap<Long, ByteArray>(128,0.75f,true)
    private val sb = readBytes(1024L,1024)
    private val bs: Int
    private val inodeSize: Int
    private val blocksPerGroup: Long
    private val inodesPerGroup: Long
    private val firstDataBlock: Long
    private val descBytes: Int
    private val groupDescriptorOffset: Long
    private val totalBlocks: Long
    private val needsJournalRecovery: Boolean
    private val featureIncompat: Long
    val usage: Usage

    init {
        if (u16(sb,56)!=0xef53) throw IOException("ไม่พบ EXT superblock magic")
        val log=u32(sb,24)
        if(log > 4L) throw IOException("EXT block size ไม่รองรับ")
        bs=1024 shl log.toInt()
        if(bs < sectorBytes || bs % sectorBytes!=0)
            throw IOException("EXT block size ไม่ลงตัวกับ USB sector")
        val rev=u32(sb,76)
        inodeSize=if(rev==0L)128 else u16(sb,88)
        if(inodeSize !in 128..1024 || inodeSize>bs || bs%inodeSize!=0)
            throw IOException("EXT inode size ไม่รองรับ")
        blocksPerGroup=u32(sb,32)
        inodesPerGroup=u32(sb,40)
        firstDataBlock=u32(sb,20)
        if(blocksPerGroup<=0 || inodesPerGroup<=0)
            throw IOException("EXT group geometry ไม่ถูกต้อง")
        featureIncompat=u32(sb,96)
        val featureRo=u32(sb,100)
        val is64=(featureIncompat and 0x80L)!=0L
        val hasMetaBg=(featureIncompat and 0x10L)!=0L
        val hasBigAlloc=(featureRo and 0x200L)!=0L
        val hasInline=(featureIncompat and 0x8000L)!=0L
        if(hasMetaBg || hasBigAlloc || hasInline)
            throw IOException("EXT ใช้ feature ที่ยังไม่รองรับ (meta_bg/bigalloc/inline_data)")
        descBytes=if(is64)u16(sb,254).coerceAtLeast(64) else 32
        if(descBytes !in 32..512 || descBytes>bs)
            throw IOException("EXT group descriptor size ไม่ถูกต้อง")
        groupDescriptorOffset=if(bs==1024)2048L else bs.toLong()
        totalBlocks=u32(sb,4)+(if(is64)u32(sb,0x150) shl 32 else 0L)
        val rawFree=u32(sb,12)+(if(is64)u32(sb,0x158) shl 32 else 0L)
        val fsBytes=totalBlocks*bs.toLong()
        if(totalBlocks<=0 || fsBytes>partitionBytes || rawFree>totalBlocks)
            throw IOException("EXT ขนาดใน superblock ไม่สอดคล้องกับ Partition")
        needsJournalRecovery=(featureIncompat and 0x04L)!=0L
        val warnings=mutableListOf<String>()
        if(needsJournalRecovery)
            warnings.add("Filesystem มี journal ที่อาจยังไม่ได้ replay; ข้อมูลที่อ่านอาจไม่เป็นปัจจุบัน")
        if((featureIncompat and 0x10000L)!=0L)
            warnings.add("Filesystem ใช้ casefold/feature พิเศษบางส่วน")
        val name=String(sb.copyOfRange(120,136),Charsets.UTF_8)
            .trim('\u0000',' ')
        val fs=if((featureIncompat and 0x40L)!=0L)"EXT4" else
            if((u32(sb,92) and 0x4L)!=0L)"EXT3" else "EXT2"
        usage=Usage(fs,fsBytes,(totalBlocks-rawFree)*bs.toLong(),
            rawFree*bs.toLong(),bs,totalBlocks,rawFree,name,warnings.joinToString("\n"))
    }

    fun list(directoryInode: Long = 2L): List<Entry> {
        val inode=readInode(directoryInode)
        if(!isDirectory(inode.mode))throw IOException("Inode ไม่ใช่โฟลเดอร์")
        if(inode.size>32L*1024*1024)
            throw IOException("โฟลเดอร์ใหญ่เกินขีดจำกัดที่รองรับ")
        val content=readInodeBytes(inode,minOf(inode.size.toInt(),4*1024*1024))
        val entries=mutableListOf<Entry>()
        var at=0
        while(at+8<=content.size) {
            val id=u32(content,at)
            val rec=u16(content,at+4)
            val len=u8(content,at+6)
            if(rec<8 || rec%4!=0 || at+rec>content.size)
                throw IOException("EXT directory record ไม่ถูกต้อง at "+at)
            if(id!=0L && len>0 && len<=rec-8) {
                val name=String(content,at+8,len,Charsets.UTF_8)
                if(name!="." && name!=".." && name.isNotBlank()) {
                    val fileInode=readInode(id)
                    entries.add(Entry(name,id,isDirectory(fileInode.mode),fileInode.size))
                    if(entries.size>=600)break
                }
            }
            at+=rec
        }
        return entries.sortedWith(compareByDescending<Entry>{it.directory}
            .thenBy{it.name.lowercase(Locale.ROOT)})
    }

    fun readText(inodeNumber:Long, maxBytes:Int=8192):String {
        val inode=readInode(inodeNumber)
        if(isDirectory(inode.mode))throw IOException("เลือกไฟล์ ไม่ใช่โฟลเดอร์")
        if((inode.mode and 0xf000)!=0x8000)
            throw IOException("ไฟล์นี้เป็นชนิดพิเศษ ไม่รองรับพรีวิว")
        if(inode.size==0L)return ""
        if((inode.flags and 0x800L)!=0L)
            throw IOException("ไฟล์ถูกเข้ารหัส ไม่สามารถพรีวิว")
        val n=minOf(inode.size,maxBytes.toLong(),32768L).toInt()
        val data=readInodeBytes(inode,n)
        if(data.isEmpty())return ""
        if(data.count{it==0.toByte()}>data.size/8)
            throw IOException("เป็นไฟล์ไบนารี ไม่ใช่ไฟล์ข้อความ")
        return String(data,Charsets.UTF_8)
    }

    private fun readInode(id:Long):Inode {
        val totalInodes=u32(sb,0)
        if(id<2 || id>totalInodes)throw IOException("EXT inode อยู่นอกช่วง: "+id)
        val group=(id-1L)/inodesPerGroup
        val index=(id-1L)%inodesPerGroup
        val descAt=groupDescriptorOffset+group*descBytes
        val desc=readBytes(descAt,descBytes)
        val inodeTable=u32(desc,8)+
            (if(descBytes>=64)u32(desc,40) shl 32 else 0L)
        if(inodeTable==0L || inodeTable>=totalBlocks)
            throw IOException("EXT inode table ไม่ถูกต้อง")
        val offset=inodeTable*bs.toLong()+index*inodeSize
        val bytes=readBytes(offset,inodeSize)
        val mode=u16(bytes,0)
        val flags=u32(bytes,32)
        val lo=u32(bytes,4)
        val hi=if(inodeSize>=112 && ((mode and 0xf000)==0x8000 ||
            (mode and 0xf000)==0x4000))u32(bytes,108)else 0L
        val size=lo+(hi shl 32)
        if(size<0 || size>partitionBytes)throw IOException("EXT inode size ผิดปกติ")
        return Inode(id,mode,size,flags,bytes.copyOfRange(40,100))
    }

    private fun readInodeBytes(inode:Inode,limit:Int):ByteArray {
        if(limit<=0)return ByteArray(0)
        val total=minOf(limit.toLong(),inode.size).toInt()
        val out=ByteArrayOutputStream(total)
        val blocks=((total.toLong()+bs-1L)/bs).toInt()
        for(logical in 0 until blocks) {
            val physical=physicalBlock(inode,logical.toLong())
            val n=minOf(bs,total-out.size())
            if(physical==0L)out.write(ByteArray(n))
            else {
                val data=readFsBlock(physical)
                out.write(data,0,n)
            }
        }
        return out.toByteArray()
    }

    private fun physicalBlock(inode:Inode,logical:Long):Long {
        if((inode.flags and 0x80000L)!=0L)
            return extentLookup(inode.blockBytes,logical,0)
        if(logical<12) return u32(inode.blockBytes,(logical*4).toInt())
        val per=bs/4L
        if(logical>=12+per)
            throw IOException("EXT indirect tree หลายชั้นยังไม่รองรับ")
        val pointer=u32(inode.blockBytes,48)
        if(pointer==0L)return 0L
        val table=readFsBlock(pointer)
        return u32(table,((logical-12)*4).toInt())
    }

    private fun extentLookup(tree:ByteArray,logical:Long,depthVisited:Int):Long {
        if(depthVisited>5 || tree.size<12 || u16(tree,0)!=0xf30a)
            throw IOException("EXT extent header ไม่รองรับ/ไม่ถูกต้อง")
        val entries=u16(tree,2)
        val capacity=u16(tree,4)
        val depth=u16(tree,6)
        if(entries>capacity || entries> (tree.size-12)/12 || depth>5)
            throw IOException("EXT extent tree ผิดปกติ")
        var best=-1
        for(i in 0 until entries) {
            val at=12+i*12
            val start=u32(tree,at)
            if(start<=logical)best=at else break
        }
        if(best<0)return 0L // sparse hole
        if(depth==0) {
            val first=u32(tree,best)
            val lengthRaw=u16(tree,best+4)
            val length=lengthRaw and 0x7fff
            if(length<=0 || logical>=first+length)return 0L
            // EXT4 unwritten extent: physical blocks must not be exposed.
            if(lengthRaw>32768)return 0L
            val start=(u16(tree,best+6).toLong() shl 32)+u32(tree,best+8)
            return start+logical-first
        }
        val branch=(u16(tree,best+8).toLong() shl 32)+u32(tree,best+4)
        val next=readFsBlock(branch)
        return extentLookup(next,logical,depthVisited+1)
    }

    private fun readFsBlock(number:Long):ByteArray {
        if(number<0 || number>=totalBlocks)
            throw IOException("EXT block อยู่นอกระบบไฟล์: "+number)
        return readBytes(number*bs.toLong(),bs)
    }

    private fun readBytes(offset:Long,count:Int):ByteArray {
        if(offset<0 || count<0 || offset>partitionBytes-count)
            throw IOException("EXT อ่านนอกขอบเขตพาร์ทิชัน")
        val bytes=ByteArray(count)
        var pos=0
        while(pos<count){
            val abs=(firstLba*sectorBytes.toLong())+offset+pos
            val lba=abs/sectorBytes
            val within=(abs%sectorBytes).toInt()
            val data=readSector(lba)
            val n=minOf(data.size-within,count-pos)
            data.copyInto(bytes,pos,within,within+n)
            pos+=n
        }
        return bytes
    }

    private fun readSector(lba:Long):ByteArray {
        diskCache[lba]?.let{return it}
        val data=ByteArray(sectorBytes)
        var last:Exception?=null
        for(attempt in 0..2) {
            try {
                raw.blockDevice.read(lba,ByteBuffer.wrap(data))
                if(diskCache.size>=256){
                    val iter=diskCache.entries.iterator()
                    if(iter.hasNext()){iter.next();iter.remove()}
                }
                diskCache[lba]=data
                return data
            }catch(e:Exception) {
                last=e
                if(attempt<2)Thread.sleep(300L)
            }
        }
        throw IOException("อ่าน EXT LBA "+lba+" ไม่สำเร็จ",last)
    }

    private fun isDirectory(mode:Int)= (mode and 0xf000)==0x4000
    private fun u8(a:ByteArray,i:Int)=a[i].toInt() and 255
    private fun u16(a:ByteArray,i:Int)=u8(a,i) or (u8(a,i+1) shl 8)
    private fun u32(a:ByteArray,i:Int):Long=
        (u8(a,i).toLong() or (u8(a,i+1).toLong() shl 8) or
            (u8(a,i+2).toLong() shl 16) or (u8(a,i+3).toLong() shl 24)) and 0xffffffffL
}