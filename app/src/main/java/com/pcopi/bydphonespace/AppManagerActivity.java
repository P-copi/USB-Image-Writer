package com.pcopi.bydphonespace;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AppManagerActivity extends Activity {
    private static final int REQ_APK = 4401;
    private static final int REQ_UNKNOWN = 4402;
    private final int BG=Color.rgb(7,16,27),CARD=Color.rgb(17,31,47),TEXT=Color.rgb(242,247,252),MUTED=Color.rgb(157,177,197),CYAN=Color.rgb(77,196,255),GREEN=Color.rgb(93,219,168),ORANGE=Color.rgb(255,184,77);
    private LinearLayout content;
    private Uri pendingApk;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} 
    private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);build();}
    @Override protected void onResume(){super.onResume();if(content!=null)refresh();}

    private void build(){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));root.setBackgroundColor(BG);sv.addView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(tv("แอป / APK / Gboard",23,TEXT,true));TextView sub=tv("ตรวจสถานะ • เปิด • ตั้งค่า • ติดตั้ง",12.5f,MUTED,false);sub.setPadding(0,dp(2),0,0);names.addView(sub);top.addView(names,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        TextView note=tv("หน้านี้แยกจากระบบรูปและเส้นทาง หาก Google หรือ Gboard ใช้ไม่ได้บนจอรถ ส่วนรูปนำทางยังทำงานได้ตามปกติ",13,MUTED,false);note.setPadding(dp(14),dp(14),dp(14),dp(14));note.setBackground(round(Color.rgb(12,25,39),18));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.setMargins(0,dp(14),0,dp(14));root.addView(note,np);

        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);

        Button install=primary("＋  เลือก APK จากเครื่อง / Flash Drive");install.setOnClickListener(v->pickApk());LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(56));ip.setMargins(0,dp(8),0,dp(10));root.addView(install,ip);
        Button permission=dark("ตั้งค่าสิทธิ์ติดตั้งแอปจากแหล่งนี้");permission.setOnClickListener(v->openInstallPermission());root.addView(permission,new LinearLayout.LayoutParams(-1,dp(50)));
        Button refresh=dark("↻  รีเฟรชสถานะทั้งหมด");refresh.setOnClickListener(v->refresh());LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(50));rp.setMargins(0,dp(8),0,0);root.addView(refresh,rp);

        setContentView(sv);refresh();
    }

    private void refresh(){
        content.removeAllViews();
        content.addView(appCard("Google Maps","com.google.android.apps.maps",true,()->launchPackage("com.google.android.apps.maps"),"เปิด Maps"));
        content.addView(appCard("Gboard","com.google.android.inputmethod.latin",true,()->launchPackage("com.google.android.inputmethod.latin"),"เปิด Gboard"));
        content.addView(appCard("Google Play Services","com.google.android.gms",false,null,null));
        content.addView(appCard("Google Services Framework","com.google.android.gsf",false,null,null));
        content.addView(appCard("Google Play Store","com.android.vending",true,()->launchPackage("com.android.vending"),"เปิด Play Store"));

        boolean can=canInstallUnknown();
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(CARD,19));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(10));c.setLayoutParams(cp);
        c.addView(tv("ติดตั้ง APK",17,TEXT,true));TextView st=tv((can?"● พร้อม":"○ ยังไม่อนุญาต")+" • REQUEST_INSTALL_PACKAGES",13,can?GREEN:ORANGE,true);st.setPadding(0,dp(5),0,0);c.addView(st);content.addView(c);

        LinearLayout kb=new LinearLayout(this);kb.setOrientation(LinearLayout.VERTICAL);kb.setPadding(dp(16),dp(14),dp(16),dp(14));kb.setBackground(round(CARD,19));LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(-1,-2);kp.setMargins(0,0,0,dp(10));kb.setLayoutParams(kp);
        kb.addView(tv("แป้นพิมพ์ / เสียง",17,TEXT,true));TextView ks=tv("เปิดหน้าตั้งค่าแป้นพิมพ์เพื่อเลือก Gboard และตรวจไมโครโฟนจากหน้า Inspector ได้",13,MUTED,false);ks.setPadding(0,dp(5),0,dp(10));kb.addView(ks);Button k=dark("ตั้งค่าแป้นพิมพ์");k.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));kb.addView(k,new LinearLayout.LayoutParams(-1,dp(48)));content.addView(kb);
    }

    private LinearLayout appCard(String title,String pkg,boolean actionable,Runnable action,String actionText){
        boolean installed=isInstalled(pkg);String version=installed?versionOf(pkg):"-";
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(CARD,19));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(10));c.setLayoutParams(cp);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(tv(title,17,TEXT,true));TextView p=tv(pkg,11.5f,MUTED,false);p.setPadding(0,dp(3),0,0);t.addView(p);head.addView(t,new LinearLayout.LayoutParams(0,-2,1));TextView state=tv(installed?"● มี":"○ ไม่มี",13,installed?GREEN:ORANGE,true);head.addView(state);c.addView(head);
        TextView v=tv(installed?("เวอร์ชัน "+version):"ยังไม่พบแพ็กเกจนี้ในเครื่อง",12.5f,MUTED,false);v.setPadding(0,dp(7),0,0);c.addView(v);
        if(installed&&actionable&&action!=null){Button b=dark(actionText);b.setOnClickListener(x->action.run());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(46));bp.setMargins(0,dp(10),0,0);c.addView(b,bp);}return c;
    }

    private boolean isInstalled(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(Exception e){return false;}}
    private String versionOf(String pkg){try{PackageInfo p=getPackageManager().getPackageInfo(pkg,0);String v=p.versionName==null?"?":p.versionName;long code=Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;return v+" ("+code+")";}catch(Exception e){return "?";}}
    private void launchPackage(String pkg){Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null)startActivity(i);else Toast.makeText(this,"แอปนี้ไม่มีหน้าสำหรับเปิดโดยตรง",Toast.LENGTH_SHORT).show();}

    private void pickApk(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/vnd.android.package-archive");startActivityForResult(i,REQ_APK);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_APK&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){pendingApk=data.getData();try{getContentResolver().takePersistableUriPermission(pendingApk,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}String name=fileName(pendingApk);new android.app.AlertDialog.Builder(this).setTitle("ติดตั้ง APK นี้?").setMessage(name+"\n\nระบบ Android จะเปิดหน้าติดตั้งให้ตรวจสอบและยืนยันอีกครั้ง").setNegativeButton("ยกเลิก",null).setPositiveButton("ติดตั้ง",(d,w)->installPending()).show();}else if(requestCode==REQ_UNKNOWN&&pendingApk!=null&&canInstallUnknown()){installPending();}}

    private void installPending(){if(pendingApk==null)return;if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){openInstallPermission();return;}try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(pendingApk,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception e){Toast.makeText(this,"เปิดตัวติดตั้ง APK ไม่สำเร็จ: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private boolean canInstallUnknown(){return Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls();}
    private void openInstallPermission(){if(Build.VERSION.SDK_INT>=26){try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),REQ_UNKNOWN);}catch(Exception e){startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));}}else startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));}
    private String fileName(Uri u){String name="ไฟล์ APK";try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)name=c.getString(i);}}catch(Exception ignored){}return name==null?"ไฟล์ APK":name;}

    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.rgb(4,22,33));b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(CYAN,17));return b;}
    private Button dark(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(TEXT);b.setBackground(round(Color.rgb(13,27,42),17));return b;}
}
