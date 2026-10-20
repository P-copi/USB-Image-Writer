package com.pcopi.byd.webbridge;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.ExifInterface;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.InputStream;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
  private static final String URL="http://127.0.0.1:8765";
  private static final String BANGKOK="13.7563310,100.5017650";
  private static final String DONCHEDI="อนุสรณ์ดอนเจดีย์ สุพรรณบุรี";
  private SharedPreferences prefs;
  private EditText input;
  private TextView status;
  private String feedback="ยังไม่ได้รับผล";
  private boolean autoShare=false;

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    prefs=getSharedPreferences("bridge",MODE_PRIVATE);
    feedback=prefs.getString("feedback",feedback);
    layout();
    handleShare(getIntent());
  }
  @Override protected void onNewIntent(Intent it){super.onNewIntent(it);setIntent(it);handleShare(it);}
  @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
  private void layout(){
    ScrollView sc=new ScrollView(this);LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,16,24,30);sc.addView(root);
    header(root,"BYD Web Bridge V0.2.5",23);
    header(root,"เลือกภาพจริง ส่ง GPS และทดสอบเปิดนำทางอัตโนมัติผ่าน Browser ภายใน GPack\nสำหรับ Phone Space V0.7.4 • ทดลองขณะรถจอดสนิท",15);
    header(root,"ขั้นที่ 1 — เปิดเซิร์ฟเวอร์สะพาน",19);
    button(root,"เริ่ม Web Bridge บนจอรถ",()->startBridge());
    button(root,"คัดลอกลิงก์ Browser: "+URL,()->copy(URL));
    button(root,"เปิด GPack",()->{
      try{Intent i=getPackageManager().getLaunchIntentForPackage("com.gpack.agent");
        if(i==null){message("ไม่พบปุ่มเปิด GPack ใน Android หลัก");return;}
        startActivity(i);
      }catch(Exception e){message("เปิด GPack ไม่สำเร็จ: "+e.getMessage());}
    });
    header(root,"ขั้นที่ 2 — เปิด Browser ภายใน GPack",19);
    header(root,"ใน Browser ภายใน GPack เปิด Bookmark เดิม: "+URL+"\nเมื่อเข้าได้ควรเห็นข้อความ 'เชื่อมต่อ Web Bridge สำเร็จ' และปุ่มเปิด Google Maps\nบันทึกเว็บไซต์ไว้ใน Bookmark เพื่อใช้ครั้งถัดไป",16);
    header(root,"ขั้นที่ 3 — เลือกภาพ GPS หรือส่งพิกัด",19);
    button(root,"เลือกภาพถ่ายจริงจากจอ BYD → อ่าน GPS → ส่งไปนำทาง",()->choosePhoto());
    header(root,"ถ้าเปิดรูปจาก Phone Space แล้วแชร์ภาพให้เลือก 'BYD Web Bridge Test 0.2.5' ได้ (ถ้าระบบมีปุ่มแชร์)",14);
    input=new EditText(this);input.setText(BANGKOK);input.setSingleLine(false);
    input.setHint("ชื่อสถานที่ / พิกัด GPS / ลิงก์ Maps");input.setTextSize(17);root.addView(input);
    button(root,"ส่งพิกัดตัวอย่างจากภาพ GPS → Browser",()->send(BANGKOK,"พิกัดตัวอย่างจากภาพ GPS"));
    button(root,"ส่งสถานที่: อนุสรณ์ดอนเจดีย์",()->send(DONCHEDI,"อนุสรณ์ดอนเจดีย์"));
    button(root,"ส่งข้อความในช่องด้านบน → Browser",()->send(input.getText().toString(),"จุดหมายที่ผู้ใช้ระบุ"));
    header(root,"ขั้นที่ 4 — ตรวจสิ่งที่เกิดขึ้นจริง",19);
    button(root,"✓ Browser เปิดและแสดง Web Bridge ได้",()->record("Browser เชื่อมสะพานได้"));
    button(root,"× Browser เปิด Web Bridge ไม่ได้",()->record("Browser เชื่อมสะพานไม่ได้"));
    button(root,"✓ Google Maps เปิดพร้อมหมุดตรงพิกัด",()->record("Google Maps มีหมุดพิกัดตรง"));
    button(root,"✓ Google Maps เปิดพร้อมเส้นทางถูกต้อง",()->record("Google Maps มีเส้นทางถูกต้อง"));
    button(root,"! Maps เปิดแต่ไม่มีพิกัด/หมุด",()->record("Maps เปิดแต่ไม่มีหมุด/จุดหมาย"));
    button(root,"× Maps ไม่เปิด",()->record("Maps ไม่เปิด"));
    header(root,"รายงานสภาพการเชื่อมต่อ",19);
    status=new TextView(this);status.setTextSize(16);status.setTextIsSelectable(true);
    status.setPadding(8,8,8,8);root.addView(status);
    button(root,"รีเฟรชรายงาน",this::refresh);
    button(root,"คัดลอกรายงานทั้งหมด",()->copy(report()));
    button(root,"หยุด Web Bridge หลังทดสอบ",()->{
      stopService(new Intent(this,BridgeService.class));prefs.edit().putString("server","สั่งหยุดบริการแล้ว").apply();refresh();
    });
    header(root,"โหมดอัตโนมัติ: เปิดสวิตช์ที่หน้า Web Bridge ใน Browser ก่อน เมื่อมีจุดหมายใหม่และหน้า Browser กลับมาทำงาน จะทดลองเปิดเส้นทางอัตโนมัติ (อาจถูก Browser บล็อก)\nสำคัญ: จอดรถก่อนทดสอบ • ไม่รูท ไม่แก้ GPack • หากยังใช้ V0.2.4 ให้หยุดบริการตัวเก่าก่อน",14);
    setContentView(sc);refresh();
  }
  private void header(LinearLayout box,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(4,12,4,12);box.addView(t);}
  private void button(LinearLayout box,String s,Runnable a){
    Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(54);
    b.setOnClickListener(v->a.run());box.addView(b,new LinearLayout.LayoutParams(-1,-2));
  }
  private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  private void startBridge(){
    try{
      Intent i=new Intent(this,BridgeService.class);
      if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
      if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},901);
      }
      message("กำลังเปิด Web Bridge ที่ "+URL);
    }catch(Exception e){prefs.edit().putString("server","เริ่มบริการไม่สำเร็จ: "+e).apply();message(e.getMessage());}
    refresh();
  }
  private void handleShare(Intent in){
    if(in!=null && Intent.ACTION_SEND.equals(in.getAction()) && in.getType()!=null
       && in.getType().startsWith("image/")){
      Uri uri=null;
      if(Build.VERSION.SDK_INT>=33)uri=in.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);
      else uri=(Uri)in.getParcelableExtra(Intent.EXTRA_STREAM);
      if(uri!=null)readPhotoGps(uri);
      return;
    }
    if(in!=null && Intent.ACTION_SEND.equals(in.getAction())&&"text/plain".equals(in.getType())){
      String s=in.getStringExtra(Intent.EXTRA_TEXT);
      if(s!=null&&!s.trim().isEmpty() && input!=null){
        input.setText(s.trim());send(s.trim(),"ข้อความที่แชร์มาจากแอปอื่น");
      }
    }
  }
  private static final int PICK_IMAGE_GPS=452;
  private void choosePhoto(){
    try{
      Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
      i.setType("image/*");
      i.addCategory(Intent.CATEGORY_OPENABLE);
      startActivityForResult(i,PICK_IMAGE_GPS);
    }catch(Exception e){message("เปิดตัวเลือกภาพไม่สำเร็จ: "+e.getMessage());}
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==PICK_IMAGE_GPS && result==RESULT_OK && data!=null && data.getData()!=null){
      readPhotoGps(data.getData());
    }
  }
  private String photoName(Uri uri){
    String fallback="รูปที่เลือก";
    try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
      if(c!=null && c.moveToFirst()){
        int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if(n>=0)return c.getString(n);
      }
    }catch(Exception ignored){}
    return fallback;
  }
  private void readPhotoGps(Uri uri){
    try(InputStream stream=getContentResolver().openInputStream(uri)){
      if(stream==null)throw new IllegalStateException("เปิดไฟล์ไม่ได้");
      ExifInterface exif=new ExifInterface(stream);
      float[] xy=new float[2];
      if(!exif.getLatLong(xy)){
        message("รูปนี้ไม่มี GPS ใน EXIF กรุณาเลือกรูปที่เปิดบันทึกพิกัดไว้");
        prefs.edit().putString("photoStatus","ไม่มี GPS: "+photoName(uri)).apply();
        refresh();return;
      }
      if(!Float.isFinite(xy[0]) || !Float.isFinite(xy[1])||
          Math.abs(xy[0])>90||Math.abs(xy[1])>180){
        message("พิกัดในรูปไม่ถูกต้อง");return;
      }
      String gps=String.format(Locale.US,"%.7f,%.7f",xy[0],xy[1]);
      String label="GPS จากภาพ "+photoName(uri);
      prefs.edit().putString("photoStatus",label+" → "+gps).apply();
      if(input!=null)input.setText(gps);
      send(gps,label);
    }catch(Exception e){
      prefs.edit().putString("photoStatus","อ่าน EXIF ไม่สำเร็จ: "+e.getClass().getSimpleName()+" "+e.getMessage()).apply();
      refresh();message("อ่าน GPS ไม่สำเร็จ: "+e.getMessage());
    }
  }
  private void send(String s,String label){
    s=s==null?"":s.trim();
    if(s.isEmpty()){message("กรุณาระบุจุดหมาย");return;}
    if(s.length()>500){message("ข้อความยาวเกินไป");return;}
    prefs.edit().putString("destination",s).putString("label",label)
      .putLong("revision",prefs.getLong("revision",0)+1)
      .putString("feedback","รอทดสอบใน Browser")
      .apply();
    feedback="รอทดสอบใน Browser";startBridge();refresh();
    message("ส่ง GPS แล้ว • กลับหน้า Web Bridge ใน GPack (อัตโนมัติถ้าเปิดไว้)");
  }
  private void record(String s){feedback=s;prefs.edit().putString("feedback",s).apply();refresh();message("บันทึกผลแล้ว");}
  private String report(){
    return "BYD Web Bridge V0.2.4\n"
      +"Phone Space baseline V0.7.4\n"
      +"Android "+Build.VERSION.SDK_INT+" / "+Build.MODEL+"\n"
      +"Service: "+prefs.getString("server","ยังไม่เริ่ม")+"\n"
      +"Browser page visits: "+prefs.getInt("visits",0)+"\n"
      +"Browser polls: "+prefs.getInt("polls",0)+"\n"
      +"Last browser activity: "+prefs.getString("lastSeen","ยังไม่พบ")+"\n"
      +"Destination: "+prefs.getString("destination",BANGKOK)+"\n"
      +"Revision: "+prefs.getLong("revision",0)+"\n"
      +"Image GPS: "+prefs.getString("photoStatus","ยังไม่ได้เลือกรูป") +"\n"
      +"Test result: "+prefs.getString("feedback",feedback)+"\n"
      +"เปิด Browser ภายใน GPack ไปที่ "+URL+"\n"
      +"หมายเหตุ: จำนวนครั้งเชื่อมต่อไม่ได้ยืนยันว่า Maps ได้รับพิกัด";
  }
  private void refresh(){if(status!=null)status.setText(report());}
  private void copy(String s){
    ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    if(c==null){message("ระบบคลิปบอร์ดไม่พร้อม");return;}
    c.setPrimaryClip(ClipData.newPlainText("BYD Web Bridge",s));
    message("คัดลอกแล้ว");
  }
}