package com.pcopi.bydphonespace;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class NasConnectActivity extends Activity {
    private final int BG=Color.rgb(7,16,27),CARD=Color.rgb(17,31,47),TEXT=Color.rgb(242,247,252),MUTED=Color.rgb(157,177,197),CYAN=Color.rgb(77,196,255),GREEN=Color.rgb(93,219,168);
    private EditText lan,tail,user,pass;
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;} private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);build();}
    private void build(){
        SharedPreferences p=getSharedPreferences("nas",MODE_PRIVATE);ScrollView sv=new ScrollView(this);sv.setBackgroundColor(BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));sv.addView(root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(tv("NAS Connection",23,TEXT,true));TextView sub=tv("LAN → Tailscale",12.5f,MUTED,false);sub.setPadding(0,dp(2),0,0);names.addView(sub);bar.addView(names,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
        LinearLayout status=new LinearLayout(this);status.setOrientation(LinearLayout.VERTICAL);status.setPadding(dp(18),dp(16),dp(18),dp(16));status.setBackground(round(CARD,22));TextView s=tv("○  ยังไม่ได้เชื่อมต่อ NAS",16,MUTED,true);status.addView(s);TextView sh=tv("V0.1 เตรียมค่าการเชื่อมต่อไว้ก่อน • ขั้นถัดไปจะย้าย SMB Explorer ที่เราเคยทดสอบแล้วเข้ามา",13,MUTED,false);sh.setPadding(0,dp(7),0,0);status.addView(sh);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(18),0,dp(18));root.addView(status,sp);
        lan=field("LAN IP / hostname",p.getString("lan",""),false);tail=field("Tailscale IP / MagicDNS",p.getString("tail",""),false);user=field("Username",p.getString("user",""),false);pass=field("Password (V0.1 ไม่บันทึกรหัสผ่าน)","",true);root.addView(label("LAN หลัก"));root.addView(lan,fp());root.addView(label("Tailscale สำรอง"));root.addView(tail,fp());root.addView(label("บัญชี NAS"));root.addView(user,fp());root.addView(pass,fp());
        Button save=button("บันทึกค่าการเชื่อมต่อ");save.setOnClickListener(v->{p.edit().putString("lan",lan.getText().toString().trim()).putString("tail",tail.getText().toString().trim()).putString("user",user.getText().toString().trim()).apply();s.setText("●  บันทึกค่าแล้ว • รอเปิด SMB ในขั้นถัดไป");s.setTextColor(GREEN);Toast.makeText(this,"บันทึกแล้ว",Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(54));bp.setMargins(0,dp(18),0,0);root.addView(save,bp);
        TextView note=tv("แนวทางจริง: แอปจะลอง LAN ก่อนอัตโนมัติ และถ้าอยู่นอกบ้านจึงสลับไป Tailscale โดยไม่ให้คุณเลือกใหม่ทุกครั้ง",13,MUTED,false);note.setPadding(dp(6),dp(18),dp(6),0);root.addView(note);setContentView(sv);
    }
    private TextView label(String s){TextView v=tv(s,13,MUTED,true);v.setPadding(dp(4),dp(12),0,dp(6));return v;}
    private EditText field(String hint,String value,boolean secret){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(105,130,154));e.setText(value);e.setTextColor(TEXT);e.setTextSize(15);e.setSingleLine(true);e.setPadding(dp(15),0,dp(15),0);e.setBackground(round(Color.rgb(13,27,42),16));if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private LinearLayout.LayoutParams fp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(0,0,0,dp(5));return p;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15.5f);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.rgb(4,22,33));b.setBackground(round(CYAN,18));return b;}
}
