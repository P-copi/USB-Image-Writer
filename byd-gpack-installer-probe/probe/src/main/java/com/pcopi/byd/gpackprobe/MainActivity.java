package com.pcopi.byd.gpackprobe;

import android.app.Activity;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
  private static final String GPACK="com.gpack.agent";
  private static final String INSTALLER="com.bratwzi.auto.gpack.activity.AppInstallerActivity";
  private static final String BRIDGE="com.pcopi.byd.minibridge";
  private static final String MIME="application/vnd.android.package-archive";
  private static final String AUTHORITY="com.pcopi.byd.gpackprobe.files";
  private TextView status, report;
  private SharedPreferences pref;
  private String lastAction="ยังไม่เริ่มทดสอบ", lastException="", userResult="รอยืนยันจากหน้าจอจริง";
  private File embeddedFile;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);pref=getSharedPreferences("results",MODE_PRIVATE);
    lastAction=pref.getString("action",lastAction); lastException=pref.getString("exception","");
    userResult=pref.getString("userResult",userResult);
    try {
      embeddedFile=new File(getFilesDir(),"test-mini-bridge.apk");
      if(!embeddedFile.isFile()||embeddedFile.length()<1000){
        try(InputStream in=getAssets().open("test-mini-bridge.apk");
            OutputStream out=new FileOutputStream(embeddedFile)){
          byte[] buffer=new byte[8192];int n;
          while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }
      }
    }catch(Exception e){lastException="เตรียม Mini Bridge ไม่สำเร็จ: "+e.toString();}
    render();
  }
  private void render(){
    ScrollView sc=new ScrollView(this);
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,16,24,30);
    sc.addView(box);
    box.addView(text("GPack Installer Probe V0.2.3",23));
    box.addView(text("สำหรับทดสอบการติดตั้ง Mini Bridge ภายใน GPack โดยไม่เปลี่ยนระบบรถหรือ GPack\nกรุณาทดลองเมื่อรถจอดสนิท",15));
    button(box,"1. ตรวจสอบช่องทาง AppInstaller ของ GPack",()->check());
    button(box,"2. เปิดตัวติดตั้ง GPack (ACTION_VIEW APK)",()->startInstaller(Intent.ACTION_VIEW));
    button(box,"3. ทางเลือก: เปิดตัวติดตั้ง GPack (INSTALL_PACKAGE)",()->startInstaller(Intent.ACTION_INSTALL_PACKAGE));
    button(box,"4. ตรวจสถานะ Mini Bridge ใน Android หลัก",()->checkBridge());
    button(box,"5. เปิด GPack ตามปกติ",()->openGPack());
    box.addView(text("หลังจากกดเปิดตัวติดตั้ง หากหน้าจอให้เลือก APK หรือให้ยืนยันการติดตั้ง ให้สังเกตว่าหน้าอยู่ภายใน GPack หรือระบบ Android หลัก อย่ากดยืนยันถ้าขึ้นคำสั่งแก้ไข/อัปเดต GPack",14));
    button(box,"ผลจริง: พบหน้าติดตั้งใน GPack",()->confirm("พบหน้าติดตั้งใน GPack"));
    button(box,"ผลจริง: เปิดตัวติดตั้ง Android ภายนอก",()->confirm("เปิดตัวติดตั้ง Android ภายนอก"));
    button(box,"ผลจริง: กดแล้วไม่มีหน้าติดตั้ง",()->confirm("กดแล้วไม่มีหน้าติดตั้ง"));
    button(box,"ผลจริง: Mini Bridge ปรากฏเป็นไอคอนใน GPack",()->confirm("Mini Bridge ปรากฏเป็นไอคอนใน GPack"));
    button(box,"ผลจริง: ระบบแจ้งว่าไม่อนุญาต",()->confirm("ระบบแจ้งว่าไม่อนุญาต"));
    status=text("",15);report=text("",14);report.setTextIsSelectable(true);
    box.addView(status);box.addView(report);
    button(box,"คัดลอกรายงานผล",()->copyReport());
    setContentView(sc);refresh();
  }
  private TextView text(String s,int sp){ TextView t=new TextView(this);
    t.setText(s);t.setTextSize(sp);t.setPadding(6,10,6,10);return t;}
  private void button(LinearLayout l,String t,Runnable r){
    Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextSize(16);
    b.setMinHeight(58);b.setOnClickListener(v->r.run());l.addView(b);
  }
  private void setStatus(String message){
    lastAction=message;persist();refresh();Toast.makeText(this,message,Toast.LENGTH_LONG).show();
  }
  private void persist(){pref.edit().putString("action",lastAction)
    .putString("exception",lastException).putString("userResult",userResult).apply();}
  private void refresh(){
    if(status!=null)status.setText("สถานะล่าสุด: "+lastAction+"\nผลจากจอ: "+userResult);
    if(report!=null)report.setText(buildReport());
  }
  private void check(){
    try{
      ActivityInfo a=getPackageManager().getActivityInfo(new ComponentName(GPACK,INSTALLER),0);
      lastException="";setStatus("AppInstallerActivity พบ; enabled="+a.enabled+" exported="+a.exported);
    }catch(Exception e){lastException=e.toString();setStatus("Android มองไม่เห็น AppInstallerActivity");}
  }
  private void checkBridge(){
    try{
      getPackageManager().getPackageInfo(BRIDGE,0);
      setStatus("พบ Mini Bridge ใน Android หลัก (ไม่ได้ยืนยันว่าอยู่ใน GPack)");
    }catch(Exception e){setStatus("ไม่พบ Mini Bridge ใน Android หลัก (อาจอยู่ใน GPack หรือยังไม่ได้ติดตั้ง)");}
  }
  private void startInstaller(String action){
    if(embeddedFile==null||!embeddedFile.exists()){setStatus("ไม่พบ APK ตัวอย่าง");return;}
    Uri uri=new Uri.Builder().scheme("content").authority(AUTHORITY)
       .appendPath("test-mini-bridge.apk").build();
    Intent it=new Intent(action);
    it.setClassName(GPACK,INSTALLER);
    it.setDataAndType(uri,MIME);
    it.setClipData(ClipData.newRawUri("BYD Mini Bridge Test APK",uri));
    it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    lastException="";
    lastAction="กำลังส่ง "+action+" ถึง GPack AppInstaller";
    persist();refresh();
    try{
      grantUriPermission(GPACK,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(it);
      lastAction="Android รับคำสั่งเปิด AppInstaller แล้ว (รอผู้ใช้ดูผลที่หน้าจอ)";
    }catch(Exception e){
      lastException=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
      lastAction="เรียก AppInstaller ไม่สำเร็จ";
    }
    persist();refresh();
  }
  private void openGPack(){
    try{
      Intent it=getPackageManager().getLaunchIntentForPackage(GPACK);
      if(it==null){setStatus("ไม่พบทางเปิด GPack ผ่าน Android หลัก");return;}
      startActivity(it);setStatus("ส่งคำสั่งเปิด GPack แล้ว");
    }catch(Exception e){lastException=e.toString();setStatus("เปิด GPack ไม่สำเร็จ");}
  }
  private void confirm(String result){userResult=result;lastAction="ผู้ใช้บันทึกผลจากหน้าจอ";persist();refresh();}
  private String buildReport(){
    int count=pref.getInt("fileOpenedCount",0);
    long length=embeddedFile==null?0:embeddedFile.length();
    return "BYD GP ACK INSTALLER PROBE v0.2.3\n"
     +"Android API: "+Build.VERSION.SDK_INT+" / "+Build.MODEL+"\n"
     +"GPack: "+GPACK+"\n"
     +"Installer: "+INSTALLER+"\n"
     +"Mini Bridge APK: "+length+" bytes\n"
     +"FileProvider เปิดอ่านไฟล์โดยผู้รับ: "+count+" ครั้ง\n"
     +"คำสั่งล่าสุด: "+lastAction+"\n"
     +"ข้อผิดพลาด: "+lastException+"\n"
     +"ผลผู้ใช้: "+userResult+"\n"
     +"หมายเหตุ: เปิด AppInstaller ได้ ไม่ได้แปลว่าติดตั้งเข้า GPack แล้ว\n";
  }
  private void copyReport(){
    ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    c.setPrimaryClip(ClipData.newPlainText("GPack Installer Test",buildReport()));
    Toast.makeText(this,"คัดลอกรายงานแล้ว",Toast.LENGTH_SHORT).show();
  }
  @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
}