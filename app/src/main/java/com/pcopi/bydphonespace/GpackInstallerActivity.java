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
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/**
 * Read-only GPack installer diagnostics plus an EXPLICIT, user-approved open action.
 * Never installs packages silently, never resets GPack and never touches Google accounts.
 */
public final class GpackInstallerActivity extends Activity {
    private static final int REQUEST_APK=7581;
    private static final String GPACK="com.gpack.agent";
    private static final String INSTALLER="com.bratwzi.auto.gpack.activity.AppInstallerActivity";
    private static final String MIME="application/vnd.android.package-archive";
    private final int BG=0xff07101b,CARD=0xff111f2f,TEXT=0xfff2f7fc,MUTED=0xff9db1c5,CYAN=0xff4dc4ff;
    private Uri selected;
    private String selectedName="";
    private TextView reportLabel,fileLabel;
    private Button tryGpack;

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable round(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;
    }
    private TextView text(String s,int size,int color){
        TextView v=new TextView(this);v.setText(s);v.setTextColor(color);
        v.setTextSize(size);return v;
    }
    private Button button(String label,boolean accent){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);
        b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextSize(16);
        b.setTextColor(accent?BG:TEXT);b.setBackground(round(accent?CYAN:CARD));return b;
    }
    private void addButton(LinearLayout root,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.setMargins(0,dp(9),0,0);root.addView(b,p);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19),dp(17),dp(19),dp(30));scroll.addView(root);
        TextView back=text("‹ กลับ",18,TEXT);
        back.setPadding(0,0,0,dp(12));back.setOnClickListener(v->finish());root.addView(back);
        TextView heading=text("GPack Installer Inspector",23,TEXT);
        heading.setTypeface(Typeface.DEFAULT_BOLD);root.addView(heading);
        TextView hint=text("V0.7.0 • ตรวจช่องทางติดตั้ง Companion ภายใน GPack (ไม่ติดตั้งอัตโนมัติ)",13,MUTED);
        hint.setPadding(0,dp(8),0,dp(16));root.addView(hint);

        TextView warning=text("สิ่งที่เราต้องพิสูจน์: GPack เปิดตัวติดตั้ง APK ของตัวเองหรือไม่ "
            +"และ Companion ปรากฏภายใน GPack จริงหรือไม่\n"
            +"แอปนี้จะไม่รีเซ็ต GPack ไม่เข้า Google Account และไม่เรียกตัวติดตั้งหากคุณไม่ได้กด",14,MUTED);
        warning.setPadding(dp(13),dp(12),dp(13),dp(12));warning.setBackground(round(CARD));root.addView(warning);
        reportLabel=text("",13,MUTED);reportLabel.setTextIsSelectable(true);
        reportLabel.setPadding(dp(10),dp(17),dp(10),dp(10));root.addView(reportLabel);
        Button refresh=button("ตรวจสอบ GPack AppInstallerActivity",false);
        refresh.setOnClickListener(v->refresh());addButton(root,refresh);
        Button copy=button("คัดลอกรายงาน",false);
        copy.setOnClickListener(v->{
            ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText("GPack Installer Report",report()));
            Toast.makeText(this,"คัดลอกรายงานแล้ว",Toast.LENGTH_SHORT).show();
        });addButton(root,copy);

        TextView actionHead=text("ทดลองเปิดตัวช่วยนำทาง",18,TEXT);
        actionHead.setTypeface(Typeface.DEFAULT_BOLD);
        actionHead.setPadding(0,dp(21),0,dp(5));root.addView(actionHead);
        TextView explain=text("ขั้นแรกติดตั้งและทดลอง Companion บน Samsung ตามปกติ "
            +"หลังจากนั้นจึงนำไฟล์ Companion APK ไปให้ GPack เลือกติดตั้งบนจอรถ",13,MUTED);
        root.addView(explain);
        Button pick=button("1. เลือกไฟล์ BYD Nav Companion APK",true);
        pick.setOnClickListener(v->pickApk());addButton(root,pick);
        fileLabel=text("ยังไม่ได้เลือกไฟล์ APK",13,MUTED);
        fileLabel.setPadding(dp(9),dp(11),dp(9),dp(9));root.addView(fileLabel);
        tryGpack=button("2. ทดลองเปิด APK ด้วยตัวติดตั้งของ GPack",false);
        tryGpack.setOnClickListener(v->confirmGpack());
        addButton(root,tryGpack);
        Button guide=button("หาก GPack ไม่รับไฟล์: วิธีทดสอบผ่านตัวจัดการไฟล์",false);
        guide.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("วิธีสำรองที่ปลอดภัย")
            .setMessage("1. เปิดตัวจัดการไฟล์บนรถ\n"
                +"2. เลือก BYD Nav Companion.apk\n"
                +"3. ถ้าระบบแสดงตัวเลือก GPack Installer ให้เลือก GPack\n"
                +"4. ตรวจว่าตัวช่วยปรากฏในหน้ารวมแอปภายใน GPack ไม่ใช่เฉพาะใน Android หลัก\n"
                +"5. เปิดตัวช่วย → ดูสถานะ Google Maps → ทดสอบพิกัด\n\n"
                +"ถ้าไม่มีตัวเลือก GPack อย่าฝืนติดตั้งหรือรีเซ็ตเครื่องจำลอง")
            .setPositiveButton("ตกลง",null).show());
        addButton(root,guide);
        setContentView(scroll);
        refresh();updateButton();
    }
    private void updateButton(){
        if(tryGpack!=null)tryGpack.setEnabled(selected!=null&&exportedInstaller());
    }
    private boolean exportedInstaller(){
        try{
            ActivityInfo info=getPackageManager().getActivityInfo(new ComponentName(GPACK,INSTALLER),0);
            return info.exported&&(info.permission==null||info.permission.isEmpty());
        }catch(Exception e){return false;}
    }
    private String report(){
        StringBuilder out=new StringBuilder("BYD Phone Space V0.7.0 • GPack APK Installer\n");
        try{
            android.content.pm.PackageInfo pkg=getPackageManager().getPackageInfo(GPACK,0);
            out.append("GPack visible: yes / version: ").append(pkg.versionName).append('\n');
        }catch(Exception e){out.append("GPack visible: no\n");}
        try{
            ActivityInfo info=getPackageManager().getActivityInfo(new ComponentName(GPACK,INSTALLER),0);
            out.append("AppInstallerActivity found: true\n");
            out.append("Exported: ").append(info.exported).append('\n');
            out.append("Requires permission: ").append(info.permission==null?"none":info.permission).append('\n');
        }catch(Exception e){out.append("AppInstallerActivity: not accessible (").append(e.getClass().getSimpleName()).append(")\n");}
        Intent candidate=new Intent(Intent.ACTION_VIEW);
        candidate.setPackage(GPACK);candidate.setDataAndType(Uri.parse("content://example.invalid/test.apk"),MIME);
        try{
            List<ResolveInfo> receivers=getPackageManager().queryIntentActivities(candidate,0);
            out.append("GPack APK VIEW receivers: ").append(receivers.size()).append('\n');
            for(ResolveInfo r:receivers){
                if(r.activityInfo!=null)out.append(" - ").append(r.activityInfo.name).append('\n');
            }
        }catch(Exception e){out.append("APK VIEW discovery blocked: ").append(e.getClass().getSimpleName()).append('\n');}
        out.append("Companion selected: ").append(selected==null?"no":selectedName).append('\n');
        out.append("Install action: explicit user approval only. No silent install.\n");
        return out.toString();
    }
    private void refresh(){
        if(reportLabel!=null)reportLabel.setText(report());
        updateButton();
    }
    private void pickApk(){
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.setType("*/*");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(picker,REQUEST_APK);}
        catch(Exception e){Toast.makeText(this,"ระบบไฟล์ไม่รองรับการเลือก APK",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_APK||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        String filename="APK ที่เลือก";
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())filename=cursor.getString(0);
        }catch(Exception ignored){}
        if(filename==null||!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")){
            Toast.makeText(this,"โปรดเลือกไฟล์นามสกุล .apk เท่านั้น",Toast.LENGTH_LONG).show();return;
        }
        selected=uri;selectedName=filename;
        fileLabel.setText(filename);
        refresh();
    }
    private void confirmGpack(){
        if(selected==null)return;
        if(!exportedInstaller()){
            new AlertDialog.Builder(this).setTitle("GPack ไม่เปิดช่องทางติดตั้ง")
                .setMessage("AppInstallerActivity ไม่ได้เปิดให้แอปภายนอกเรียกใช้ตามสิทธิ์ที่ตรวจพบ "
                    +"กรุณาลองเปิดไฟล์ APK จากตัวจัดการไฟล์บนรถ แล้วดูว่ามีตัวเลือก GPack Installer หรือไม่")
                .setPositiveButton("ตกลง",null).show();return;
        }
        new AlertDialog.Builder(this)
            .setTitle("ทดลองส่งไฟล์ให้ GPack")
            .setMessage("จะเปิด GPack AppInstallerActivity ด้วยไฟล์ “"+selectedName+"” "
                +"ที่คุณเลือก ระบบอาจขอสิทธิ์หรือแสดงหน้าติดตั้ง โปรดอ่านก่อนยืนยัน\n\n"
                +"ผลลัพธ์ยังไม่ยืนยันว่าแอปจะถูกติดตั้ง *ภายใน* GPack")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("เปิดตัวติดตั้ง",(d,w)->openInstaller()).show();
    }
    private void openInstaller(){
        Intent intent=new Intent(Intent.ACTION_VIEW);
        intent.setComponent(new ComponentName(GPACK,INSTALLER));
        intent.setDataAndType(selected,MIME);
        intent.setClipData(ClipData.newUri(getContentResolver(),"Selected Companion APK",selected));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{
            startActivity(intent);
            Toast.makeText(this,"ส่งคำขอเปิด GPack แล้ว โปรดตรวจผลติดตั้งจริง",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("GPack ไม่รับไฟล์ APK")
                .setMessage("ระบบไม่อนุญาตให้เปิด AppInstallerActivity ด้วยไฟล์นี้: "
                    +e.getClass().getSimpleName()
                    +"\nลองผ่านตัวจัดการไฟล์ตามวิธีสำรอง โดยไม่แก้ไขระบบ GPack")
                .setPositiveButton("ตกลง",null).show();
        }
    }
}
