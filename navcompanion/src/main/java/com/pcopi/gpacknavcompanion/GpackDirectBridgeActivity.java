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
            "V0.1.5 • คำสั่งข้างล่างพบใน AndroidManifest ของ GPack 2.20260321.01 จริง\n"
            +"อย่างไรก็ตาม Manifest ของไฟล์ที่สำรองระบุ exported=false ใน LaunchAppActivity "
            +"จึงอาจไม่อนุญาตให้ Companion เรียกจาก Android หลัก\n"
            +"ทดสอบเมื่อรถจอดเท่านั้น และบันทึกเฉพาะผลที่เห็นบนจอจริง",13,MUTED);
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
        Button testMap=button("2. ทดลอง ACTION_SUPPORT_MAP_NAVIGATION",true);
        testMap.setOnClickListener(v->tryAction(ACTION_MAP));addButton(root,testMap);
        Button testLaunch=button("3. ทดลอง com.gpack.launch",false);
        testLaunch.setOnClickListener(v->tryAction(ACTION_LAUNCH));addButton(root,testLaunch);
        Button fallback=button("4. ทางสำรอง: คัดลอกจุดหมาย + เปิด GPack",false);
        fallback.setOnClickListener(v->openFallback());addButton(root,fallback);
        Button review=button("5. ดู/คัดลอกรายงานผลทดสอบ",false);
        review.setOnClickListener(v->showReport());addButton(root,review);
        result=text("ยังไม่ได้ทดลอง",13,MUTED);
        result.setTextIsSelectable(true);result.setPadding(dp(6),dp(20),dp(6),dp(10));root.addView(result);
        setContentView(scroll);
        refresh();
    }

    private String destination(){return destination.getText().toString().trim();}
    private String capability(){
        StringBuilder out=new StringBuilder("GPack 2.20260321.01 direct bridge / Companion V0.1.5\n");
        PackageManager pm=getPackageManager();
        try{
            ActivityInfo a=pm.getActivityInfo(new ComponentName(GPACK,LAUNCH_ACTIVITY),0);
            out.append("LaunchAppActivity installed/exported: ").append(a.exported)
                .append(" / permission: ").append(a.permission==null?"none":a.permission).append('\n');
        }catch(Exception e){out.append("LaunchAppActivity not accessible: ").append(e.getClass().getSimpleName()).append('\n');}
        for(String action:new String[]{ACTION_MAP,ACTION_LAUNCH}){
            Intent query=new Intent(action).addCategory(Intent.CATEGORY_DEFAULT).setPackage(GPACK);
            try{
                List<ResolveInfo> handlers=pm.queryIntentActivities(query,0);
                out.append(action).append(" handlers: ").append(handlers.size()).append('\n');
                for(ResolveInfo r:handlers)if(r.activityInfo!=null)
                    out.append("  ").append(r.activityInfo.packageName).append("/")
                        .append(r.activityInfo.name).append(" exported=").append(r.activityInfo.exported).append('\n');
            }catch(Exception e){out.append(action).append(" inspect error: ").append(e.getClass().getSimpleName()).append('\n');}
        }
        out.append("Important: exported=false blocks calls from a different Android UID.\n");
        return out.toString();
    }
    private void tryAction(String action){
        String q=destination();
        if(q.isEmpty()){destination.setError("กรุณาเลือกจุดหมาย");return;}
        boolean accessible=false;
        try{
            ActivityInfo info=getPackageManager().getActivityInfo(new ComponentName(GPACK,LAUNCH_ACTIVITY),0);
            accessible=info.exported;
        }catch(Exception ignored){}
        if(!accessible){
            record(action,"Android host: LaunchAppActivity not exported or not visible — did not attempt launch");
            new AlertDialog.Builder(this).setTitle("GPack ปิดช่องทางนี้ไว้")
                .setMessage("Manifest และระบบที่อ่านได้ไม่เปิด LaunchAppActivity ให้แอปอื่นเรียก "
                    +"จึงไม่พยายามข้ามสิทธิ์ Android\n"
                    +"ใช้วิธีสำรองระหว่างที่หาช่องทางรองรับจริง")
                .setPositiveButton("ตกลง",null).show();
            return;
        }
        // Action names are confirmed by the supplied APK. The optional extras
        // are compatibility probes, NOT a confirmed or documented GPack API.
        Intent intent=new Intent(action).addCategory(Intent.CATEGORY_DEFAULT).setPackage(GPACK);
        intent.setData(Uri.parse("geo:0,0?q="+Uri.encode(q)));
        intent.putExtra(Intent.EXTRA_TEXT,q);
        intent.putExtra("destination",q);
        intent.putExtra("query",q);
        intent.putExtra("packageName","com.google.android.apps.maps");
        intent.putExtra("package_name","com.google.android.apps.maps");
        intent.putExtra("pkgName","com.google.android.apps.maps");
        new AlertDialog.Builder(this).setTitle("ทดลองช่องทาง GPack ที่พบจาก APK")
            .setMessage(action+"\n\n"
                +"ชื่อ action ยืนยันจาก Manifest แล้ว แต่รูปแบบ extra ที่ GPack รองรับยังไม่ทราบ\n"
                +"ถ้าเปิดได้ ให้ตรวจว่า Maps และพิกัดตรงจริงหรือไม่")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ทดลองเปิด",(d,w)->{
                try{
                    startActivity(intent);
                    activeProbe=action;
                    record(action,"Android accepted startActivity; Maps/destination not yet verified");
                }catch(Exception e){
                    activeProbe=null;
                    record(action,"Rejected: "+e.getClass().getSimpleName()
                        +(e.getMessage()==null?"":" / "+e.getMessage()));
                    new AlertDialog.Builder(this).setTitle("Android ปฏิเสธ")
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
        getSharedPreferences("direct_bridge_results_015",MODE_PRIVATE)
            .edit().putString(method,text).apply();refresh();
    }
    private void refresh(){
        if(result!=null)result.setText(fullReport());
    }
    private String fullReport(){
        StringBuilder b=new StringBuilder(capability());
        for(String a:new String[]{ACTION_MAP,ACTION_LAUNCH,"fallback"}){
            b.append("\n").append(a).append(": ")
                .append(getSharedPreferences("direct_bridge_results_015",MODE_PRIVATE)
                    .getString(a,"not tested")).append('\n');
        }
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
        if(activeProbe==null)return;
        final String probe=activeProbe;activeProbe=null;
        new AlertDialog.Builder(this).setTitle("ผลที่คุณเห็นจริงหลังทดลอง")
            .setMessage("คำสั่ง "+probe+" เปิด Google Maps ภายใน GPack พร้อมจุดหมายที่ถูกต้องหรือไม่?")
            .setPositiveButton("ใช่ • จุดหมายตรง",(d,w)->record(probe,"User verified Google Maps + matching destination"))
            .setNeutralButton("เปิด GPack/Maps แต่จุดหมายไม่ตรง",(d,w)->record(probe,"Opened GPack/Maps but destination not matched"))
            .setNegativeButton("ไม่เปิด Maps",(d,w)->record(probe,"Did not open Google Maps")).show();
    }
}
