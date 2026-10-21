package com.pcopi.byd.webbridge;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.ExifInterface;
import android.content.res.AssetFileDescriptor;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import java.security.MessageDigest;
import java.io.IOException;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.content.pm.PackageManager;
import java.io.InputStream;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
  private static final String URL="http://127.0.0.1:8770";
  private static final String BANGKOK="13.7563310,100.5017650";
  private static final String DONCHEDI="อนุสรณ์ดอนเจดีย์ สุพรรณบุรี";
  private SharedPreferences prefs;
  private EditText input;
  private TextView status;
  private String feedback="ยังไม่ได้รับผล";
  private boolean autoShare=false;
  private Uri pendingExifUri;
  private boolean lastPermissionGranted=false;

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    prefs=getSharedPreferences("bridge",MODE_PRIVATE);
    feedback=prefs.getString("feedback",feedback);
    layout();
    startBridge(); // Starts while main activity is foreground; no separate service-start step.
    handleShare(getIntent());
  }
  @Override protected void onNewIntent(Intent it){super.onNewIntent(it);setIntent(it);handleShare(it);}
  @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
  private void layout(){
    ScrollView sc=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24,16,24,30);
    sc.addView(root);

    header(root,"BYD Photo Navigation V0.2.12",24);
    header(root,"เลือกภาพ GPS → ตรวจและยืนยันหนึ่งครั้ง → ส่งไป GPack → ทดลองนำทางทันที",16);
    button(root,"▶ เลือกภาพ GPS เพื่อเปิด Google Maps",()->choosePhoto());
    header(root,"เมื่อส่งภาพสำเร็จ แอปจะลองเปิด GPack • หาก Chrome ยังอยู่หน้าแอป ให้เปิด Web Bridge ที่ "+URL+" แล้วกดปุ่มนำทางสีเขียวหนึ่งครั้ง",14);

    button(root,"เปิด GPack",this::openGpackAfterPhoto);
    button(root,"เปิด/เริ่มบริการ Web Bridge อีกครั้ง",this::startBridge);
    header(root,"Browser ภายใน GPack: "+URL+" (บันทึกเป็น Bookmark) • เปิดแอปนี้จะเริ่ม Bridge ให้อัตโนมัติ",14);

    LinearLayout advanced=new LinearLayout(this);
    advanced.setOrientation(LinearLayout.VERTICAL);
    advanced.setVisibility(android.view.View.GONE);
    button(root,"แสดง/ซ่อน เครื่องมือทดสอบและรายงาน",()->{
      advanced.setVisibility(advanced.getVisibility()==android.view.View.VISIBLE
        ? android.view.View.GONE : android.view.View.VISIBLE);
      refresh();
    });
    root.addView(advanced);

    header(advanced,"ตั้งค่าและเครื่องมือทดสอบ",19);
    CheckBox openGpack=new CheckBox(this);
    openGpack.setText("ส่งภาพแล้วเปิด GPack อัตโนมัติ (ทดลอง)");
    openGpack.setTextSize(17);
    openGpack.setChecked(prefs.getBoolean("autoOpenGpack",true));
    openGpack.setOnCheckedChangeListener((v,yes)->prefs.edit().putBoolean("autoOpenGpack",yes).apply());
    advanced.addView(openGpack);

    CheckBox directMaps=new CheckBox(this);
    directMaps.setText("ทดลองเปิด Maps จาก Android หลัก (ปกติ Maps อยู่ภายใน GPack)");
    directMaps.setTextSize(17);
    directMaps.setChecked(prefs.getBoolean("tryNativeMaps",false));
    directMaps.setOnCheckedChangeListener((v,yes)->prefs.edit().putBoolean("tryNativeMaps",yes).apply());
    advanced.addView(directMaps);

    button(advanced,"ทดลองเปิด Maps บน Android หลัก",()->{
      String q=prefs.getString("destination","");
      if(!validGpsText(q)){message("กรุณาส่งภาพที่มี GPS จริงก่อน");return;}
      launchNativeMaps(q);
    });
    input=new EditText(this);
    input.setText(BANGKOK);
    input.setHint("ชื่อสถานที่ / พิกัด GPS / ลิงก์ Maps");
    input.setTextSize(17);
    advanced.addView(input);
    button(advanced,"ส่งพิกัดตัวอย่าง → Browser",()->send(BANGKOK,"พิกัดตัวอย่าง"));
    button(advanced,"ส่งอนุสรณ์ดอนเจดีย์ → Browser",()->send(DONCHEDI,"อนุสรณ์ดอนเจดีย์"));
    button(advanced,"ส่งข้อความในช่อง → Browser",()->send(input.getText().toString(),"จุดหมายจากผู้ใช้"));
    button(advanced,"✓ Browser เชื่อมต่อสำเร็จ",()->record("Browser เชื่อมต่อสำเร็จ"));
    button(advanced,"✓ Maps นำทางสำเร็จ",()->record("Google Maps นำทางสำเร็จ"));
    button(advanced,"× Maps ไม่เปิด",()->record("Google Maps ไม่เปิด"));
    header(advanced,"รายงานสภาพการเชื่อมต่อ",19);
    status=new TextView(this);
    status.setTextSize(16);
    status.setTextIsSelectable(true);
    status.setPadding(8,8,8,8);
    advanced.addView(status);
    button(advanced,"รีเฟรชรายงาน",this::refresh);
    button(advanced,"คัดลอกรายงานทั้งหมด",()->copy(report()));
    button(advanced,"หยุด Web Bridge หลังทดสอบ",()->{
      stopService(new Intent(this,BridgeService.class));
      prefs.edit().putString("server","สั่งหยุดบริการแล้ว").apply();
      refresh();
    });
    header(advanced,"พอร์ต 8770 แยกจากรุ่นก่อน • ไม่แก้ไข GPack หรือภาพต้นฉบับ • ทดลองขณะรถจอดสนิท",14);
    setContentView(sc);
    refresh();
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
      if(uri!=null)showSelection(uri);
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
      showSelection(data.getData());
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
  private int dp(int value){
    return (int)(getResources().getDisplayMetrics().density*value+0.5f);
  }
  private long fileLength(Uri uri){
    try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){
      if(c!=null && c.moveToFirst()){
        int ix=c.getColumnIndex(OpenableColumns.SIZE);
        if(ix>=0&&!c.isNull(ix))return c.getLong(ix);
      }
    }catch(Exception ignored){}
    try(AssetFileDescriptor fd=getContentResolver().openAssetFileDescriptor(uri,"r")){
      if(fd!=null)return fd.getLength();
    }catch(Exception ignored){}
    return -1;
  }
  private Bitmap imageThumbnail(Uri uri) throws IOException {
    BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;
    try(InputStream s=getContentResolver().openInputStream(uri)){
      if(s==null)throw new IOException("เปิดรูปไม่ได้");
      BitmapFactory.decodeStream(s,null,b);
    }
    if(b.outWidth<=0||b.outHeight<=0)throw new IOException("ไม่ใช่ภาพที่รองรับ");
    int sample=1;
    while(b.outWidth/sample>800||b.outHeight/sample>800)sample*=2;
    BitmapFactory.Options opt=new BitmapFactory.Options();
    opt.inSampleSize=sample;opt.inPreferredConfig=Bitmap.Config.RGB_565;
    try(InputStream s=getContentResolver().openInputStream(uri)){
      if(s==null)throw new IOException("เปิดรูปไม่ได้");
      Bitmap result=BitmapFactory.decodeStream(s,null,opt);
      if(result==null)throw new IOException("ไม่สามารถแสดงตัวอย่างรูปได้");
      return result;
    }
  }
  // The Android file picker returns a URI when a thumbnail is tapped.
  // This is a valid single file selection, but our separate confirmation avoids silent submission.
  private void showSelection(Uri uri){
    if(uri==null){message("ไม่ได้เลือกรูป");return;}
    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(18),dp(8),dp(18),0);
    TextView info=new TextView(this);
    String name=photoName(uri);
    long bytes=fileLength(uri);
    info.setText("ไฟล์ที่กำลังเลือก:\n"+name+"\nขนาด: "+
         (bytes>=0?String.format(Locale.US,"%,d bytes",bytes):"ไม่ทราบ")+
         "\nประเภท: "+getContentResolver().getType(uri)+
         "\nยังไม่อ่าน GPS และยังไม่ส่งนำทาง");
    info.setTextSize(16);info.setTextIsSelectable(true);
    panel.addView(info);
    ImageView image=new ImageView(this);
    image.setAdjustViewBounds(true);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setMinimumHeight(dp(160));
    image.setMaxHeight(dp(280));
    try{
      image.setImageBitmap(imageThumbnail(uri));
      panel.addView(image,new LinearLayout.LayoutParams(-1,dp(280)));
    }catch(Exception e){
      TextView warn=new TextView(this);
      warn.setText("ไม่สามารถแสดงตัวอย่าง: "+e.getMessage()+
                   "\nคุณยังสามารถตรวจ EXIF ของไฟล์ที่เลือกได้");
      panel.addView(warn);
    }
    CheckBox confirm=new CheckBox(this);
    confirm.setText("✓ ยืนยันว่าเลือกภาพนี้ถูกต้องแล้ว");
    confirm.setTextSize(18);
    confirm.setChecked(false);
    panel.addView(confirm);
    TextView tip=new TextView(this);
    tip.setText("เมื่อติ๊กยืนยันจึงจะตรวจ GPS • พิกัด 0,0 จะไม่ถูกส่ง");
    tip.setTextSize(14);panel.addView(tip);
    ScrollView scroll=new ScrollView(this);
    scroll.addView(panel);
    android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
        .setTitle("ตรวจภาพก่อนอ่าน GPS")
        .setView(scroll)
        .setNegativeButton("ยกเลิก", (d,w)->{})
        .setPositiveButton("ยืนยันภาพ • อ่าน GPS • ส่งนำทาง", (d,w)->{
          prefs.edit().putString("selectedName",name)
            .putString("selectionStatus","ผู้ใช้ยืนยันไฟล์จากตัวเลือกภาพ").apply();
          requestGpsWithPermission(uri);
        }).create();
    dialog.setOnShowListener(d->{
      android.widget.Button positive=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
      positive.setEnabled(false);
      confirm.setOnCheckedChangeListener((buttonView,checked)->positive.setEnabled(checked));
    });
    dialog.show();
  }
  private void requestGpsWithPermission(Uri uri){
    if(uri==null){message("ไม่มีไฟล์ที่เลือก");return;}
    if(Build.VERSION.SDK_INT>=29 && checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
         !=PackageManager.PERMISSION_GRANTED){
      pendingExifUri=uri;
      prefs.edit().putString("photoStatus","กำลังรออนุญาตให้แอปเข้าถึง GPS EXIF ของภาพต้นฉบับ • ยังไม่ส่งนำทาง").apply();
      refresh();
      new android.app.AlertDialog.Builder(this)
       .setTitle("ขอสิทธิ์อ่าน GPS จากภาพ")
       .setMessage("ระบบ Android อาจซ่อนพิกัด GPS ของภาพ เมื่อกดอนุญาต แอปจะลองอ่านภาพต้นฉบับอีกครั้งจาก Flash Drive เพื่อใช้สำหรับนำทางเท่านั้น ไม่มีการส่งรูปไปอินเทอร์เน็ต")
       .setPositiveButton("ขออนุญาต", (d,w)->{
          requestPermissions(new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION},928);
       })
       .setNegativeButton("ยกเลิก", (d,w)->{
          pendingExifUri=null;
          prefs.edit().putString("photoStatus","ผู้ใช้ยังไม่ได้อนุญาตเข้าถึง EXIF ต้นฉบับ").apply();
          refresh();
       }).show();
    }else{
      lastPermissionGranted=Build.VERSION.SDK_INT<29 ||
         checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)==PackageManager.PERMISSION_GRANTED;
      readPhotoGps(uri);
    }
  }
  @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] granted){
    super.onRequestPermissionsResult(req,permissions,granted);
    if(req!=928)return;
    Uri uri=pendingExifUri;pendingExifUri=null;
    boolean ok=granted.length>0&&granted[0]==PackageManager.PERMISSION_GRANTED;
    lastPermissionGranted=ok;
    if(ok && uri!=null){readPhotoGps(uri);}
    else{
      prefs.edit().putString("photoStatus","ระบบไม่ได้รับสิทธิ์ ACCESS_MEDIA_LOCATION • ไม่อ่าน GPS ที่ถูกซ่อน และไม่ส่งนำทาง").apply();
      refresh();message("ยังไม่อนุญาตอ่าน GPS ต้นฉบับ จึงไม่ได้ส่งนำทาง");
    }
  }
  private String checksum(Uri uri) throws Exception{
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    long bytes=0;
    try(InputStream s=getContentResolver().openInputStream(uri)){
      if(s==null)throw new IOException("เปิดไฟล์ไม่ได้");
      byte[] buf=new byte[16*1024];
      int n;
      while((n=s.read(buf))!=-1){
        if(n==0)continue;
        digest.update(buf,0,n);bytes+=n;
      }
    }
    byte[] hash=digest.digest();
    StringBuilder text=new StringBuilder(hash.length*2);
    for(byte b:hash)text.append(String.format(Locale.US,"%02x",b&255));
    return text.toString()+" ("+bytes+" bytes)";
  }
  private static boolean isValidGps(double lat,double lon){
    return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180
      &&!(Math.abs(lat)<0.000001&&Math.abs(lon)<0.000001);
  }
  private static String formatGps(double lat,double lon){
    return String.format(Locale.US,"%.7f,%.7f",lat,lon);
  }
  private static String safe(String v){
    if(v==null || v.length()==0)return "(ไม่มี)";
    return v.length()>100?v.substring(0,100)+"…":v;
  }
  // GPS EXIF values are often three rational numbers: 16/1,49/1,123456/10000.
  private static double fraction(String token)throws NumberFormatException{
    String[] p=token.trim().split("/",-1);
    double num=Double.parseDouble(p[0].trim());
    double den=p.length==2?Double.parseDouble(p[1].trim()):1.0;
    if(den==0)throw new NumberFormatException("GPS rational denominator zero");
    return num/den;
  }
  private static double dms(String raw,String ref)throws NumberFormatException{
    String[] d=raw.split(",",-1);
    if(d.length!=3)throw new NumberFormatException("GPS DMS missing components");
    double value=fraction(d[0])+fraction(d[1])/60.0+fraction(d[2])/3600.0;
    if("S".equalsIgnoreCase(ref)||"W".equalsIgnoreCase(ref))value=-value;
    return value;
  }
  private void readPhotoGps(Uri uri){
    StringBuilder diag=new StringBuilder();
    String name=photoName(uri);
    diag.append("ไฟล์: ").append(name).append('\n');
    diag.append("Android ACCESS_MEDIA_LOCATION: ")
        .append(Build.VERSION.SDK_INT<29?"ไม่จำเป็น":(checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
         ==PackageManager.PERMISSION_GRANTED?"ได้รับอนุญาต":"ไม่ได้รับอนุญาต")).append('\n');
    diag.append("URI authority: ").append(safe(uri.getAuthority())).append('\n');

    diag.append("ยืนยันการเลือก: ")
        .append(prefs.getString("selectionStatus","จาก Android picker")).append('\n');
    diag.append("ชื่อไฟล์ที่ยืนยัน: ").append(prefs.getString("selectedName","(ไม่ได้บันทึก)")).append('\n');
    diag.append("รูปแบบ: ").append(getContentResolver().getType(uri))
        .append(" • URI: ").append(uri.getScheme()).append('\n');
    try{
      // Android may use a different document provider for images read directly from USB.
      try(AssetFileDescriptor fd=getContentResolver().openAssetFileDescriptor(uri,"r")){
        if(fd!=null)diag.append("ความยาวจากผู้ให้บริการไฟล์: ").append(fd.getLength()).append(" bytes\n");
      }catch(Exception e){diag.append("ขนาดไฟล์: ตรวจไม่ได้\n");}
      try{
        diag.append("SHA-256 ของไฟล์ที่แอปได้รับ: ").append(checksum(uri)).append('\n');
      }catch(Exception e){
        diag.append("SHA-256 อ่านไม่ได้: ").append(e.getMessage()).append('\n');
      }
      // Diagnostic EXIF from two independent parsers without changing original USB image.
      double[] stock=null, modern=null;
      try(InputStream s=getContentResolver().openInputStream(uri)){
        if(s==null)throw new IllegalStateException("อ่านภาพไม่ได้");
        ExifInterface classic=new ExifInterface(s);
        float[] xy=new float[2];
        boolean ok=classic.getLatLong(xy);
        if(ok)stock=new double[]{xy[0],xy[1]};
        diag.append("Android EXIF: ").append(ok?formatGps(xy[0],xy[1]):"(อ่านไม่พบ)").append('\n');
        diag.append("Android Lat: ").append(safe(classic.getAttribute(ExifInterface.TAG_GPS_LATITUDE))).append('\n');
        diag.append("Android Lon: ").append(safe(classic.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))).append('\n');
      }catch(Exception e){diag.append("Android reader error: ").append(e.getClass().getSimpleName()).append('\n');}
      try(InputStream s=getContentResolver().openInputStream(uri)){
        if(s==null)throw new IllegalStateException("อ่านภาพไม่ได้");
        androidx.exifinterface.media.ExifInterface x=
          new androidx.exifinterface.media.ExifInterface(s);
        modern=x.getLatLong();
        diag.append("AndroidX EXIF: ").append(modern!=null?formatGps(modern[0],modern[1]):"(อ่านไม่พบ)").append('\n');
        String lat=x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE);
        String lon=x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE);
        String latRef=x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF);
        String lonRef=x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF);
        diag.append("GPSLatitude: ").append(safe(lat)).append('\n');
        diag.append("GPSLatitudeRef: ").append(safe(latRef)).append('\n');
        diag.append("GPSLongitude: ").append(safe(lon)).append('\n');
        diag.append("GPSLongitudeRef: ").append(safe(lonRef)).append('\n');
        if((modern==null||!isValidGps(modern[0],modern[1])) && lat!=null && lon!=null){
          try{
            double candidateLat=dms(lat,latRef), candidateLon=dms(lon,lonRef);
            if(isValidGps(candidateLat,candidateLon)){
              modern=new double[]{candidateLat,candidateLon};
              diag.append("ใช้การอ่านค่า DMS โดยตรงสำเร็จ\n");
            }
          }catch(Exception e){diag.append("DMS error: ").append(e.getMessage()).append('\n');}
        }
      }catch(Exception e){diag.append("AndroidX reader error: ").append(e.getClass().getSimpleName())
                       .append(": ").append(e.getMessage()).append('\n');}
      double[] originalGps=null;
      String originalMethod="ไม่สามารถอ่านไฟล์ต้นฉบับ";
      if(Build.VERSION.SDK_INT>=29){
        Uri mediaUri=null;
        try{
          if("media".equals(uri.getAuthority())){
            mediaUri=uri;
            diag.append("URI source: MediaStore\n");
          }else{
            mediaUri=MediaStore.getMediaUri(this,uri);
            diag.append("SAF → MediaStore: ").append(mediaUri!=null?"สำเร็จ":"ไม่มีรายการเทียบเคียง").append('\n');
          }
        }catch(Exception e){diag.append("SAF → MediaStore error: ")
                            .append(e.getClass().getSimpleName()).append('\n');}
        // First try a true MediaStore original URI, then original-only flag on SAF URI.
        Uri[] candidates=mediaUri==null?new Uri[]{uri}:new Uri[]{mediaUri,uri};
        for(int i=0;i<candidates.length;i++){
          try{
            Uri original=MediaStore.setRequireOriginal(candidates[i]);
            try(InputStream s=getContentResolver().openInputStream(original)){
              if(s==null)throw new IOException("Original stream unavailable");
              androidx.exifinterface.media.ExifInterface x=
                new androidx.exifinterface.media.ExifInterface(s);
              double[] xy=x.getLatLong();
              diag.append("Original URI ").append(i+1).append(" EXIF: ")
                .append(xy==null?"(ไม่มี)":formatGps(xy[0],xy[1])).append('\n');
              String lat=x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE);
              String lon=x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE);
              String lr=x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF);
              String lo=x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF);
              diag.append("Original raw Lat: ").append(safe(lat)).append(" / ").append(safe(lr)).append('\n');
              diag.append("Original raw Lon: ").append(safe(lon)).append(" / ").append(safe(lo)).append('\n');
              if(xy!=null&&isValidGps(xy[0],xy[1])){
                originalGps=xy;originalMethod="ต้นฉบับ MediaStore/SAF";
                break;
              }
              if(lat!=null&&lon!=null){
                try{
                  double a=dms(lat,lr),b=dms(lon,lo);
                  if(isValidGps(a,b)){
                    originalGps=new double[]{a,b};originalMethod="ต้นฉบับ DMS";
                    break;
                  }
                }catch(Exception ignored){}
              }
            }
          }catch(Exception e){
            diag.append("Original URI ").append(i+1).append(" error: ")
              .append(e.getClass().getSimpleName()).append(": ")
              .append(safe(e.getMessage())).append('\n');
          }
        }
      }
      double[] coordinates=null;
      String used="AndroidX";
      if(originalGps!=null){coordinates=originalGps;used=originalMethod;}
      else if(modern!=null&&isValidGps(modern[0],modern[1]))coordinates=modern;
      else if(stock!=null&&isValidGps(stock[0],stock[1])){
        coordinates=stock;used="Android";
      }
      if(coordinates==null){
        diag.append("ผล: ไม่ส่งพิกัด เนื่องจาก GPS ว่าง/0,0/อ่านไม่ได้\n");
        prefs.edit().putString("photoStatus",diag.toString()).apply();
        refresh();
        message("GPS ยังอ่านไม่ได้ กรุณาส่งภาพหน้ารายงาน • ไม่มีการนำทางไป 0,0");
        return;
      }
      String gps=formatGps(coordinates[0],coordinates[1]);
      String label="GPS จากภาพ "+name;
      diag.append("ใช้พิกัดจาก ").append(used).append(": ").append(gps).append('\n');
      prefs.edit().putString("photoStatus",diag.toString()).apply();
      if(input!=null)input.setText(gps);
      saveConfirmedThumbnail(uri,name);
      send(gps,label);
    }catch(Exception e){
      diag.append("อ่านไฟล์ไม่สำเร็จ: ").append(e.getClass().getSimpleName())
          .append(": ").append(e.getMessage());
      prefs.edit().putString("photoStatus",diag.toString()).apply();
      refresh();message("อ่านรูปไม่ได้ — กรุณาคัดลอกรายงาน");
    }
  }
  private void clearThumbnail(){
    prefs.edit().putBoolean("photoAvailable",false).putString("photoName","").apply();
    try{new java.io.File(getCacheDir(),"bridge-photo.jpg").delete();}catch(Exception ignored){}
  }
  private void saveConfirmedThumbnail(Uri uri,String name){
    clearThumbnail();
    java.io.File tmp=new java.io.File(getCacheDir(),"bridge-photo.tmp");
    java.io.File result=new java.io.File(getCacheDir(),"bridge-photo.jpg");
    try{
      Bitmap image=imageThumbnail(uri);
      try(java.io.FileOutputStream out=new java.io.FileOutputStream(tmp)){
        if(!image.compress(Bitmap.CompressFormat.JPEG,78,out)){
          throw new IOException("JPEG thumbnail creation failed");
        }
        out.flush();
      }finally{image.recycle();}
      if(tmp.length()>450000)throw new IOException("ภาพย่อใหญ่เกินไป");
      if(!tmp.renameTo(result))throw new IOException("บันทึกภาพย่อไม่สำเร็จ");
      prefs.edit().putBoolean("photoAvailable",true).putString("photoName",name).apply();
    }catch(Exception e){
      prefs.edit().putBoolean("photoAvailable",false).putString("thumbnailError",e.toString()).apply();
    }finally{tmp.delete();}
  }
  private void send(String s,String label){
    s=s==null?"":s.trim();
    if(s.isEmpty()){message("กรุณาระบุจุดหมาย");return;}
    if(s.length()>500){message("ข้อความยาวเกินไป");return;}
    if(s.matches("^[-+]?0+(?:\\.0+)?\\s*,\\s*[-+]?0+(?:\\.0+)?$")){
      message("ไม่ส่งพิกัด 0,0 เพื่อป้องกันนำทางผิดจุด");return;
    }
    if(label==null||!label.startsWith("GPS จากภาพ"))clearThumbnail();
    prefs.edit().putString("destination",s).putString("label",label)
      .putLong("revision",prefs.getLong("revision",0)+1)
      .putLong("sentAtMs",System.currentTimeMillis())
      .putString("feedback","รอทดสอบใน Browser")
      .apply();
    feedback="รอทดสอบใน Browser";startBridge();refresh();
    message("ส่งภาพ GPS ไป Web Bridge แล้ว: "+s);
    if(label!=null && label.startsWith("GPS จากภาพ") && prefs.getBoolean("tryNativeMaps",false)){
      String goal=s;
      new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
        if(!launchNativeMaps(goal))openGpackAfterPhoto();
      },550);
      return;
    }
    if(label!=null && label.startsWith("GPS จากภาพ") && prefs.getBoolean("autoOpenGpack",true)){
      new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::openGpackAfterPhoto,400);
    }
  }

  private boolean validGpsText(String q){
    if(q==null)return false;
    String[] a=q.trim().split(",",-1);
    if(a.length!=2)return false;
    try{
      double latitude=Double.parseDouble(a[0].trim());
      double longitude=Double.parseDouble(a[1].trim());
      return isValidGps(latitude,longitude);
    }catch(Exception e){return false;}
  }
  // Android host may not see apps installed inside the separate GPack virtual environment.
  // Launch only official Maps package; never let a random navigation app take the destination.
  private boolean launchNativeMaps(String coords){
    if(!validGpsText(coords)){message("ไม่มี GPS จริง จึงไม่เปิด Maps");return false;}
    try{
      Intent nav=new Intent(Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q="+Uri.encode(coords)+"&mode=d"));
      nav.setPackage("com.google.android.apps.maps");
      nav.addCategory(Intent.CATEGORY_DEFAULT);
      nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(nav);
      prefs.edit().putString("nativeLaunch",
        "Android host เรียก Google Maps โดยตรงแล้ว (รอยืนยันว่าหน้าจอนำทางเปิด)").apply();
      refresh();
      return true;
    }catch(ActivityNotFoundException|SecurityException e){
      prefs.edit().putString("nativeLaunch",
        "ไม่พบ Maps บน Android host: "+e.getClass().getSimpleName()+
        " • หาก Maps อยู่ใน GPack ต้องเปิดผ่าน Browser ภายใน GPack").apply();
      refresh();
      message("Maps อยู่ใน GPack หรือ Android host เปิดไม่ได้ • ใช้ปุ่มตรงจาก Browser");
      return false;
    }catch(Exception e){
      prefs.edit().putString("nativeLaunch","เรียก Maps ไม่สำเร็จ: "+e.getMessage()).apply();
      refresh();
      return false;
    }
  }
  private void openGpackAfterPhoto(){
    // Keep a real host-side open action available from UI even if auto-open is disabled.
    if(!prefs.getBoolean("autoOpenGpack",true))return;
    // GPack might offer a BROWSABLE URL dispatcher. If not, return to its launcher safely.
    try{
      Intent open=new Intent(Intent.ACTION_VIEW,Uri.parse(URL));
      open.addCategory(Intent.CATEGORY_BROWSABLE);
      open.setPackage("com.gpack.agent");
      open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if(open.resolveActivity(getPackageManager())!=null){
        startActivity(open);
        prefs.edit().putString("gpackLaunch","เปิด Bridge URL ผ่าน GPack agent โดยตรง (รอยืนยันผล)").apply();
        return;
      }
    }catch(Exception ignored){}
    try{
      Intent go=getPackageManager().getLaunchIntentForPackage("com.gpack.agent");
      if(go!=null){
        go.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(go);
        prefs.edit().putString("gpackLaunch","เปิด GPack launcher (อาจต้องแตะ Chrome)").apply();
      }else{
        prefs.edit().putString("gpackLaunch","ไม่พบ GPack agent").apply();
        message("กรุณาเปิด GPack Browser: "+URL);
      }
    }catch(Exception e){
      prefs.edit().putString("gpackLaunch","เปิด GPack ไม่สำเร็จ: "+e.getMessage()).apply();
      message("เปิด GPack ไม่สำเร็จ: "+e.getMessage());
    }
  }
  private void record(String s){feedback=s;prefs.edit().putString("feedback",s).apply();refresh();message("บันทึกผลแล้ว");}
  private String report(){
    return "BYD Web Bridge V0.2.12\n"
      +"Phone Space baseline V0.7.4 • Separate test port 8770\n"
      +"Android "+Build.VERSION.SDK_INT+" / "+Build.MODEL+"\n"
      +"Service: "+prefs.getString("server","ยังไม่เริ่ม")+"\n"
      +"Browser page visits: "+prefs.getInt("visits",0)+"\n"
      +"Browser polls: "+prefs.getInt("polls",0)+"\n"
      +"Last browser activity: "+prefs.getString("lastSeen","ยังไม่พบ")+"\n"
      +"Destination: "+prefs.getString("destination",BANGKOK)+"\n"
      +"Revision: "+prefs.getLong("revision",0)+"\n"
      +"Auto-open GPack: "+prefs.getBoolean("autoOpenGpack",true)+"\n"
      +"Try native Google Maps: "+prefs.getBoolean("tryNativeMaps",true)+"\n"
      +"Android direct result: "+prefs.getString("nativeLaunch","ยังไม่ได้ลอง")+"\n"
      +"Thumbnail ready: "+prefs.getBoolean("photoAvailable",false)+" • "+prefs.getString("photoName","")+"\n"
      +"GPack entry: "+prefs.getString("gpackLaunch","ยังไม่ได้ลอง")+"\n"
      +"Sent at ms: "+prefs.getLong("sentAtMs",0)+"\n"
      +"ภาพจาก Flash Drive / Original EXIF diagnostic:\n"+prefs.getString("photoStatus","ยังไม่ได้เลือกรูป") +"\n"
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