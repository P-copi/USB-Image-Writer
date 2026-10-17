package com.pcopi.bydphonespace;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class RouteImagesActivity extends Activity {
    private final int BG=Color.rgb(7,16,27), CARD=Color.rgb(17,31,47), TEXT=Color.rgb(242,247,252), MUTED=Color.rgb(157,177,197), CYAN=Color.rgb(77,196,255);
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;} private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);build();}
    private void build(){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));sv.addView(root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(tv("รูปภาพนำทาง",23,TEXT,true));TextView sub=tv("Navigation Images",12.5f,MUTED,false);sub.setPadding(0,dp(2),0,0);names.addView(sub);bar.addView(names,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(0,dp(18),0,dp(16));tabs.addView(tab("ล่าสุด",true),new LinearLayout.LayoutParams(0,dp(44),1));tabs.addView(tab("จาก NAS",false),new LinearLayout.LayoutParams(0,dp(44),1));tabs.addView(tab("Offline",false),new LinearLayout.LayoutParams(0,dp(44),1));root.addView(tabs);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(18),dp(18),dp(18),dp(18));hero.setBackground(round(CARD,22));hero.addView(tv("▣",34,CYAN,false));TextView h=tv("พร้อมรับรูปจาก NAS",19,TEXT,true);h.setPadding(0,dp(8),0,dp(5));hero.addView(h);hero.addView(tv("โครงหน้า V0.1 พร้อมแล้ว • ขั้นถัดไปจะผูกโฟลเดอร์ Navigation บน NAS และดึงเฉพาะไฟล์ใหม่มาเก็บ Offline",13.5f,MUTED,false));Button nas=button("ตั้งค่า NAS");nas.setOnClickListener(v->startActivity(new Intent(this,NasConnectActivity.class)));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(54));np.setMargins(0,dp(16),0,0);hero.addView(nas,np);root.addView(hero);
        TextView recent=tv("ล่าสุด",16,MUTED,true);recent.setPadding(dp(2),dp(24),0,dp(10));root.addView(recent);root.addView(empty("ยังไม่มีรูปที่ซิงก์","เมื่อโทรศัพท์ส่งรูปเข้า NAS รูปใหม่จะขึ้นตรงนี้ก่อน และเลือกเก็บ Offline ได้"));
        TextView plan=tv("รูปแบบที่เตรียมไว้",16,MUTED,true);plan.setPadding(dp(2),dp(24),0,dp(10));root.addView(plan);root.addView(info("01","ภาพเต็มจอ","แตะดูเต็มจอ • ซูม • ปัดซ้ายขวา"));root.addView(info("02","เปิดพิกัด","ผูกรูปกับพิกัด แล้วส่งต่อให้ Google Maps"));root.addView(info("03","Offline Cache","เก็บรูปสำคัญไว้บนรถ แม้ NAS หรือเน็ตไม่พร้อม"));setContentView(sv);
    }
    private TextView tab(String s,boolean a){TextView v=tv(s,14,a?Color.rgb(4,22,33):MUTED,true);v.setGravity(Gravity.CENTER);v.setBackground(round(a?CYAN:Color.rgb(13,27,42),18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),0,dp(3),0);v.setLayoutParams(p);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15.5f);b.setTextColor(Color.rgb(4,22,33));b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(CYAN,18));return b;}
    private LinearLayout empty(String t,String s){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(22),dp(30),dp(22),dp(30));c.setBackground(round(Color.rgb(12,25,39),22));TextView a=tv(t,17,TEXT,true);a.setGravity(Gravity.CENTER);c.addView(a);TextView b=tv(s,13,MUTED,false);b.setGravity(Gravity.CENTER);b.setPadding(dp(8),dp(8),dp(8),0);c.addView(b);return c;}
    private LinearLayout info(String n,String t,String s){LinearLayout c=new LinearLayout(this);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(round(CARD,18));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(10));c.setLayoutParams(cp);TextView no=tv(n,13,CYAN,true);no.setGravity(Gravity.CENTER);no.setBackground(round(Color.rgb(11,43,66),16));c.addView(no,new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),0,0,0);x.addView(tv(t,16,TEXT,true));TextView st=tv(s,12.5f,MUTED,false);st.setPadding(0,dp(4),0,0);x.addView(st);c.addView(x,new LinearLayout.LayoutParams(0,-2,1));return c;}
}
