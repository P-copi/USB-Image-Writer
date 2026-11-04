package com.pcopi.usbimager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.BitSet
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * v0.2.20 EXPERIMENTAL, READ-ONLY SOURCE: copies required EXT4 blocks from USB
 * into a sparse, app-private regular file, then validates those copied blocks
 * and runs e2fsck -fn on the reconstructed filesystem.
 *
 * Not a full raw backup: unallocated bytes are deliberately not mirrored.
 * Do not enable writing USB or mark this as bootable without separately
 * validating the resized copy and booting a disposable destination USB.
 */
object Arc3SparseMirror {
    data class Result(val success: Boolean, val report: String)
    private const val EXT_BYTES = 4096
    private const val MIN_FREE_AFTER = 3L * 1024 * 1024 * 1024
    private const val MAX_WRITTEN_BYTES = 8L * 1024 * 1024 * 1024
    private const val PROC_TIMEOUT_MINUTES = 15L
    private const val MAX_OUTPUT = 2500
    private const val CHUNK_BLOCKS = 8

    fun create(
        context: Context,
        manager: UsbManager,
        source: UsbDevice,
        progress: (String) -> Unit,
        cancelled: () -> Boolean = { false }
    ): Result {
        val b = StringBuilder("=== ARC3 Sparse Mirror • v0.2.20 (EXPERIMENTAL) ===\n")
        b.append("ต้นฉบับ: READ-ONLY | ไม่เปิด USB ปลายทาง | ยังไม่ใช่ Compact IMG\n")
        b.append("สำเนาชั่วคราวเป็น Sparse EXT4 ในพื้นที่ส่วนตัวของแอป\n")
        val base = File(context.filesDir, "legacy-arc3-mirror-v0220")
        val partial = File(base, "arc3-mirror.partial")
        val finished = File(base, "arc3-mirror.ext4")
        var success = false
        try {
            if (cancelled()) throw IOException("ยกเลิกโดยผู้ใช้")
            if (!base.exists() && !base.mkdirs()) throw IOException("สร้างโฟลเดอร์สำเนาไม่ได้")
            if (finished.exists()) throw IOException(
                "มีสำเนาที่ผ่านการตรวจเดิมอยู่แล้ว — ไม่เขียนทับ: " + finished.absolutePath)
            // A partial file is always disposable; failed runs are never resumed blindly.
            if (partial.exists() && !partial.delete()) throw IOException("ลบสำเนาที่ทำค้างไม่ได้")
            if (base.usableSpace <= MIN_FREE_AFTER + 1024L * 1024L * 1024L)
                throw IOException("พื้นที่ว่างโทรศัพท์ไม่เพียงพอสำหรับสำเนาและพื้นที่สำรอง")

            progress("1/5 ตรวจ MBR, EXT4 และ CRC32C ของ ARC3")
            val r = ExtRebuildInspector.inspect(
                manager, source, null, { s -> progress(s) }, cancelled)
            if (r.layout != "MBR" || r.sectorBytes != 512 || r.partitions.size != 3 ||
                r.partitions.map { it.number } != listOf(1,2,3))
                throw IOException("MBR/Partition layout ไม่ตรงกับ ARC3 3-partition")
            val p1 = r.partitions[0]
            val p2 = r.partitions[1]
            val p3 = r.partitions[2]
            if (p1.filesystem != "FAT32" || p2.filesystem != "EXT2" ||
                p3.filesystem != "EXT4" || p3.startLba != 206848L ||
                p3.endLba != 60125183L ||
                p3.startLba != p2.endLba + 1L ||
                p2.cleanState != true || p3.cleanState != true ||
                p3.journalRecoveryRequired != false || p2.journalRecoveryRequired != false)
                throw IOException("โครงสร้าง ARC3 / สถานะ EXT ไม่ตรงกับต้นฉบับที่รองรับ")
            val audit = Ext4MetadataAudit.inspect(
                manager,source,p3,{ s -> progress(s) },cancelled)
            if (audit.errors.isNotEmpty() || audit.descriptors != audit.groups || audit.groups != 229)
                throw IOException("EXT4 metadata checksum ตรวจไม่ครบ/ไม่ผ่าน: "+
                    audit.errors.take(2).joinToString("; "))
            b.append("ตรวจ CRC32C: ").append(audit.descriptors)
                .append("/").append(audit.groups).append(" group descriptors PASS\n")

            val required = BitSet()
            var totalBlocks = 0
            var blocksRequired = 0
            var blockUninit = 0
            var inodeUninit = 0
            var groups = 0
            var logicalSize = 0L
            var originalDigest = ""
            var writtenBytes = 0L
            UsbRawDevice.open(manager,source).use { usb ->
                usb.init()
                if (usb.blockSize != 512) throw IOException("ต้องเป็น USB 512-byte sectors")
                val startLba = p3.startLba
                val partitionBytes = (p3.endLba - p3.startLba + 1L)*512L
                fun u8(data: ByteArray, at: Int) = data[at].toInt() and 255
                fun u16(data: ByteArray, at: Int) = u8(data,at) or (u8(data,at+1) shl 8)
                fun u32(data: ByteArray, at: Int): Long =
                    u8(data,at).toLong() or (u8(data,at+1).toLong() shl 8) or
                    (u8(data,at+2).toLong() shl 16) or (u8(data,at+3).toLong() shl 24)
                // No guessed/padded bytes: all read failures abort this copy.
                fun readAt(offset: Long, size: Int): ByteArray {
                    if (offset < 0L || size <= 0 || offset > partitionBytes - size)
                        throw IOException("อ่านข้อมูลเกินขอบเขต ARC3")
                    // EXT4 group descriptors are often 64 bytes: align the
                    // physical SCSI read to USB 512-byte sectors, then slice.
                    val firstSector = offset/512L
                    val within = (offset%512L).toInt()
                    val paddedSize = ((within+size+511)/512)*512
                    val raw = ByteArray(paddedSize)
                    var last: Exception? = null
                    for (attempt in 1..2) {
                        if (cancelled()) throw IOException("ยกเลิกการสร้างสำเนา")
                        try {
                            usb.blockDevice.read(startLba + firstSector,
                                ByteBuffer.wrap(raw))
                            return raw.copyOfRange(within,within+size)
                        } catch (e: Exception) {
                            last = e
                            if (attempt == 1) Thread.sleep(150)
                        }
                    }
                    // Explicit error for any failed sector, including known bad source cells.
                    throw IOException("USB READ ERROR ที่ ARC3 offset=$offset / LBA="+
                        (startLba + firstSector),last)
                }
                val sb = readAt(1024L,1024)
                if (u16(sb,0x38) != 0xef53) throw IOException("Superblock signature ไม่ถูกต้อง")
                val incompat = u32(sb,0x60)
                val compat = u32(sb,0x5c)
                val ro = u32(sb,0x64)
                if ((ro and 0x400L) == 0L ||
                    (ro and 0x200L) != 0L ||
                    (incompat and 0x10L) != 0L ||
                    (compat and 0x200L) != 0L)
                    throw IOException("พบ unsupported EXT4 flags: metadata_csum, bigalloc, meta_bg, sparse_super2")
                val blockSize = 1024 shl u32(sb,0x18).toInt()
                if (blockSize != EXT_BYTES) throw IOException("ARC3 นี้ต้องมี EXT block 4096B")
                val blocks = u32(sb,4) +
                    (if ((incompat and 0x80L) != 0L) (u32(sb,0x150) shl 32) else 0L)
                val groupBlocks = u32(sb,0x20).toInt()
                val inodesPerGroup = u32(sb,0x28)
                val inodeSize = u16(sb,0x58)
                val descSize = if ((incompat and 0x80L) != 0L) u16(sb,0xfe) else 32
                val firstData = u32(sb,0x14)
                val reservedGdt = u16(sb,0xce)
                if (blocks <= 0 || blocks*EXT_BYTES != partitionBytes ||
                    firstData != 0L || groupBlocks != 32768 ||
                    inodesPerGroup < 8 || inodeSize !in 128..512 ||
                    descSize !in 32..256 || descSize % 4 != 0)
                    throw IOException("EXT4 geometry นอกขอบเขตที่ผ่านการทดสอบ")
                val countLong = (blocks + groupBlocks - 1L)/groupBlocks
                if (countLong != audit.groups.toLong() || blocks > Int.MAX_VALUE)
                    throw IOException("EXT4 group count ไม่ตรงกับ CRC Audit")
                totalBlocks = blocks.toInt()
                groups = countLong.toInt()
                logicalSize = partitionBytes
                val descTableBlocks = ((groups*descSize+EXT_BYTES-1)/EXT_BYTES)
                val inodeTableBlocks = ((inodesPerGroup*inodeSize+EXT_BYTES-1)/EXT_BYTES).toInt()

                fun mark(start: Long, count: Long, name: String) {
                    if (start < 0L || count < 0L || start + count > blocks)
                        throw IOException("EXT4 $name pointer นอกขอบเขต: $start + $count")
                    required.set(start.toInt(),(start+count).toInt())
                }
                fun backupGroup(group:Int):Boolean {
                    if ((ro and 0x1L)==0L) return true
                    if (group == 0 || group == 1) return true
                    for (baseNumber in intArrayOf(3,5,7)) {
                        var n=baseNumber.toLong()
                        while(n <= group) {
                            if(n==group.toLong()) return true
                            n *= baseNumber.toLong()
                        }
                    }
                    return false
                }
                // Fixed primary and redundant superblock/GDT/reserved GDT regions.
                for(group in 0 until groups){
                    if(backupGroup(group)) {
                        val start=group.toLong()*groupBlocks
                        val count=minOf(1L+descTableBlocks+reservedGdt,
                            blocks-start)
                        mark(start,count,"backup superblock/GDT")
                    }
                }
                progress("2/5 อ่าน EXT4 Group Descriptors, Bitmap และเตรียมบล็อกสำเนา")
                for (group in 0 until groups) {
                    if(cancelled()) throw IOException("ยกเลิกการสำรวจ")
                    if(group%16==0) progress("สำรวจ Bitmap "+(group+1)+"/"+groups)
                    val desc=readAt(EXT_BYTES.toLong()+group.toLong()*descSize,descSize)
                    // Descriptor CRCs already verified by Ext4MetadataAudit.
                    val flags=u16(desc,0x12)
                    val bitBlock=u32(desc,0) +
                        (if(descSize>=64) (u32(desc,0x20) shl 32) else 0L)
                    val bitInode=u32(desc,4) +
                        (if(descSize>=64) (u32(desc,0x24) shl 32) else 0L)
                    val inodeTable=u32(desc,8) +
                        (if(descSize>=64) (u32(desc,0x28) shl 32) else 0L)
                    mark(bitBlock,1,"block bitmap")
                    mark(bitInode,1,"inode bitmap")
                    mark(inodeTable,inodeTableBlocks.toLong(),"inode table")
                    if ((flags and 1)!=0) inodeUninit++
                    if ((flags and 2)!=0) {
                        blockUninit++
                        // It is crucial NOT to treat an uninitialized bitmap's
                        // arbitrary disk contents as an allocation mask.
                    } else {
                        val bitmap=readAt(bitBlock*EXT_BYTES,EXT_BYTES)
                        val first=group.toLong()*groupBlocks
                        val valid=minOf(groupBlocks.toLong(),blocks-first).toInt()
                        for(bit in 0 until valid) {
                            if ((u8(bitmap,bit/8) and (1 shl(bit%8)))!=0)
                                required.set((first+bit).toInt())
                        }
                    }
                }
                // Safety: explicitly include primary superblock and GDT.
                mark(0,1L+descTableBlocks,"primary superblock/GDT")
                blocksRequired=required.cardinality()
                val requestedPhysical=blocksRequired.toLong()*EXT_BYTES
                b.append("ARC3 logical: ").append(FormatUtils.bytes(partitionBytes))
                    .append(" | required blocks: ").append(blocksRequired)
                    .append("/").append(totalBlocks).append("\n")
                b.append("Bitmap BLOCK_UNINIT: ").append(blockUninit)
                    .append(" | INODE_UNINIT: ").append(inodeUninit).append("\n")
                b.append("ปริมาณที่คาดว่าจะอ่าน/เก็บสูงสุดตามบล็อกที่เลือก: ")
                    .append(FormatUtils.bytes(requestedPhysical)).append("\n")
                if(requestedPhysical > MAX_WRITTEN_BYTES)
                    throw IOException("พื้นที่บล็อกที่ต้องคัดลอกมากกว่าเกณฑ์ทดลอง 8 GiB")
                if(base.usableSpace <= requestedPhysical+MIN_FREE_AFTER)
                    throw IOException("พื้นที่โทรศัพท์ไม่พอเมื่อเผื่ออย่างน้อย 3 GiB")
                if (required[totalBlocks])
                    throw IOException("Block allocation mask เกินขนาด EXT4")
                progress("3/5 คัดลอกเฉพาะ EXT4 allocated/metadata blocks แบบ Sparse")
                val originalHash=MessageDigest.getInstance("SHA-256")
                RandomAccessFile(partial,"rw").use { dest ->
                    dest.setLength(partitionBytes)
                    var cursor=required.nextSetBit(0)
                    var lastProgress=0L
                    while(cursor>=0) {
                        if(cancelled()) throw IOException("ยกเลิกการคัดลอก")
                        val end=required.nextClearBit(cursor)
                        var pos=cursor
                        while(pos<end) {
                            val len=minOf(CHUNK_BLOCKS,end-pos)
                            val offset=pos.toLong()*EXT_BYTES
                            val bytes=readAt(offset,len*EXT_BYTES)
                            dest.seek(offset)
                            dest.write(bytes)
                            originalHash.update(bytes)
                            writtenBytes+=bytes.size
                            pos+=len
                            if(writtenBytes-lastProgress>=64L*1024L*1024L) {
                                lastProgress=writtenBytes
                                progress("Sparse copy "+FormatUtils.bytes(writtenBytes)+
                                    " / "+FormatUtils.bytes(requestedPhysical))
                                if(base.usableSpace<MIN_FREE_AFTER)
                                    throw IOException("พื้นที่ว่างเหลือต่ำกว่า 3 GiB")
                            }
                        }
                        cursor=required.nextSetBit(end)
                    }
                    dest.fd.sync()
                }
                originalDigest=originalHash.digest().toHex()
                if(writtenBytes != requestedPhysical)
                    throw IOException("จำนวนบล็อกที่คัดลอกไม่ครบ")
            }
            progress("4/5 Verify บล็อกที่คัดลอกกลับจากไฟล์ Sparse")
            val copiedHash=MessageDigest.getInstance("SHA-256")
            RandomAccessFile(partial,"r").use { file ->
                var cursor=required.nextSetBit(0)
                val buffer=ByteArray(CHUNK_BLOCKS*EXT_BYTES)
                while(cursor>=0){
                    if(cancelled()) throw IOException("ยกเลิกการ Verify")
                    val end=required.nextClearBit(cursor)
                    var pos=cursor
                    while(pos<end){
                        val len=minOf(CHUNK_BLOCKS,end-pos)
                        file.seek(pos.toLong()*EXT_BYTES)
                        file.readFully(buffer,0,len*EXT_BYTES)
                        copiedHash.update(buffer,0,len*EXT_BYTES)
                        pos+=len
                    }
                    cursor=required.nextSetBit(end)
                }
            }
            val verifyDigest=copiedHash.digest().toHex()
            if(!originalDigest.equals(verifyDigest,true))
                throw IOException("SHA-256 allocated-block readback ไม่ตรงกัน")
            b.append("Copied-block SHA-256: ").append(verifyDigest).append(" MATCH\n")

            // e2fsck -fn cannot repair. A non-zero exit keeps mirror EXPERIMENTAL.
            fun runTool(name:String,args:List<String>):Pair<Int,String>{
                val file=File(context.applicationInfo.nativeLibraryDir,"lib"+name+".so")
                if(!file.isFile || !file.canExecute())
                    throw IOException("ไม่พบ $name ที่ใช้งานได้ใน APK")
                val process=ProcessBuilder(listOf(file.absolutePath)+args)
                    .directory(base).redirectErrorStream(true).start()
                val out=ByteArrayOutputStream()
                val drain=thread(isDaemon=true) {
                    runCatching {
                        process.inputStream.use{ stream ->
                            val buf=ByteArray(2048)
                            while(true){
                                val n=stream.read(buf)
                                if(n<0)break
                                synchronized(out){
                                    if(out.size()<MAX_OUTPUT)
                                        out.write(buf,0,minOf(n,MAX_OUTPUT-out.size()))
                                }
                            }
                        }
                    }
                }
                try{
                    val start=System.nanoTime()
                    while(true){
                        if(cancelled()) throw IOException("ยกเลิกการตรวจ")
                        if(process.waitFor(400,TimeUnit.MILLISECONDS))break
                        if(TimeUnit.NANOSECONDS.toMinutes(System.nanoTime()-start)>PROC_TIMEOUT_MINUTES)
                            throw IOException("หมดเวลาตรวจ EXT4")
                    }
                    drain.join(1500L)
                    return process.exitValue() to synchronized(out){out.toString("UTF-8")}
                } finally {
                    if(process.isAlive)process.destroyForcibly()
                    runCatching{process.inputStream.close()}
                    drain.join(500L)
                }
            }
            progress("5/5 e2fsck ตรวจสำเนา ARC3 บนโทรศัพท์ (ไม่แก้ไข)")
            val fsck=runTool("e2fsck",listOf("-f","-n",partial.absolutePath))
            b.append("e2fsck -fn EXIT ").append(fsck.first).append("\n")
            b.append(fsck.second.take(1800)).append("\n")
            if(fsck.first!=0)
                throw IOException("e2fsck ตรวจสำเนาไม่ผ่าน — ไม่บันทึกเป็นไฟล์สำเร็จ")
            val min=runTool("resize2fs",listOf("-P",partial.absolutePath))
            b.append("resize2fs -P EXIT ").append(min.first).append("\n")
            b.append(min.second.take(500)).append("\n")
            if(min.first!=0)throw IOException("resize2fs -P ไม่สามารถอ่านสำเนาได้")
            if(!partial.renameTo(finished))
                throw IOException("ย้ายสำเนาที่ผ่านการตรวจไปชื่อถาวรไม่ได้")
            val st=Os.stat(finished.absolutePath)
            success=true
            b.append("Sparse physical blocks: ")
                .append(FormatUtils.bytes(st.st_blocks*512L)).append("\n")
            b.append("APP-PRIVATE MIRROR READY (EXPERIMENTAL): ")
                .append(finished.absolutePath).append("\n")
            b.append("ยังไม่ใช่ IMG บูตได้ และยังไม่ได้ย่อ ARC3 — ห้ามเขียนลง USB\n")
            return Result(true,b.toString())
        } catch (error:Exception) {
            b.append("BLOCKED: ").append(error.message?:error.javaClass.simpleName)
                .append("\n")
            b.append("ไม่มีการเขียน USB ต้นฉบับ • ลบไฟล์ partial ที่ทำค้าง\n")
            return Result(false,b.toString())
        } finally {
            if(!success)runCatching{partial.delete()}
        }
    }

    private fun ByteArray.toHex() =
        joinToString("") { "%02x".format(it) }
}
