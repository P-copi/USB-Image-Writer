package com.pcopi.usbimager

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * No write/format operations exist on this screen.
 * Collects a reproducible EXT preflight on a real bootable source + optional
 * smaller destination. Do not interpret "allocated bytes" as safe shrink size.
 */
class SmartRebuildActivity : Activity() {
    private lateinit var usb: UsbManager
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var source:Spinner
    private lateinit var target:Spinner
    private lateinit var state:TextView
    private lateinit var result:TextView
    private lateinit var checkButton:Button
    private lateinit var copyButton:Button
    private var devices:List<UsbDevice> = emptyList()
    private var busy=false
    private var reportText=""
    private var permissionNext:(()->Unit)?=null
    private val action="com.pcopi.usbimager.REBUILD_USB_PERMISSION"
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(ctx:Context?,intent:Intent?){
            if(intent?.action!=action)return
            val next=permissionNext
            permissionNext=null
            if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))
                next?.invoke()
            else state.text="ยังไม่ได้รับสิทธิ์อ่าน USB"
        }
    }

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        usb=getSystemService(USB_SERVICE) as UsbManager
        val filter=IntentFilter(action)
        if(Build.VERSION.SDK_INT>=33)
            registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(receiver,filter) }
        buildUi()
        refresh()
    }
    override fun onDestroy() {
        runCatching{unregisterReceiver(receiver)}
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll=ScrollView(this).apply{
            setBackgroundColor(ToolkitUi.BG)
            isFillViewport=true
        }
        ToolkitUi.applySafeInsets(scroll,this)
        val root=ToolkitUi.column(this)
        scroll.addView(root)
        root.addView(ToolkitUi.text(this,"‹  กลับหน้าแรก",15f,ToolkitUi.BLUE,true)
            .apply{setOnClickListener{finish()}})
        root.addView(ToolkitUi.text(this,"Smart Rebuild",27f,ToolkitUi.INK,true),
            ToolkitUi.margin(this,14))
        root.addView(ToolkitUi.text(this,
            "เตรียมย้าย Arc Loader 30 GB → USB 14 GB โดยตรวจโครงสร้างจริงก่อน",
            14f,ToolkitUi.SUB),ToolkitUi.margin(this,6,16))

        val cards=ToolkitUi.card(this)
        cards.addView(ToolkitUi.text(this,"1. เลือก USB ต้นฉบับที่บูตได้",17f,ToolkitUi.INK,true))
        source=Spinner(this)
        cards.addView(source,ToolkitUi.margin(this,6,12))
        cards.addView(ToolkitUi.text(this,"2. เลือก USB ปลายทาง (ถ้ามี)",17f,ToolkitUi.INK,true))
        target=Spinner(this)
        cards.addView(target,ToolkitUi.margin(this,6,12))
        cards.addView(ToolkitUi.text(this,
            "หากเสียบผ่าน Hub พร้อมกัน เลือก 2 ลูกให้ถูกต้อง ถ้าเสียบต้นฉบับลูกเดียวก็ตรวจ EXT ก่อนได้",
            13f,ToolkitUi.SUB),ToolkitUi.margin(this,8))
        root.addView(cards)
        val controls=ToolkitUi.card(this)
        controls.addView(ToolkitUi.button(this,"รีเฟรช Flash Drive",ToolkitUi.BLUE){refresh()})
        checkButton=ToolkitUi.button(this,"ตรวจทั้งหมดครั้งเดียว (FAT / EXT / Journal / MBR)",
            ToolkitUi.GREEN){requestInspection()}
        controls.addView(checkButton,ToolkitUi.margin(this,8))
        state=ToolkitUi.text(this,"รอการตรวจสอบ",14f,ToolkitUi.SUB)
        controls.addView(state,ToolkitUi.margin(this,9))
        root.addView(controls,ToolkitUi.margin(this,12))
        val out=ToolkitUi.card(this)
        out.addView(ToolkitUi.text(this,"รายงานตรวจครบชุด Smart Rebuild",17f,ToolkitUi.INK,true))
        result=ToolkitUi.text(this,
            "ตรวจ FAT, EXT, MBR, UUID, Feature flags, Journal และคำเตือนในครั้งเดียว",
            13f,ToolkitUi.INK)
        result.setTextIsSelectable(true)
        out.addView(result,ToolkitUi.margin(this,9))
        copyButton=ToolkitUi.button(this,"คัดลอกรายงาน",ToolkitUi.BLUE){copyReport()}
        copyButton.isEnabled=false
        out.addView(copyButton,ToolkitUi.margin(this,12))
        root.addView(out,ToolkitUi.margin(this,12))
        val safety=ToolkitUi.card(this)
        safety.addView(ToolkitUi.text(this,"ยังไม่เขียนลง USB",16f,ToolkitUi.INK,true))
        safety.addView(ToolkitUi.text(this,
            "รุ่นนี้เป็นขั้นวิเคราะห์ ไม่ได้ย่อ EXT4 หรือสร้าง USB 14 GB ทันที\n"+
                "พื้นที่จัดสรรน้อยไม่ได้แปลว่าสามารถตัดส่วนท้ายดิสก์ได้\n"+
                "ถ้า EXT Journal ไม่ Clean ต้องตรวจและซ่อมบนสำเนา ไม่ใช่ต้นฉบับ",
            13f,ToolkitUi.SUB),ToolkitUi.margin(this,8))
        root.addView(safety,ToolkitUi.margin(this,12))
        setContentView(scroll)
    }

    private fun refresh() {
        if(busy)return
        devices=usb.deviceList.values.filter{UsbRawDevice.isSupportedMassStorage(it)}
            .sortedBy{it.deviceId}
        val names=devices.mapIndexed{i,d->
            (i+1).toString()+". "+
                runCatching{d.productName}.getOrNull().orEmpty().ifBlank{"USB Mass Storage"}+
                " ["+"%04X:%04X".format(d.vendorId,d.productId)+"]"
        }
        source.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            if(names.isEmpty())listOf("ยังไม่พบ USB") else names)
        target.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            listOf("ยังไม่ได้เลือกปลายทาง")+names)
        state.text=if(names.isEmpty())"กรุณาเสียบ Flash Drive ผ่าน OTG" else "พร้อมตรวจสอบ"
    }

    private fun requestPermission(device:UsbDevice,onReady:()->Unit){
        if(usb.hasPermission(device)) {onReady();return}
        permissionNext=onReady
        val pending=PendingIntent.getBroadcast(this,217,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        usb.requestPermission(device,pending)
    }

    private fun requestInspection(){
        if(busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงาน USB อื่นทำงานอยู่ กรุณารอให้เสร็จก่อน"
            return
        }
        val src=devices.getOrNull(source.selectedItemPosition)
        if(src==null){state.text="กรุณาเลือก USB ต้นฉบับ";return}
        val dest=devices.getOrNull(target.selectedItemPosition-1)
        if(dest?.deviceId==src.deviceId) {
            AlertDialog.Builder(this).setTitle("เลือก USB ซ้ำกัน")
                .setMessage("ต้นฉบับและปลายทางต้องเป็นคนละลูก")
                .setPositiveButton("ตกลง",null).show()
            return
        }
        requestPermission(src) {
            if(dest!=null)requestPermission(dest){inspect(src,dest)}
            else inspect(src,null)
        }
    }

    private fun inspect(src:UsbDevice,dest:UsbDevice?) {
        busy=true
        checkButton.isEnabled=false
        source.isEnabled=false
        target.isEnabled=false
        copyButton.isEnabled=false
        reportText=""
        state.text="กำลังอ่านข้อมูล EXT บน USB ต้นฉบับ…"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        worker.execute {
            try{
                // Read target first; close before opening original to reduce hub contention.
                val targetSize=if(dest==null)null else UsbRawDevice.open(usb,dest).use{
                    it.init()
                    it.capacityBytes
                }
                val data=ExtRebuildInspector.inspect(
                    usb,src,targetSize,
                    onProgress={message -> runOnUiThread{if(!isFinishing)state.text=message}},
                    cancelled={Thread.currentThread().isInterrupted}
                )
                val text=formatReport(data)+formatRebuildGate(data)+inspectBootFiles(src)
                runOnUiThread {
                    reportText=text
                    result.text=text
                    copyButton.isEnabled=true
                    state.text="ตรวจเสร็จ • ไม่เปลี่ยนข้อมูล USB"
                }
            }catch(e:Exception){
                runOnUiThread{
                    state.text="ตรวจไม่สำเร็จ: "+(e.message ?: e.javaClass.simpleName)
                    result.text="โปรดตรวจการเชื่อมต่อ USB และทดลองใหม่ โดยไม่ฟอร์แมตต้นฉบับ"
                }
            }finally {
                runOnUiThread {
                    busy=false
                    checkButton.isEnabled=true
                    source.isEnabled=true
                    target.isEnabled=true
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    private fun formatReport(data:ExtRebuildInspector.Report):String {
        val b=StringBuilder()
        b.append("USB ต้นฉบับ: ").append(FormatUtils.bytes(data.usbBytes))
            .append(" | Block ").append(data.sectorBytes).append(" B\n")
        b.append("Partition Table: ").append(data.layout).append('\n')
        b.append("USB ปลายทาง: ").append(
            if(data.targetBytes==null)"ยังไม่เลือก" else FormatUtils.bytes(data.targetBytes)
        ).append("\n\n")
        for(p in data.partitions){
            b.append("พาร์ทิชัน ").append(p.number).append(": ").append(p.filesystem)
                .append(" (").append(p.typeCode).append(")\n")
            b.append("LBA ").append(p.startLba).append("–").append(p.endLba).append('\n')
            if(p.filesystemBytes!=null && p.allocatedBytes!=null && p.freeBytes!=null){
                b.append("ขนาดระบบไฟล์: ").append(FormatUtils.bytes(p.filesystemBytes)).append('\n')
                b.append("บล็อกที่จัดสรร (รวม Metadata): ").append(
                    FormatUtils.bytes(p.allocatedBytes)).append('\n')
                b.append("ว่าง: ").append(FormatUtils.bytes(p.freeBytes)).append('\n')
                b.append("UUID: ").append(p.uuid ?: "ไม่มี").append('\n')
                b.append("ชื่อ Volume: ").append(p.volumeName?.ifBlank{"(ว่าง)"} ?: "(ไม่มี)").append('\n')
                b.append("สถานะ EXT: ").append(
                    when(p.cleanState){true->"Clean";false->"Not clean";null->"ไม่ทราบ"}
                ).append('\n')
                b.append("Journal recovery: ").append(
                    when(p.journalRecoveryRequired){true->"จำเป็น/มีค้าง";false->"ไม่ค้าง";null->"ไม่ทราบ"}
                ).append('\n')
                b.append("ขนาด EXT Block: ").append(p.blockBytes).append(" B\n")
                b.append("Block groups: ").append(p.groups).append('\n')
                if(p.highAllocatedBlock!=null) {
                    b.append("บล็อกที่จัดสรรตำแหน่งสูงสุด: ")
                        .append(p.highAllocatedBlock).append('\n')
                    b.append("ตำแหน่งสัมพัทธ์: ")
                        .append(FormatUtils.bytes(p.highAllocatedBlock*(p.blockBytes?:4096).toLong()))
                        .append(" จากต้นระบบไฟล์\n")
                }else b.append("บล็อกตำแหน่งสูงสุด: ตรวจไม่ครบ/ไม่รองรับ\n")
            }
            for(n in p.notes)b.append("• ").append(n).append('\n')
            b.append('\n')
        }
        if(data.issues.isNotEmpty()){
            b.append("คำเตือนและแนวทางต่อ:\n")
            for(issue in data.issues)b.append("• ").append(issue).append('\n')
        }
        b.append("\nหมายเหตุ: ยังไม่ได้ย่อ หรือโคลนข้อมูลใด ๆ\n")
        return b.toString()
    }

    /**
     * Strict fail-closed direct-USB rebuild gate. This is a feasibility report,
     * NOT a filesystem resizer. No write operation is enabled here.
     */
    private fun formatRebuildGate(data:ExtRebuildInspector.Report):String {
        val b=StringBuilder("\n=== Direct USB Rebuild • Safety Gate ===\n")
        b.append("โหมด: เขียน Flash Drive โดยตรง | Verify: เปิด | IMG compression: ปิด\n")
        b.append("USB ต้นฉบับ: READ-ONLY • ไม่ซ่อม/ฟอร์แมต/ย่อ\n")
        val target=data.targetBytes
        if(target==null) {
            b.append("BLOCKED: ยังไม่ได้เลือก USB ปลายทาง\n")
            return b.toString()
        }
        if(data.layout!="MBR" || data.partitions.size!=3 ||
            data.partitions.map{it.number}!=listOf(1,2,3)) {
            b.append("BLOCKED: รูปแบบพาร์ทิชันไม่ตรงกับ Arc Loader 3 partitions ที่ตรวจสอบ\n")
            return b.toString()
        }
        val p1=data.partitions[0]
        val p2=data.partitions[1]
        val p3=data.partitions[2]
        val expected=p1.filesystem=="FAT32" && p2.filesystem=="EXT2" &&
            p3.filesystem=="EXT4" && p1.startLba==2048L &&
            p2.startLba==p1.endLba+1L && p3.startLba==p2.endLba+1L
        if(!expected) {
            b.append("BLOCKED: ระบบไฟล์หรือขอบเขตพาร์ทิชันไม่ตรงกับรูปแบบที่รองรับ\n")
            return b.toString()
        }
        val sector=data.sectorBytes.toLong()
        val targetSectors=target/sector
        val p3Available=targetSectors-p3.startLba
        b.append("ความจุปลายทาง: ").append(FormatUtils.bytes(target)).append('\n')
        b.append("ตำแหน่งเริ่ม ARC3 ที่ต้องรักษา: LBA ").append(p3.startLba).append('\n')
        if(target<=0 || target%sector!=0L || p3Available<=0L) {
            b.append("BLOCKED: USB ปลายทางไม่พอสำหรับโครงสร้างพาร์ทิชัน\n")
            return b.toString()
        }
        b.append("พื้นที่ ARC3 สูงสุดตามขนาดปลายทาง (ก่อนเผื่อพื้นที่): ")
            .append(FormatUtils.bytes(p3Available*sector)).append('\n')
        val reserve=64L*1024L*1024L
        val availableAfterReserve=p3Available*sector-reserve
        b.append("เผื่อท้ายดิสก์เบื้องต้น: ").append(FormatUtils.bytes(reserve)).append('\n')
        if(availableAfterReserve<=0L ||
            (p3.allocatedBytes ?: Long.MAX_VALUE)>=availableAfterReserve) {
            b.append("BLOCKED: พื้นที่ ARC3 หลังสำรองไม่เพียงพอตามข้อมูลที่ตรวจได้\n")
            return b.toString()
        }
        b.append("ความจุมีแนวโน้มเพียงพอ แต่ยังไม่ใช่ขนาดย่อขั้นต่ำที่ยืนยันแล้ว\n")
        val reasons=mutableListOf<String>()
        if(p2.cleanState!=true)reasons.add("ARC2 ไม่อยู่ในสถานะ Clean")
        if(p3.cleanState!=true)reasons.add("ARC3 ไม่อยู่ในสถานะ Clean")
        if(p2.journalRecoveryRequired==true || p3.journalRecoveryRequired==true)
            reasons.add("มี EXT Journal ที่ต้อง Recovery บนสำเนา")
        if(p3.highAllocatedBlock==null)
            reasons.add("ยังไม่ยืนยันตำแหน่ง Metadata/Block ที่ต้องย้าย")
        if((p3.notes.any{it.contains("checksums",ignoreCase=true)}))
            reasons.add("ยังไม่ได้ Verify EXT4 metadata CRC32C")
        reasons.add("ยังไม่มี EXT4 filesystem-aware rebuild และการรักษา boot metadata ที่ผ่านการทดสอบ")
        reasons.add("ยังไม่มี Direct Write + Read-back Verify ที่ผ่านการทดสอบบน USB ปลายทาง")
        b.append("สถานะ: BLOCKED — ยังไม่อนุญาตให้เขียน\n")
        for(reason in reasons)b.append("• ").append(reason).append('\n')
        b.append("การตรวจนี้ไม่ได้เปลี่ยนแปลง USB ทั้งสองลูก\n")
        return b.toString()
    }

    /** Bounded read-only file inventory using the existing FAT/EXT explorer. */
    private fun inspectBootFiles(device:UsbDevice):String {
        val out=StringBuilder("\n=== รายการไฟล์และโฟลเดอร์ (Read-only) ===\n")
        try {
            BootDiskReader.open(usb,device).use { reader ->
                val scan=reader.scan()
                out.append("รูปแบบดิสก์: ").append(scan.style).append('\n')
                for(volume in scan.volumes) {
                    out.append("\nPartition ").append(volume.number)
                        .append(" [").append(volume.fileSystem).append("]\n")
                    if(!volume.isReadable) {
                        out.append("ยังอ่านรายการไฟล์ไม่ได้: ").append(volume.warning).append('\n')
                        continue
                    }
                    try {
                        val root=reader.list(volume.number,null)
                        out.append("Root entries: ").append(root.size)
                            .append(" (แสดงสูงสุด 80)\n")
                        for(entry in root.take(80)) {
                            out.append(if(entry.directory)"[DIR] " else "[FILE] ")
                                .append(entry.name).append(" (")
                                .append(entry.size).append(" B)\n")
                        }
                        if(root.size>80)out.append("... รายการอื่นไม่ได้แสดง\n")
                        for(folder in root.filter{it.directory &&
                            it.name.lowercase() in setOf("boot","efi","grub","arc","config","loader")
                        }.take(5)) {
                            try {
                                val children=reader.list(volume.number,folder)
                                out.append("  /").append(folder.name).append(":\n")
                                for(child in children.take(25))
                                    out.append("    ").append(if(child.directory)"[DIR] " else "[FILE] ")
                                        .append(child.name).append('\n')
                            }catch(e:Exception){
                                out.append("  /").append(folder.name).append(": ")
                                    .append(e.message ?: "อ่านไม่ได้").append('\n')
                            }
                        }
                    }catch(e:Exception){
                        out.append("อ่าน Root ไม่สำเร็จ: ")
                            .append(e.message ?: e.javaClass.simpleName).append('\n')
                    }
                }
            }
        }catch(e:Exception) {
            out.append("ตรวจรายการไฟล์ไม่สำเร็จ: ")
                .append(e.message ?: e.javaClass.simpleName).append('\n')
        }
        out.append("ไม่มีการแก้ไขไฟล์หรือเขียนลง USB\n")
        return out.toString()
    }

    private fun copyReport() {
        if(reportText.isBlank())return
        val manager=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("USB Smart Rebuild Report",reportText))
        state.text="คัดลอกรายงานแล้ว • ตรวจสอบก่อนนำไปแชร์"
    }
}