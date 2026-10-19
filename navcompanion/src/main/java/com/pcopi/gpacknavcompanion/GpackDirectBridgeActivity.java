package com.pcopi.gpacknavcompanion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Probes action names taken from the *exact* GPack 2.20260321.01 AndroidManifest.
 * No automatic installation, launcher changes, privileged access, or GPack data edits.
 * A successful startActivity() is NOT proof of Google Maps navigation.
 */
public final class GpackDirectBridgeActivity extends Activity {
    private static final String GPACK="com.gpack.agent";
    private static final String LAUNCH_ACTIVITY="com.bratwzi.auto.gpack.activity.LaunchAppActivity";
    private static final String ACTION_MAP="com.intent.ACTION_SUPPORT_MAP_NAVIGATION";
    private static final String ACTION_LAUNCH="com.gpack.launch";
    private static final int BG=0xff07101b,CARD=0xff111f2f,CYAN=0xff4dc4ff,WHITE=0xfff2f7fc,MUTED=0xff9db1c5;
    private EditText destination;
    private TextView result;
    private String activeProbe;
    private boolean launchedProbe=false;
    private static final String RESULTS="direct_bridge_results_016";
    private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(13));return d;}
    private TextView text(String s,int sz,int color){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(sz);v.setTextColor(color);return v;
    }
    private Button button(String title,boolean accent){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(accent?BG:WHITE);
        b.setBackground(bg(accent?CYAN:CARD));return b;
    }
    private void addButton(LinearLayout root,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(53));
        p.setMargins(0,dp(9),0,0);root.addView(b,p);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(17),dp(20),dp(32));scroll.addView(root);
        Button back=button("‹ กลับ",false);back.setOnClickListener(v->finish());addButton(root,back);
        TextView title=text("GPack Direct Maps Bridge",22,WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);title.setPadding(0,dp(18),0,dp(8));root.addView(title);
        TextView note=text(
            "V0.1.6 • แก้ปัญหา Android หา Activity ไม่พบจากรอบก่อน\n"
            +"Manifest ของ GPack มี Action ทั้งสอง แต่ไม่ประกาศรับ geo: URI จึงต้องทดลองโดยไม่แนบ data ก่อน\n"
            +"ผลตรวจจากรถ: LaunchAppActivity exported=true และไม่ต้องขอ permission\n"
            +"ทดสอบขณะรถจอดเท่านั้น • ยังไม่ยืนยันคำสั่งนำทางอัตโนมัติ",13,MUTED);
        note.setPadding(dp(10),dp(10),dp(10),dp(10));note.setBackground(bg(CARD));root.addView(note);
        TextView label=text("จุดหมายสำหรับทดสอบ",15,WHITE);
        label.setPadding(0,dp(18),0,dp(6));root.addView(label);
        destination=new EditText(this);destination.setSingleLine(false);destination.setMinLines(2);
        destination.setText("อนุสรณ์ดอนเจดีย์ สุพรรณบุรี");
        destination.setHint("ชื่อสถานที่หรือ lat,lng");destination.setHintTextColor(MUTED);
        destination.setTextColor(WHITE);destination.setTextSize(17);
        destination.setBackground(bg(CARD));destination.setPadding(dp(12),dp(12),dp(12),dp(12));
        root.addView(destination,new LinearLayout.LayoutParams(-1,dp(93)));

        Button report=button("1. ตรวจสิทธิ์และช่องรับคำสั่งของ GPack",false);
        report.setOnClickListener(v->showReport());addButton(root,report);
        Button testMap=button("2. เปิดด้วย ACTION_SUPPORT_MAP_NAVIGATION (ไม่แนบ URI)",true);
        testMap.setOnClickListener(v->tryAction(ACTION_MAP,0));addButton(root,testMap);
        Button testLaunch=button("3. เปิดด้วย com.gpack.launch (ไม่แนบ URI)",true);
        testLaunch.setOnClickListener(v->tryAction(ACTION_LAUNCH,0));addButton(root,testLaunch);
        Button testExtras=button("4. ส่งจุดหมายเป็นข้อความ Extra (ไม่แนบ URI)",false);
        testExtras.setOnClickListener(v->tryAction(ACTION_MAP,1));addButton(root,testExtras);
        Button testExplicit=button("5. เรียก LaunchAppActivity ตรง + จุดหมาย",false);
        testExplicit.setOnClickListener(v->tryAction(ACTION_MAP,2));addButton(root,testExplicit);
        Button fallback=button("6. ทางสำรอง: คัดลอกจุดหมาย + เปิด GPack",false);
        fallback.setOnClickListener(v->openFallback());addButton(root,fallback);
        Button review=button("7. ดู/คัดลอกรายงานผลทดสอบ",false);
        review.setOnClickListener(v->showReport());addButton(root,review);
        result=text("ยังไม่ได้ทดลอง",13,MUTED);
        result.setTextIsSelectable(true);result.setPadding(dp(6),dp(20),dp(6),dp(10));root.addView(result);
        setContentView(scroll);
        refresh();
    }

    private String destination(){return destination.getText().toString().trim();}
    private String capability(){
        StringBuilder out=new StringBuilder("GPack 2.20260321.01 direct bridge / Companion V0.1.6\n");
        PackageManager pm=getPackageManager();
        try{
            ActivityInfo a=pm.getActivityInfo(new ComponentName(GPACK,LAUNCH_ACTIVITY),0);
            out.append("LaunchAppActivity installed/exported: ").append(a.exported)
                .append(" / permission: ").append(a.permission==null?"none":a.permission).append('\n');
        }catch(Exception e){out.append("LaunchAppActivity not accessible: ").append(e.getClass().getSimpleName()).append('\n');}
        for(String action:new String[]{ACTION_MAP,ACTION_LAUNCH}){
            Intent plain=new Intent(action).addCategory(Intent.CATEGORY_DEFAULT).setPackage(GPACK);
            Intent withGeo=new Intent(action).addCategory(Intent.CATEGORY_DEFAULT).setPackage(GPACK)
                .setData(Uri.parse("geo:0,0?q=example"));
            try{
                List<ResolveInfo> valid=pm.queryIntentActivities(plain,0);
                List<ResolveInfo> geo=pm.queryIntentActivities(withGeo,0);
                out.append(action).append(" • NO data matches: ").append(valid.size())
                    .append(" • WITH geo: matches: ").append(geo.size()).append('\n');
                for(ResolveInfo r:valid)if(r.activityInfo!=null)
                    out.append("  ").append(r.activityInfo.packageName).append("/")
                        .append(r.activityInfo.name).append(" exported=").append(r.activityInfo.exported).append('\n');
            }catch(Exception e){out.append(action).append(" inspect error: ").append(e.getClass().getSimpleName()).append('\n');}
        }
        out.append("Expected: GPack action filters do not declare geo URI data.\n");
        return out.toString();
    }
    private String methodKey(String action,int variant){
        return action+"#"+variant;
    }
    private void tryAction(String action,int variant){
        String q=destination();
        if(q.isEmpty()){destination.setError("กรุณาระบุจุดหมาย");return;}
        String key=methodKey(action,variant);
        boolean accessible=false;
        try{
            ActivityInfo info=getPackageManager().getActivityInfo(
                new ComponentName(GPACK,LAUNCH_ACTIVITY),0);
            accessible=info.exported
                &&(info.permission==null||info.permission.isEmpty());
        }catch(Exception ignored){}
        if(!accessible){
            record(key,"GPack LaunchAppActivity not exported/visible or needs permission");
            new AlertDialog.Builder(this).setTitle("ไม่สามารถเรียกจากแอปภายนอก")
                .setMessage("Android ไม่อนุญาตให้เรียก LaunchAppActivity ในเครื่องนี้ "
                    +"จึงไม่พยายามข้ามสิทธิ์")
                .setPositiveButton("ตกลง",null).show();
            return;
        }

        // The v0.1.5 failure came from adding a geo: URI although the
        // GPack action filters did not declare a matching data scheme.
        // Extras do NOT take part in Android intent-filter resolution.
        Intent intent=new Intent(action).addCategory(Intent.CATEGORY_DEFAULT);
        if(variant==2)intent.setComponent(new ComponentName(GPACK,LAUNCH_ACTIVITY));
        else intent.setPackage(GPACK);
        // Keep URI/data unset for ALL probes.
        if(variant!=0){
            intent.putExtra(Intent.EXTRA_TEXT,q);
            intent.putExtra("packageName","com.google.android.apps.maps");
        }
        boolean matches;
        try{
            matches=intent.resolveActivity(getPackageManager())!=null;
        }catch(Exception e){matches=false;}
        final boolean resolved=matches;
        new AlertDialog.Builder(this).setTitle("ทดสอบ GPack โดยไม่แนบ geo URI")
            .setMessage("Action: "+action+"\nวิธี: "+(
                variant==0?"Action อย่างเดียว ไม่มีจุดหมายแนบ":
                variant==1?"Action + ชื่อจุดหมายใน Extra (รูปแบบยังไม่ยืนยัน)":
                "ระบุ LaunchAppActivity ตรง + Extra")+" \n"
                +"Android resolveActivity: "+resolved+"\n\n"
                +"หากเปิดสำเร็จ ต้องดูอีกครั้งว่าเป็น Google Maps และจุดหมายตรงจริงหรือไม่")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ทดลองเปิด",(d,w)->{
                if(!resolved&&variant!=2){
                    record(key,"Android did not resolve a receiver for action without data");
                    new AlertDialog.Builder(this).setTitle("ไม่มีตัวรับสำหรับคำสั่งนี้")
                        .setMessage("ระบบตรวจไม่พบตัวรับ Action แบบไม่มี URI")
                        .setPositiveButton("ตกลง",null).show();
                    return;
                }
                try{
                    activeProbe=key;
                    launchedProbe=false;
                    startActivity(intent);
                    launchedProbe=true;
                    record(key,"startActivity succeeded • NOT yet verified inside GPack or Maps");
                }catch(Exception e){
                    activeProbe=null;
                    launchedProbe=false;
                    record(key,"Rejected: "+e.getClass().getSimpleName()
                        +(e.getMessage()==null?"":" / "+e.getMessage()));
                    new AlertDialog.Builder(this).setTitle("Android ปฏิเสธคำสั่ง")
                        .setMessage(e.getClass().getSimpleName()+"\n"
                            +(e.getMessage()==null?"":e.getMessage()))
                        .setPositiveButton("ตกลง",null).show();
                }
            }).show();
    }

    private void openFallback(){
        String q=destination();if(q.isEmpty()){destination.setError("กรุณาใส่จุดหมาย");return;}
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("จุดหมาย BYD",q));
        try{
            Intent opener=getPackageManager().getLaunchIntentForPackage(GPACK);
            if(opener==null){
                opener=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(GPACK,"com.bratwzi.auto.gpack.activity.SplashActivity");
            }
            startActivity(opener);record("fallback","Opened GPack + copied destination; manual paste required");
        }catch(Exception e){record("fallback","Cannot open GPack: "+e.getClass().getSimpleName());}
    }
    private void record(String method,String text){
        getSharedPreferences(RESULTS,MODE_PRIVATE)
            .edit().putString(method,text).apply();refresh();
    }
    private void refresh(){
        if(result!=null)result.setText(fullReport());
    }
    private String fullReport(){
        StringBuilder b=new StringBuilder(capability());
        for(String a:new String[]{methodKey(ACTION_MAP,0),methodKey(ACTION_LAUNCH,0),
                methodKey(ACTION_MAP,1),methodKey(ACTION_MAP,2),"fallback"}){
            b.append("\n").append(a).append(": ")
                .append(getSharedPreferences(RESULTS,MODE_PRIVATE)
                    .getString(a,"not tested")).append('\n');
        }
        b.append("\nPrevious v0.1.5 geo: failure: ")
            .append(getSharedPreferences("direct_bridge_results_015",MODE_PRIVATE)
                .getString(ACTION_MAP,"not recorded")).append('\n');
        return b.toString();
    }
    private void showReport(){
        String report=fullReport();
        new AlertDialog.Builder(this).setTitle("GPack Direct Maps Bridge Report")
            .setMessage(report).setPositiveButton("ตกลง",null)
            .setNeutralButton("คัดลอกผล",(d,w)->{
                ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("GPack direct bridge",report));
            }).show();
    }
    @Override protected void onResume(){
        super.onResume();
        if(activeProbe==null||!launchedProbe)return;
        final String probe=activeProbe;activeProbe=null;launchedProbe=false;
        new AlertDialog.Builder(this).setTitle("ผลที่คุณเห็นจริงหลังทดลอง")
            .setMessage("คำสั่ง "+probe+" เปิด Google Maps ภายใน GPack พร้อมจุดหมายที่ถูกต้องหรือไม่?")
            .setPositiveButton("ใช่ • จุดหมายตรง",(d,w)->record(probe,"User verified Google Maps + matching destination"))
            .setNeutralButton("เปิด GPack/Maps แต่จุดหมายไม่ตรง",(d,w)->record(probe,"Opened GPack/Maps but destination not matched"))
            .setNegativeButton("ไม่เปิด Maps",(d,w)->record(probe,"Did not open Google Maps")).show();
    }
}
