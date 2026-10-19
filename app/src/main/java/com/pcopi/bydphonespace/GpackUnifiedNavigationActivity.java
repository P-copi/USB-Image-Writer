package com.pcopi.bydphonespace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
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

/**
 * One installed app, multiple focused GPack navigation experiments.
 * Nothing changes default routing until the user confirms both Maps AND destination.
 */
public final class GpackUnifiedNavigationActivity extends Activity {
    private static final int BG=0xff07101b, CARD=0xff111f2f, WHITE=0xfff2f7fc,
        MUTED=0xff9db1c5, CYAN=0xff4dc4ff, GREEN=0xff5ddba8;
    private static final String PREF="gpack_unified_results_072";
    // Prioritise ways that can actually carry a destination.
    private static final int[] ORDER={2,4,6,3,5,8,7,9,0,1};
    private EditText destinationInput;
    private TextView reportView;
    private String destination="อนุสรณ์ดอนเจดีย์ สุพรรณบุรี";
    private int pending=-1;
    private boolean exitedForTest=false;
    private boolean allShown=false;

    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;
    }
    private TextView text(String s,int z,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(color);return t;
    }
    private Button button(String s,boolean accent){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(accent?BG:WHITE);
        b.setBackground(bg(accent?CYAN:CARD));return b;
    }
    private void addButton(LinearLayout parent,Button b){
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(54));
        params.setMargins(0,dp(8),0,0);parent.addView(b,params);
    }
    private LinearLayout panel(LinearLayout parent){
        LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(15),dp(14),dp(15),dp(15));p.setBackground(bg(CARD));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(0,dp(11),0,0);parent.addView(p,params);return p;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(state!=null){
            destination=state.getString("destination",destination);
            pending=state.getInt("pending",-1);
            exitedForTest=state.getBoolean("exitedForTest",false);
            allShown=state.getBoolean("allShown",false);
        }
        draw();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        if(destinationInput!=null)destination=destinationInput.getText().toString();
        out.putString("destination",destination);out.putInt("pending",pending);
        out.putBoolean("exitedForTest",exitedForTest);out.putBoolean("allShown",allShown);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause(){
        if(pending>=0)exitedForTest=true;
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        if(pending>=0&&exitedForTest){
            int index=pending;pending=-1;exitedForTest=false;
            getWindow().getDecorView().postDelayed(()->askResult(index),200);
        }
    }
    private String resultKey(int index){return "result_"+index;}
    private String result(int index){return getSharedPreferences(PREF,MODE_PRIVATE).getString(resultKey(index),"ยังไม่ทดสอบ");}
    private void record(int index,String value){
        getSharedPreferences(PREF,MODE_PRIVATE).edit().putString(resultKey(index),value).apply();
        if(reportView!=null)reportView.setText(fullReport());
    }
    private int nextIndex(){
        for(int n:ORDER)if("ยังไม่ทดสอบ".equals(result(n)))return n;
        return -1;
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19),dp(17),dp(19),dp(30));scroll.addView(root);
        Button back=button("‹ กลับหน้าหลัก",false);back.setOnClickListener(v->finish());addButton(root,back);
        TextView heading=text("GPack • ตรวจครั้งเดียว",23,WHITE);heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0,dp(15),0,dp(5));root.addView(heading);
        root.addView(text("BYD Phone Space V0.7.2 — รวมผล APK GPack จริง + Companion V0.1.6",13,GREEN));
        LinearLayout intro=panel(root);
        intro.addView(text("ตรวจจาก GPack 2.20260321.01: พบ Action สำหรับการนำทางและเปิดแอป "
            +"แต่ยังไม่ทราบรูปแบบ Extra/URI ที่อ่านจริง\n"
            +"แยกการทดสอบโดยไม่แนบ URI ออกจากการระบุ Activity ตรง\n"
            +"ทดสอบขณะรถจอดเท่านั้น • ห้ามนับแค่เปิด GPack ว่านำทางสำเร็จ\n"
            +"ไม่ติดตั้ง/แก้ GPack และไม่แตะบัญชี Google",13,WHITE));

        TextView label=text("สถานที่หรือพิกัด GPS สำหรับทดลอง",15,WHITE);
        label.setPadding(0,dp(19),0,dp(7));root.addView(label);
        destinationInput=new EditText(this);destinationInput.setText(destination);
        destinationInput.setTextColor(WHITE);destinationInput.setHintTextColor(MUTED);
        destinationInput.setHint("15.12345,100.12345 หรือชื่อสถานที่");
        destinationInput.setSingleLine(false);destinationInput.setMinLines(2);
        destinationInput.setBackground(bg(CARD));destinationInput.setPadding(dp(13),dp(10),dp(13),dp(10));
        root.addView(destinationInput,new LinearLayout.LayoutParams(-1,dp(88)));

        Button next=button(nextIndex()<0?"ทดสอบครบทุกวิธีแล้ว":"ทดสอบวิธีที่ควรลองถัดไป",true);
        next.setEnabled(nextIndex()>=0);
        next.setOnClickListener(v->{int i=nextIndex();if(i>=0)runProbe(i);});
        addButton(root,next);
        TextView nextLabel=text(nextIndex()<0?"ครบทุกวิธีแล้ว":
            "ถัดไป: "+GpackUnifiedRoutes.title(nextIndex())+" — "+GpackUnifiedRoutes.explain(nextIndex()),13,MUTED);
        nextLabel.setPadding(0,dp(7),0,0);root.addView(nextLabel);
        Button backup=button("ทางสำรองที่ผ่านแล้ว: คัดลอกจุดหมาย + เปิด GPack",false);
        backup.setOnClickListener(v->{
            String q=query();if(q!=null)NavigationBridge.launchUsing(this,NavigationBridge.GPACK_CLIP,q);
        });addButton(root,backup);

        LinearLayout group=panel(root);
        group.addView(text("หลักฐานก่อนทดสอบ",18,WHITE));
        group.addView(text(capabilities(),12,MUTED));
        Button all=button(allShown?"ซ่อนรายละเอียดวิธีทดลอง":"แสดงวิธีทดลองทั้งหมดและผลแยก",false);
        all.setOnClickListener(v->{if(destinationInput!=null)destination=destinationInput.getText().toString();
            allShown=!allShown;draw();});addButton(root,all);
        if(allShown){
            for(int i=0;i<GpackUnifiedRoutes.COUNT;i++){
                final int index=i;LinearLayout item=panel(root);
                item.addView(text(GpackUnifiedRoutes.title(i),15,WHITE));
                item.addView(text(GpackUnifiedRoutes.explain(i)+"\nผล: "+result(i),12,MUTED));
                Button execute=button("ทดสอบวิธี "+(i+1),false);
                execute.setOnClickListener(v->runProbe(index));addButton(item,execute);
                Button assess=button("บันทึกผลจริงของวิธีนี้",false);
                assess.setOnClickListener(v->askResult(index));addButton(item,assess);
            }
        }
        TextView reportTitle=text("รายงานผลรวม",18,WHITE);
        reportTitle.setPadding(0,dp(20),0,dp(7));root.addView(reportTitle);
        reportView=text(fullReport(),12,MUTED);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(dp(13),dp(12),dp(13),dp(12));reportView.setBackground(bg(CARD));
        root.addView(reportView);
        Button copy=button("คัดลอกรายงานทั้งหมด",true);
        copy.setOnClickListener(v->{
            android.content.ClipboardManager cm=(android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
            if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("GPack real APK integration result",fullReport()));
            Toast.makeText(this,"คัดลอกรายงานผลแล้ว",Toast.LENGTH_SHORT).show();
        });addButton(root,copy);
        setContentView(scroll);
    }
    private String query(){
        String q=destinationInput.getText().toString().trim();
        if(q.isEmpty()){destinationInput.setError("กรุณากรอกจุดหมาย");return null;}
        if(q.length()>480){destinationInput.setError("จุดหมายยาวเกินไป");return null;}
        destination=q;return q;
    }
    private boolean exported(){
        try{
            ActivityInfo a=getPackageManager().getActivityInfo(
                new ComponentName(GpackUnifiedRoutes.GPACK,GpackUnifiedRoutes.LAUNCH_ACTIVITY),0);
            return a.exported&&(a.permission==null||a.permission.isEmpty());
        }catch(Exception e){return false;}
    }
    private int matches(String action){
        try{
            Intent i=new Intent(action).addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(GpackUnifiedRoutes.GPACK);
            return getPackageManager().queryIntentActivities(i,0).size();
        }catch(Exception e){return -1;}
    }
    private String capabilities(){
        StringBuilder b=new StringBuilder();
        try{
            PackageInfo p=getPackageManager().getPackageInfo(GpackUnifiedRoutes.GPACK,0);
            b.append("GPack visible: YES version ").append(p.versionName).append('\n');
        }catch(Exception e){b.append("GPack visible: NO\n");}
        b.append("LaunchAppActivity externally callable: ").append(exported()).append('\n');
        b.append("NAV action no-data handlers: ").append(matches(GpackUnifiedRoutes.NAV_ACTION)).append('\n');
        b.append("GPack launch action no-data handlers: ").append(matches(GpackUnifiedRoutes.LAUNCH_ACTION)).append('\n');
        b.append("0 handlers may also reflect manifest/Android visibility restrictions; explicit test separate.\n");
        return b.toString();
    }
    private boolean explicit(int i){return i==4||i==5||i>=6;}
    private void runProbe(int i){
        String q=query();if(q==null)return;
        if(!exported()){
            new AlertDialog.Builder(this).setTitle("GPack ไม่เปิดให้เรียกจาก Android หลัก")
                .setMessage("ยังไม่พบ LaunchAppActivity ที่ exported และไม่ใช้สิทธิ์พิเศษ "
                    +"โปรดส่งรายงานก่อนทดลองต่อ")
                .setPositiveButton("ตกลง",null).show();return;
        }
        int matched=matches(i==1||i==3||i==5||i==8?
            GpackUnifiedRoutes.LAUNCH_ACTION:GpackUnifiedRoutes.NAV_ACTION);
        if(!explicit(i)&&matched==0){
            record(i,"ไม่พบตัวรับ Action แบบไม่มี URI");
            new AlertDialog.Builder(this).setTitle("ไม่มีตัวรับ Action แบบ Implicit")
                .setMessage("Android ไม่พบ Activity ที่จับคู่ Action นี้ได้ จึงไม่ส่งคำสั่ง\n"
                    +"ให้ลองวิธีระบุ Activity ตรง ซึ่งแยกไว้ในชุดเดียวกัน")
                .setPositiveButton("ตกลง",(d,w)->draw()).show();return;
        }
        Intent intent=GpackUnifiedRoutes.build(i,q);
        new AlertDialog.Builder(this).setTitle("ทดลอง "+GpackUnifiedRoutes.title(i))
            .setMessage(GpackUnifiedRoutes.explain(i)+"\n\nจุดหมายที่ใส่: "+q
                +(GpackUnifiedRoutes.carriesDestination(i)?"":"\nวิธีนี้ไม่ได้ส่งจุดหมาย")
                +"\n\nGPack ต้องเปิด Maps พร้อมจุดหมายตรงจึงถือว่าผ่าน")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เปิดทดสอบ",(d,w)->{
                try{
                    pending=i;exitedForTest=false;
                    startActivity(intent);
                    record(i,"Android รับคำสั่งเปิด Activity — รอยืนยันผลบนจอ");
                }catch(Exception e){
                    pending=-1;exitedForTest=false;
                    record(i,"Android เปิดไม่ได้: "+e.getClass().getSimpleName()
                        +(e.getMessage()==null?"":" / "+e.getMessage()));
                    new AlertDialog.Builder(this).setTitle("ไม่สามารถเปิด GPack ได้")
                        .setMessage(e.getClass().getSimpleName()+"\n"
                            +(e.getMessage()==null?"":e.getMessage()))
                        .setPositiveButton("ตกลง",(dia,w2)->draw()).show();
                }
            }).show();
    }
    private void askResult(int i){
        String[] result={
            "Google Maps เปิดพร้อมจุดหมายตรง",
            "Google Maps เปิด แต่ไม่มีหรือผิดจุดหมาย",
            "เปิด GPack แต่ไม่เข้า Maps",
            "ไม่เปิด หรือเกิดข้อผิดพลาด",
            "ยังไม่ทราบผล"
        };
        new AlertDialog.Builder(this).setTitle("ผลจากจอจริง • วิธี "+(i+1))
            .setMessage("เปิดถึงจุดหมายใน Google Maps ภายใน GPack จริงหรือไม่?\n"
                +(!GpackUnifiedRoutes.carriesDestination(i)?"วิธีนี้ไม่ได้ส่งจุดหมาย จึงไม่สามารถยืนยันเป็นทางหลัก":""))
            .setItems(result,(d,index)->{
                if(index==0&&!GpackUnifiedRoutes.carriesDestination(i)){
                    record(i,"เปิดได้ แต่คำสั่งนี้ไม่มีจุดหมาย; ไม่ใช้เป็นทางหลัก");draw();return;
                }
                if(index==4){record(i,"ยังไม่ทราบผล");draw();return;}
                record(i,result[index]);
                if(index==0){
                    new AlertDialog.Builder(this).setTitle("ยืนยันเป็นวิธีนำทางหลัก?")
                        .setMessage("คุณยืนยันว่า Google Maps ภายใน GPack เปิดพร้อมจุดหมายตรงจริง\n"
                            +"เราจะใช้วิธีนี้เมื่อกดนำทางจากรูป/เส้นทาง และยังคงทางสำรองเดิมไว้")
                        .setNegativeButton("เก็บผลไว้ก่อน",(d2,w)->draw())
                        .setPositiveButton("บันทึกวิธีที่ผ่าน",(d2,w)->{
                            NavigationBridge.saveConfirmed(this,GpackUnifiedRoutes.id(i));
                            draw();
                        }).show();
                }else draw();
            }).setNegativeButton("ไว้ก่อน",null).show();
    }
    private String fullReport(){
        StringBuilder b=new StringBuilder("BYD Phone Space V0.7.2 • GPack 2.20260321.01 static manifest probes\n");
        b.append("Tested destination: ").append(destination).append('\n');
        b.append(capabilities());
        for(int index:ORDER)b.append(GpackUnifiedRoutes.title(index)).append(" = ")
            .append(result(index)).append('\n');
        b.append("Confirmed default: ").append(NavigationBridge.label(NavigationBridge.selected(this))).append('\n');
        b.append("StartActivity success does NOT prove actual maps navigation.\n");
        return b.toString();
    }
}
