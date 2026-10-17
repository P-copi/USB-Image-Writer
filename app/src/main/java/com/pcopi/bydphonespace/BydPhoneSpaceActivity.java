package com.pcopi.bydphonespace;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class BydPhoneSpaceActivity extends Activity {
    private static final int REQ_OVERLAY = 5101;
    private boolean startOverlayWhenAllowed = false;
    private boolean openMapsWhenAllowed = false;

    private final int BG = Color.rgb(7, 16, 27);
    private final int CARD = Color.rgb(17, 31, 47);
    private final int CARD2 = Color.rgb(22, 39, 58);
    private final int TEXT = Color.rgb(242, 247, 252);
    private final int MUTED = Color.rgb(157, 177, 197);
    private final int CYAN = Color.rgb(77, 196, 255);
    private final int GREEN = Color.rgb(93, 219, 168);

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable rounded(int c, float r) { GradientDrawable d = new GradientDrawable(); d.setColor(c); d.setCornerRadius(dp(r)); return d; }
    private TextView tv(String s, float z, int c, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(c); v.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL)); return v; }

    private TextView chip(String text, boolean on) {
        TextView v = tv((on ? "●  " : "○  ") + text, 12.5f, on ? GREEN : MUTED, true);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(12), dp(8), dp(12), dp(8)); v.setBackground(rounded(Color.rgb(13,27,42), 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2,-2); p.setMargins(0,0,dp(8),0); v.setLayoutParams(p); return v;
    }

    private LinearLayout card(String icon, String title, String sub, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(18),dp(15),dp(18),dp(15)); c.setBackground(rounded(CARD,22)); c.setClickable(true); c.setOnClickListener(click);
        TextView i=tv(icon,27,TEXT,false); c.addView(i,new LinearLayout.LayoutParams(-1,dp(40)));
        c.addView(tv(title,17.5f,TEXT,true)); TextView s=tv(sub,12.5f,MUTED,false); s.setPadding(0,dp(5),0,0); c.addView(s); return c;
    }

    private void row(LinearLayout root, LinearLayout a, LinearLayout b) {
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(134),1); p1.setMargins(0,0,dp(6),dp(12)); r.addView(a,p1);
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(134),1); p2.setMargins(dp(6),0,0,dp(12)); r.addView(b,p2); root.addView(r);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); buildHome();
    }

    private void buildHome() {
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(18),dp(20),dp(28)); root.setBackgroundColor(BG); sv.addView(root);

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); LinearLayout names=new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL);
        names.addView(tv("BYD PHONE SPACE",25,TEXT,true)); TextView sub=tv("Navigation • NAS • Quick Access",13.5f,MUTED,false); sub.setPadding(0,dp(4),0,0); names.addView(sub); top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        TextView ver=tv("V0.1.1",12,CYAN,true); ver.setPadding(dp(12),dp(8),dp(12),dp(8)); ver.setGravity(Gravity.CENTER); ver.setBackground(rounded(Color.rgb(11,43,66),18)); top.addView(ver); root.addView(top);

        LinearLayout status=new LinearLayout(this); status.setPadding(0,dp(16),0,dp(18)); status.addView(chip("พร้อมใช้งาน",true)); status.addView(chip("NAS",false)); status.addView(chip("Overlay",Settings.canDrawOverlays(this))); root.addView(status);

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(17),dp(18),dp(17)); hero.setBackground(rounded(CARD2,24));
        hero.addView(tv("QUICK OVERLAY",12,CYAN,true)); TextView ht=tv("เรียกเครื่องมือของเราโดยไม่ปิดการนำทาง",19,TEXT,true); ht.setPadding(0,dp(7),0,dp(5)); hero.addView(ht); hero.addView(tv("กด Google Maps ครั้งเดียว • Overlay จะเปิดให้อัตโนมัติ",13.5f,MUTED,false));
        Button ob=new Button(this); ob.setText(Settings.canDrawOverlays(this)?"เปิด Quick Overlay":"อนุญาต Quick Overlay"); ob.setAllCaps(false); ob.setTextSize(15.5f); ob.setTextColor(Color.rgb(4,22,33)); ob.setTypeface(Typeface.DEFAULT_BOLD); ob.setBackground(rounded(CYAN,18)); LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(54)); op.setMargins(0,dp(15),0,0); hero.addView(ob,op); ob.setOnClickListener(v->ensureOverlay(false)); root.addView(hero);

        TextView sec=tv("เมนูหลัก",16,MUTED,true); sec.setPadding(dp(2),dp(24),0,dp(12)); root.addView(sec);
        row(root, card("⌖","Google Maps","เปิด Maps + Quick Overlay",v->ensureOverlay(true)), card("▣","รูปภาพนำทาง","ล่าสุด • NAS • Offline",v->startActivity(new Intent(this,RouteImagesActivity.class))));
        row(root, card("★","เส้นทางโปรด","จุดหมายที่ใช้บ่อย",v->toast("เส้นทางโปรดจะเปิดใช้ในขั้นถัดไป")), card("▤","NAS Explorer","LAN / Tailscale",v->startActivity(new Intent(this,NasConnectActivity.class))));
        row(root, card("↻","ซิงก์จาก NAS","โหลดเฉพาะรายการใหม่",v->toast("V0.1.1 ตรวจ UI/Overlay ก่อน • ขั้นถัดไปต่อ SMB Sync จริง")), card("＋","แอป / APK","ติดตั้งและอัปเดต",v->pickApk()));

        TextView foot=tv("ทดสอบบน Samsung Galaxy S22 Ultra ก่อน แล้วจึงปรับสเกลสำหรับ BYD Sealion 7",12.5f,Color.rgb(112,135,158),false); foot.setGravity(Gravity.CENTER); foot.setPadding(dp(10),dp(10),dp(10),0); root.addView(foot);
        setContentView(sv);
    }

    private void openMapsOnly() {
        Intent app=getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
        if(app!=null){ startActivity(app); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))); }
        catch(Exception e){ toast("ยังไม่พบแอปแผนที่ในเครื่อง"); }
    }

    private void pickApk(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/vnd.android.package-archive"); try{startActivity(i);}catch(Exception e){toast("ยังไม่พบตัวเลือกไฟล์ APK");} }

    private void ensureOverlay(boolean openMapsAfter) {
        openMapsWhenAllowed = openMapsAfter;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)){
            startOverlayWhenAllowed=true;
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),REQ_OVERLAY);
            return;
        }
        startOverlay();
        if(openMapsAfter) new Handler(Looper.getMainLooper()).postDelayed(this::openMapsOnly, 300);
    }

    private void startOverlay(){
        Intent i=new Intent(this,QuickOverlayService.class);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
    }

    @Override protected void onResume(){
        super.onResume();
        if(startOverlayWhenAllowed && Settings.canDrawOverlays(this)){
            startOverlayWhenAllowed=false;
            startOverlay();
            if(openMapsWhenAllowed) new Handler(Looper.getMainLooper()).postDelayed(this::openMapsOnly, 300);
        }
    }

    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
