package com.pcopi.gpacknavcompanion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
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
    private TextView status;

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
        TextView sub=label("GPack internal Google Maps compatibility test • V0.1.0",13,MUTED);
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
        TextView warning=label(
            "ทดสอบขณะรถจอดเท่านั้น • แอปนี้ไม่เข้าถึง NAS หรือบัญชี Google "
            +"และไม่บันทึกคลิปบอร์ดลงดิสก์\n"
            +"หาก Google Maps เปิดด้วยพิกัดได้ภายใน GPack เราจะพัฒนาช่องทางส่งข้อมูลจาก BYD Phone Space ต่อ",13,MUTED);
        warning.setPadding(0,dp(20),0,0);root.addView(warning);
        setContentView(sc);
        updateReport();
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
