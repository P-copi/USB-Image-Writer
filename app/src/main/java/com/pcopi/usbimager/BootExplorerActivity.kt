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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Read-only boot volume browser over raw USB, independent of Android's mount. */
class BootExplorerActivity : Activity() {
    private val executor=Executors.newSingleThreadExecutor()
    private lateinit var usb:UsbManager
    private lateinit var source:Spinner
    private lateinit var partition:Spinner
    private lateinit var status:TextView
    private lateinit var info:TextView
    private lateinit var files:LinearLayout
    private lateinit var path:TextView
    private lateinit var openButton:Button
    private lateinit var backButton:Button
    private var devices=emptyList<UsbDevice>()
    private var reader:BootDiskReader?=null
    private var report:BootDiskReader.Scan?=null
    private var currentVolume=0
    private val stack=mutableListOf<BootDiskReader.Entry>()
    private var busy=false
    private var waiting:(()->Unit)?=null
    private val permissionAction="com.pcopi.usbimager.BOOT_EXPLORER_PERMISSION"
    private val receiver=object:BroadcastReceiver(){
        override fun onReceive(context:Context?,intent:Intent?){
            if(intent?.action!=permissionAction)return
            val callback=waiting
            waiting=null
            if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))callback?.invoke()
            else status.text="ไม่ได้รับสิทธิ์ USB • กรุณาลองอีกครั้ง"
        }
    }

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        usb=getSystemService(USB_SERVICE) as UsbManager
        val filter=IntentFilter(permissionAction)
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(receiver,filter) }
        createUi()
        refresh()
    }

    override fun onDestroy(){
        runCatching{unregisterReceiver(receiver)}
        val closing=reader
        reader=null
        executor.execute{runCatching{closing?.close()}}
        executor.shutdown()
        super.onDestroy()
    }

    private fun createUi(){
        val scroll=ScrollView(this).apply{
            setBackgroundColor(ToolkitUi.BG);isFillViewport=true
        }
        ToolkitUi.applySafeInsets(scroll,this)
        val root=ToolkitUi.column(this);scroll.addView(root)
        root.addView(ToolkitUi.text(this,"‹  กลับหน้าหลัก",15f,ToolkitUi.BLUE,true)
            .apply{setOnClickListener{finish()}})
        root.addView(ToolkitUi.text(this,"สำรวจ USB Boot",27f,ToolkitUi.INK,true),
            ToolkitUi.margin(this,14))
        root.addView(ToolkitUi.text(this,
            "อ่านไฟล์บูตและระบุระบบ โดยไม่ต้องให้ Samsung ฟอร์แมต USB",
            14f,ToolkitUi.SUB),ToolkitUi.margin(this,5,15))
        val selector=ToolkitUi.card(this)
        selector.addView(ToolkitUi.text(this,"Flash Drive",18f,ToolkitUi.INK,true))
        source=Spinner(this);selector.addView(source,ToolkitUi.margin(this,9))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val refresh=ToolkitUi.button(this,"รีเฟรช",ToolkitUi.BLUE){refresh()}
        openButton=ToolkitUi.button(this,"สำรวจ Boot",ToolkitUi.GREEN){inspect()}
        actions.addView(refresh,LinearLayout.LayoutParams(0,ToolkitUi.dp(this,54),1f))
        actions.addView(openButton,LinearLayout.LayoutParams(0,ToolkitUi.dp(this,54),1f))
        selector.addView(actions)
        status=ToolkitUi.text(this,"พร้อมตรวจสอบ",13f,ToolkitUi.SUB)
        selector.addView(status,ToolkitUi.margin(this,8))
        root.addView(selector)

        val id=ToolkitUi.card(this)
        id.addView(ToolkitUi.text(this,"ผลการตรวจ Bootloader",18f,ToolkitUi.INK,true))
        info=ToolkitUi.text(this,"ยังไม่ได้ตรวจสอบ",14f,ToolkitUi.INK)
        info.setTextIsSelectable(true)
        id.addView(info,ToolkitUi.margin(this,8))
        root.addView(id,ToolkitUi.margin(this,14))

        val browser=ToolkitUi.card(this)
        browser.addView(ToolkitUi.text(this,"พาร์ทิชันและไฟล์",18f,ToolkitUi.INK,true))
        partition=Spinner(this)
        browser.addView(partition,ToolkitUi.margin(this,10))
        partition.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){
                val selected=report?.volumes?.getOrNull(position)?:return
                currentVolume=selected.number
                stack.clear()
                browse()
            }
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?)=Unit
        }
        backButton=ToolkitUi.button(this,"↑ ย้อนกลับโฟลเดอร์",ToolkitUi.BLUE){
            if(stack.isNotEmpty()){stack.removeAt(stack.lastIndex);browse()}
        }
        browser.addView(backButton)
        path=ToolkitUi.text(this,"ยังไม่ได้เปิดพาร์ทิชัน",13f,ToolkitUi.SUB)
        browser.addView(path,ToolkitUi.margin(this,8))
        files=ToolkitUi.column(this)
        browser.addView(files,ToolkitUi.margin(this,8))
        root.addView(browser,ToolkitUi.margin(this,14))

        val safety=ToolkitUi.card(this)
        safety.addView(ToolkitUi.text(this,"ความปลอดภัย",16f,ToolkitUi.INK,true))
        safety.addView(ToolkitUi.text(this,
            "หาก Samsung เสนอให้ฟอร์แมต USB ให้กด “ยกเลิก”\n"+
            "เมนูนี้อ่านอย่างเดียว ไม่เปลี่ยนข้อมูลต้นฉบับ\n"+
            "รุ่นแรกเปิดไฟล์ FAT16/FAT32 ได้ ส่วน EXT2/EXT4, NTFS และ exFAT "+
            "จะแสดงเพียงชนิดพาร์ทิชัน ยังเปิดโฟลเดอร์ภายในไม่ได้",
            13f,ToolkitUi.SUB),ToolkitUi.margin(this,9))
        root.addView(safety,ToolkitUi.margin(this,14))
        setContentView(scroll)
        updateButtons()
    }

    private fun refresh(){
        if(busy)return
        val old=reader
        reader=null
        report=null
        stack.clear()
        files.removeAllViews()
        info.text="ยังไม่ได้ตรวจสอบ"
        if(old!=null)executor.execute{old.close()}
        devices=usb.deviceList.values.filter{UsbRawDevice.isSupportedMassStorage(it)}
            .sortedBy{it.deviceId}
        val labels=if(devices.isEmpty())listOf("ไม่พบ Flash Drive")else
            devices.mapIndexed{i,d->
                (i+1).toString()+". "+
                    runCatching{d.productName}.getOrNull().orEmpty().ifBlank{"USB"}+
                    " ["+"%04X:%04X".format(d.vendorId,d.productId)+"]"
            }
        source.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
        partition.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            listOf("รอผลการตรวจ"))
        status.text=if(devices.isEmpty())"กรุณาเสียบ USB ผ่าน OTG" else "พร้อมตรวจสอบ"
        updateButtons()
    }
    private fun permission(device:UsbDevice,onGranted:()->Unit){
        if(usb.hasPermission(device)){onGranted();return}
        waiting=onGranted
        val pi=PendingIntent.getBroadcast(this,35,
            Intent(permissionAction).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        usb.requestPermission(device,pi)
    }
    private fun inspect(){
        if(busy || UsbOperationService.snapshot.running || UsbBackupService.snapshot.running){
            status.text="กรุณารอให้งาน USB อื่นเสร็จก่อน"
            return
        }
        val d=devices.getOrNull(source.selectedItemPosition)
        if(d==null){status.text="กรุณาเลือก USB";return}
        permission(d){doInspect(d)}
    }
    private fun doInspect(device:UsbDevice){
        busy=true;updateButtons()
        status.text="กำลังอ่านพาร์ทิชันและไฟล์บูต..."
        executor.execute{
            var newReader:BootDiskReader?=null
            try{
                reader?.close()
                newReader=BootDiskReader.open(usb,device)
                val scan=newReader.scan()
                val ready=newReader
                val text=formatReport(scan)
                runOnUiThread{
                    reader=ready;report=scan
                    info.text=text
                    stack.clear()
                    val items=if(scan.volumes.isEmpty())listOf("ไม่พบพาร์ทิชัน")
                    else scan.volumes.map{v->
                        "พาร์ทิชัน "+v.number+" • "+v.fileSystem+
                            (if(v.isReadable)" • เปิดไฟล์ได้" else " • ตรวจชนิดอย่างเดียว")
                    }
                    partition.adapter=ArrayAdapter(this,
                        android.R.layout.simple_spinner_dropdown_item,items)
                    busy=false;updateButtons()
                    status.text="ตรวจสอบเสร็จ • ไม่มีการเขียน USB"
                    if(scan.volumes.isNotEmpty()){
                        currentVolume=scan.volumes.first().number
                        browse()
                    }
                }
            }catch(e:Exception){
                newReader?.close()
                runOnUiThread{
                    busy=false;updateButtons()
                    status.text="ตรวจไม่สำเร็จ: "+(e.message?:e.javaClass.simpleName)
                }
            }
        }
    }
    private fun browse(){
        val active=reader?:return
        val v=report?.volumes?.firstOrNull{it.number==currentVolume}?:return
        path.text="พาร์ทิชัน "+v.number+" / "+stack.joinToString("/"){it.name}
        backButton.isEnabled=stack.isNotEmpty()
        if(!v.isReadable){
            files.removeAllViews()
            files.addView(ToolkitUi.text(this,
                "พบ "+v.fileSystem+" แต่ยังไม่รองรับการเปิดไฟล์ในรุ่นนี้",
                14f,ToolkitUi.SUB))
            return
        }
        if(busy)return
        busy=true;updateButtons()
        val volume=currentVolume
        val folder=stack.lastOrNull()
        executor.execute{
            val response=runCatching{active.list(volume,folder)}
            runOnUiThread{
                busy=false;updateButtons()
                response.onSuccess{showEntries(it)}
                    .onFailure{
                        files.removeAllViews()
                        files.addView(ToolkitUi.text(this,
                            "อ่านโฟลเดอร์ไม่สำเร็จ: "+it.message,14f,ToolkitUi.SUB))
                    }
            }
        }
    }
    private fun showEntries(entries:List<BootDiskReader.Entry>){
        files.removeAllViews()
        if(entries.isEmpty())files.addView(ToolkitUi.text(this,"โฟลเดอร์ว่าง",14f,ToolkitUi.SUB))
        entries.forEach{e->
            val label=(if(e.directory)"📁  " else "📄  ")+e.name+
                (if(e.directory)"" else " ("+FormatUtils.bytes(e.size)+")")
            val view=ToolkitUi.text(this,label,14f,ToolkitUi.INK).apply{
                setPadding(0,ToolkitUi.dp(this@BootExplorerActivity,8),
                    0,ToolkitUi.dp(this@BootExplorerActivity,8))
                setOnClickListener{
                    if(busy)return@setOnClickListener
                    if(e.directory){stack.add(e);browse()}else preview(e)
                }
            }
            files.addView(view)
        }
    }
    private fun preview(file:BootDiskReader.Entry){
        val active=reader?:return
        val vol=currentVolume
        busy=true;updateButtons();status.text="กำลังอ่าน "+file.name
        executor.execute{
            val value=runCatching{active.readText(vol,file,8192)}
                .getOrElse{"ไม่สามารถแสดงข้อความจากไฟล์: "+(it.message?:it.javaClass.simpleName)}
            runOnUiThread{
                busy=false;updateButtons();status.text="อ่านอย่างเดียว"
                AlertDialog.Builder(this)
                    .setTitle(file.name).setMessage(value.take(8192))
                    .setPositiveButton("ปิด",null).show()
            }
        }
    }
    private fun formatReport(r:BootDiskReader.Scan):String{
        val s=StringBuilder()
        s.append("ผล: ").append(r.identity.title).append('\n')
        if(r.identity.version!=null)s.append("เวอร์ชัน: ").append(r.identity.version).append('\n')
        s.append("ความมั่นใจ: ").append(r.identity.confidence).append('\n')
        r.identity.evidence.forEach{s.append("✓ ").append(it).append('\n')}
        s.append("\nดิสก์: ").append(r.style).append(" • ")
            .append(FormatUtils.bytes(r.totalBytes)).append('\n')
        r.volumes.forEach{s.append("พาร์ทิชัน ").append(it.number).append(": ")
            .append(it.fileSystem).append(if(it.isReadable)" • อ่านไฟล์ได้" else " • ยังไม่รองรับ")
            .append("\nLBA ").append(it.startLba).append("–").append(it.endLba).append('\n')}
        r.notes.forEach{s.append("• ").append(it).append('\n')}
        return s.toString()
    }
    private fun updateButtons(){
        if(::openButton.isInitialized)openButton.isEnabled=!busy
        if(::source.isInitialized)source.isEnabled=!busy
    }
}