package com.pcopi.gpacknavcompanion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.ParcelFileDescriptor;
import java.util.List;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Standalone private prototype. No network permission, no NAS/Gmail access. */
public final class MainActivity extends Activity {
    private final int BG=Color.rgb(7,16,27),SURFACE=Color.rgb(17,31,47),WHITE=0xfff2f7fc,
        MUTED=0xff9db1c5,CYAN=0xff4dc4ff;
    private EditText destination;
    private TextView status,installerStatus;
    private static final String GPACK="com.gpack.agent";
    private static final String GPACK_INSTALLER="com.bratwzi.auto.gpack.activity.AppInstallerActivity";
    private static final String APK_MIME="application/vnd.android.package-archive";

    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private GradientDrawable background(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView label(String text,int size,int color){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);
        t.setTextColor(color);t.setTypeface(Typeface.create("sans",Typeface.NORMAL));return t;
    }
    private Button button(String title,boolean accent){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);
        b.setTextSize(17);b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(background(accent?CYAN:SURFACE,14));
        b.setTextColor(accent?0xff07101b:WHITE);
        return b;
    }
    private void addButton(LinearLayout root,Button btn){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(57));
        p.setMargins(0,dp(10),0,0);root.addView(btn,p);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(20),dp(22),dp(30));sc.addView(root);
        TextView heading=label("BYD NAV COMPANION",23,WHITE);
        heading.setTypeface(Typeface.DEFAULT_BOLD);root.addView(heading);
        TextView sub=label("GPack internal Google Maps compatibility test • V0.1.3",13,MUTED);
        sub.setPadding(0,dp(6),0,dp(15));root.addView(sub);
        destination=new EditText(this);destination.setSingleLine(false);destination.setMinLines(2);
        destination.setTextColor(WHITE);destination.setTextSize(17);
        destination.setHintTextColor(MUTED);destination.setHint("ชื่อสถานที่ / พิกัด เช่น 15.8700,100.9925");
        destination.setText("อนุสรณ์ดอนเจดีย์ สุพรรณบุรี");
        destination.setPadding(dp(14),dp(12),dp(14),dp(12));
        destination.setBackground(background(SURFACE,15));
        root.addView(destination,new LinearLayout.LayoutParams(-1,dp(98)));

        Button clip=button("1. อ่านจุดหมายจากคลิปบอร์ด",false);
        clip.setOnClickListener(v->readClipboard());
        addButton(root,clip);
        Button maps=button("2. เปิด Google Maps พร้อมจุดหมาย",true);
        maps.setOnClickListener(v->openMaps());addButton(root,maps);

        Button diagnostics=button("ตรวจว่าพบ Google Maps ภายในหรือไม่",false);
        diagnostics.setOnClickListener(v->updateReport());addButton(root,diagnostics);
        status=label("",13,MUTED);status.setPadding(dp(7),dp(18),dp(7),dp(4));root.addView(status);

        TextView installTitle=label("ส่ง Companion เข้า GPack (ทดสอบ)",19,WHITE);
        installTitle.setTypeface(Typeface.DEFAULT_BOLD);
        installTitle.setPadding(0,dp(20),0,dp(8));root.addView(installTitle);
        TextView installationNote=label(
            "สำหรับ BYD: ติดตั้ง Companion APK จากแฟลชไดรฟ์บน Android ของรถก่อน "
            +"จากนั้นกดปุ่มในหน้านี้โดยไม่ต้องเลือกไฟล์อีก\\n"
            +"GPack ต้องยอมรับไฟล์จากแอปภายนอก จึงจะติดตั้งเข้าเครื่องจำลองได้ "
            +"แอปจะไม่ติดตั้งเองเงียบ ๆ หรือรีเซ็ต GPack",13,MUTED);
        installationNote.setPadding(dp(4),dp(7),dp(4),dp(9));root.addView(installationNote);

        Button checkOwn=button("3. ตรวจสอบ APK ตัวช่วยที่ติดตั้งอยู่",false);
        checkOwn.setOnClickListener(v->checkOwnApk());addButton(root,checkOwn);
        Button install=button("4. ส่ง APK ตัวช่วยให้ GPack ติดตั้ง",true);
        install.setOnClickListener(v->confirmInstallToGpack());addButton(root,install);
        Button chooser=button("5. เปิด APK ด้วยตัวติดตั้งที่เครื่องรองรับ",false);
        chooser.setOnClickListener(v->confirmOpenSystemInstaller());addButton(root,chooser);
        Button inspect=button("รายงานช่องทางติดตั้ง GPack",false);
        inspect.setOnClickListener(v->showInstallerReport());addButton(root,inspect);
        Button readTrace=button("6. ตรวจว่า GPack อ่านไฟล์ APK จริงหรือไม่",true);
        readTrace.setOnClickListener(v->showHandoffReport());addButton(root,readTrace);
        Button audit=button("7. ตรวจ GPack ละเอียด: วงเหลือง / ติดตั้ง / เปิดแอป",true);
        audit.setOnClickListener(v->startActivity(new Intent(this,GpackAppStateActivity.class)));
        addButton(root,audit);
        installerStatus=label("สถานะ: ยังไม่ได้ทดลองการติดตั้งบนรถ",13,MUTED);
        installerStatus.setTextIsSelectable(true);
        installerStatus.setPadding(dp(7),dp(15),dp(7),dp(6));root.addView(installerStatus);
        TextView warning=label(
            "ทดสอบขณะรถจอดเท่านั้น • แอปนี้ไม่เข้าถึง NAS หรือบัญชี Google "
            +"และไม่บันทึกคลิปบอร์ดลงดิสก์\n"
            +"หาก Google Maps เปิดด้วยพิกัดได้ภายใน GPack เราจะพัฒนาช่องทางส่งข้อมูลจาก BYD Phone Space ต่อ",13,MUTED);
        warning.setPadding(0,dp(20),0,0);root.addView(warning);
        setContentView(sc);
        updateReport();
    }
    private void updateInstallStatus(String text){
        if(installerStatus!=null)installerStatus.setText("ผลทดลองล่าสุด: "+text);
    }

    /** Tests whether this app can hand out its installed APK without a file picker. */
    private boolean checkOwnApk(){
        try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(OwnApkProvider.URI,"r")){
            long length=fd==null?-1:fd.getStatSize();
            if(length<=0)throw new java.io.IOException("APK มีขนาด 0 หรืออ่านไม่ได้");
            updateInstallStatus("อ่าน APK ของตัวช่วยจาก ContentProvider ได้ ("+(length/1024)+" KB)");
            return true;
        }catch(Exception e){
            updateInstallStatus("อ่าน APK ของตัวเองไม่ได้: "+e.getClass().getSimpleName());
            return false;
        }
    }

    private ActivityInfo externalGpackInstaller(){
        try{
            ActivityInfo info=getPackageManager().getActivityInfo(
                    new ComponentName(GPACK,GPACK_INSTALLER),0);
            if(!info.exported || (info.permission!=null&&!info.permission.isEmpty()))return null;
            return info;
        }catch(Exception e){return null;}
    }

    private String installerReport(){
        StringBuilder out=new StringBuilder("BYD Nav Companion V0.1.3 — GPack self-APK handoff\n");
        out.append("Android: ").append(Build.VERSION.RELEASE).append(" API ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("Companion APK: ").append(getApplicationInfo().sourceDir).append('\n');
        out.append("Own APK URI: ").append(OwnApkProvider.URI).append('\n');
        out.append("GPack visible: ");
        try{
            android.content.pm.PackageInfo pkg=getPackageManager().getPackageInfo(GPACK,0);
            out.append("YES v").append(pkg.versionName).append('\n');
        }catch(Exception e){out.append("NO\n");}
        try{
            ActivityInfo info=getPackageManager().getActivityInfo(new ComponentName(GPACK,GPACK_INSTALLER),0);
            out.append("GPack AppInstaller exists: YES\n");
            out.append("Exported: ").append(info.exported).append('\n');
            out.append("Required permission: ").append(info.permission==null?"none":info.permission).append('\n');
        }catch(Exception e){
            out.append("GPack AppInstaller not accessible: ").append(e.getClass().getSimpleName()).append('\n');
        }
        Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(OwnApkProvider.URI,APK_MIME);
        try{
            List<ResolveInfo> apps=getPackageManager().queryIntentActivities(view,0);
            out.append("Host APK view handlers: ").append(apps.size()).append('\n');
            for(ResolveInfo r:apps)if(r.activityInfo!=null){
                out.append(" • ").append(r.activityInfo.packageName)
                    .append("/").append(r.activityInfo.name).append('\n');
            }
        }catch(Exception e){out.append("Cannot enumerate APK handlers\n");}
        out.append("NOTE: startActivity success is NOT proof of installation inside GPack.\n");
        out.append(ApkHandoffLog.report(this));
        return out.toString();
    }

    private void showHandoffReport(){
        String report=ApkHandoffLog.report(this);
        updateInstallStatus(report);
        new AlertDialog.Builder(this).setTitle("ผลการอ่าน APK จากตัวติดตั้ง")
            .setMessage(report+"\nหมายเหตุ: Android อาจเปิด Activity ได้โดยยังไม่ได้ร้องขออ่าน APK")
            .setPositiveButton("ตกลง",null)
            .setNeutralButton("คัดลอกผล",(d,w)->{
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Installer APK read report",report));
            }).show();
    }
    private void showInstallerReport(){
        String report=installerReport();
        new AlertDialog.Builder(this).setTitle("ผลตรวจ GPack Installer")
            .setMessage(report)
            .setPositiveButton("ตกลง",null)
            .setNeutralButton("คัดลอกรายงาน",(d,w)->{
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("GPack Installer Report",report));
            }).show();
    }

    private Intent attachOwnApk(Intent intent){
        intent.setDataAndType(OwnApkProvider.URI,APK_MIME);
        intent.setClipData(ClipData.newUri(getContentResolver(),"BYD Nav Companion APK",OwnApkProvider.URI));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private void confirmInstallToGpack(){
        if(!checkOwnApk())return;
        if(externalGpackInstaller()==null){
            new AlertDialog.Builder(this)
                .setTitle("ยังสั่งตัวติดตั้ง GPack จากภายนอกไม่ได้")
                .setMessage("ไม่พบ AppInstallerActivity ที่เปิดให้แอปภายนอกใช้ได้ "
                    +"หรือ GPack ยังไม่ติดตั้งใน Android หลัก\n\n"
                    +"การติดตั้ง APK บนรถตามปกติไม่ได้หมายความว่า APK ถูกติดตั้งภายใน GPack")
                .setPositiveButton("ดูรายงาน",(d,w)->showInstallerReport())
                .setNegativeButton("ปิด",null).show();
            updateInstallStatus("GPack installer ไม่เปิดให้เรียกตรง หรือไม่พบ GPack");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("ส่งตัวช่วยเข้า GPack?")
            .setMessage("ตัวช่วยจะส่ง APK ที่ติดตั้งอยู่ของตัวเองให้ GPack AppInstaller "
                +"โดยไม่ต้องเลือกไฟล์จาก USB อีกครั้ง\n\n"
                +"ระบบ GPack อาจไม่ยอมรับไฟล์ หรืออาจแสดงหน้าติดตั้งให้คุณกดยืนยัน "
                +"กรุณาตรวจว่าตัวช่วยปรากฏ *ในหน้ารวมแอปภายใน GPack* จริงหรือไม่\n\n"
                +"ไม่แก้ไขบัญชี Google และไม่รีเซ็ต GPack")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ทดลองส่งไฟล์",(d,w)->{
                ApkHandoffLog.start(this,"Direct to GPack AppInstallerActivity");
                Intent request=attachOwnApk(new Intent(Intent.ACTION_VIEW)
                    .setComponent(new ComponentName(GPACK,GPACK_INSTALLER)));
                try{
                    startActivity(request);
                    updateInstallStatus("ส่งคำขอเปิดตัวติดตั้ง GPack แล้ว — ต้องตรวจผลจริง ไม่ใช่ยืนยันว่าติดตั้งสำเร็จ");
                }catch(Exception error){
                    updateInstallStatus("GPack ปฏิเสธคำขอ: "+error.getClass().getSimpleName()
                        +(error.getMessage()==null?"":" / "+error.getMessage()));
                    new AlertDialog.Builder(this).setTitle("ส่งเข้า GPack ไม่สำเร็จ")
                        .setMessage("GPack ไม่รับคำขอติดตั้งจาก Companion\n"
                            +error.getClass().getSimpleName()+"\n"
                            +"กรุณาถ่ายภาพรายงานส่งกลับมา ไม่ต้องแก้ระบบรถ")
                        .setPositiveButton("ตกลง",null).show();
                }
            }).show();
    }

    private void confirmOpenSystemInstaller(){
        if(!checkOwnApk())return;
        new AlertDialog.Builder(this)
            .setTitle("เปิดด้วยตัวติดตั้ง APK ที่เครื่องรองรับ")
            .setMessage("วิธีนี้อาจเปิดตัวติดตั้ง Android หลักของ BYD ซึ่งไม่ใช่ GPack\n\n"
                +"ถ้ามีตัวเลือก GPack ให้เลือก GPack แล้วตรวจว่าติดตั้งภายในจริง "
                +"ถ้าไม่มี ไม่จำเป็นต้องกดยืนยันติดตั้งซ้ำ")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("แสดงตัวเลือก",(d,w)->{
                ApkHandoffLog.start(this,"Android APK installer chooser");
                Intent view=attachOwnApk(new Intent(Intent.ACTION_VIEW));
                try{
                    Intent chooser=Intent.createChooser(view,"เลือกตัวติดตั้ง APK");
                    chooser.setClipData(ClipData.newUri(getContentResolver(),"BYD Nav Companion APK",OwnApkProvider.URI));
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(chooser);
                    updateInstallStatus("เปิดตัวเลือกรับไฟล์ APK แล้ว — ตรวจว่า GPack ปรากฏหรือไม่");
                }catch(Exception error){
                    updateInstallStatus("ระบบรถไม่เปิดตัวเลือกติดตั้ง: "+error.getClass().getSimpleName());
                }
            }).show();
    }

    private void readClipboard(){
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm==null||!cm.hasPrimaryClip()){message("ไม่พบข้อมูลคลิปบอร์ดในสภาพแวดล้อมนี้");return;}
        try{
            ClipData data=cm.getPrimaryClip();
            if(data==null||data.getItemCount()==0){message("คลิปบอร์ดว่าง");return;}
            CharSequence text=data.getItemAt(0).coerceToText(this);
            if(text==null||text.toString().trim().isEmpty()){message("คลิปบอร์ดไม่มีข้อความ");return;}
            String value=text.toString().trim();
            if(value.length()>2048){message("ข้อความยาวเกินไป");return;}
            destination.setText(value);
            status.setText("อ่านคลิปบอร์ดได้แล้ว โปรดตรวจข้อความก่อนกดเปิด Maps");
        }catch(Exception e){message("GPack อาจแยกคลิปบอร์ด: "+e.getClass().getSimpleName());}
    }
    private String normalized(String value){
        String q=value.trim();
        if(q.startsWith("https://www.google.com/maps/")||q.startsWith("https://maps.google.com/")){
            try{
                Uri uri=Uri.parse(q);
                String parsed=uri.getQueryParameter("destination");
                if(parsed==null||parsed.isEmpty())parsed=uri.getQueryParameter("query");
                if(parsed!=null&&!parsed.isEmpty())return parsed.trim();
            }catch(Exception ignored){}
        }
        return q;
    }
    private void openMaps(){
        String query=normalized(destination.getText().toString());
        if(query.isEmpty()){destination.setError("กรุณาระบุจุดหมาย");return;}
        Intent direct=new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(query)+"&mode=d"));
        direct.setPackage("com.google.android.apps.maps");
        if(tryStart(direct,"เปิด Google Maps ผ่าน google.navigation: ได้"))return;
        Intent geo=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(query)));
        geo.setPackage("com.google.android.apps.maps");
        if(tryStart(geo,"เปิด Google Maps ผ่าน geo: ได้"))return;
        new AlertDialog.Builder(this).setTitle("Maps ไม่รับคำสั่งมาตรฐานในสภาพแวดล้อมนี้")
            .setMessage("ไม่พบช่องเปิดแผนที่พร้อมจุดหมายจากแอปภายใน GPack\n"
                +"กรุณาคัดลอกผลรายงานหรือถ่ายภาพหน้าจอส่งกลับไปตรวจ โดยไม่ต้องเปลี่ยนบัญชีหรือรีเซ็ต GPack")
            .setPositiveButton("ตกลง",null).show();
    }
    private boolean tryStart(Intent intent,String result){
        try{
            startActivity(intent);status.setText(result+" • กรุณาตรวจว่าจุดหมายบนแผนที่ตรงจริงหรือไม่");
            return true;
        }catch(Exception ignored){return false;}
    }
    private void updateReport(){
        if(status==null)return;
        boolean found=false;
        String version="ไม่พบ";
        try{
            android.content.pm.PackageInfo pkg=getPackageManager().getPackageInfo("com.google.android.apps.maps",0);
            found=true;version=pkg.versionName;
        }catch(Exception ignored){}
        String report="Android "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT
            +"\nGoogle Maps package visible: "+found+" ("+version+")"
            +"\nApp package: "+getPackageName()
            +"\nMicrophone/network permissions: not requested"
            +"\nทดสอบ: คัดลอกจุดหมายจาก BYD Phone Space → เข้า Companion ใน GPack → อ่านคลิปบอร์ด → เปิด Maps";
        status.setText(report);
    }
    private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();if(status!=null)status.setText(s);}
}
