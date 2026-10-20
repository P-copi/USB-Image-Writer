package com.pcopi.bydmapsbridge;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity {
  static final String GP="com.gpack.agent", GA="com.bratwzi.auto.gpack.activity.LaunchAppActivity";
  static final String COORD="13.7563310,100.5017650", PLACE="อนุสรณ์ดอนเจดีย์ สุพรรณบุรี";
  static final String[] NAMES={"0 Maps ค้นหา","1 Maps URL เส้นทาง","2 Maps แอปตรง","3 geo:","4 google.navigation:",
    "5 GPack Navigation","6 GPack Launch","7 คัดลอกลิงก์ + GPack"};
  static final int[] ORDER={1,2,3,4,5,6,7,0};
  static final String NOT="ยังไม่ได้ทดสอบ", PENDING="ส่งคำสั่งแล้ว รอยืนยัน",
    PASS="ยืนยัน: เปิด Maps + จุดหมายตรง", WRONG="เปิดได้ แต่จุดหมายไม่ตรง", FAIL="ไม่เปิด Maps/ถูกบล็อก";
  TextView info,report,check; EditText destination; LinearLayout panel; ImageView sample;
  SharedPreferences prefs; String[] results=new String[8], notes=new String[8];
  String audit="",lastLink=""; int pending=-1,step=-1;
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    prefs=getSharedPreferences("diagnostic",0);
    for(int n=0;n<8;n++){results[n]=prefs.getString("r"+n,NOT);notes[n]=prefs.getString("n"+n,"");}
    pending=prefs.getInt("pending",-1);step=prefs.getInt("step",-1);
    audit=prefs.getString("audit","ยังไม่ตรวจ");lastLink=prefs.getString("link","");
    ui(); refresh();
  }
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
  TextView label(String s,int size){
    TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(20,49,72));
    t.setPadding(dp(6),dp(10),dp(6),dp(10));return t;
  }
  void btn(LinearLayout l,String s,Runnable r){
    Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);
    b.setMinHeight(dp(57));b.setOnClickListener(v->r.run());l.addView(b,new LinearLayout.LayoutParams(-1,-2));
  }
  void section(LinearLayout l,String s){l.addView(label(s,19));}
  void toast(String s){if(info!=null)info.setText(s);Toast.makeText(this,s.length()>100?s.substring(0,100):s,Toast.LENGTH_LONG).show();}
  void ui(){
    ScrollView sc=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);
    box.setPadding(dp(16),dp(12),dp(16),dp(32));sc.addView(box);
    box.addView(label("BYD Maps Bridge QUICK TEST 0.2.1",22));
    box.addView(label("สำหรับ Phone Space V0.7.4 • ทดสอบเฉพาะขณะจอดรถ • ไม่ต้องตั้งค่าระบบ",14));
    sample=new ImageView(this);sample.setAdjustViewBounds(true);
    sample.setScaleType(ImageView.ScaleType.FIT_CENTER);
    box.addView(sample,new LinearLayout.LayoutParams(-1,dp(160)));preview("sample_gps.jpg");
    section(box,"กดปุ่มเดียว เปิด Google Maps");
    btn(box,"📷 ภาพ GPS ที่ฝังในแอป → Google Maps",()->{
      String c=readGPS("sample_gps.jpg");if(c==null){toast("อ่าน GPS จากภาพตัวอย่างไม่ได้");return;}
      destination.setText(c);preview("sample_gps.jpg");launch(1);
    });
    btn(box,"🔗 ลิงก์ Google Maps สำเร็จรูป → เปิดแผนที่",()->{destination.setText(COORD);launch(1);});
    btn(box,"📍 พิกัด GPS → google.navigation",()->{destination.setText(COORD);launch(4);});
    btn(box,"🔎 ชื่อสถานที่: อนุสรณ์ดอนเจดีย์",()->{destination.setText(PLACE);launch(0);});
    btn(box,"ภาพไม่มี GPS: ตรวจว่าระบบไม่สร้างพิกัดปลอม",()->{
      preview("sample_no_gps.jpg");toast(readGPS("sample_no_gps.jpg")==null?"ถูกต้อง: ภาพไม่มี GPS":"ผิดปกติ: ภาพที่ไม่มี GPS กลับอ่านได้");
    });
    section(box,"ตรวจรวม + บันทึกผล");
    btn(box,"ตรวจสอบทั้งหมดในคลิกเดียว (ไม่เปิดแอปภายนอก)",this::checkAll);
    btn(box,"เริ่มทดสอบเปิด Google Maps ครบ 8 วิธี",()->{
      if(pending>=0){toast("กรุณาบันทึกผลการทดสอบเดิมก่อน");return;}
      Arrays.fill(results,NOT);Arrays.fill(notes,"");step=0;destination.setText(COORD);
      save();launch(ORDER[step]);
    });
    panel=new LinearLayout(this);panel.setOrientation(1);
    panel.setPadding(dp(8),dp(7),dp(8),dp(7));panel.setBackgroundColor(Color.rgb(225,241,251));
    panel.addView(label("เมื่อกลับจาก Google Maps/GPack กรุณาระบุผลที่เห็นจริง",17));
    btn(panel,"✓ เปิด Maps พร้อมจุดหมายตรง",()->confirm(PASS));
    btn(panel,"! เปิด Maps แต่จุดหมายไม่ตรง",()->confirm(WRONG));
    btn(panel,"× ไม่เปิด Maps หรือถูกปฏิเสธ",()->confirm(FAIL));
    box.addView(panel);
    btn(box,"ไปทดสอบวิธีถัดไป (หากวิธีปัจจุบันส่งไม่สำเร็จ)",()->{
      if(pending>=0){toast("กรุณายืนยันผลที่รออยู่");return;}next();
    });
    check=label(audit,13);check.setTextIsSelectable(true);box.addView(check);
    section(box,"สรุปผล / ส่งกลับเพื่อวิเคราะห์");
    report=label("",14);report.setTextIsSelectable(true);box.addView(report);
    btn(box,"คัดลอกรายงานครบทุกวิธี",()->{
      ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      if(cm==null){toast("ไม่มีคลิปบอร์ด");return;}
      cm.setPrimaryClip(ClipData.newPlainText("BYD Maps Test",reportText()));
      toast("คัดลอกรายงานเรียบร้อยแล้ว");
    });
    btn(box,"แชร์รายงาน",()->{
      Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,reportText());
      try{startActivity(Intent.createChooser(i,"แชร์รายงานทดสอบ"));}catch(Exception e){toast("แชร์ไม่ได้: "+e.getMessage());}
    });
    section(box,"ตัวเลือกเสริม: ข้อมูลจุดหมายและการทดสอบทีละวิธี");
    destination=new EditText(this);destination.setText(COORD);destination.setTextSize(16);
    destination.setHint("ชื่อสถานที่ / พิกัด lat,lon / ลิงก์ Google Maps");
    box.addView(destination);
    for(int n=0;n<8;n++){final int id=n;btn(box,"ทดสอบ "+NAMES[n],()->launch(id));}
    info=label("พร้อมทดสอบ",14);box.addView(info);
    box.addView(label("ข้อควรจำ: ส่ง Intent สำเร็จไม่ใช่หลักฐานว่า Maps แสดงจุดหมาย ต้องดูและยืนยันด้วยตนเอง",13));
    setContentView(sc);
  }
  void preview(String f){try(InputStream in=getAssets().open(f)){
     sample.setImageBitmap(BitmapFactory.decodeStream(in));
   }catch(Exception e){sample.setImageDrawable(null);}}
  String readGPS(String f){try(InputStream in=getAssets().open(f)){
     ExifInterface ex=new ExifInterface(in);float[] xy=new float[2];
     return ex.getLatLong(xy)?String.format(Locale.US,"%.7f,%.7f",xy[0],xy[1]):null;
   }catch(Exception e){return null;}}
  String mapsUrl(String dest,boolean direction){
    if(dest.startsWith("https://")||dest.startsWith("http://")){
      Uri x=Uri.parse(dest);String h=x.getHost();
      if(h==null || !(h.equals("www.google.com")||h.equals("maps.google.com")||h.equals("maps.app.goo.gl")||
          h.equals("google.com")))throw new IllegalArgumentException("รับเฉพาะ Google Maps URL");
      return dest;
    }
    Uri.Builder u=new Uri.Builder().scheme("https").authority("www.google.com")
      .path(direction?"/maps/dir/":"/maps/search/").appendQueryParameter("api","1");
    if(direction){u.appendQueryParameter("destination",dest).appendQueryParameter("travelmode","driving")
      .appendQueryParameter("dir_action","navigate");}
    else u.appendQueryParameter("query",dest);
    return u.build().toString();
  }
  void save(){SharedPreferences.Editor e=prefs.edit();
    for(int n=0;n<8;n++){e.putString("r"+n,results[n]);e.putString("n"+n,notes[n]);}
    e.putInt("pending",pending).putInt("step",step).putString("audit",audit)
      .putString("link",lastLink).apply();
  }
  void refresh(){
    if(panel!=null)panel.setVisibility(pending>=0?View.VISIBLE:View.GONE);
    if(check!=null)check.setText(audit);
    if(report!=null){int ok=0,wrong=0,wait=0;
      for(String s:results){if(s.equals(PASS))ok++;else if(s.equals(PENDING))wait++;else if(!s.equals(NOT))wrong++;}
      StringBuilder b=new StringBuilder("ยืนยันผ่าน "+ok+"/8 | ไม่ผ่าน "+wrong+" | รอยืนยัน "+wait+"\n");
      for(int k=0;k<8;k++)b.append(NAMES[k]).append(" : ").append(results[k]).append("\n");
      report.setText(b.toString());
    }
  }
  void launch(int id){
    if(pending>=0){toast("ยังรอยืนยันผลก่อนหน้า");return;}
    String dest=destination.getText().toString().trim();if(dest.isEmpty()){toast("ยังไม่มีจุดหมาย");return;}
    String dir;try{dir=mapsUrl(dest,true);}catch(Exception e){toast(e.getMessage());return;}
    lastLink=dir;
    try{
      Intent i;
      switch(id){
        case 0: i=new Intent(Intent.ACTION_VIEW,Uri.parse(mapsUrl(dest,false)));break;
        case 1: i=new Intent(Intent.ACTION_VIEW,Uri.parse(dir));break;
        case 2: i=new Intent(Intent.ACTION_VIEW,Uri.parse(dir));i.setPackage("com.google.android.apps.maps");break;
        case 3: if(dest.startsWith("http"))throw new IllegalArgumentException("geo: ต้องใช้พิกัดหรือชื่อสถานที่");
          i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(dest)));break;
        case 4: if(dest.startsWith("http"))throw new IllegalArgumentException("google.navigation ต้องใช้พิกัดหรือชื่อ");
          i=new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(dest)+"&mode=d"));break;
        case 5: i=new Intent("com.intent.ACTION_SUPPORT_MAP_NAVIGATION",Uri.parse(dir));
          i.setClassName(GP,GA);i.putExtra("url",dir);i.putExtra("destination",dest);break;
        case 6: i=new Intent("com.gpack.launch",Uri.parse(dir));i.setClassName(GP,GA);break;
        case 7:
          ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
          if(cb==null)throw new IllegalStateException("ไม่มีคลิปบอร์ด");
          cb.setPrimaryClip(ClipData.newPlainText("Google Maps",dir));
          i=getPackageManager().getLaunchIntentForPackage(GP);
          if(i==null)throw new ActivityNotFoundException("คัดลอกแล้ว แต่ไม่พบ GPack บน Android host");break;
        default:throw new IllegalArgumentException("method");
      }
      startActivity(i);results[id]=PENDING;notes[id]="ส่ง Intent สำเร็จ (ยังไม่ได้ยืนยันจุดหมาย) "+dir;
      pending=id;save();refresh();toast("โปรดกลับมาที่ QuickTest และยืนยันสิ่งที่เห็นจริง");
    }catch(Exception e){
      results[id]="ส่งไม่สำเร็จ";notes[id]=e.getClass().getSimpleName()+": "+e.getMessage();
      pending=-1;save();refresh();toast("วิธี "+id+" ส่งไม่ได้: "+notes[id]);
    }
  }
  void confirm(String result){
    if(pending<0)return;
    int old=pending;results[old]=result;notes[old]="ผู้ใช้ยืนยัน: "+result+"; "+lastLink;
    pending=-1;save();refresh();if(step>=0)next();else toast("บันทึกผลแล้ว");
  }
  void next(){step++;save();if(step<ORDER.length){destination.setText(COORD);launch(ORDER[step]);}
    else{step=-1;save();refresh();toast("ทดสอบครบแล้ว กดคัดลอกรายงาน");}}
  String visible(String p){try{getPackageManager().getPackageInfo(p,0);return "พบ";}catch(Exception e){return "ไม่พบ/อาจอยู่ภายใน GPack";}}
  void checkAll(){
    int ok=0;StringBuilder b=new StringBuilder("PRE-FLIGHT (ตรวจโครงสร้าง ไม่ยืนยัน Maps ภายนอก)\n");
    String gps=readGPS("sample_gps.jpg");
    boolean a=gps!=null && gps.startsWith("13.7563");if(a)ok++;b.append(a?"PASS ":"FAIL ").append("อ่านภาพมี GPS: ").append(gps).append("\n");
    boolean c=readGPS("sample_no_gps.jpg")==null;if(c)ok++;b.append(c?"PASS ":"FAIL ").append("ภาพไม่มี GPS\n");
    String u=mapsUrl(COORD,true);boolean d=u.contains("destination=")&&u.contains("api=1");if(d)ok++;b.append(d?"PASS ":"FAIL ").append("สร้างลิงก์ Directions\n");
    String s=mapsUrl(PLACE,false);boolean f=s.contains("query=")&&s.contains("api=1");if(f)ok++;b.append(f?"PASS ":"FAIL ").append("ค้นหาสถานที่ภาษาไทย\n");
    b.append("ตรวจโครงสร้างผ่าน ").append(ok).append("/4\n");
    b.append("Google Maps host: ").append(visible("com.google.android.apps.maps")).append("\n");
    b.append("GPack host: ").append(visible(GP)).append("\n");
    try{ActivityInfo ainfo=getPackageManager().getActivityInfo(new ComponentName(GP,GA),0);
      b.append("GPack activity: enabled=").append(ainfo.enabled).append(" exported=").append(ainfo.exported).append("\n");}
    catch(Exception e){b.append("GPack activity: ไม่พบ / อาจถูกซ่อน\n");}
    b.append("การมีตัวรับ Android Intent ไม่รับประกันว่า Google Maps ใน GPack เปิดจุดหมายถูกต้อง\n");
    audit=b.toString();save();refresh();toast("ตรวจครบแล้ว "+ok+"/4; กรุณาคัดลอกรายงาน");
  }
  String reportText(){
    StringBuilder b=new StringBuilder("BYD MAPS BRIDGE QUICKTEST 0.2.1\nPhone Space baseline V0.7.4\n");
    b.append("Android ").append(Build.VERSION.SDK_INT).append(" / ").append(Build.MODEL).append("\n\n");
    b.append(audit).append("\n\n");
    for(int n=0;n<8;n++)b.append(NAMES[n]).append(" = ").append(results[n]).append("\n  ").append(notes[n]).append("\n");
    b.append("NOTE: ผล PASS หมายถึงผู้ทดสอบยืนยัน Google Maps พร้อมจุดหมายเท่านั้น\n");
    return b.toString();
  }
}