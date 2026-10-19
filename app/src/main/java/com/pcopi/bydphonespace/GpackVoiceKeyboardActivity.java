package com.pcopi.bydphonespace;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Non-invasive Gboard / Thai voice readiness probe.
 * Host Android IME and speech recognition ARE NOT the same as apps inside GPack.
 * The user separately observes the guest Maps keyboard and microphone.
 */
public final class GpackVoiceKeyboardActivity extends Activity {
    private static final int BG=0xff07101b,SURFACE=0xff111f2f,FG=0xfff2f7fc,
        MUTED=0xff9db1c5,CYAN=0xff4dc4ff;
    private static final int REQUEST_SPEECH=7391;
    private static final String PREF="gpack_voice_ime_car_test_v073";
    private static final String GBOARD="com.google.android.inputmethod.latin";
    private static final String GPACK="com.gpack.agent";
    private static final String[] OBSERVATIONS={
        "ยังไม่ได้ตรวจใน GPack",
        "Maps ใน GPack แสดง Gboard และมีปุ่มไมค์",
        "Maps ใน GPack แสดง Gboard แต่ไม่มีปุ่มไมค์",
        "Maps ใน GPack ใช้คีย์บอร์ดอื่น ไม่ใช่ Gboard",
        "Maps ใน GPack ไม่มีคีย์บอร์ดขึ้น",
        "GPack Maps ยังเข้าไม่ถึงช่องค้นหา"
    };
    private static final String[] VOICE_OUTCOMES={
        "ยังไม่ได้ลองเสียงใน GPack",
        "กดไมค์แล้วรู้จำภาษาไทยถูกต้อง",
        "มีปุ่มไมค์ แต่เปิดไมค์/รู้จำเสียงไม่สำเร็จ",
        "ไม่มีปุ่มไมค์ให้ทดลอง",
        "ขึ้นหน้าขอสิทธิ์ไมโครโฟน",
        "ไม่สามารถทดสอบภายใน GPack ได้"
    };
    private EditText sample;
    private TextView result;
    private boolean fromNavigation;
    private String lastHostVoice="ยังไม่ได้ทดลอง";
    private String lastRecognized="";
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(15));return d;
    }
    private TextView label(String s,int sp,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;
    }
    private Button button(String s,boolean accent){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);
        b.setTextColor(accent?BG:FG);b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(background(accent?CYAN:SURFACE));return b;
    }
    private void addButton(LinearLayout root,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
        p.setMargins(0,dp(8),0,0);root.addView(b,p);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        fromNavigation=getIntent().getBooleanExtra("fromNavigation",false);
        String initial=getIntent().getStringExtra("destination");
        if(initial==null||initial.trim().isEmpty())initial="อนุสรณ์ดอนเจดีย์ สุพรรณบุรี";
        draw(initial);
    }
    private void draw(String initial){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(17),dp(18),dp(34));sc.addView(root);
        Button back=button("‹ กลับ",false);back.setOnClickListener(v->finish());addButton(root,back);
        TextView head=label("Gboard + เสียงภาษาไทย",23,FG);
        head.setTypeface(Typeface.DEFAULT_BOLD);head.setPadding(0,dp(18),0,dp(7));root.addView(head);
        root.addView(label("BYD Phone Space V0.7.3 • ตรวจควบคู่กับ GPack Maps",13,CYAN));
        TextView explain=label("ข้อสำคัญ: Gboard บน Android ของรถกับ Gboard ภายใน GPack "
            +"อาจเป็นคนละสภาพแวดล้อม การพบ Gboard ในระบบรถไม่ได้พิสูจน์ว่าปุ่มไมค์ใช้ได้ใน Google Maps ของ GPack\n"
            +"ทดลองขณะรถจอดเท่านั้น ไม่มีการอัดหรือบันทึกเสียง และไม่เปลี่ยนคีย์บอร์ดเริ่มต้นเอง",13,MUTED);
        explain.setPadding(dp(12),dp(14),dp(12),dp(14));
        explain.setBackground(background(SURFACE));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);
        ep.setMargins(0,dp(14),0,dp(13));root.addView(explain,ep);

        TextView reportTitle=label("สถานะอัตโนมัติจาก Android หลัก",17,FG);
        reportTitle.setTypeface(Typeface.DEFAULT_BOLD);root.addView(reportTitle);
        result=label(report(),13,MUTED);result.setTextIsSelectable(true);
        result.setPadding(dp(12),dp(12),dp(12),dp(12));result.setBackground(background(SURFACE));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
        rp.setMargins(0,dp(8),0,dp(8));root.addView(result,rp);

        TextView inputTitle=label("ทดสอบแป้นพิมพ์และข้อความภาษาไทย",17,FG);
        inputTitle.setPadding(0,dp(20),0,dp(7));root.addView(inputTitle);
        sample=new EditText(this);
        sample.setText(initial);sample.setTextSize(17);sample.setSingleLine(false);sample.setMinLines(2);
        sample.setTextColor(FG);sample.setHintTextColor(MUTED);
        sample.setHint("ลองพิมพ์หรือพูดชื่อสถานที่");sample.setBackground(background(SURFACE));
        sample.setPadding(dp(14),dp(13),dp(14),dp(13));
        root.addView(sample,new LinearLayout.LayoutParams(-1,dp(91)));

        Button ime=button("1. แสดงตัวเลือกคีย์บอร์ด Android หลัก",false);
        ime.setOnClickListener(v->showImePicker());addButton(root,ime);
        Button config=button("2. เปิดตั้งค่าแป้นพิมพ์ (ไม่เปลี่ยนเอง)",false);
        config.setOnClickListener(v->{
            try{startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));}
            catch(Exception e){toast("ระบบรถไม่เปิดหน้าตั้งค่าคีย์บอร์ด");}
        });addButton(root,config);
        Button speak=button("3. ทดลองระบบรู้จำเสียงไทยของ Android หลัก",true);
        speak.setOnClickListener(v->startSpeech());addButton(root,speak);

        TextView guestTitle=label("ทดสอบภายใน Google Maps ของ GPack",17,FG);
        guestTitle.setTypeface(Typeface.DEFAULT_BOLD);
        guestTitle.setPadding(0,dp(24),0,dp(7));root.addView(guestTitle);
        TextView guestNote=label("ขั้นตอน: กดเปิด GPack → เข้า Google Maps → แตะช่องค้นหา "
            +"แล้วดูว่าแป้นพิมพ์เป็น Gboard หรือไม่และมีปุ่มไมค์หรือไม่ "
            +"ถ้ามี ลองพูดชื่อสถานที่ภาษาไทย แล้วกลับมาบันทึกผลตรงนี้\n"
            +"ห้ามเปลี่ยน GPack หรือยืนยันสิทธิ์ใด ๆ โดยไม่อ่านหน้าจอก่อน",13,MUTED);
        guestNote.setPadding(0,dp(6),0,dp(12));root.addView(guestNote);
        Button gpack=button("4. เปิด GPack เพื่อดูคีย์บอร์ดใน Maps",false);
        gpack.setOnClickListener(v->{
            if(!NavigationBridge.openGpack(this))toast("ไม่พบ GPack ในเครื่องนี้");
        });addButton(root,gpack);
        Button chooseKeyboard=button("5. บันทึกผล Gboard ที่เห็นใน GPack",false);
        chooseKeyboard.setOnClickListener(v->choose("keyboard",OBSERVATIONS,"ผลคีย์บอร์ดใน GPack"));addButton(root,chooseKeyboard);
        Button chooseMic=button("6. บันทึกผลปุ่มไมค์และการพูดไทยใน GPack",false);
        chooseMic.setOnClickListener(v->choose("guestVoice",VOICE_OUTCOMES,"ผลการค้นหาด้วยเสียงใน GPack"));addButton(root,chooseMic);

        Button shareReport=button("คัดลอกรายงาน Gboard + เสียงทั้งหมด",false);
        shareReport.setOnClickListener(v->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("GPack Voice Keyboard Report",report()));
            toast("คัดลอกรายงานแล้ว");
        });addButton(root,shareReport);
        Button sendDest=button(fromNavigation?
            "7. ใช้ข้อความนี้เป็นจุดหมายในการทดสอบ Maps":"7. คัดลอกจุดหมายที่พิมพ์/พูดแล้วเปิด GPack",true);
        sendDest.setOnClickListener(v->sendDestination());addButton(root,sendDest);
        setContentView(sc);
    }
    private void showImePicker(){
        try{
            InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(imm==null){toast("ไม่พบระบบคีย์บอร์ด");return;}
            imm.showInputMethodPicker();
        }catch(Exception e){toast("ระบบรถไม่อนุญาตเปิดตัวเลือกคีย์บอร์ด");}
    }
    private void startSpeech(){
        Intent speech=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"th-TH");
        speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"th-TH");
        speech.putExtra(RecognizerIntent.EXTRA_PROMPT,"พูดชื่อสถานที่ที่ต้องการนำทาง");
        speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        try{startActivityForResult(speech,REQUEST_SPEECH);lastHostVoice="เปิดคำขอรู้จำเสียงแล้ว รอตรวจผล";}
        catch(Exception e){
            lastHostVoice="ไม่พบแอปที่รับ RecognizerIntent: "+e.getClass().getSimpleName();
            if(result!=null)result.setText(report());
            new AlertDialog.Builder(this).setTitle("Android หลักไม่รองรับวิธีนี้")
                .setMessage("ไม่พบแอปรู้จำเสียงจากคำสั่งมาตรฐาน แม้ Gboard จะติดตั้งอยู่ "
                    +"การพิมพ์ด้วยเสียงใน GPack อาจยังทำงานได้ ต้องทดลองแยกใน Maps")
                .setPositiveButton("ตกลง",null).show();
        }
    }
    @Override protected void onActivityResult(int request,int status,Intent data){
        super.onActivityResult(request,status,data);
        if(request!=REQUEST_SPEECH)return;
        if(status==RESULT_OK&&data!=null){
            ArrayList<String> alternatives=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(alternatives!=null&&!alternatives.isEmpty()&&alternatives.get(0)!=null){
                lastRecognized=alternatives.get(0).trim();
                lastHostVoice="ระบบรู้จำเสียงส่งข้อความกลับสำเร็จ";
                sample.setText(lastRecognized);sample.setSelection(lastRecognized.length());
            }else lastHostVoice="ระบบเปิดได้ แต่ไม่ได้ผลข้อความ";
        }else lastHostVoice="ยกเลิกหรือระบบไม่ส่งผลรู้จำเสียงกลับ";
        if(result!=null)result.setText(report());
    }
    private void choose(String key,String[] values,String title){
        int selected=getSharedPreferences(PREF,MODE_PRIVATE).getInt(key,0);
        new AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(values,selected,(d,index)->{
                getSharedPreferences(PREF,MODE_PRIVATE).edit().putInt(key,index).apply();
                d.dismiss();
                if(result!=null)result.setText(report());
            }).setNegativeButton("ยกเลิก",null).show();
    }
    private String pkgVersion(String packageName){
        try{
            PackageInfo p=getPackageManager().getPackageInfo(packageName,0);
            return "มี • "+p.versionName;
        }catch(Exception e){return "ไม่พบใน Android หลัก";}
    }
    private String enabledIme(){
        try{
            InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            List<InputMethodInfo> list=imm==null?null:imm.getEnabledInputMethodList();
            if(list==null||list.isEmpty())return "ไม่พบข้อมูล";
            StringBuilder b=new StringBuilder();
            for(InputMethodInfo info:list){
                if(b.length()>0)b.append(", ");
                b.append(info.getPackageName());
            }
            return b.toString();
        }catch(Exception e){return "อ่านข้อมูลไม่ได้";}
    }
    private String report(){
        StringBuilder r=new StringBuilder("BYD Phone Space Gboard / Voice V0.7.3\n");
        r.append("GPack: ").append(pkgVersion(GPACK)).append('\n');
        r.append("Gboard (host): ").append(pkgVersion(GBOARD)).append('\n');
        r.append("Default IME (host): ").append(Settings.Secure.getString(
            getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD)).append('\n');
        r.append("Enabled host IMEs: ").append(enabledIme()).append('\n');
        r.append("Microphone hardware: ").append(
            getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)).append('\n');
        r.append("Our app RECORD_AUDIO: ").append(
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED).append('\n');
        r.append("GPack RECORD_AUDIO (host grant): ").append(
            getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO,GPACK)==PackageManager.PERMISSION_GRANTED).append('\n');
        try{
            Intent voice=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            r.append("Host recognizer handlers: ").append(
                getPackageManager().queryIntentActivities(voice,0).size()).append('\n');
        }catch(Exception e){r.append("Host recognizer handlers: unavailable\n");}
        r.append("Host voice test: ").append(lastHostVoice).append('\n');
        android.content.SharedPreferences p=getSharedPreferences(PREF,MODE_PRIVATE);
        r.append("Observed GPack keyboard: ").append(OBSERVATIONS[p.getInt("keyboard",0)]).append('\n');
        r.append("Observed GPack voice: ").append(VOICE_OUTCOMES[p.getInt("guestVoice",0)]).append('\n');
        r.append("Notes: Host Gboard != GPack virtual Gboard. No voice recording saved.");
        return r.toString();
    }
    private void sendDestination(){
        String q=sample.getText().toString().trim();
        if(q.isEmpty()){sample.setError("กรุณาพิมพ์หรือพูดจุดหมาย");return;}
        if(fromNavigation){
            Intent data=new Intent().putExtra("destination",q);
            setResult(RESULT_OK,data);finish();return;
        }
        ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Voice Maps destination",q));
        NavigationBridge.openGpack(this);
        toast("คัดลอกจุดหมายแล้ว • เปิด Maps ใน GPack แล้ววางข้อความ");
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
