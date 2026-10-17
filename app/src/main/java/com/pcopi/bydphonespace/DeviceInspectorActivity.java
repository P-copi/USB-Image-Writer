package com.pcopi.bydphonespace;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class DeviceInspectorActivity extends Activity {
    private static final int REQ_LOCATION = 7301;
    private static final int REQ_MIC = 7302;

    private final int BG=Color.rgb(7,16,27), CARD=Color.rgb(17,31,47), CARD2=Color.rgb(22,39,58);
    private final int TEXT=Color.rgb(242,247,252), MUTED=Color.rgb(157,177,197), CYAN=Color.rgb(77,196,255);
    private final int GREEN=Color.rgb(93,219,168), ORANGE=Color.rgb(255,190,92), RED=Color.rgb(255,112,112);

    private LinearLayout content;
    private TextView gpsTestText;
    private TextView micTestText;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        build();
    }

    @Override protected void onResume(){
        super.onResume();
        if(content!=null) build();
    }

    private void build(){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));root.setBackgroundColor(BG);sv.addView(root);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(tv("ตรวจสอบเครื่อง / จอรถ",23,TEXT,true));TextView sub=tv("Car Compatibility Inspector • V0.4",12.5f,MUTED,false);sub.setPadding(0,dp(2),0,0);names.addView(sub);top.addView(names,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        int ready=0,total=8;
        boolean overlay=Settings.canDrawOverlays(this); if(overlay)ready++;
        boolean maps=hasPackage("com.google.android.apps.maps"); if(maps)ready++;
        boolean gps=hasGpsProvider(); if(gps)ready++;
        boolean mic=hasMicrophone(); if(mic)ready++;
        boolean network=isNetworkConnected(); if(network)ready++;
        boolean storage=getExternalFilesDir(null)!=null; if(storage)ready++;
        boolean apk=Build.VERSION.SDK_INT<26 || getPackageManager().canRequestPackageInstalls(); if(apk)ready++;
        boolean webview=webViewVersion()!=null; if(webview)ready++;

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(18),dp(16),dp(18),dp(16));hero.setBackground(round(CARD2,22));
        hero.addView(tv("DEVICE READINESS",12,CYAN,true));
        TextView score=tv("พร้อม "+ready+" / "+total+" รายการ",24,ready>=7?GREEN:ready>=5?ORANGE:RED,true);score.setPadding(0,dp(6),0,dp(5));hero.addView(score);
        hero.addView(tv("หน้านี้ใช้ตรวจโทรศัพท์ก่อน และใช้ซ้ำบนจอ BYD เพื่อดูความต่างของระบบจริง",13,MUTED,false));
        root.addView(hero,new LinearLayout.LayoutParams(-1,-2));

        content=root;
        addSection(root,"อุปกรณ์","ข้อมูลพื้นฐานของ Android และหน้าจอ");
        addRow(root,"ผู้ผลิต / รุ่น", Build.MANUFACTURER+"  "+Build.MODEL, true, false);
        addRow(root,"Android / API", Build.VERSION.RELEASE+"  • API "+Build.VERSION.SDK_INT, true, false);
        addRow(root,"ABI", join(Build.SUPPORTED_ABIS), true, false);
        DisplayMetrics dm=getResources().getDisplayMetrics();
        String screen=dm.widthPixels+" × "+dm.heightPixels+" px  •  "+Math.round(dm.widthPixels/dm.density)+" × "+Math.round(dm.heightPixels/dm.density)+" dp  • "+String.format(Locale.US,"%.2fx",dm.density);
        addRow(root,"หน้าจอ",screen,true,false);
        addRow(root,"Build",Build.DISPLAY==null?"-":Build.DISPLAY,true,false);

        addSection(root,"นำทาง / Google","ดูสิ่งที่จำเป็นต่อ Maps และ GPS");
        addRow(root,"GPS provider",gps?(isGpsEnabled()?"มี • เปิดอยู่":"มี • ยังปิดอยู่"):"ไม่พบ GPS provider",gps,gps&&!isGpsEnabled());
        addRow(root,"สิทธิ์ตำแหน่ง",hasLocationPermission()?"อนุญาตแล้ว":"ยังไม่ได้อนุญาต",hasLocationPermission(),!hasLocationPermission());
        addRow(root,"Google Maps",maps?"พบ • "+packageVersion("com.google.android.apps.maps"):"ไม่พบ",maps,!maps);
        boolean gms=hasPackage("com.google.android.gms");
        addRow(root,"Google Play Services",gms?"พบ • "+packageVersion("com.google.android.gms"):"ไม่พบ (ระบบหลักของแอปเรายังทำงานได้)",gms,!gms);
        boolean play=hasPackage("com.android.vending");
        addRow(root,"Google Play Store",play?"พบ":"ไม่พบ",play,!play);

        Button gpsTest=primary("◎  ทดสอบ GPS จริง");gpsTest.setOnClickListener(v->testGps());root.addView(gpsTest,buttonLp());
        gpsTestText=tv("ยังไม่ได้ทดสอบตำแหน่งในรอบนี้",12.5f,MUTED,false);gpsTestText.setGravity(Gravity.CENTER);root.addView(gpsTestText);

        addSection(root,"เสียง / แป้นพิมพ์","ตรวจไมค์และการพิมพ์ภาษาไทยบนจอรถ");
        addRow(root,"ไมโครโฟน",mic?"มี hardware microphone":"ไม่พบ microphone feature",mic,!mic);
        addRow(root,"สิทธิ์ไมโครโฟน",hasMicPermission()?"อนุญาตแล้ว":"ยังไม่ได้อนุญาต",hasMicPermission(),!hasMicPermission());
        boolean gboard=hasPackage("com.google.android.inputmethod.latin");
        addRow(root,"Gboard",gboard?"พบ • "+packageVersion("com.google.android.inputmethod.latin"):"ไม่พบ",gboard,!gboard);
        String defaultIme=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        addRow(root,"แป้นพิมพ์ปัจจุบัน",defaultIme==null||defaultIme.isEmpty()?"ไม่พบข้อมูล":defaultIme,defaultIme!=null&&!defaultIme.isEmpty(),false);
        addRow(root,"IME ที่เปิดใช้",enabledImes(),true,false);

        Button micTest=primary("🎤  ทดสอบไมโครโฟนจริง");micTest.setOnClickListener(v->testMic());root.addView(micTest,buttonLp());
        micTestText=tv("ยังไม่ได้ทดสอบระดับเสียงในรอบนี้",12.5f,MUTED,false);micTestText.setGravity(Gravity.CENTER);root.addView(micTestText);
        Button keyboard=dark("⌨  เปิดตั้งค่าแป้นพิมพ์");keyboard.setOnClickListener(v->safeStart(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));root.addView(keyboard,buttonLp());

        addSection(root,"การทำงานร่วมกับระบบ","Overlay, APK, WebView และเครือข่าย");
        addRow(root,"Quick Overlay",overlay?"อนุญาตแล้ว":"ยังไม่ได้อนุญาตแสดงทับแอปอื่น",overlay,!overlay);
        addRow(root,"ติดตั้ง APK",apk?"อนุญาต Unknown apps แล้ว":"ยังไม่ได้อนุญาต Unknown apps",apk,!apk);
        String wv=webViewVersion();addRow(root,"Android System WebView",wv==null?"ไม่พบ provider":wv,wv!=null,wv==null);
        addRow(root,"เครือข่าย",network?networkText():"ยังไม่พบเครือข่ายที่ใช้งาน",network,!network);
        addRow(root,"พื้นที่เก็บของแอป",storage?externalPath():"external app storage ไม่พร้อม",storage,!storage);

        Button overlayBtn=dark("◫  ตั้งค่า Quick Overlay");overlayBtn.setOnClickListener(v->safeStart(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));root.addView(overlayBtn,buttonLp());
        if(Build.VERSION.SDK_INT>=26){Button install=dark("＋  ตั้งค่าสิทธิ์ติดตั้ง APK");install.setOnClickListener(v->safeStart(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))));root.addView(install,buttonLp());}

        addSection(root,"รายงานสำหรับทดสอบบน BYD","เมื่อลองบนจอรถ กดคัดลอกแล้วส่งข้อความนี้กลับมาได้เลย");
        TextView report=tv(buildReport(),12.5f,TEXT,false);report.setPadding(dp(14),dp(13),dp(14),dp(13));report.setBackground(round(Color.rgb(11,24,37),16));report.setTextIsSelectable(true);root.addView(report);
        Button copy=primary("คัดลอกรายงานเครื่อง");copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("BYD Phone Space Device Report",buildReport()));Toast.makeText(this,"คัดลอกรายงานแล้ว",Toast.LENGTH_SHORT).show();});root.addView(copy,buttonLp());
        Button refresh=dark("↻  ตรวจสอบใหม่");refresh.setOnClickListener(v->build());root.addView(refresh,buttonLp());

        setContentView(sv);
    }

    private void addSection(LinearLayout root,String title,String sub){TextView t=tv(title,16,MUTED,true);t.setPadding(dp(2),dp(22),0,dp(2));root.addView(t);TextView s=tv(sub,12,MUTED,false);s.setPadding(dp(2),0,0,dp(9));root.addView(s);}

    private void addRow(LinearLayout root,String name,String value,boolean ok,boolean warn){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(12),dp(15),dp(12));c.setBackground(round(CARD,16));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(8));root.addView(c,cp);
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);TextView n=tv(name,14.5f,TEXT,true);h.addView(n,new LinearLayout.LayoutParams(0,-2,1));TextView status=tv(ok?"● พร้อม":warn?"● ตั้งค่า":"● ข้อมูล",11.5f,ok?GREEN:warn?ORANGE:CYAN,true);h.addView(status);c.addView(h);TextView v=tv(value,12.5f,MUTED,false);v.setPadding(0,dp(4),0,0);v.setTextIsSelectable(true);c.addView(v);
    }

    private void testGps(){
        if(!hasLocationPermission()){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}
        gpsTestText.setText("กำลังอ่านตำแหน่ง...");gpsTestText.setTextColor(ORANGE);
        LocationHelper helper=new LocationHelper(this);Location best=helper.getBestLocation();if(best!=null&&!LocationHelper.isStale(best)){showGpsResult(best);return;}
        helper.refresh(new LocationHelper.Callback(){private boolean done;@Override public void onLocation(Location l){if(done)return;done=true;runOnUiThread(()->showGpsResult(l));}@Override public void onUnavailable(String reason){if(done)return;done=true;runOnUiThread(()->{gpsTestText.setText("GPS ยังไม่พร้อม: "+reason);gpsTestText.setTextColor(RED);});}});
    }

    private void showGpsResult(Location l){if(l==null){gpsTestText.setText("ยังไม่ได้ตำแหน่ง");gpsTestText.setTextColor(RED);return;}String accuracy=l.hasAccuracy()?String.format(Locale.US," ±%.0f m",l.getAccuracy()):"";gpsTestText.setText(String.format(Locale.US,"✓ %.6f, %.6f%s",l.getLatitude(),l.getLongitude(),accuracy));gpsTestText.setTextColor(GREEN);}

    private void testMic(){
        if(!hasMicPermission()){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        if(!hasMicrophone()){micTestText.setText("เครื่องรายงานว่าไม่มีไมโครโฟน");micTestText.setTextColor(RED);return;}
        micTestText.setText("กำลังฟังเสียงประมาณ 1 วินาที...");micTestText.setTextColor(ORANGE);
        new Thread(()->{
            AudioRecord record=null;String result;boolean ok=false;
            try{
                int rate=8000;int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<1)throw new IllegalStateException("buffer ไม่พร้อม");int size=Math.max(min,2048);
                record=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("เปิดไมค์ไม่สำเร็จ");
                short[] buf=new short[size/2];record.startRecording();long end=System.currentTimeMillis()+1200;int peak=0;while(System.currentTimeMillis()<end){int n=record.read(buf,0,buf.length);if(n>0)for(int i=0;i<n;i++)peak=Math.max(peak,Math.abs((int)buf[i]));}
                record.stop();int percent=Math.min(100,Math.round(peak*100f/32767f));result="✓ ไมค์อ่านสัญญาณได้ • Peak "+percent+"%";ok=true;
            }catch(Exception e){result="ทดสอบไมค์ไม่สำเร็จ: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
            finally{try{if(record!=null)record.release();}catch(Exception ignored){}}
            String r=result;boolean good=ok;runOnUiThread(()->{micTestText.setText(r);micTestText.setTextColor(good?GREEN:RED);});
        },"byd-mic-test").start();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_LOCATION){if(hasLocationPermission())testGps();else Toast.makeText(this,"ต้องอนุญาตตำแหน่งก่อนทดสอบ GPS",Toast.LENGTH_LONG).show();}else if(requestCode==REQ_MIC){if(hasMicPermission())testMic();else Toast.makeText(this,"ต้องอนุญาตไมโครโฟนก่อนทดสอบ",Toast.LENGTH_LONG).show();}}

    private boolean hasPackage(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(Exception e){return false;}}
    private String packageVersion(String pkg){try{PackageInfo p=getPackageManager().getPackageInfo(pkg,0);return p.versionName==null?"version ?":p.versionName;}catch(Exception e){return "-";}}
    private boolean hasGpsProvider(){LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(lm==null)return false;try{return lm.getAllProviders()!=null&&lm.getAllProviders().contains(LocationManager.GPS_PROVIDER);}catch(Exception e){return false;}}
    private boolean isGpsEnabled(){LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(lm==null)return false;try{return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception e){return false;}}
    private boolean hasLocationPermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean hasMicrophone(){return getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);}
    private boolean hasMicPermission(){return checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}

    private String enabledImes(){try{InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);List<InputMethodInfo> list=imm.getEnabledInputMethodList();if(list==null||list.isEmpty())return "ไม่พบ";StringBuilder b=new StringBuilder();for(InputMethodInfo i:list){if(b.length()>0)b.append("\n");CharSequence label=i.loadLabel(getPackageManager());b.append(label==null?i.getPackageName():label).append(" • ").append(i.getPackageName());}return b.toString();}catch(Exception e){return "อ่านข้อมูลไม่ได้";}}

    private String webViewVersion(){try{PackageInfo p=WebView.getCurrentWebViewPackage();return p==null?null:p.packageName+" • "+p.versionName;}catch(Exception e){return null;}}
    private boolean isNetworkConnected(){try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null)return false;Network n=cm.getActiveNetwork();if(n==null)return false;NetworkCapabilities c=cm.getNetworkCapabilities(n);return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);}catch(Exception e){return false;}}
    private String networkText(){try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities c=cm.getNetworkCapabilities(cm.getActiveNetwork());if(c==null)return "เชื่อมต่อ";if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi‑Fi • Internet";if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return "Mobile • Internet";if(c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return "Ethernet • Internet";if(c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return "VPN/Tailscale • Internet";return "Internet พร้อม";}catch(Exception e){return "เชื่อมต่อ";}}
    private String externalPath(){File f=getExternalFilesDir(null);return f==null?"ไม่พร้อม":f.getAbsolutePath();}

    private String buildReport(){
        DisplayMetrics dm=getResources().getDisplayMetrics();
        StringBuilder b=new StringBuilder();
        b.append("BYD Phone Space Device Report V0.4\n");
        b.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        b.append("Model: ").append(Build.MODEL).append("\n");
        b.append("Android: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append("\n");
        b.append("Build: ").append(Build.DISPLAY).append("\n");
        b.append("ABI: ").append(join(Build.SUPPORTED_ABIS)).append("\n");
        b.append("Screen: ").append(dm.widthPixels).append("x").append(dm.heightPixels).append(" px / density ").append(String.format(Locale.US,"%.2f",dm.density)).append("\n");
        b.append("GPS provider: ").append(hasGpsProvider()).append(" / enabled: ").append(isGpsEnabled()).append(" / permission: ").append(hasLocationPermission()).append("\n");
        b.append("Microphone: ").append(hasMicrophone()).append(" / permission: ").append(hasMicPermission()).append("\n");
        b.append("Maps: ").append(hasPackage("com.google.android.apps.maps")).append("\n");
        b.append("GMS: ").append(hasPackage("com.google.android.gms")).append("\n");
        b.append("Play Store: ").append(hasPackage("com.android.vending")).append("\n");
        b.append("Gboard: ").append(hasPackage("com.google.android.inputmethod.latin")).append("\n");
        b.append("Default IME: ").append(Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD)).append("\n");
        b.append("Overlay: ").append(Settings.canDrawOverlays(this)).append("\n");
        b.append("Install APK permission: ").append(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls()).append("\n");
        b.append("WebView: ").append(webViewVersion()).append("\n");
        b.append("Network: ").append(isNetworkConnected()).append(" / ").append(networkText()).append("\n");
        b.append("App storage: ").append(externalPath()).append("\n");
        return b.toString();
    }

    private String join(String[] a){if(a==null||a.length==0)return "-";StringBuilder b=new StringBuilder();for(String s:a){if(b.length()>0)b.append(", ");b.append(s);}return b.toString();}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14.5f);b.setTextColor(Color.rgb(4,22,33));b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(CYAN,17));return b;}
    private Button dark(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(TEXT);b.setBackground(round(Color.rgb(13,27,42),17));return b;}
    private LinearLayout.LayoutParams buttonLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(0,dp(8),0,0);return p;}
    private void safeStart(Intent i){try{startActivity(i);}catch(Exception e){Toast.makeText(this,"เปิดการตั้งค่านี้ไม่ได้บนเครื่องนี้",Toast.LENGTH_SHORT).show();}}
}
