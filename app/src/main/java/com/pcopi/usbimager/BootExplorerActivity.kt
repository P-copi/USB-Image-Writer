package com.pcopi.usbimager

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
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
    private lateinit var deepReport:TextView
    private var deepReportValue=""
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

        val diagnostics=ToolkitUi.card(this)
        diagnostics.addView(ToolkitUi.text(this,"ARC Deep Inspector • v0.2.47",18f,ToolkitUi.INK,true))
        diagnostics.addView(ToolkitUi.text(this,"ตรวจโฟลเดอร์หลักแบบอ่านอย่างเดียว ไม่คัดลอกเนื้อหาไฟล์หรือข้อมูลลับ",13f,ToolkitUi.SUB))
        diagnostics.addView(ToolkitUi.button(this,"ตรวจทั้งหมด",ToolkitUi.GREEN){runDeepAudit()},ToolkitUi.margin(this,9))
        diagnostics.addView(ToolkitUi.button(this,"คัดลอกรายงาน",ToolkitUi.BLUE){copyDeepReport()},ToolkitUi.margin(this,8))
        deepReport=ToolkitUi.text(this,"ยังไม่ได้ตรวจ",13f,ToolkitUi.INK)
        deepReport.setTextIsSelectable(true)
        diagnostics.addView(deepReport,ToolkitUi.margin(this,9))
        root.addView(diagnostics,ToolkitUi.margin(this,14))

        val safety=ToolkitUi.card(this)
        safety.addView(ToolkitUi.text(this,"ความปลอดภัย",16f,ToolkitUi.INK,true))
        safety.addView(ToolkitUi.text(this,
            "หาก Samsung เสนอให้ฟอร์แมต USB ให้กด “ยกเลิก”\n"+
            "เมนูนี้อ่านอย่างเดียว ไม่เปลี่ยนข้อมูลต้นฉบับ\n"+
            "รุ่นนี้เปิดไฟล์ FAT16/FAT32 และ EXT2/EXT4 ที่รองรับได้\n"+
            "ส่วน NTFS/exFAT ยังแสดงได้เพียงชนิดระบบไฟล์\n"+
            "อย่าส่งภาพเนื้อหา user-config.yml หรือข้อมูล Serial/Token",
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
                "พบ "+v.fileSystem+" แต่ยังไม่รองรับการเปิดไฟล์ในรุ่นนี้\n"+v.warning,
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
    private fun copyDeepReport(){
        if(deepReportValue.isBlank()){status.text="ยังไม่มีรายงานให้คัดลอก";return}
        val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ARC Deep Inspector",deepReportValue))
        status.text="คัดลอกรายงานแล้ว • วางลงในแชทได้เลย"
    }

    private fun runDeepAudit(){
        if(busy || UsbOperationService.snapshot.running || UsbBackupService.snapshot.running){
            status.text="มีงาน USB กำลังทำงาน";return
        }
        val active=reader
        val scan=report
        if(active==null || scan==null){status.text="กดสำรวจ Boot ก่อน";return}
        busy=true;updateButtons();status.text="กำลังตรวจโฟลเดอร์แบบอ่านอย่างเดียว..."
        executor.execute{
            val output=StringBuilder("=== ARC Deep Inspector v0.2.47 ===\n")
            output.append("READ ONLY / FILE CONTENTS NOT EXPORTED\n")
            output.append("Bootloader: ").append(scan.identity.title).append('\n')
            output.append("Version evidence: ").append(scan.identity.version ?: "UNKNOWN").append('\n')
            output.append("Disk: ").append(scan.style).append(" bytes=").append(scan.totalBytes).append('\n')
            output.append("AUDIT: directory consistency, DS920+ template, raw LBA, bounded regions and all listed EXT module archives; no boot test\n")
            val rawCheck=runCatching{active.auditRawLba(237632L,3)}
            output.append(if(rawCheck.getOrNull()==true)"PASS" else "FAIL")
                .append(" raw LBA 237632 SHA-256 consistency 3 uncached reads")
                .append(if(rawCheck.isFailure)" error="+rawCheck.exceptionOrNull()?.javaClass?.simpleName else "")
                .append('\n')
            // Three bounded regions: FAT32, EXT2, EXT4. No bytes or digests exported.
            listOf(2048L,104448L,237632L).forEach{start->
                val result=runCatching{active.auditRawRegion(start,64,3)}
                output.append(if(result.getOrNull()==true)"PASS" else "FAIL")
                    .append(" raw region LBA=").append(start)
                    .append(" sectors=64 SHA-256 consistency 3 uncached reads")
                    .append(if(result.isFailure)" error="+result.exceptionOrNull()?.javaClass?.simpleName else "")
                    .append('\n')
            }
            scan.volumes.forEach{v->
                output.append("P").append(v.number).append(" ").append(v.fileSystem)
                    .append(" LBA=").append(v.startLba).append("-").append(v.endLba)
                    .append(" readable=").append(v.isReadable).append('\n')
                if(v.warning.isNotBlank())output.append("WARN P").append(v.number).append(": ").append(v.warning.take(180)).append('\n')
                if(!v.isReadable)return@forEach
                val roots=runCatching{active.list(v.number,null)}
                roots.onFailure{e->
                    output.append("FAIL P").append(v.number).append(" root: ")
                        .append(e.javaClass.simpleName).append(": ").append(e.message?.take(180)).append('\n')
                }
                roots.onSuccess{entries->
                    output.append("PASS P").append(v.number).append(" root entries=").append(entries.size).append('\n')
                    val baseline=entries.map{Triple(it.name,it.directory,it.size)}.sortedBy{it.first}
                    repeat(2){pass->
                        val check=runCatching{active.list(v.number,null).map{Triple(it.name,it.directory,it.size)}.sortedBy{it.first}}
                        output.append(if(check.isSuccess && check.getOrNull()==baseline)"PASS" else "FAIL")
                            .append(" P").append(v.number).append(" root repeat=").append(pass+2)
                            .append(if(check.isFailure)" error="+check.exceptionOrNull()?.javaClass?.simpleName else "")
                            .append('\n')
                    }
                    val targets=if(v.number==3)listOf("configs","addons","extensions","modules","patches")
                        else if(v.number==1)listOf("EFI") else emptyList()
                    targets.forEach{target->
                        val folder=entries.firstOrNull{it.directory && it.name.equals(target,true)}
                        if(folder==null){output.append("NOT FOUND P").append(v.number).append("/").append(target).append('\n')}
                        else {
                            val result=runCatching{active.list(v.number,folder)}
                            result.onSuccess{children->
                                output.append("PASS P").append(v.number).append("/").append(target)
                                    .append(" entries=").append(children.size).append('\n')
                                if(v.number==3 && target=="configs"){
                                    val model=children.firstOrNull{!it.directory && it.name=="DS920+.yml"}
                                    if(model==null)output.append("WARN DS920+ template missing\n")
                                    else {
                                        val attempts=(1..3).map{runCatching{
                                            val value=active.readText(v.number,model,8192)
                                            java.security.MessageDigest.getInstance("SHA-256")
                                                .digest(value.toByteArray(Charsets.UTF_8))
                                                .joinToString(""){"%02x".format(it)}
                                        }}
                                        val hashes=attempts.mapNotNull{it.getOrNull()}
                                        val ok=hashes.size==3 && hashes.distinct().size==1
                                        output.append(if(ok)"PASS" else "FAIL")
                                            .append(" DS920+ template content consistency 3 reads")
                                            .append(" bytes=").append(model.size)
                                            .append(" (hash withheld; config contents not exported)")
                                            .append('\n')
                                        attempts.filter{it.isFailure}.forEach{
                                            output.append("WARN DS920+ read error: ")
                                                .append(it.exceptionOrNull()?.javaClass?.simpleName)
                                                .append('\n')
                                        }
                                    }
                                }
                                if(v.number==3 && target=="modules"){
                                    val archives=children.filter{!it.directory && it.name.endsWith(".tgz",true)}
                                    var passed=0
                                    var failed=0
                                    var checkedBytes=0L
                                    var tgzPassed=0
                                    var tgzFailed=0
                                    archives.forEachIndexed{index,item->
                                        runOnUiThread{
                                            status.text="ตรวจไฟล์ EXT "+(index+1)+"/"+archives.size+" • อ่านอย่างเดียว"
                                        }
                                        val result=runCatching{active.auditExtFile(v.number,item)}
                                        if(result.getOrNull()==true)passed++ else failed++
                                        if(result.getOrNull()==true)checkedBytes+=item.size
                                        val structure=if(result.getOrNull()==true)
                                            runCatching{active.auditExtTgz(v.number,item)}
                                            else null
                                        if(structure?.isSuccess==true)tgzPassed++ else tgzFailed++
                                        output.append(if(structure?.isSuccess==true)"PASS" else "FAIL")
                                            .append(" TGZ structure=").append(item.name.take(90))
                                            .append(" tarEntries=").append(structure?.getOrNull() ?: 0)
                                            .append(" gzipCRC+tarHeader+bounds")
                                            .append(if(structure?.isFailure==true)" error="+structure.exceptionOrNull()?.javaClass?.simpleName else "")
                                            .append('\n')
                                        output.append(if(result.getOrNull()==true)"PASS" else "FAIL")
                                            .append(" EXT archive=").append(item.name.take(90))
                                            .append(" fresh-read SHA-256 bytes=").append(item.size)
                                            .append(" (digest withheld)")
                                            .append(if(result.isFailure)" error="+result.exceptionOrNull()?.javaClass?.simpleName else "")
                                            .append('\n')
                                    }
                                    output.append("SUMMARY EXT archives total=").append(archives.size)
                                        .append(" pass=").append(passed)
                                        .append(" fail=").append(failed)
                                        .append(" passedBytes=").append(checkedBytes)
                                        .append(" readsPerFile=2 (not IMG comparison)\n")
                                    output.append("SUMMARY TGZ structure total=").append(archives.size)
                                        .append(" pass=").append(tgzPassed)
                                        .append(" fail=").append(tgzFailed)
                                        .append(" (gzip CRC and TAR structure; not IMG comparison)\n")
                                }
                                children.take(80).forEach{item->
                                    output.append("  ").append(if(item.directory)"DIR " else "FILE ")
                                        .append(item.name.take(100)).append(if(item.directory)"" else " bytes="+item.size).append('\n')
                                }
                                if(children.size>80)output.append("  TRUNCATED entry list\n")
                            }
                            result.onFailure{e->
                                output.append("FAIL P").append(v.number).append("/").append(target)
                                    .append(": ").append(e.javaClass.simpleName).append(": ")
                                    .append(e.message?.take(180)).append('\n')
                            }
                        }
                    }
                }
            }
            output.append("NOTE: raw LBA 237632 and three 64-sector regions checked uncached; DS920+ template compared in memory; listed EXT module archives checked twice with cache eviction; other files and IMG comparison NOT checked\n")
            output.append("END REPORT • no SN/MAC/key/token values collected\n")
            val value=output.toString()
            runOnUiThread{
                deepReportValue=value;deepReport.text=value
                busy=false;updateButtons();status.text="ตรวจเสร็จ • กดคัดลอกรายงาน"
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
        val sensitive=Regex("(?i)(user-config|serial|token|credential|password|secret|\\.key|\\.pem)")
            .containsMatchIn(file.name)
        if(sensitive){
            AlertDialog.Builder(this).setTitle("ไฟล์ตั้งค่าอาจมีข้อมูลลับ")
                .setMessage("โปรดอย่าส่งภาพเนื้อหาไฟล์นี้ หากต้องการอ่านเฉพาะบนมือถือให้กดดูไฟล์")
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("ดูไฟล์"){_,_->showPreview(file)}
                .show()
        }else showPreview(file)
    }
    private fun showPreview(file:BootDiskReader.Entry){
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
        r.volumes.forEach { v ->
            s.append("พาร์ทิชัน ").append(v.number).append(": ")
                .append(v.fileSystem).append(if(v.isReadable)" • อ่านไฟล์ได้" else " • ยังไม่รองรับ")
                .append("\nLBA ").append(v.startLba).append("–").append(v.endLba).append('\n')
            if(v.fsBytes!=null && v.usedBytes!=null && v.freeBytes!=null) {
                s.append("พื้นที่จัดสรร (ไฟล์ + Metadata): ").append(FormatUtils.bytes(v.usedBytes))
                    .append('\n')
                    .append("พื้นที่ว่างตาม EXT Superblock: ").append(FormatUtils.bytes(v.freeBytes))
                    .append('\n')
                    .append("ขนาด EXT Filesystem: ").append(FormatUtils.bytes(v.fsBytes)).append('\n')
            }
            if(v.warning.isNotBlank())s.append("หมายเหตุ: ").append(v.warning).append('\n')
        }
        r.notes.forEach{s.append("• ").append(it).append('\n')}
        return s.toString()
    }
    private fun updateButtons(){
        if(::openButton.isInitialized)openButton.isEnabled=!busy
        if(::source.isInitialized)source.isEnabled=!busy
    }
}