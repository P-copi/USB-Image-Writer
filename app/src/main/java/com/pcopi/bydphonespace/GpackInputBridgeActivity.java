package com.pcopi.bydphonespace;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** User-controlled diagnostics and invocation for accessibility and IME routes. */
public final class GpackInputBridgeActivity extends Activity {
    private static final int BG=0xff07101b,CARD=0xff111f2f,FG=0xfff2f7fc,
        MUTED=0xff9db1c5,CYAN=0xff4dc4ff;
    private EditText destination;
    private TextView status;
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;
    }
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
    }
    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(primary?BG:FG);
        b.setBackground(bg(primary?CYAN:CARD));return b;
    }
    private void add(LinearLayout root,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
        p.setMargins(0,dp(9),0,0);root.addView(b,p);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19),dp(17),dp(19),dp(34));scroll.addView(root);
        Button back=button("‹ กลับ",false);back.setOnClickListener(v->finish());add(root,back);
        TextView title=text("GPack Maps • ส่งจุดหมายผ่านหน้าจอ",22,FG);
        title.setTypeface(Typeface.DEFAULT_BOLD);title.setPadding(0,dp(20),0,dp(6));root.addView(title);
        root.addView(text("V0.7.4 • ไม่ต้องเดาชื่อ Extra ภายใน GPack",13,CYAN));
        TextView note=text("ใช้คำสั่งรับข้อความมาตรฐานของ Android แทนการส่งพิกัดผ่าน Intent ของ GPack\n"
            +"การเข้าถึงหน้าจอและแป้นพิมพ์ต้องเปิดเองในการตั้งค่า • ไม่มีการให้สิทธิ์อัตโนมัติ\n"
            +"ทดสอบขณะรถจอด และตรวจชื่อจุดหมายก่อนเริ่มนำทาง",13,MUTED);
        note.setPadding(dp(11),dp(13),dp(11),dp(13));note.setBackground(bg(CARD));
        LinearLayout.LayoutParams pn=new LinearLayout.LayoutParams(-1,-2);pn.setMargins(0,dp(12),0,dp(13));
        root.addView(note,pn);
        destination=new EditText(this);destination.setTextColor(FG);destination.setHintTextColor(MUTED);
        String last=GpackInputBridge.current(this);
        destination.setText(last.isEmpty()?"อนุสรณ์ดอนเจดีย์ สุพรรณบุรี":last);
        destination.setSingleLine(false);destination.setMinLines(2);destination.setTextSize(17);
        destination.setHint("จุดหมายจากรูปหรือพิกัด GPS");destination.setBackground(bg(CARD));
        destination.setPadding(dp(12),dp(12),dp(12),dp(12));
        root.addView(destination,new LinearLayout.LayoutParams(-1,dp(93)));

        Button save=button("1. เก็บจุดหมายในแอปเพื่อส่งเข้า Maps",false);
        save.setOnClickListener(v->{if(remember())toast("บันทึกจุดหมายไว้ในแอปแล้ว");});
        add(root,save);
        Button accessibility=button("2. เปิดการตั้งค่าตัวช่วยใส่จุดหมาย (Accessibility)",false);
        accessibility.setOnClickListener(v->openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        add(root,accessibility);
        Button arm=button("3. ทดลองเปิด GPack + ใส่จุดหมายอัตโนมัติ",true);
        arm.setOnClickListener(v->{
            if(!remember())return;
            if(!accessibilityEnabled()){
                new AlertDialog.Builder(this).setTitle("ยังไม่ได้เปิด GPack Nav Assist")
                    .setMessage("ต้องเปิดบริการช่วยการเข้าถึง GPack Nav Assist ด้วยตัวเองใน Android Settings ก่อน\n\n"
                        +"หากระบบรถไม่มีเมนูนี้ ให้ใช้วิธีแป้นพิมพ์เฉพาะกิจหรือคัดลอกจุดหมายแทน")
                    .setPositiveButton("ไปตั้งค่า",(d,w)->openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    .setNegativeButton("ยกเลิก",null).show();return;
            }
            GpackInputBridge.arm(this,destination.getText().toString().trim());
            NavigationBridge.openGpack(this);
            toast("เปิด GPack แล้ว • ถ้า Maps ไม่เปิดเอง ให้แตะ Maps แล้วแตะช่องค้นหา");
        });
        add(root,arm);
        Button imeSettings=button("4. เปิดตั้งค่าแป้นพิมพ์ BYD Nav Keyboard",false);
        imeSettings.setOnClickListener(v->openSettings(Settings.ACTION_INPUT_METHOD_SETTINGS));add(root,imeSettings);
        Button choose=button("5. เลือก BYD Nav Keyboard เพื่อใส่จุดหมาย",false);
        choose.setOnClickListener(v->{
            if(!remember())return;
            InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showInputMethodPicker();
            else toast("ไม่มีตัวเลือกแป้นพิมพ์บนเครื่องนี้");
        });add(root,choose);
        Button normal=button("6. เปิด GPack เพื่อใช้แป้นพิมพ์กับ Maps",false);
        normal.setOnClickListener(v->{
            if(remember()){
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("จุดหมาย BYD Phone Space",destination.getText().toString().trim()));
                NavigationBridge.openGpack(this);
            }
        });add(root,normal);
        Button check=button("ตรวจสถานะช่องทางป้อนข้อความบนรถ",false);
        check.setOnClickListener(v->{if(status!=null)status.setText(report());});add(root,check);
        Button clear=button("ยกเลิกตัวช่วยและล้างจุดหมายที่เก็บไว้",false);
        clear.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("ล้างจุดหมายที่บันทึกไว้?")
            .setMessage("ล้างเฉพาะข้อมูลใน BYD Phone Space และยกเลิกคำสั่งช่วยกรอก ไม่เปลี่ยนแป้นพิมพ์หรือสิทธิ์ของรถ")
            .setNegativeButton("ยกเลิก",null)
            .setPositiveButton("ล้าง",(d,w)->{
                GpackInputBridge.clear(this);
                if(status!=null)status.setText(report());
            }).show());add(root,clear);
        status=text(report(),13,MUTED);status.setTextIsSelectable(true);
        status.setPadding(dp(12),dp(15),dp(12),dp(15));status.setBackground(bg(CARD));
        LinearLayout.LayoutParams ps=new LinearLayout.LayoutParams(-1,-2);ps.setMargins(0,dp(20),0,0);
        root.addView(status,ps);
        setContentView(scroll);
    }
    @Override protected void onResume(){
        super.onResume();
        if(status!=null)status.setText(report());
    }
    private boolean remember(){
        String value=destination.getText().toString().trim();
        if(value.isEmpty()||value.length()>480){
            destination.setError("กรุณาระบุจุดหมายไม่เกิน 480 ตัวอักษร");
            return false;
        }
        GpackInputBridge.remember(this,value);
        if(status!=null)status.setText(report());
        return true;
    }
    private boolean accessibilityEnabled(){
        try{
            AccessibilityManager am=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
            if(am==null)return false;
            List<AccessibilityServiceInfo> services=am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if(services==null)return false;
            for(AccessibilityServiceInfo service:services){
                if(service.getResolveInfo()!=null&&service.getResolveInfo().serviceInfo!=null
                    &&getPackageName().equals(service.getResolveInfo().serviceInfo.packageName)
                    &&GpackNavAssistService.class.getName().equals(service.getResolveInfo().serviceInfo.name))return true;
            }
        }catch(Exception ignored){}
        return false;
    }
    private boolean keyboardEnabled(){
        try{
            InputMethodManager mgr=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(mgr==null)return false;
            List<InputMethodInfo> list=mgr.getEnabledInputMethodList();
            for(InputMethodInfo info:list){
                if(info!=null&&getPackageName().equals(info.getPackageName()))return true;
            }
        }catch(Exception ignored){}
        return false;
    }
    private void openSettings(String action){
        try{startActivity(new Intent(action));}
        catch(Exception e){toast("BYD ไม่อนุญาตเปิดหน้าตั้งค่านี้: "+e.getClass().getSimpleName());}
    }
    private String report(){
        return "GPack Input Bridge V0.7.4\n"+
            "GPack installed: "+NavigationBridge.hasGpack(this)+"\n"+
            "Accessibility enabled (user-controlled): "+accessibilityEnabled()+"\n"+
            "BYD Nav Keyboard enabled: "+keyboardEnabled()+"\n"+
            "Last destination: "+(GpackInputBridge.current(this).isEmpty()?"ไม่มี":"บันทึกไว้แล้ว")+"\n"+
            "Automatic assist armed: "+GpackInputBridge.isArmed(this)+"\n"+
            "Last assist result: "+GpackInputBridge.observation(this)+"\n"+
            "NOTE: Neither enabled setting proves GPack allows the input. Verify the real Maps search field.";
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
