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
import java.util.*;

public class MainActivity extends Activity {
  static final String GP="com.gpack.agent", GA="com.bratwzi.auto.gpack.activity.LaunchAppActivity";
  static final String COORD="13.7563310,100.5017650", PLACE="อนุสรณ์ดอนเจดีย์ สุพรรณบุรี";
  static final String[] NAMES={"0 Maps ค้นหา (เดิม)","1 Maps URL (เดิม)","2 Maps แอปตรง (เดิม)","3 geo: (เดิม)","4 google.navigation: (เดิม)","5 GPack Navigation (เดิม)","6 GPack Launch (เดิม)","7 คัดลอกลิงก์ (เดิม)",
    "8 GPack + google.navigation URI","9 GPack + geo URI","10 GPack + Maps Search URL",
    "11 GPack + extras URL รูปแบบต่าง ๆ","12 GPack + extras ตัวเลข GPS",
    "13 com.gpack.launch + navigation URI","14 ACTION_VIEW + GPack Navigation URI",
    "15 GPack + Directions URL/EXTRA_TEXT"};
  static final int[] ORDER={8,9,10,11,12,13,14,15};
  static final String NOT="ยังไม่ได้ทดสอบ", PENDING="ส่งคำสั่งแล้ว รอยืนยัน",
    PASS="เปิด Maps + พิกัดตรง + เส้นทาง", PIN="เปิด Maps + พิกัดตรง แต่ไม่เริ่มเส้นทาง", WRONG="เปิด Maps แต่ไม่มีจุดหมาย/จุดหมายผิด", FAIL="ไม่เปิด Maps/ถูกบล็อก";
  TextView info,report,check; EditText destination; LinearLayout panel; ImageView sample;
  SharedPreferences prefs; String[] results=new String[16], notes=new String[16];
  String audit="",lastLink=""; int pending=-1,step=-1;
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    prefs=getSharedPreferences("diagnostic022",0);
    for(int n=0;n<16;n++){results[n]=prefs.getString("r"+n,NOT);notes[n]=prefs.getString("n"+n,"");}
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
    box.addView(label("BYD GPack MAPS TEST 0.2.2",22));
    box.addView(label("สำหรับ Phone Space V0.7.4 • เปิด GPack ได้แล้ว แต่ยังต้องหาวิธีส่งพิกัด • ทดสอบขณะจอดรถ",14));
    sample=new ImageView(this);sample.setAdjustViewBounds(true);
    sample.setScaleType(ImageView.ScaleType.FIT_CENTER);
    box.addView(sample,new LinearLayout.LayoutParams(-1,dp(160)));preview("sample_gps.jpg");
    section(box,"กดครั้งเดียว: ส่งพิกัดเข้า GPack");
    btn(box,"📷 ภาพ GPS ที่ฝังในแอป → Google Maps",()->{
      String c=readGPS("sample_gps.jpg");if(c==null){toast("อ่าน GPS จากภาพตัวอย่างไม่ได้");return;}
      destination.setText(c);preview("sample_gps.jpg");launch(8);
    });
    btn(box,"🔗 ลิงก์ Google Maps สำเร็จรูป → เปิดแผนที่",()->{destination.setText(COORD);launch(10);});
    btn(box,"📍 พิกัด GPS → google.navigation",()->{destination.setText(COORD);launch(8);});
    btn(box,"🔎 ชื่อสถานที่: อนุสรณ์ดอนเจดีย์",()->{destination.setText(PLACE);launch(10);});
    btn(box,"ภาพไม่มี GPS: ตรวจว่าระบบไม่สร้างพิกัดปลอม",()->{
      preview("sample_no_gps.jpg");toast(readGPS("sample_no_gps.jpg")==null?"ถูกต้อง: ภาพไม่มี GPS":"ผิดปกติ: ภาพที่ไม่มี GPS กลับอ่านได้");
    });
    section(box,"ตรวจรวม + บันทึกผล");
    btn(box,"ตรวจสอบทั้งหมดในคลิกเดียว (ไม่เปิดแอปภายนอก)",this::checkAll);
    btn(box,"เริ่มชุดทดสอบ GPack ทั้ง 8 รูปแบบ (ยืนยันผลทีละรูปแบบ)",()->{
      if(pending>=0){toast("กรุณาบันทึกผลการทดสอบเดิมก่อน");return;}
      Arrays.fill(results,NOT);Arrays.fill(notes,"");step=0;destination.setText(COORD);
      save();launch(ORDER[step]);
    });
    panel=new LinearLayout(this);panel.setOrientation(1);
    panel.setPadding(dp(8),dp(7),dp(8),dp(7));panel.setBackgroundColor(Color.rgb(225,241,251));
    panel.addView(label("เมื่อกลับจาก GPack กรุณายืนยันสิ่งที่ปรากฏบนแผนที่จริง",17));
    btn(panel,"✓ เปิด Maps + พิกัดตรง + มีเส้นทาง",()->confirm(PASS));
    btn(panel,"✓ เปิด Maps + พิกัดตรง แต่ยังไม่มีเส้นทาง",()->confirm(PIN));
    btn(panel,"! เปิด Maps แต่ไม่แสดงจุดหมาย/จุดหมายผิด",()->confirm(WRONG));
    btn(panel,"× ไม่เปิด Maps หรือถูกปฏิเสธ",()->confirm(FAIL));
    box.addView(panel);
    btn(box,"ปลดล็อก / ยกเลิกสถานะรอยืนยัน (ไม่ต้องลบแอป)",this::clearPending);
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
    for(int n=8;n<16;n++){final int id=n;btn(box,"ทดสอบ "+NAMES[n],()->launch(id));}
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
    for(int n=0;n<16;n++){e.putString("r"+n,results[n]);e.putString("n"+n,notes[n]);}
    e.putInt("pending",pending).putInt("step",step).putString("audit",audit)
      .putString("link",lastLink).apply();
  }
  void refresh(){
    if(panel!=null)panel.setVisibility(pending>=0?View.VISIBLE:View.GONE);
    if(check!=null)check.setText(audit);
    if(report!=null){int ok=0,wrong=0,wait=0;
      for(int k=8;k<16;k++){String s=results[k];if(s.equals(PASS)||s.equals(PIN))ok++;else if(s.equals(PENDING))wait++;else if(!s.equals(NOT))wrong++;}
      StringBuilder b=new StringBuilder("ยืนยันผ่าน "+ok+"/8 | ไม่ผ่าน "+wrong+" | รอยืนยัน "+wait+"\n");
      for(int k=8;k<16;k++)b.append(NAMES[k]).append(" : ").append(results[k]).append("\n");
      report.setText("ข้อมูลทดสอบ GPack 8 รูปแบบ (รหัส 8–15)\n"+b.toString());
    }
  }
  void launch(int id){
    if(pending>=0){toast("ยังรอยืนยันผลก่อนหน้า");return;}
    String dest=destination.getText().toString().trim();if(dest.isEmpty()){toast("ยังไม่มีจุดหมาย");return;}
    String dir;try{dir=mapsUrl(dest,true);}catch(Exception e){toast(e.getMessage());return;}
    String nav="google.navigation:q="+Uri.encode(dest)+"&mode=d";
    String geo="geo:0,0?q="+Uri.encode(dest);
    String search=mapsUrl(dest,false);
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
        case 8: // GPack action with google.navigation: custom scheme in data
          i=new Intent("com.intent.ACTION_SUPPORT_MAP_NAVIGATION",Uri.parse(nav));i.setClassName(GP,GA);break;
        case 9: // GPack action with geo: query in data
          i=new Intent("com.intent.ACTION_SUPPORT_MAP_NAVIGATION",Uri.parse(geo));i.setClassName(GP,GA);break;
        case 10: // GPack action with Google Maps search URL in data
          i=new Intent("com.intent.ACTION_SUPPORT_MAP_NAVIGATION",Uri.parse(search));i.setClassName(GP,GA);break;
        case 11: // Experimental string extras; actual GPack contract not yet known
          i=new Intent("com.intent.ACTION_SUPPORT_MAP_NAVIGATION");i.setClassName(GP,GA);
          i.putExtra("url",nav);i.putExtra("uri",nav);i.putExtra("navigation",nav);
          i.putExtra("data",nav);i.putExtra("destination",dest);i.putExtra("q",dest);
          i.putExtra(Intent.EXTRA_TEXT,nav);break;
        case 12: // Experimental coordinate extras, no external permissions
          i=new Intent("com.intent.ACTION_SUPPORT_MAP_NAVIGATION");i.setClassName(GP,GA);
          String[] p=dest.split(",");
          if(p.length!=2)throw new IllegalArgumentException("รูปแบบนี้ต้องระบุพิกัด latitude,longitude");
          double lat=Double.parseDouble(p[0].trim()),lng=Double.parseDouble(p[1].trim());
          i.putExtra("latitude",lat);i.putExtra("longitude",lng);
          i.putExtra("lat",lat);i.putExtra("lon",lng);
          i.putExtra("destination",dest);i.putExtra("query",dest);
          i.putExtra("navi_url",nav);break;
        case 13:
          i=new Intent("com.gpack.launch",Uri.parse(nav));i.setClassName(GP,GA);
          i.putExtra("url",nav);i.putExtra("destination",dest);break;
        case 14:
          i=new Intent(Intent.ACTION_VIEW,Uri.parse(nav));i.setClassName(GP,GA);break;
        case 15:
          i=new Intent("com.intent.ACTION_SUPPORT_MAP_NAVIGATION",Uri.parse(dir));i.setClassName(GP,GA);
          i.putExtra(Intent.EXTRA_TEXT,dir);i.putExtra("url",dir);i.putExtra("uri",dir);
          i.putExtra("destination",dest);i.putExtra("navi_url",nav);break;
        default:throw new IllegalArgumentException("method");
      }
      // Persist before launching external apps; the host may pause/kill this Activity.
      results[id]=PENDING;notes[id]="Android accepted launch request, not destination confirmation; action="+i.getAction()+" data="+String.valueOf(i.getData())+" link="+dir;
      pending=id;save();refresh();startActivity(i);
      toast("ส่งคำสั่งแล้ว โปรดกลับมายืนยันว่าพิกัดตรงหรือไม่");
    }catch(Exception e){
      results[id]="ส่งไม่สำเร็จ";notes[id]=e.getClass().getSimpleName()+": "+e.getMessage();
      pending=-1;save();refresh();toast("วิธี "+id+" ส่งไม่ได้: "+notes[id]);
    }
  }
  void clearPending(){
    if(pending>=0){results[pending]="ยกเลิกสถานะค้าง";notes[pending]="ผู้ใช้ยกเลิกการรอยืนยัน";pending=-1;}
    step=-1;save();refresh();toast("ปลดล็อกแล้ว สามารถเลือกทดสอบวิธีใดก็ได้");
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
    StringBuilder b=new StringBuilder("BYD GPACK MAPS QUICKTEST 0.2.2\nPhone Space baseline V0.7.4\n");
    b.append("Android ").append(Build.VERSION.SDK_INT).append(" / ").append(Build.MODEL).append("\n\n");
    b.append(audit).append("\n\n");
    for(int n=8;n<16;n++)b.append(NAMES[n]).append(" = ").append(results[n]).append("\n  ").append(notes[n]).append("\n");
    b.append("NOTE: ส่ง Intent สำเร็จไม่ยืนยันการรับพิกัดของ GPack; สถานะจริงต้องผู้ใช้ดูบนจอและยืนยัน\n");
    return b.toString();
  }
}