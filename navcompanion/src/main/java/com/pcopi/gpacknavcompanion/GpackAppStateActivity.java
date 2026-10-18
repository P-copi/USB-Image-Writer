package com.pcopi.gpacknavcompanion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * GPack icons and install-state investigation. The host cannot inspect apps
 * installed inside a virtual GPack environment. All GPack observations are
 * entered by the user; host PM results are a separate evidence channel.
 */
public final class GpackAppStateActivity extends Activity {
    private static final String PREF="gpack_icon_survey_013";
    private static final int BG=0xff07101b,CARD=0xff111f2f,WHITE=0xfff2f7fc,
        MUTED=0xff9db1c5,CYAN=0xff4dc4ff;
    private static final String[] BADGES={
        "ยังไม่ตรวจสีสัญลักษณ์",
        "มีวงกลมสีเหลือง",
        "ไม่มีวงกลมสีเหลือง",
        "มองไม่ชัด / ไม่แน่ใจ"
    };
    private static final String[] BEHAVIORS={
        "ยังไม่ได้กดทดสอบ",
        "กดแล้วเปิดแอปใช้งานได้",
        "กดแล้วมีปุ่มดาวน์โหลด/ติดตั้ง",
        "กดแล้วแสดงข้อมูล/ร้านแอป",
        "กดแล้วเกิดข้อผิดพลาด",
        "ไม่ปรากฏไอคอนใน GPack"
    };
    private static final String[][] APPS={
        {"Google Maps","com.google.android.apps.maps"},
        {"Chrome","com.android.chrome"},
        {"SoundCloud","com.soundcloud.android"},
        {"YouTube","com.google.android.youtube"},
        {"Waze","com.waze"},
        {"YouTube Music","com.google.android.apps.youtube.music"},
        {"JOOX","com.tencent.ibg.joox"},
        {"TikTok","com.zhiliaoapp.musically"},
        {"BYD Nav Companion","com.pcopi.gpacknavcompanion"}
    };
    private SharedPreferences prefs;
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;
    }
    private TextView label(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
    }
    private Button button(String s,boolean primary){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(primary?BG:WHITE);b.setBackground(bg(primary?CYAN:CARD));return b;
    }
    private void addButton(LinearLayout box,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));
        p.setMargins(0,dp(5),0,0);box.addView(b,p);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences(PREF,MODE_PRIVATE);
        render();
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(19),dp(18),dp(30));
        scroll.addView(root);
        Button back=button("‹ กลับไป Nav Companion",false);
        back.setOnClickListener(v->finish());addButton(root,back);
        TextView title=label("ตรวจสถานะ GPack แบบละเอียด",22,WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0,dp(20),0,dp(8));root.addView(title);
        TextView note=label("V0.1.3 • เปรียบเทียบวงกลมสีเหลือง + ผลการเปิดไอคอนจริง "
            +"กับรายชื่อแพ็กเกจบน Android หลัก\n\n"
            +"ข้อจำกัด: Android หลักไม่สามารถตรวจว่าแอปติดตั้งภายใน GPack ได้โดยตรง "
            +"ข้อมูลที่คุณเลือกเป็นหลักฐานจากหน้าจอ GPack ไม่ใช่ผลอ่านเครื่องจำลอง\n\n"
            +"ไม่ต้องดาวน์โหลดแอปใหม่เพื่อทดสอบ หากขึ้นหน้าติดตั้งให้ยกเลิกได้",13,MUTED);
        note.setPadding(dp(12),dp(14),dp(12),dp(14));note.setBackground(bg(CARD));root.addView(note);
        Button report=button("ดูรายงานรวมทุกแอป + ผลอ่าน APK",true);
        report.setOnClickListener(v->showReport());addButton(root,report);
        Button clear=button("เริ่มบันทึกสถานะใหม่ (ไม่แตะ GPack)",false);
        clear.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("ล้างเฉพาะผลสำรวจ?")
            .setMessage("จะล้างเฉพาะผลที่คุณบันทึกในหน้านี้ ไม่ถอนแอป ไม่รีเซ็ต GPack")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ล้างผลสำรวจ",(d,w)->{prefs.edit().clear().apply();render();}).show());
        addButton(root,clear);

        for(int i=0;i<APPS.length;i++){
            final int index=i;
            String[] row=APPS[i];
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(12),dp(13),dp(12),dp(13));box.setBackground(bg(CARD));
            LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);
            outer.setMargins(0,dp(12),0,0);root.addView(box,outer);
            TextView heading=label(row[0],17,WHITE);
            heading.setTypeface(Typeface.DEFAULT_BOLD);box.addView(heading);
            TextView host=label("Android หลัก: "+hostStatus(row[1]),12,MUTED);
            host.setPadding(0,dp(5),0,dp(6));box.addView(host);
            int badge=prefs.getInt("badge_"+i,0),behavior=prefs.getInt("behavior_"+i,0);
            Button badgeButton=button("วงกลม: "+BADGES[badge],false);
            badgeButton.setOnClickListener(v->chooseBadge(index));addButton(box,badgeButton);
            Button behaviorButton=button("กดไอคอน: "+BEHAVIORS[behavior],false);
            behaviorButton.setOnClickListener(v->chooseBehavior(index));addButton(box,behaviorButton);
        }
        TextView hint=label("แนะนำให้เริ่มจาก Maps (ไม่มีวงเหลือง), YouTube หรือ Waze (มีวงเหลือง) "
            +"และ Companion ซึ่งเรากำลังตรวจการติดตั้ง แล้วส่งรายงานรวมเพียงครั้งเดียวครับ",13,MUTED);
        hint.setPadding(0,dp(22),0,dp(10));root.addView(hint);
        setContentView(scroll);
    }
    private void chooseBadge(int index){
        int selected=prefs.getInt("badge_"+index,0);
        new AlertDialog.Builder(this).setTitle(APPS[index][0]+" — วงกลมสีเหลือง")
            .setSingleChoiceItems(BADGES,selected,(dialog,chosen)->{
                prefs.edit().putInt("badge_"+index,chosen).apply();
                dialog.dismiss();render();
            }).setNegativeButton("ยกเลิก",null).show();
    }
    private void chooseBehavior(int index){
        int selected=prefs.getInt("behavior_"+index,0);
        new AlertDialog.Builder(this).setTitle(APPS[index][0]+" — ผลที่กดไอคอนจริง")
            .setSingleChoiceItems(BEHAVIORS,selected,(dialog,chosen)->{
                prefs.edit().putInt("behavior_"+index,chosen).apply();
                dialog.dismiss();render();
            }).setNegativeButton("ยกเลิก",null).show();
    }
    private String hostStatus(String pkg){
        try{
            PackageInfo x=getPackageManager().getPackageInfo(pkg,0);
            String version=x.versionName==null?"ไม่ทราบรุ่น":x.versionName;
            return "พบแพ็กเกจ "+pkg+" ("+version+")";
        }catch(Exception e){
            return "ไม่พบแพ็กเกจใน Android หลัก ("+pkg+")";
        }
    }
    private String report(){
        StringBuilder out=new StringBuilder("BYD NAV COMPANION V0.1.3 • GPACK STATUS REPORT\n");
        out.append("Android ").append(Build.VERSION.RELEASE).append(" API ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("หมายเหตุ: วงกลมสีเหลืองเป็นเพียงข้อสังเกต ยังไม่ยืนยันความหมาย\n");
        out.append("GPack state below: User-observed, NOT virtual PM query.\n");
        for(int i=0;i<APPS.length;i++){
            out.append("\n").append(APPS[i][0]).append('\n');
            out.append(" Host: ").append(hostStatus(APPS[i][1])).append('\n');
            out.append(" Icon badge: ").append(BADGES[prefs.getInt("badge_"+i,0)]).append('\n');
            out.append(" Tap result: ").append(BEHAVIORS[prefs.getInt("behavior_"+i,0)]).append('\n');
        }
        try{
            PackageInfo g=getPackageManager().getPackageInfo("com.gpack.agent",0);
            out.append("\nGPack host version: ").append(g.versionName).append('\n');
        }catch(Exception e){out.append("\nGPack host package not visible\n");}
        out.append("\nAPK handoff read test\n").append(ApkHandoffLog.report(this));
        out.append("\nNOT PROVEN: Installing an APK inside GPack. "
            +"A visible catalog icon or APK VIEW receiver does not prove virtual installation.\n");
        return out.toString();
    }
    private void showReport(){
        String report=report();
        TextView txt=label(report,13,WHITE);
        txt.setTextIsSelectable(true);txt.setPadding(dp(8),dp(12),dp(8),dp(12));
        ScrollView sc=new ScrollView(this);sc.addView(txt);
        new AlertDialog.Builder(this).setTitle("รายงาน GPack + Android หลัก")
            .setView(sc)
            .setPositiveButton("ตกลง",null)
            .setNeutralButton("คัดลอกรายงาน",(d,w)->{
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("GPack audit",report));
                Toast.makeText(this,"คัดลอกรายงานแล้ว",Toast.LENGTH_SHORT).show();
            }).show();
    }
}
