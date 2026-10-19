package com.pcopi.bydphonespace;

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
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single-install navigation integration test against the real GPack 2.20260321.01
 * AndroidManifest, NOT guesswork using unrelated virtual-app shortcut internals.
 * Launch tests never update the default routing until user confirms Maps AND target.
 */
public final class GpackNativeNavigationActivity extends Activity {
    private static final int BG=0xff07101b,CARD=0xff111f2f,FG=0xfff2f7fc,
        MUTED=0xff9db1c5,CYAN=0xff4dc4ff,GREEN=0xff5ddba8;
    private static final String PKG="com.gpack.agent";
    private static final String COMPONENT="com.bratwzi.auto.gpack.activity.LaunchAppActivity";
    private static final String ACTION_MAP="com.intent.ACTION_SUPPORT_MAP_NAVIGATION";
    private static final String ACTION_LAUNCH="com.gpack.launch";
    private static final String PREF="gpack_native_nav_result_071";
    private static final String[] MODES={
        NavigationBridge.GPACK_NATIVE_NAV,
        NavigationBridge.GPACK_NATIVE_GEO,
        NavigationBridge.GPACK_NATIVE_LAUNCH
    };
    private EditText destinationInput;
    private TextView reportView;
    private String lastLaunchedMode;
    private String destination="อนุสรณ์ดอนเจดีย์ สุพรรณบุรี";

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable bg(int color){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(17));return g;
    }
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);return t;
    }
    private Button button(String s,boolean main){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);
        b.setTextColor(main?BG:FG);b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(bg(main?CYAN:CARD));return b;
    }
    private void addButton(LinearLayout box,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));
        p.setMargins(0,dp(8),0,0);box.addView(b,p);
    }
    private LinearLayout panel(LinearLayout parent){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(bg(CARD));box.setPadding(dp(15),dp(14),dp(15),dp(15));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(10),0,0);parent.addView(box,p);return box;
    }

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(state!=null){
            lastLaunchedMode=state.getString("lastLaunchedMode");
            destination=state.getString("destination",destination);
        }
        draw();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        if(destinationInput!=null)destination=destinationInput.getText().toString();
        out.putString("destination",destination);
        out.putString("lastLaunchedMode",lastLaunchedMode);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume(){
        super.onResume();
        // The GPack destination may stay open; always also expose a manual result button.
        if(lastLaunchedMode!=null){
            final String mode=lastLaunchedMode;
            lastLaunchedMode=null;
            getWindow().getDecorView().postDelayed(()->askOutcome(mode),280);
        }
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(16),dp(18),dp(30));scroll.addView(root);
        Button back=button("‹ กลับหน้าหลัก",false);back.setOnClickListener(v->finish());addButton(root,back);
        TextView title=text("GPack Native Maps Bridge",22,FG);
        title.setTypeface(Typeface.DEFAULT_BOLD);title.setPadding(0,dp(17),0,dp(6));root.addView(title);
        TextView subtitle=text("V0.7.1 • ตรวจจาก GPack จริงรุ่น 2.20260321.01",13,GREEN);root.addView(subtitle);
        LinearLayout proof=panel(root);
        TextView discovered=text("พบคำสั่งใน AndroidManifest ของ GPack:\n"
            +"• "+ACTION_MAP+"\n"
            +"• "+ACTION_LAUNCH+"\n"
            +"ตัวรับ: "+COMPONENT+"\n"
            +"สถานะ: ส่งคำสั่งจาก Android ภายนอกได้ตาม Manifest\n"
            +"ยังไม่ทราบรูปแบบพิกัดที่ GPack ใช้จริง ต้องทดลองการเปิดปลายทาง",13,FG);
        discovered.setTextIsSelectable(true);proof.addView(discovered);

        TextView label=text("ชื่อสถานที่หรือพิกัด GPS ที่ต้องการทดสอบ",15,FG);
        label.setPadding(0,dp(19),0,dp(8));root.addView(label);
        destinationInput=new EditText(this);destinationInput.setText(destination);
        destinationInput.setHint("ชื่อสถานที่ หรือ latitude,longitude");
        destinationInput.setSingleLine(false);destinationInput.setMinLines(2);
        destinationInput.setTextColor(FG);destinationInput.setHintTextColor(MUTED);
        destinationInput.setBackground(bg(CARD));
        destinationInput.setPadding(dp(13),dp(12),dp(13),dp(12));
        root.addView(destinationInput,new LinearLayout.LayoutParams(-1,dp(88)));

        for(int i=0;i<MODES.length;i++){
            final String method=MODES[i];
            LinearLayout box=panel(root);
            box.addView(text((i+1)+". "+NavigationBridge.label(method),16,FG));
            String old=getPreferences(MODE_PRIVATE).getString(method,"ยังไม่ทดสอบบนรถ");
            TextView desc=text(i==0?
                "ใช้ ACTION_SUPPORT_MAP_NAVIGATION + google.navigation:q=<จุดหมาย>":
                i==1?"ใช้ ACTION_SUPPORT_MAP_NAVIGATION + geo:0,0?q=<จุดหมาย>":
                "ใช้ com.gpack.launch ระบุ Google Maps และจุดหมาย",
                12,MUTED);
            desc.setPadding(0,dp(6),0,0);box.addView(desc);
            TextView result=text("ผลเดิม: "+old,12,GREEN);result.setPadding(0,dp(7),0,0);box.addView(result);
            Button trial=button("ทดสอบคำสั่งนี้ (จอดรถ)",true);
            trial.setOnClickListener(v->test(method));addButton(box,trial);
            Button outcome=button("บันทึกผลหลังกลับจาก GPack",false);
            outcome.setOnClickListener(v->askOutcome(method));addButton(box,outcome);
        }
        LinearLayout fallback=panel(root);
        fallback.addView(text("วิธีสำรองที่ผ่านแล้ว",17,FG));
        fallback.addView(text("คัดลอกจุดหมาย → เปิด GPack → เปิด Maps → แตะค้นหาและวาง",
            12,MUTED));
        Button safe=button("คัดลอกจุดหมาย + เปิด GPack",false);
        safe.setOnClickListener(v->{
            String q=readQuery();if(q!=null)NavigationBridge.launchUsing(this,NavigationBridge.GPACK_CLIP,q);
        });addButton(fallback,safe);

        TextView head=text("รายงานผลและตัวรับคำสั่ง",17,FG);
        head.setTypeface(Typeface.DEFAULT_BOLD);head.setPadding(0,dp(20),0,dp(7));root.addView(head);
        reportView=text(report(),12,MUTED);reportView.setBackground(bg(CARD));
        reportView.setTextIsSelectable(true);
        reportView.setPadding(dp(13),dp(13),dp(13),dp(13));root.addView(reportView);
        Button copy=button("คัดลอกรายงานผลทั้งชุด",true);
        copy.setOnClickListener(v->{
            ClipboardManager manager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(manager!=null)manager.setPrimaryClip(ClipData.newPlainText("GPack Native Navigation report",report()));
            Toast.makeText(this,"คัดลอกรายงานแล้ว",Toast.LENGTH_SHORT).show();
        });addButton(root,copy);
        setContentView(scroll);
    }
    private String readQuery(){
        if(destinationInput==null)return null;
        String q=destinationInput.getText().toString().trim();
        if(q.isEmpty()){destinationInput.setError("กรุณาระบุจุดหมาย");return null;}
        if(q.length()>500){destinationInput.setError("จุดหมายยาวเกินไป");return null;}
        destination=q;return q;
    }
    private boolean exported(){
        try{
            ActivityInfo info=getPackageManager().getActivityInfo(new ComponentName(PKG,COMPONENT),0);
            return info.exported&&(info.permission==null||info.permission.isEmpty());
        }catch(Exception e){return false;}
    }
    private void test(String method){
        String q=readQuery();if(q==null)return;
        if(!exported()){
            new AlertDialog.Builder(this).setTitle("ไม่พบช่อง GPack ที่จะทดลอง")
                .setMessage("LaunchAppActivity ไม่พบหรือไม่ได้เปิดให้แอปภายนอกเรียก "
                    +"ให้ติดตั้ง/ทดสอบรุ่นนี้บนจอ BYD ที่มี GPack จริง")
                .setPositiveButton("ตกลง",null).show();return;
        }
        Intent intent=NavigationBridge.intentFor(method,q);
        if(intent==null)return;
        new AlertDialog.Builder(this)
            .setTitle("ทดสอบเปิด Google Maps ผ่าน GPack")
            .setMessage(NavigationBridge.label(method)+"\n\nจุดหมาย: "+q
                +"\n\nขั้นนี้ทดลองเฉพาะคำสั่งเปิดนำทางที่พบใน Manifest ของ GPack"
                +" ไม่แก้บัญชี Google ไม่ติดตั้งแอป และไม่รีเซ็ตระบบ\n\n"
                +"เมื่อเปิดได้ ให้ตรวจว่าตำแหน่งจริงตรงกับจุดหมายก่อนบันทึกผล")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ทดลอง",(d,w)->{
                try{
                    lastLaunchedMode=method;
                    startActivity(intent);
                }catch(Exception e){
                    lastLaunchedMode=null;
                    record(method,"Android ปฏิเสธคำสั่ง: "+e.getClass().getSimpleName());
                    new AlertDialog.Builder(this).setTitle("เปิดคำสั่งไม่ได้")
                        .setMessage("ไม่สามารถเรียก GPack ผ่านวิธีนี้\n"+e.getClass().getSimpleName()
                            +(e.getMessage()==null?"":"\n"+e.getMessage()))
                        .setPositiveButton("ตกลง",(dia,k)->draw()).show();
                }
            }).show();
    }
    private void record(String method,String result){
        getPreferences(MODE_PRIVATE).edit().putString(method,result).apply();
    }
    private void askOutcome(String method){
        new AlertDialog.Builder(this)
            .setTitle("ผลจริงของ "+NavigationBridge.label(method))
            .setMessage("อย่าเลือกผ่านหากเพียงแค่เปิด GPack หรือ Maps โดยไม่มีจุดหมาย")
            .setItems(new String[]{
                "ผ่าน: เปิด Google Maps พร้อมจุดหมายตรง",
                "เปิด Google Maps แต่ยังไม่ถึงจุดหมาย",
                "เปิด GPack หน้าแรก ต้องกด Maps เอง",
                "ไม่ได้เปิด หรือเกิดข้อผิดพลาด",
                "ยังไม่ได้ตรวจจุดหมาย"
            },(d,index)->{
                String[] results={"เปิด Google Maps พร้อมจุดหมายตรงจริง",
                    "เปิด Maps แต่ไม่มีจุดหมาย","เปิดแค่ GPack","เปิดไม่ได้/ข้อผิดพลาด","ยังไม่ยืนยันจุดหมาย"};
                record(method,results[index]);
                if(index==0){
                    new AlertDialog.Builder(this).setTitle("ตั้งวิธีนี้เป็นตัวนำทางหลัก?")
                        .setMessage("ยืนยันแล้วว่า Maps ภายใน GPack เปิดพร้อมจุดหมายถูกต้อง"
                            +" เมื่อตั้งเป็นหลัก ปุ่มนำทางจากรูปจะใช้วิธีนี้\n"
                            +"วิธีคัดลอกจุดหมายเดิมยังเลือกกลับมาใช้ได้")
                        .setNegativeButton("ยังไม่ตั้ง",(dia,w)->draw())
                        .setPositiveButton("บันทึกเป็นหลัก",(dia,w)->{
                            NavigationBridge.saveConfirmed(this,method);draw();
                        }).show();
                }else draw();
            })
            .setNegativeButton("ข้าม",null).show();
    }
    private String report(){
        StringBuilder r=new StringBuilder("BYD Phone Space GPack Native Navigation V0.7.1\n");
        r.append("APK evidence: GPack 2.20260321.01 AndroidManifest\n");
        r.append("GPack visible: ").append(NavigationBridge.hasGpack(this)).append('\n');
        r.append("Exported LaunchAppActivity: ").append(exported()).append('\n');
        for(String action:new String[]{ACTION_MAP,ACTION_LAUNCH}){
            Intent test=new Intent(action).setPackage(PKG);
            List<ResolveInfo> handlers;
            try{handlers=getPackageManager().queryIntentActivities(test,0);}
            catch(Exception e){handlers=java.util.Collections.emptyList();}
            r.append(action).append(" candidate receivers: ").append(handlers.size()).append('\n');
            for(ResolveInfo info:handlers){
                if(info.activityInfo!=null)r.append("  ").append(info.activityInfo.packageName)
                    .append("/").append(info.activityInfo.name).append('\n');
            }
        }
        r.append("Selected routing: ").append(NavigationBridge.label(NavigationBridge.selected(this))).append('\n');
        for(String method:MODES)
            r.append(NavigationBridge.label(method)).append(" → ")
                .append(getPreferences(MODE_PRIVATE).getString(method,"not tested")).append('\n');
        r.append("Note: Android accepted startActivity != actual Maps navigation. User must verify.\n");
        return r.toString();
    }
}
