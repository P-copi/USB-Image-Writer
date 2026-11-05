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
    private lateinit var quickButton:Button
    private lateinit var crcButton:Button
    private lateinit var sandboxButton:Button
    private lateinit var sparseButton:Button
    private lateinit var workspaceButton:Button
    private lateinit var preflightButton:Button
    private lateinit var shrinkButton:Button
    private lateinit var auditButton:Button
    private lateinit var fileAuditButton:Button
    private lateinit var compactPlanButton:Button
    private lateinit var mbrAuditButton:Button
    private lateinit var bootAuditButton:Button
    private lateinit var prefixCaptureButton:Button
    private lateinit var sparseCancelButton:Button
    private var sparseTask:java.util.concurrent.Future<*>?=null
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
        quickButton=ToolkitUi.button(this,
            "Smart Compact V1 • ตรวจความเป็นไปได้อย่างรวดเร็ว (อ่านอย่างเดียว)",
            ToolkitUi.BLUE){requestInspection(false)}
        controls.addView(quickButton,ToolkitUi.margin(this,8))
        crcButton=ToolkitUi.button(this,
            "EXT4 CRC32C • ตรวจ Superblock / Groups / Bitmaps (อ่านอย่างเดียว)",
            ToolkitUi.VIOLET){requestCrcAudit()}
        controls.addView(crcButton,ToolkitUi.margin(this,8))
        sandboxButton=ToolkitUi.button(this,
            "v0.2.19 • ทดสอบย่อ EXT4 และตรวจ SHA-256 ของไฟล์ (ไม่แตะ USB)",
            ToolkitUi.AMBER){requestSandbox()}
        controls.addView(sandboxButton,ToolkitUi.margin(this,8))
        sparseButton=ToolkitUi.button(this,
            "v0.2.20 • ทำสำเนา ARC3 แบบ Sparse บนมือถือ (ทดลอง)",
            ToolkitUi.VIOLET){requestSparseMirror()}
        controls.addView(sparseButton,ToolkitUi.margin(this,8))
        workspaceButton=ToolkitUi.button(this,
            "v0.2.21 • ตรวจและสำรอง ARC3 Sparse ในมือถือ (ไม่แตะ USB)",
            ToolkitUi.BLUE){requestSafeWorkspace()}
        controls.addView(workspaceButton,ToolkitUi.margin(this,8))
        preflightButton=ToolkitUi.button(this,
            "v0.2.22 • ตรวจ EXT4 Workspace ด้วย e2fsck / resize2fs -P (อ่านอย่างเดียว)",
            ToolkitUi.VIOLET){requestArc3Preflight()}
        controls.addView(preflightButton,ToolkitUi.margin(this,8))
        shrinkButton=ToolkitUi.button(this,
            "v0.2.23 • ทดลองย่อ ARC3 Workspace เหลือ 2 GiB (ไม่แตะ USB)",
            ToolkitUi.AMBER){requestArc3Shrink()}
        controls.addView(shrinkButton,ToolkitUi.margin(this,8))
        auditButton=ToolkitUi.button(this,
            "v0.2.25 • ตรวจ Metadata ARC3 หลังย่อ เทียบ Mirror (อ่านอย่างเดียว)",
            ToolkitUi.VIOLET){requestPostShrinkAudit()}
        controls.addView(auditButton,ToolkitUi.margin(this,8))
        fileAuditButton=ToolkitUi.button(this,
            "v0.2.27 • ตรวจ SHA-256 ไฟล์ ARC3 ทุกโฟลเดอร์ (อ่านอย่างเดียว)",
            ToolkitUi.BLUE){requestFileAudit()}
        controls.addView(fileAuditButton,ToolkitUi.margin(this,8))
        compactPlanButton=ToolkitUi.button(this,"v0.2.31 • คำนวณ Compact IMG (ไม่เขียนไฟล์)",ToolkitUi.VIOLET){requestCompactPlan()}
        controls.addView(compactPlanButton,ToolkitUi.margin(this,8))
        mbrAuditButton=ToolkitUi.button(this,"v0.2.32 • ตรวจ MBR ต้นฉบับ (อ่าน USB อย่างเดียว)",ToolkitUi.BLUE){requestSourceMbrAudit()}
        controls.addView(mbrAuditButton,ToolkitUi.margin(this,8))
        bootAuditButton=ToolkitUi.button(this,"v0.2.33 • ตรวจ Boot Headers P1/P2/P3 (อ่านอย่างเดียว)",ToolkitUi.VIOLET){requestBootHeadersAudit()}
        controls.addView(bootAuditButton,ToolkitUi.margin(this,8))
        prefixCaptureButton=ToolkitUi.button(this,"v0.2.34 • สำรอง MBR/P1/P2 (USB อ่านอย่างเดียว)",ToolkitUi.BLUE){requestBootPrefixCapture()}
        controls.addView(prefixCaptureButton,ToolkitUi.margin(this,8))
        sparseCancelButton=ToolkitUi.button(this,
            "ยกเลิกการคัดลอก ARC3 (ลบไฟล์บางส่วน)",
            ToolkitUi.AMBER){
                sparseTask?.cancel(true)
                state.text="ร้องขอยกเลิก ARC3 Sparse Copy แล้ว"
            }
        sparseCancelButton.isEnabled=false
        controls.addView(sparseCancelButton,ToolkitUi.margin(this,8))
        checkButton=ToolkitUi.button(this,"ตรวจทั้งหมด (รวมไฟล์/โฟลเดอร์ใน USB)",
            ToolkitUi.GREEN){requestInspection(true)}
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
            "Smart Compact V1 ตรวจได้เร็วขึ้นโดยไม่เดินสำรวจทุกโฟลเดอร์\n"+
                "EXT4 CRC32C ตรวจเฉพาะ Metadata ส่วนที่รองรับ ยังไม่ใช่ e2fsck\n"+
                "v0.2.19 ตรวจข้อมูลไฟล์ SHA-256 ก่อนและหลังย่อในพื้นที่ของแอป\n"+
                "v0.2.20 สำเนา Sparse แบบทดลอง อ่าน USB ต้นฉบับอย่างเดียว\n"+
                "สำเนา 28.57 GB แบบ Sparse ใช้พื้นที่จริงน้อยกว่า แต่ไม่ได้ย่อ ARC3\n"+
                "ยังไม่มีการย่อ ARC3 จริงหรือสร้าง USB 8 GB\n"+
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

    private fun requestBootPrefixCapture() {
        if(busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) return
        val src=devices.getOrNull(source.selectedItemPosition)
        if(src==null) {state.text="กรุณาเลือก USB ต้นฉบับ";return}
        AlertDialog.Builder(this)
            .setTitle("สำรองส่วนบูตลงมือถือ")
            .setMessage("อ่านข้อมูลจาก USB ต้นฉบับ 105,906,176 ไบต์ และตรวจ SHA-256 ของไฟล์ที่บันทึก ไม่เขียน USB ไม่แก้ไข ARC3")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เริ่มสำรอง"){_,_->
                requestPermission(src) {
                    busy=true
                    prefixCaptureButton.isEnabled=false
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    state.text="กำลังสำรอง MBR/P1/P2 • คงหน้าจอแอปไว้"
                    worker.execute {
                        val report=CompactBootPrefixCapture.run(applicationContext,usb,src) {
                            Thread.currentThread().isInterrupted
                        }
                        runOnUiThread {
                            if(!isFinishing) {
                                reportText=report.text
                                result.text=report.text
                                copyButton.isEnabled=true
                                state.text=if(report.passed) "PASS: สำรองส่วนบูตพร้อม SHA-256" else "BLOCKED: สำรองส่วนบูตไม่สำเร็จ"
                            }
                            busy=false
                            prefixCaptureButton.isEnabled=true
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }.show()
    }

    private fun requestBootHeadersAudit() {
        if(busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) return
        val src=devices.getOrNull(source.selectedItemPosition)
        if(src==null) { state.text="กรุณาเลือก USB ต้นฉบับ"; return }
        AlertDialog.Builder(this)
            .setTitle("ตรวจ Boot Headers จาก USB ต้นฉบับ")
            .setMessage("อ่าน 4 sectors เพื่อตรวจ FAT/EXT เท่านั้น ไม่มีการเขียน USB")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เริ่มตรวจ"){_,_->
                requestPermission(src) {
                    busy=true
                    bootAuditButton.isEnabled=false
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    worker.execute {
                        val report=CompactBootHeadersAudit.run(usb,src)
                        runOnUiThread {
                            if(!isFinishing) {
                                reportText=report.text
                                result.text=report.text
                                copyButton.isEnabled=true
                                state.text=if(report.passed) "PASS: Boot headers" else "BLOCKED: Boot headers"
                            }
                            busy=false
                            bootAuditButton.isEnabled=true
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }.show()
    }

    private fun requestSourceMbrAudit() {
        if(busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) return
        val src=devices.getOrNull(source.selectedItemPosition)
        if(src==null) {
            state.text="กรุณาเสียบ USB ต้นฉบับและเลือกในรายการ"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("ตรวจ MBR จาก USB ต้นฉบับ")
            .setMessage("อ่านเฉพาะ Sector 0 ของ USB ต้นฉบับ ไม่เขียนข้อมูลใด ๆ")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เริ่มตรวจ"){_,_->
                requestPermission(src) {
                    busy=true
                    mbrAuditButton.isEnabled=false
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    worker.execute {
                        val report=CompactSourceMbrAudit.run(usb,src)
                        runOnUiThread {
                            if(!isFinishing) {
                                reportText=report.text
                                result.text=report.text
                                copyButton.isEnabled=true
                                state.text=if(report.passed) "MBR ตรงกับแผน • ยังไม่สร้าง IMG" else "BLOCKED: ตรวจ MBR ไม่ผ่าน"
                            }
                            busy=false
                            mbrAuditButton.isEnabled=true
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }.show()
    }

    private fun requestCompactPlan() {
        if(busy) return
        val report=CompactImgPlan.run(this)
        reportText=report.text
        result.text=report.text
        copyButton.isEnabled=true
        state.text=if(report.passed) "คำนวณสำเร็จ • ยังไม่สร้าง IMG" else "BLOCKED: geometry check"
    }

    private fun requestFileAudit() {
        if (busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงานอื่นทำงานอยู่ กรุณารอให้เสร็จก่อน"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("v0.2.27 • ตรวจ SHA-256 รายไฟล์")
            .setMessage("อ่านไฟล์ทั้งหมดใน ARC3 Mirror และ Workspace แบบไม่แก้ไข " +
                "เทียบรายชื่อ ชนิด ขนาด และ SHA-256 ของไฟล์ปกติทุกโฟลเดอร์ " +
                "หากพบ Symlink หรือไฟล์ชนิดพิเศษจะหยุดแบบ BLOCKED " +
                "ต้องมีพื้นที่ว่างสำหรับดึงไฟล์ทีละไฟล์ เปิดแอปค้างไว้จนเสร็จ")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เริ่มตรวจ"){_,_->
                busy=true
                fileAuditButton.isEnabled=false
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                state.text="กำลังตรวจ SHA-256 ของไฟล์ ARC3 ทั้งสองชุด"
                worker.execute {
                    val audit=Arc3FileIntegrityAudit.run(this)
                    runOnUiThread {
                        if(!isFinishing) {
                            reportText=audit.text
                            result.text=audit.text
                            copyButton.isEnabled=true
                            state.text=if(audit.passed)
                                "PASS: SHA-256 ไฟล์ปกติทั้งหมดตรงกัน"
                            else "BLOCKED: ตรวจ SHA-256 ไม่ผ่าน • เก็บไฟล์เดิมไว้"
                        }
                        busy=false
                        fileAuditButton.isEnabled=true
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }.show()
    }

    private fun requestPostShrinkAudit() {
        if (busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงานอื่นทำงานอยู่ กรุณารอให้เสร็จก่อน"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("v0.2.25 • ตรวจ ARC3 หลังย่อ")
            .setMessage("ตรวจ e2fsck, UUID, features และรายการรากของ EXT4 " +
                "เทียบกับ ARC3 Mirror เดิม โดยอ่านอย่างเดียว " +
                "ยังไม่ใช่การตรวจ SHA-256 ของไฟล์ทั้งหมด")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เริ่มตรวจ"){_,_->
                busy=true
                auditButton.isEnabled=false
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                state.text="กำลังตรวจ ARC3 Workspace และ Mirror (อ่านอย่างเดียว)"
                worker.execute {
                    val audit=Arc3PostShrinkAudit.run(this)
                    runOnUiThread {
                        if(!isFinishing) {
                            reportText=audit.text
                            result.text=audit.text
                            copyButton.isEnabled=true
                            state.text=if(audit.passed)
                                "PASS: Metadata ผ่าน • ยังไม่ตรวจ SHA-256 ไฟล์ทั้งหมด"
                            else "BLOCKED: ตรวจ Metadata ไม่ผ่าน"
                        }
                        busy=false
                        auditButton.isEnabled=true
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }.show()
    }

    private fun requestArc3Shrink() {
        if (busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงานอื่นทำงานอยู่ กรุณารอให้เสร็จก่อน"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("v0.2.23 • ทดลองย่อ Workspace เป็น 2 GiB")
            .setMessage("ขั้นตอนนี้แก้ไขไฟล์ ARC3 Workspace v0.2.21 จริงและไม่สามารถย้อนกลับในไฟล์เดิม " +
                "แต่จะรักษา ARC3 Mirror v0.2.20 ต้นฉบับไว้ ไม่เปิด USB และไม่สร้าง IMG " +
                "ต้องเปิดแอปค้างไว้จนตรวจ e2fsck หลังย่อเสร็จ ห้ามปิดแอปหรือรีสตาร์ทระหว่างทำงาน")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ยืนยันย่อ Workspace"){_,_->
                busy=true
                shrinkButton.isEnabled=false
                preflightButton.isEnabled=false
                workspaceButton.isEnabled=false
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                state.text="กำลังย่อ Workspace และตรวจ EXT4 • ห้ามปิดแอป"
                worker.execute {
                    val report=Arc3ShrinkExperiment.run(this)
                    runOnUiThread {
                        if(!isFinishing) {
                            reportText=report.text
                            result.text=report.text
                            copyButton.isEnabled=true
                            state.text=if(report.passed)
                                "PASS: ย่อ Workspace เป็น 2 GiB • ยังไม่ตรวจไฟล์/บูต"
                            else "BLOCKED: ย่อ Workspace ไม่สำเร็จ • เก็บ ARC3 Mirror เดิมไว้"
                        }
                        busy=false
                        shrinkButton.isEnabled=true
                        preflightButton.isEnabled=true
                        workspaceButton.isEnabled=true
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }.show()
    }

    private fun requestArc3Preflight() {
        if (busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงานอื่นทำงานอยู่ กรุณารอให้เสร็จก่อน"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("v0.2.22 • ตรวจ ARC3 แบบอ่านอย่างเดียว")
            .setMessage("ตรวจ e2fsck -fn และประมาณขนาดต่ำสุดด้วย resize2fs -P " +
                "เฉพาะ Workspace ที่สร้างจาก v0.2.21 เท่านั้น " +
                "ไม่มีการย่อไฟล์ ไม่ซ่อม EXT4 และไม่เปิด USB")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เริ่มตรวจ"){_,_->
                busy=true
                preflightButton.isEnabled=false
                workspaceButton.isEnabled=false
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                state.text="กำลังตรวจ e2fsck และ resize2fs -P (อ่านอย่างเดียว)"
                worker.execute {
                    val report=Arc3ReadOnlyPreflight.run(this) {
                        Thread.currentThread().isInterrupted
                    }
                    runOnUiThread {
                        if (!isFinishing) {
                            reportText=report.text
                            result.text=report.text
                            copyButton.isEnabled=true
                            state.text=if(report.passed)
                                "PASS: EXT4 preflight ผ่าน • ยังไม่ย่อ ARC3"
                            else "BLOCKED: ตรวจ EXT4 ไม่ผ่าน • ไม่ย่อ ARC3"
                        }
                        busy=false
                        preflightButton.isEnabled=true
                        workspaceButton.isEnabled=true
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }.show()
    }

    private fun requestSafeWorkspace() {
        if (busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงานอื่นทำงานอยู่ กรุณารอให้เสร็จก่อน"
            return
        }
        val sourceFile=java.io.File(filesDir,
            "legacy-arc3-mirror-v0220/arc3-mirror.ext4")
        if (!sourceFile.isFile) {
            state.text="ยังไม่มี ARC3 Sparse Mirror v0.2.20 ที่ผ่านการตรวจในมือถือ"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("v0.2.21 • Safe ARC3 Workspace")
            .setMessage("สร้างสำเนา Sparse แยกจาก ARC3 ต้นฉบับในพื้นที่ส่วนตัวของแอป " +
                "แล้วอ่านกลับเพื่อตรวจ SHA-256 ทุกไบต์ ไม่ต้องเสียบ USB และไม่ย่อระบบไฟล์ในขั้นนี้\\n\\n" +
                "อาจใช้เวลานานเนื่องจาก ARC3 มีขนาดเชิงตรรกะประมาณ 28.57 GB " +
                "กรุณาเสียบชาร์จและคงหน้าจอไว้")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เริ่มตรวจสำเนา"){_,_->
                busy=true
                workspaceButton.isEnabled=false
                sparseButton.isEnabled=false
                checkButton.isEnabled=false
                quickButton.isEnabled=false
                crcButton.isEnabled=false
                sandboxButton.isEnabled=false
                copyButton.isEnabled=false
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                state.text="กำลังสร้างและตรวจสำเนา ARC3 • ไม่เปิด USB"
                worker.execute {
                    try {
                        val folder=java.io.File(filesDir,"arc3-workspace-v0221")
                        if(!folder.exists() && !folder.mkdirs())
                            throw java.io.IOException("สร้างโฟลเดอร์ Workspace ไม่ได้")
                        val targetFile=java.io.File(folder,"arc3-working.ext4")
                        val report=SafeArc3Workspace.copyReadOnly(sourceFile,targetFile) {
                            Thread.currentThread().isInterrupted
                        }
                        val message="=== ARC3 Workspace v0.2.21 ===\\n" +
                            "ต้นฉบับไม่ถูกแก้ไข / ไม่เปิด USB\\n" +
                            "ขนาดเชิงตรรกะ: "+report.logicalBytes+" bytes\\n" +
                            "ข้อมูลบล็อกไม่เป็นศูนย์ที่เขียน: "+report.nonZeroBytesWritten+" bytes\\n" +
                            "SHA-256 source: "+report.sourceDigest+"\\n" +
                            "SHA-256 working: "+report.workingDigest+"\\n" +
                            "PASS: สำเนาตรงกัน • ยังไม่ได้ย่อ EXT4"
                        runOnUiThread {
                            if(!isFinishing) {
                                reportText=message
                                result.text=message
                                copyButton.isEnabled=true
                                state.text="ตรวจสำเนา SHA-256 ผ่าน • ยังไม่ใช่ Compact IMG"
                            }
                        }
                    }catch(e:Exception) {
                        runOnUiThread {
                            if(!isFinishing)state.text=
                                "BLOCKED: "+(e.message?:e.javaClass.simpleName)
                        }
                    }finally {
                        runOnUiThread {
                            busy=false
                            workspaceButton.isEnabled=true
                            sparseButton.isEnabled=true
                            checkButton.isEnabled=true
                            quickButton.isEnabled=true
                            crcButton.isEnabled=true
                            sandboxButton.isEnabled=true
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }.show()
    }

    private fun requestSparseMirror() {
        if(busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงาน USB อื่นทำงานอยู่ กรุณาลองใหม่"
            return
        }
        val src=devices.getOrNull(source.selectedItemPosition)
        if(src==null) {
            state.text="กรุณาต่อ USB Arc Loader 23.11.4a ผ่าน OTG และเลือกต้นฉบับ"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("v0.2.20 • สำเนา ARC3 แบบ Sparse")
            .setMessage(
                "งานทดลองนี้จะอ่านข้อมูล ARC3 จาก USB รุ่นเก่าประมาณ 1–3 GB " +
                "หรือมากกว่าตาม Metadata จริง และสร้างไฟล์ Sparse ที่มีขนาดเชิงตรรกะ 28.57 GB " +
                "แต่ใช้พื้นที่จริงน้อยกว่าในโทรศัพท์\n\n"+
                "ไม่แก้ไข USB ต้นฉบับ ไม่เขียน USB ปลายทาง ไม่ใช่ไฟล์บูตได้ทันที\n\n"+
                "หากอ่าน USB ไม่ได้ หรือ e2fsck ไม่ผ่าน จะลบไฟล์ partial " +
                "ควรเสียบผ่าน OTG ที่มั่นคงและคงหน้าจอแอปไว้ระหว่างทำงาน")
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("เริ่มทำสำเนา") { _, _ ->
                requestPermission(src) {
                    busy=true
                    checkButton.isEnabled=false
                    quickButton.isEnabled=false
                    crcButton.isEnabled=false
                    sandboxButton.isEnabled=false
                    sparseButton.isEnabled=false
                    sparseCancelButton.isEnabled=true
                    source.isEnabled=false
                    target.isEnabled=false
                    copyButton.isEnabled=false
                    reportText=""
                    state.text="กำลังตรวจและทำสำเนา ARC3 (ต้นฉบับ Read-only)…"
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    sparseTask=worker.submit {
                        try {
                            val mirror=Arc3SparseMirror.create(
                                applicationContext,usb,src,
                                progress={msg -> runOnUiThread {if(!isFinishing)state.text=msg}},
                                cancelled={Thread.currentThread().isInterrupted}
                            )
                            runOnUiThread {
                                if(!isFinishing){
                                    reportText=mirror.report
                                    result.text=mirror.report
                                    copyButton.isEnabled=true
                                    state.text=if(mirror.success)
                                        "มีสำเนา Sparse ที่ผ่าน e2fsck • ยังไม่ใช่ Compact IMG"
                                        else "Sparse Copy ถูกหยุด/ไม่ผ่าน • ดูสาเหตุในรายงาน"
                                }
                            }
                        }catch(e:Exception){
                            runOnUiThread{
                                if(!isFinishing)state.text=
                                    "Sparse Copy ไม่สำเร็จ: "+(e.message?:e.javaClass.simpleName)
                            }
                        }finally {
                            runOnUiThread{
                                busy=false
                                checkButton.isEnabled=true
                                quickButton.isEnabled=true
                                crcButton.isEnabled=true
                                sandboxButton.isEnabled=true
                                sparseButton.isEnabled=true
                                sparseCancelButton.isEnabled=false
                                source.isEnabled=true
                                target.isEnabled=true
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                    }
                }
            }.show()
    }

    private fun requestSandbox() {
        if(busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงาน USB อื่นกำลังทำงาน กรุณาลองใหม่"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("ทดสอบ EXT4 Resize แบบปลอดภัย")
            .setMessage(
                "ทดสอบสร้าง EXT4 จำลอง 96 MiB ใส่ไฟล์ข้อมูลทดสอบ 4 MiB " +
                "ตรวจ SHA-256 ก่อนและหลังย่อเหลือ 32 MiB ด้วยเครื่องมือที่อยู่ใน APK " +
                "ลบไฟล์ทดสอบเมื่อเสร็จ ไม่เปิดหรือเขียน Flash Drive")
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("เริ่มทดสอบ") { _, _ ->
                busy=true
                checkButton.isEnabled=false
                quickButton.isEnabled=false
                crcButton.isEnabled=false
                sandboxButton.isEnabled=false
                source.isEnabled=false
                target.isEnabled=false
                copyButton.isEnabled=false
                reportText=""
                state.text="กำลังทดสอบ EXT4 บนไฟล์จำลองในแอป…"
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                worker.execute {
                    try {
                        val report=Ext4ShrinkSandbox.run(
                            applicationContext,
                            progress={msg -> runOnUiThread{if(!isFinishing) state.text=msg}},
                            cancelled={Thread.currentThread().isInterrupted}
                        )
                        runOnUiThread {
                            if(!isFinishing) {
                                reportText=report.text
                                result.text=report.text
                                copyButton.isEnabled=true
                                state.text=if(report.success)
                                    "ผ่านการย่อจำลองและ Verify SHA-256 • USB ยังไม่ถูกแก้ไข"
                                    else "ไม่ผ่าน/เครื่องมือไม่พร้อม • ดูรายงานเพื่อวางแผนต่อ"
                            }
                        }
                    } catch(e:Exception) {
                        runOnUiThread {
                            if(!isFinishing)
                                state.text="ไม่สามารถทดสอบได้: "+(e.message ?: e.javaClass.simpleName)
                        }
                    } finally {
                        runOnUiThread {
                            busy=false
                            checkButton.isEnabled=true
                            quickButton.isEnabled=true
                            crcButton.isEnabled=true
                            sandboxButton.isEnabled=true
                            source.isEnabled=true
                            target.isEnabled=true
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }.show()
    }

    private fun requestCrcAudit() {
        if(busy || UsbBackupService.snapshot.running || UsbOperationService.snapshot.running) {
            state.text="มีงาน USB อื่นกำลังทำงาน กรุณาลองใหม่"
            return
        }
        val src=devices.getOrNull(source.selectedItemPosition)
        if(src==null){state.text="กรุณาเลือก USB ต้นฉบับ";return}
        requestPermission(src) {
            busy=true
            checkButton.isEnabled=false
            quickButton.isEnabled=false
            crcButton.isEnabled=false
            sandboxButton.isEnabled=false
            source.isEnabled=false
            target.isEnabled=false
            copyButton.isEnabled=false
            reportText=""
            state.text="กำลังตรวจ CRC32C ของ EXT4 (Read-only)…"
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            worker.execute {
                try {
                    val data=ExtRebuildInspector.inspect(
                        usb,src,null,
                        onProgress={message -> runOnUiThread{if(!isFinishing)state.text=message}},
                        cancelled={Thread.currentThread().isInterrupted}
                    )
                    val p3=data.partitions.firstOrNull{it.number==3 && it.filesystem=="EXT4"}
                        ?: throw java.io.IOException("ไม่พบ ARC3 EXT4 ที่รองรับ")
                    val audit=Ext4MetadataAudit.inspect(
                        usb,src,p3,
                        progress={message -> runOnUiThread{if(!isFinishing)state.text=message}},
                        cancelled={Thread.currentThread().isInterrupted}
                    )
                    val text=formatReport(data)+audit.format()
                    runOnUiThread {
                        reportText=text
                        result.text=text
                        copyButton.isEnabled=true
                        state.text=if(audit.errors.isEmpty() && audit.descriptors==audit.groups)
                            "ตรวจ CRC ส่วนที่รองรับแล้ว • ยังไม่อนุญาตให้ย่อ" else
                            "พบปัญหา CRC / ตรวจไม่ครบ • ห้ามย่อหรือเขียน"
                    }
                } catch(e:Exception) {
                    runOnUiThread {
                        state.text="ตรวจ CRC ไม่สำเร็จ: "+(e.message ?: e.javaClass.simpleName)
                        result.text="CRC: FAILED/INCOMPLETE • ไม่แก้ไข USB ต้นฉบับ"
                    }
                } finally {
                    runOnUiThread {
                        busy=false
                        checkButton.isEnabled=true
                        quickButton.isEnabled=true
                        crcButton.isEnabled=true
                        sandboxButton.isEnabled=true
                        source.isEnabled=true
                        target.isEnabled=true
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    private fun requestInspection(deep:Boolean){
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
            if(dest!=null)requestPermission(dest){inspect(src,dest,deep)}
            else inspect(src,null,deep)
        }
    }

    private fun inspect(src:UsbDevice,dest:UsbDevice?,deep:Boolean) {
        busy=true
        checkButton.isEnabled=false
        quickButton.isEnabled=false
        crcButton.isEnabled=false
        sandboxButton.isEnabled=false
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
                val text = formatReport(data) +
                    LegacyCompactPlanner.format(data) +
                    (if (deep) formatRebuildGate(data) + inspectBootFiles(src) else
                        "\n=== ตรวจด่วน ===\n" +
                        "ไม่ได้สแกนรายการไฟล์เพื่อประหยัดเวลา • ไม่ใช่การยืนยันว่าไฟล์ทั้งหมดอ่านได้\n")
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
                    quickButton.isEnabled=true
                    crcButton.isEnabled=true
                    sandboxButton.isEnabled=true
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
        // Capacity planning is read-only; it is not proof of a safe shrink.
        val extBlock=p3.blockBytes?.toLong() ?: 4096L
        val usableBytes=(availableAfterReserve/extBlock)*extBlock
        b.append("ARC3 planned capacity (aligned, estimate only): ")
            .append(FormatUtils.bytes(usableBytes)).append('\n')
        b.append("EXT4 block alignment: ").append(extBlock).append(" B\n")
        b.append("ยังไม่ได้ยืนยันว่าระบบไฟล์ EXT4 สามารถสร้างใหม่ในพื้นที่นี้ได้\n")
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
                        // Bounded recursive metadata inventory; no file contents or secrets.
                        // Inode/cluster keys prevent directory loops on malformed media.
                        val pending=java.util.ArrayDeque<Triple<BootDiskReader.Entry,String,Int>>()
                        val seen=mutableSetOf<Long>()
                        for(dir in root.filter{it.directory})
                            pending.add(Triple(dir,"/"+dir.name,1))
                        var files=root.count{!it.directory}
                        var folders=root.count{it.directory}
                        var bytes=root.filter{!it.directory}.sumOf{it.size.coerceAtLeast(0L)}
                        var walked=0
                        var errors=0
                        val errorDetails=mutableListOf<String>()
                        var truncated=false
                        val maxDirs=200
                        val maxDepth=8
                        val maxEntries=1500
                        var entries=root.size
                        while(pending.isNotEmpty()){
                            if(walked>=maxDirs || entries>=maxEntries) {
                                truncated=true
                                break
                            }
                            val (dir,location,depth)=pending.removeFirst()
                            if(depth>maxDepth){truncated=true;continue}
                            if(!seen.add(dir.cluster))continue
                            walked++
                            try {
                                val children=reader.list(volume.number,dir)
                                if(entries+children.size>maxEntries){truncated=true;break}
                                entries+=children.size
                                for(child in children){
                                    if(child.directory){
                                        folders++
                                        pending.add(Triple(child,location+"/"+child.name,depth+1))
                                    } else {
                                        files++
                                        bytes+=child.size.coerceAtLeast(0L)
                                    }
                                }
                            }catch(e:Exception){
                                errors++
                                if(errorDetails.size<12){
                                    val message=(e.message ?: e.javaClass.simpleName)
                                        .replace('\n',' ').replace('\r',' ').take(180)
                                    errorDetails.add(location.take(180)+" : "+message)
                                }
                            }
                        }
                        out.append("Recursive inventory: ")
                            .append(files).append(" files, ")
                            .append(folders).append(" folders, ")
                            .append(FormatUtils.bytes(bytes)).append(" apparent file size\n")
                        out.append("Walked directories: ").append(walked)
                            .append(" | read errors: ").append(errors).append('\n')
                        if(truncated || pending.isNotEmpty())
                            out.append("INCOMPLETE: inventory limits reached; do not use for copying\n")
                        if(errors>0)
                            out.append("INCOMPLETE: some directories could not be read\n")
                        for(detail in errorDetails)
                            out.append("READ ERROR: ").append(detail).append('\n')

                        out.append("Metadata-only: permissions, links, hardlinks and CRC not verified\n")

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