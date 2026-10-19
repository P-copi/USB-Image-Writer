package com.pcopi.usbimager

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Safe Smart Clone preflight. Never writes to either USB. Detailed real-device
 * metadata is required before implementing filesystem-specific shrinking.
 */
class SmartCloneActivity : Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var usb:UsbManager
    private lateinit var sourceSpinner:Spinner
    private lateinit var targetSpinner:Spinner
    private lateinit var message:TextView
    private lateinit var output:TextView
    private lateinit var button:android.widget.Button
    private var devices:List<UsbDevice> = emptyList()
    private var waitingAction:(()->Unit)?=null
    private var busy=false
    companion object { private const val USB_PERMISSION="com.pcopi.usbimager.SMART_CLONE_PERMISSION" }

    private val receiver=object:BroadcastReceiver(){
        override fun onReceive(c:Context?,intent:Intent?){
            if(intent?.action==USB_PERMISSION){
                val callback=waitingAction
                waitingAction=null
                if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){
                    callback?.invoke()
                }else message.text="ต้องอนุญาตการเข้าถึง USB เพื่อวิเคราะห์พาร์ทิชัน"
            }
        }
    }

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        usb=getSystemService(USB_SERVICE) as UsbManager
        val filter=IntentFilter(USB_PERMISSION)
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(receiver,filter) }
        val scroll=ScrollView(this).apply{setBackgroundColor(ToolkitUi.BG)}
        ToolkitUi.applySafeInsets(scroll,this)
        val root=ToolkitUi.column(this)
        scroll.addView(root)
        root.addView(ToolkitUi.text(this,"‹  กลับหน้าหลัก",15f,ToolkitUi.BLUE,true).apply{
            setOnClickListener{finish()}
        })
        root.addView(ToolkitUi.text(this,"Smart Clone",29f,ToolkitUi.INK,true),ToolkitUi.margin(this,15))
        root.addView(ToolkitUi.text(this,
            "วิเคราะห์ Arc Loader 30 GB เพื่อโคลนลง USB 14 GB อย่างปลอดภัย",
            15f,ToolkitUi.SUB),ToolkitUi.margin(this,7,18))
        val src=ToolkitUi.card(this)
        src.addView(ToolkitUi.text(this,"1. Flash Drive ต้นฉบับ (30 GB)",16f,ToolkitUi.INK,true))
        sourceSpinner=Spinner(this);src.addView(sourceSpinner)
        root.addView(src,ToolkitUi.margin(this,0,13))
        val dest=ToolkitUi.card(this)
        dest.addView(ToolkitUi.text(this,"2. Flash Drive ปลายทาง (14 GB)",16f,ToolkitUi.INK,true))
        targetSpinner=Spinner(this);dest.addView(targetSpinner)
        dest.addView(ToolkitUi.text(this,
            "แนะนำต่อผ่าน USB Hub เพื่อให้อ่านความจุจริงของลูกปลายทาง " +
            "หากยังไม่เสียบทั้งสองลูก สามารถตรวจโครงสร้างต้นฉบับก่อนได้",
            13f,ToolkitUi.SUB),ToolkitUi.margin(this,7))
        root.addView(dest,ToolkitUi.margin(this,0,13))
        val controls=ToolkitUi.card(this)
        controls.addView(ToolkitUi.button(this,"รีเฟรชรายการ USB",ToolkitUi.BLUE){refresh()})
        button=ToolkitUi.button(this,"วิเคราะห์พาร์ทิชัน (อ่านอย่างเดียว)",ToolkitUi.GREEN){startInspection()}
        controls.addView(button,ToolkitUi.margin(this,8))
        message=ToolkitUi.text(this,"ยังไม่เริ่มตรวจสอบ",14f,ToolkitUi.SUB)
        controls.addView(message,ToolkitUi.margin(this,10))
        root.addView(controls)
        val report=ToolkitUi.card(this)
        report.addView(ToolkitUi.text(this,"รายงานโครงสร้าง USB",18f,ToolkitUi.INK,true))
        output=ToolkitUi.text(this,
            "จะแสดง GPT/MBR, ตำแหน่งและขนาด Partition, FAT/exFAT/EXT และประเมินพื้นที่ที่ต้องใช้",
            13f,ToolkitUi.SUB)
        output.setTextIsSelectable(true)
        report.addView(output,ToolkitUi.margin(this,8))
        root.addView(report,ToolkitUi.margin(this,13))
        val note=ToolkitUi.card(this)
        note.addView(ToolkitUi.text(this,"ความปลอดภัย",17f,ToolkitUi.INK,true))
        note.addView(ToolkitUi.text(this,
            "ขั้นตอนนี้ไม่เปลี่ยนแปลง Flash Drive ทั้งสองลูกแม้แต่ Sector เดียว\n" +
            "การมีข้อมูลเพียง 2–3 GB ไม่ยืนยันว่าพาร์ทิชันขนาด 30 GB ย่อได้\n" +
            "หากเกินขอบเขต 14 GB แอปจะไม่เขียนแบบตัด Sector ทิ้ง",
            13f,ToolkitUi.SUB),ToolkitUi.margin(this,8))
        root.addView(note,ToolkitUi.margin(this,13))
        setContentView(scroll)
        refresh()
    }
    override fun onDestroy(){
        runCatching{unregisterReceiver(receiver)}
        worker.shutdownNow()
        super.onDestroy()
    }
    private fun refresh(){
        if(busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running)return
        devices=usb.deviceList.values.filter{UsbRawDevice.isSupportedMassStorage(it)}
            .sortedBy{it.deviceId}
        val labels=devices.mapIndexed{index,d->
            val name=runCatching{d.productName}.getOrNull().orEmpty().ifBlank{"USB"}
            (index+1).toString()+". "+name+" ["+
                "%04X:%04X".format(d.vendorId,d.productId)+"]"
        }
        sourceSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            if(labels.isEmpty())listOf("ไม่พบ Flash Drive") else labels)
        targetSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            listOf("ยังไม่เลือก • วิเคราะห์ต้นฉบับก่อน")+labels)
        message.text=when(devices.size){
            0->"กรุณาเสียบ Flash Drive"
            1->"พบ USB 1 ลูก • ตรวจโครงสร้างต้นฉบับได้"
            else->"พบ USB "+devices.size+" ลูก • เลือกต้นฉบับและปลายทางให้ถูกต้อง"
        }
    }
    private fun permission(d:UsbDevice,next:()->Unit){
        if(usb.hasPermission(d)){ next();return }
        waitingAction=next
        val intent=Intent(USB_PERMISSION).setPackage(packageName)
        val pi=PendingIntent.getBroadcast(this,119,intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        usb.requestPermission(d,pi)
    }
    private fun startInspection(){
        if(busy||UsbBackupService.snapshot.running||UsbOperationService.snapshot.running)return
        val source=devices.getOrNull(sourceSpinner.selectedItemPosition)
            ?:return AlertDialog.Builder(this).setMessage("ยังไม่พบ USB ต้นฉบับ")
                .setPositiveButton("ตกลง",null).show()
        val target=devices.getOrNull(targetSpinner.selectedItemPosition-1)
        if(target!=null && target.deviceId==source.deviceId)
            return AlertDialog.Builder(this).setMessage("ต้นฉบับและปลายทางต้องเป็น USB คนละลูก")
                .setPositiveButton("ตกลง",null).show()
        permission(source){
            if(target!=null)permission(target){inspect(source,target)}
            else inspect(source,null)
        }
    }
    private fun inspect(source:UsbDevice,target:UsbDevice?){
        busy=true
        button.isEnabled=false
        sourceSpinner.isEnabled=false
        targetSpinner.isEnabled=false
        message.text="กำลังอ่าน Partition Table... ห้ามถอด USB"
        output.text=""
        worker.execute{
            try{
                val report=SmartDiskInspector.inspect(usb,source)
                var destBytes:Long?=null
                if(target!=null){
                    UsbRawDevice.open(usb,target).use{
                        it.init()
                        destBytes=it.capacityBytes
                    }
                }
                val outputText=formatReport(report,destBytes)
                runOnUiThread{
                    output.text=outputText
                    message.text="วิเคราะห์เสร็จแล้ว • ไม่มีการเขียนลง USB"
                }
            }catch(e:Exception){
                runOnUiThread{
                    message.text="วิเคราะห์ไม่สำเร็จ: "+e.message
                }
            }finally{
                runOnUiThread{
                    busy=false
                    button.isEnabled=true
                    sourceSpinner.isEnabled=true
                    targetSpinner.isEnabled=true
                }
            }
        }
    }
    private fun formatReport(r:SmartDiskInspector.Report,dest:Long?):String{
        val sb=StringBuilder()
        sb.append("ระบบพาร์ทิชัน: ").append(r.scheme).append('\n')
        sb.append("ความจุต้นฉบับ: ").append(FormatUtils.bytes(r.totalBytes))
            .append(" (").append(r.totalBlocks).append(" blocks)\n")
        sb.append("Block Size: ").append(r.blockSize).append(" B\n\n")
        r.partitions.forEach{p->
            sb.append("Partition ").append(p.number).append(": ")
                .append(p.type).append('\n')
                .append("LBA ").append(p.first).append(" – ").append(p.last).append('\n')
                .append("พื้นที่: ").append(FormatUtils.bytes((p.last-p.first+1)*r.blockSize.toLong()))
                .append(" | FS: ").append(p.fs).append('\n')
            if(p.volumeBytes!=null){
                sb.append("ขนาด volume ตาม superblock: ")
                    .append(FormatUtils.bytes(p.volumeBytes)).append('\n')
            }
            sb.append('\n')
        }
        if(r.warnings.isNotEmpty()){
            sb.append("ข้อควรระวัง:\n")
            r.warnings.forEach{sb.append("• ").append(it).append('\n')}
        }
        sb.append("\nขนาดดิสก์ปลายทางขั้นต่ำ (เฉพาะโครงสร้าง Partition): ")
            .append(FormatUtils.bytes(r.minTargetBlocks*r.blockSize.toLong()))
            .append("\n")
        if(dest==null){
            sb.append("\nยังไม่ได้ตรวจ USB ปลายทางจริง • กรุณาเสียบผ่าน Hub และเลือกอีกลูกเพื่อประเมิน")
        }else{
            sb.append("\nUSB ปลายทางจริง: ").append(FormatUtils.bytes(dest)).append("\n")
            if(r.fits(dest)){
                sb.append("ผล: Partition เดิมอยู่ภายในพื้นที่ปลายทาง ")
                    .append("แต่ต้องสร้างตารางพาร์ทิชันใหม่และตรวจสอบบูตก่อนใช้งาน")
            }else{
                sb.append("ผล: ยังไม่ปลอดภัยที่จะโคลนลง USB นี้แบบ Raw ")
                    .append("ต้องย่อ/สร้างพาร์ทิชันใหม่ตามชนิดระบบไฟล์ก่อน")
            }
        }
        sb.append("\n\nรายละเอียด: ").append(r.detail)
        return sb.toString()
    }
}