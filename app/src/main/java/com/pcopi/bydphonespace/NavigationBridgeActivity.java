package com.pcopi.bydphonespace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NavigationBridgeActivity extends Activity {
    private final int BG=Color.rgb(7,16,27),CARD=Color.rgb(17,31,47),TEXT=Color.rgb(242,247,252);
    private final int MUTED=Color.rgb(157,177,197),CYAN=Color.rgb(77,196,255),GREEN=Color.rgb(93,219,168),ORANGE=Color.rgb(255,197,111);
    private final String[] MODES = {
        NavigationBridge.GPACK_CLIP,
        NavigationBridge.GPACK_CLIP_URL,
        NavigationBridge.NEUSOFT_GEO,
        NavigationBridge.GPACK_SHARE_TEXT,
        NavigationBridge.GPACK_SHARE_MAPS_URL,
        NavigationBridge.GPACK_SEARCH_VIEW,
        NavigationBridge.GPACK_GEO,
        NavigationBridge.GPACK_WEB,
        NavigationBridge.DIRECT,
        NavigationBridge.GEO,
        NavigationBridge.NAV,
        NavigationBridge.WEB_DIR,
        NavigationBridge.WEB_SEARCH
    };
    private String pendingMode;
    private String destination="อนุสรณ์ดอนเจดีย์ สุพรรณบุรี";
    private EditText destinationInput;
    private boolean showOlderTests=false;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable round(int color,float r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private TextView tv(String text,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return v;}
    private Button button(String label,boolean primary){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(primary?Color.rgb(4,22,33):TEXT);b.setBackground(round(primary?CYAN:Color.rgb(25,44,63),16));return b;}
    private LinearLayout.LayoutParams buttonLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(8),0,0);return p;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(CARD,19));return c;}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(state!=null){destination=state.getString("destination",destination);pendingMode=state.getString("pendingMode");}
        build();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        if(destinationInput!=null)destination=destinationInput.getText().toString();
        out.putString("destination",destination);out.putString("pendingMode",pendingMode);
        super.onSaveInstanceState(out);
    }

    @Override protected void onResume(){
        super.onResume();
        if(pendingMode!=null){
            String mode=pendingMode;pendingMode=null;
            getWindow().getDecorView().postDelayed(()->askOutcome(mode),200);
        }
    }

    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(17),dp(15),dp(17),dp(30));scroll.addView(root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout htxt=new LinearLayout(this);htxt.setOrientation(LinearLayout.VERTICAL);htxt.addView(tv("ทดสอบเชื่อม Maps / GPack",21,TEXT,true));htxt.addView(tv("Navigation Bridge • V0.6.2",12,MUTED,false));head.addView(htxt);root.addView(head);

        LinearLayout intro=card();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(16),0,dp(12));root.addView(intro,p);
        intro.addView(tv("นำทางจากรูป → GPack หรือแผนที่รถ",17,CYAN,true));
        intro.addView(tv("ผลจอ BYD: พบ com.gpack.agent • geo: เดิมเปิดตัวนำทาง Neusoft ไม่ใช่ Maps ใน GPack",12.5f,ORANGE,false));
        TextView note=tv("บนจอ BYD แอป Maps อาจอยู่ใน GPack ไม่ใช่แพ็กเกจ Google Maps ของ Android หลัก\n"
                +"ขั้นแรกลองทางสำรองที่เปิด GPack ได้แล้ว จากนั้นทดลองแชร์ข้อความ/ลิงก์เข้า GPack ทีละวิธี\nกดทดสอบเมื่อรถจอดเท่านั้น • ถ้าเปิดได้แต่ไม่ถึงจุดหมาย ห้ามบันทึกเป็นวิธีส่งตรง",13,MUTED,false);
        note.setPadding(0,dp(8),0,dp(8));intro.addView(note);
        intro.addView(tv("วิธีที่บันทึกไว้: "+NavigationBridge.label(NavigationBridge.selected(this)),13,GREEN,true));
        Button reset=button("คืนค่าการนำทางแบบเดิม",false);reset.setOnClickListener(v->{NavigationBridge.reset(this);build();});intro.addView(reset,buttonLp());

        TextView label=tv("สถานที่สำหรับทดสอบ (แก้ไขได้)",15,TEXT,true);label.setPadding(dp(3),dp(4),0,dp(6));root.addView(label);
        destinationInput=new EditText(this);destinationInput.setText(destination);destinationInput.setSingleLine(true);destinationInput.setTextSize(15);destinationInput.setTextColor(TEXT);destinationInput.setHintTextColor(MUTED);destinationInput.setHint("ชื่อสถานที่ หรือ latitude,longitude");
        destinationInput.setPadding(dp(13),dp(8),dp(13),dp(8));destinationInput.setBackground(round(CARD,16));root.addView(destinationInput,new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout paste=card();
        LinearLayout.LayoutParams pasteParams=new LinearLayout.LayoutParams(-1,-2);
        pasteParams.setMargins(0,dp(8),0,dp(8));root.addView(paste,pasteParams);
        paste.addView(tv("วิธีวางจุดหมาย (ไม่ต้องพิมพ์เอง)",16,CYAN,true));
        TextView pasteHelp=tv("1. กดปุ่มคัดลอกจุดหมาย + เปิด GPack\n"
            +"2. ใน GPack เปิด Google Maps แล้วแตะช่อง ค้นหา\n"
            +"3. กดค้างในช่องค้นหา → เลือก วาง (Paste)\n"
            +"4. แตะค้นหา/นำทาง แล้วดูว่าชื่อสถานที่หรือพิกัดตรงกับภาพของเราไหม\n"
            +"วิธีใหม่: คัดลอกลิงก์เส้นทาง → เปิด GPack → เปิด Chrome → แตะช่องที่อยู่ → กดค้าง วาง → ไป\n"
            +"หาก Chrome เปิดลิงก์ได้แต่ไม่เปิด Maps โดยตรง ให้บันทึกผลตามจริง",
            13,MUTED,false);
        pasteHelp.setPadding(0,dp(8),0,dp(2));paste.addView(pasteHelp);
        TextView title=tv("วิธีใช้งานและทดสอบใหม่",16,TEXT,true);title.setPadding(dp(3),dp(20),0,dp(8));root.addView(title);
        for(int i=0;i<MODES.length;i++){if(i>2&&!showOlderTests)continue;String mode=MODES[i];
            if(i==8){
                TextView other=tv("การทดสอบมาตรฐาน Android (ส่วนใหญ่ไม่เห็น Maps ภายใน GPack)",14.5f,MUTED,true);
                other.setPadding(dp(3),dp(12),0,dp(12));root.addView(other);
            }LinearLayout c=card();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(9));root.addView(c,cp);
            c.addView(tv((i+1)+". "+NavigationBridge.label(mode),14.5f,TEXT,true));
            TextView status=tv(handlerSummary(mode),12,MUTED,false);status.setPadding(0,dp(6),0,0);c.addView(status);
            String lastResult=outcome(mode);
            if(!lastResult.isEmpty()){
                TextView last=tv("ผลทดลองล่าสุด: "+lastResult,12,GREEN,false);
                last.setPadding(0,dp(5),0,0);c.addView(last);
            }
            Button test=button(i==0?"คัดลอกชื่อ/พิกัด + เปิด GPack":i==1?"คัดลอกลิงก์ Maps + เปิด GPack":i==2?"ทดสอบแผนที่รถ":"ลองอีกครั้ง",true);
            test.setOnClickListener(v->test(mode));c.addView(test,buttonLp());
        }

        Button older=button(showOlderTests?"ซ่อนวิธีเดิมที่ไม่ผ่าน":"แสดงวิธีทดลองเดิมที่ไม่ผ่าน",false);
        older.setOnClickListener(v->{
            if(destinationInput!=null)destination=destinationInput.getText().toString();
            showOlderTests=!showOlderTests;build();
        });
        root.addView(older,buttonLp());

        TextView help=tv("การแชร์เป็นการทดลอง ไม่ได้หมายความว่า GPack จะส่งข้อความให้ Google Maps ภายในได้อัตโนมัติ หากไม่ผ่าน ให้ใช้คัดลอกแล้ววางใน Maps เอง",12.5f,MUTED,false);
        help.setPadding(dp(3),dp(11),0,dp(10));root.addView(help);
        Button manual=button("คัดลอกจุดหมาย + เปิด GPack (วิธีสำรอง)",false);manual.setOnClickListener(v->openGpackManual());root.addView(manual,buttonLp());

        TextView repTitle=tv("รายงานการตรวจระบบ",16,TEXT,true);repTitle.setPadding(dp(3),dp(24),0,dp(8));root.addView(repTitle);
        TextView report=tv(report(),12.5f,MUTED,false);report.setPadding(dp(14),dp(14),dp(14),dp(14));report.setTextIsSelectable(true);report.setBackground(round(CARD,16));root.addView(report);
        Button copy=button("คัดลอกรายงาน Navigation Bridge",true);copy.setOnClickListener(v->{copy(report());Toast.makeText(this,"คัดลอกรายงานแล้ว",Toast.LENGTH_SHORT).show();});root.addView(copy,buttonLp());
        setContentView(scroll);
    }

    private void test(String mode){
        if(destinationInput!=null)destination=destinationInput.getText().toString().trim();
        if(destination.isEmpty()){destinationInput.setError("กรุณาใส่จุดหมาย");return;}
        if(NavigationBridge.GPACK_CLIP.equals(mode)||NavigationBridge.GPACK_CLIP_URL.equals(mode)){
            if(!NavigationBridge.hasGpack(this)){
                new AlertDialog.Builder(this).setTitle("ไม่พบ GPack")
                    .setMessage("บนโทรศัพท์ไม่ต้องติดตั้ง GPack เพื่อทดสอบวิธีอื่น\nให้ทดสอบวิธีนี้บนจอรถที่ติดตั้ง GPack")
                    .setPositiveButton("ตกลง",null).show();return;
            }
            pendingMode=mode;
            if(!NavigationBridge.launchUsing(this,mode,destination))pendingMode=null;
            return;
        }
        Intent intent=NavigationBridge.intentFor(mode,destination);
        try{pendingMode=mode;startActivity(intent);}
        catch(Exception e){
            pendingMode=null;
            recordOutcome(mode,"Android เปิดคำสั่งไม่ได้");
            new AlertDialog.Builder(this).setTitle("Android เปิดวิธีนี้ไม่ได้")
                .setMessage("ยังไม่พบตัวรับคำสั่งจากระบบหลักหรือจาก GPack\n"+e.getClass().getSimpleName())
                .setPositiveButton("ตกลง",null).show();
        }
    }

    private void askOutcome(String mode){
        if(NavigationBridge.GPACK_CLIP_URL.equals(mode)){
            new AlertDialog.Builder(this).setTitle("ผลการทดสอบลิงก์ใน Chrome ภายใน GPack")
                .setMessage("ลองเปิด Chrome ภายใน GPack แล้วกดค้างที่ช่องที่อยู่ → วาง → ไป จากนั้นตรวจว่าปลายทางที่ส่งตรงหรือไม่")
                .setItems(new String[]{
                    "เปิด Google Maps พร้อมจุดหมาย/เส้นทางได้",
                    "เปิดหน้าเว็บ แต่ยังไม่ใช่ Maps พร้อมจุดหมาย",
                    "เปิดไม่ได้หรือวางลิงก์ไม่ได้"
                },(d,which)->{
                    recordOutcome(mode,which==0?"เปิด Maps พร้อมจุดหมายจากลิงก์ได้":
                        which==1?"เปิดเว็บได้ แต่ยังไม่ถึง Maps พร้อมจุดหมาย":"เปิดไม่ได้หรือวางไม่ได้");
                    if(which==0)new AlertDialog.Builder(this).setTitle("ใช้วิธีนี้เป็นหลัก?")
                        .setMessage("ยังต้องเปิด Chrome ใน GPack แล้ววางลิงก์ด้วยตัวเอง แต่ไม่ต้องพิมพ์จุดหมายใหม่")
                        .setNegativeButton("ยังไม่เลือก",null)
                        .setPositiveButton("ตั้งเป็นหลัก",(dia,w)->{NavigationBridge.saveConfirmed(this,mode);build();})
                        .show();
                    else build();
                }).setNegativeButton("ไว้ก่อน",null).show();
            return;
        }
        if(NavigationBridge.NEUSOFT_GEO.equals(mode)){
            new AlertDialog.Builder(this).setTitle("ผลการทดสอบแผนที่ของ BYD")
                .setMessage("นี่เป็นระบบนำทางของรถ ไม่ใช่ Google Maps ใน GPack")
                .setItems(new String[]{
                    "เปิดแผนที่รถพร้อมจุดหมายถูกต้อง",
                    "เปิดได้แต่ไม่มีจุดหมาย",
                    "เปิดไม่ได้"
                },(d,which)->{
                    recordOutcome(mode,which==0?"แผนที่รถรับจุดหมายได้":
                        which==1?"เปิดแผนที่รถ แต่ไม่มีจุดหมาย":"เปิดไม่ได้");
                    if(which==0)new AlertDialog.Builder(this).setTitle("เลือกแผนที่รถเป็นหลัก?")
                        .setMessage("หากเลือก ปุ่มนำทางจากรูปจะไปยังแผนที่รถ แทน Google Maps ใน GPack")
                        .setNegativeButton("ยังไม่เลือก",null)
                        .setPositiveButton("เลือก",(dia,w)->{NavigationBridge.saveConfirmed(this,mode);build();}).show();
                    else build();
                }).setNegativeButton("ไว้ก่อน",null).show();
            return;
        }
        if(NavigationBridge.GPACK_CLIP.equals(mode)){
            new AlertDialog.Builder(this)
                .setTitle("ทดสอบเปิด GPack")
                .setMessage("GPack เปิดแล้วหรือไม่? กรุณาลองเปิด Maps → แตะค้นหา → กดค้าง → วาง เพื่อตรวจว่าข้อความจากแอปเราข้ามเข้า GPack ได้หรือไม่")
                .setPositiveButton("เปิดได้และวางได้",(d,w)->{
                    recordOutcome(mode,"GPack เปิดได้ + วางข้อความใน Maps ได้ (ยังไม่อัตโนมัติ)");
                    NavigationBridge.saveConfirmed(this,NavigationBridge.GPACK_CLIP);
                    build();
                    Toast.makeText(this,"บันทึกทางสำรองแล้ว ยังต้องเปิด Maps และวางข้อความเอง",Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("GPack เปิดได้ แต่วางไม่ได้",(d,w)->{
                    recordOutcome(mode,"GPack เปิดได้ แต่ยังวางข้อความใน Maps ไม่ได้");
                    NavigationBridge.saveConfirmed(this,NavigationBridge.GPACK_CLIP);
                    build();
                    new AlertDialog.Builder(this).setTitle("GPack แยกคลิปบอร์ดหรือแป้นพิมพ์")
                        .setMessage("บันทึกได้เพียงวิธีเปิด GPack ยังไม่ถือว่าส่งจุดหมายสำเร็จ กรุณาถ่ายภาพช่องค้นหา Maps ให้ดูเพื่อแก้ขั้นต่อไป")
                        .setPositiveButton("ตกลง",null).show();
                })
                .setNegativeButton("ไว้ก่อน",null).show();
            return;
        }
        String[] options={"เปิด Google Maps ใน GPack ถึงจุดหมายจริง",
                "เปิดเว็บ/แอปอื่น แต่ไม่ใช่ Maps ใน GPack",
                "เปิดไม่ได้ หรือเปิดได้แต่ไม่ถึงจุดหมาย"};
        new AlertDialog.Builder(this).setTitle("ผลทดลอง: "+NavigationBridge.label(mode))
                .setMessage("โปรดดูบนจอรถจริงก่อนเลือกผล หากไม่แน่ใจอย่าเพิ่งบันทึกวิธีนำทาง")
                .setItems(options,(d,which)->{
                    if(which==0){
                        recordOutcome(mode,"ยืนยันเปิด Maps พร้อมจุดหมายจริง");
                        new AlertDialog.Builder(this).setTitle("ตั้งเป็นวิธีนำทางหลัก?")
                                .setMessage("หลังบันทึก ปุ่ม “นำทางไปที่นี่” ของรูปและเส้นทางจะใช้วิธีนี้จนกว่าจะเปลี่ยนใหม่")
                                .setNegativeButton("ยังไม่บันทึก",null)
                                .setPositiveButton("บันทึกวิธีนี้",(d2,w2)->{NavigationBridge.saveConfirmed(this,mode);build();Toast.makeText(this,"ตั้งวิธีนำทางหลักแล้ว",Toast.LENGTH_SHORT).show();})
                                .show();
                    }else{
                        recordOutcome(mode,which==1?"เปิดหน้าอื่นหรือ GPack แต่ไม่ถึง Maps พร้อมจุดหมาย":"ไม่เปิดหรือไม่มีจุดหมาย");
                        Toast.makeText(this,"ยังไม่บันทึกวิธีนี้ ลองรูปแบบอื่นต่อได้",Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("ข้าม",null).show();
    }

    private void recordOutcome(String mode,String text){
        getSharedPreferences("gpack_nav_test_results_v061",MODE_PRIVATE)
            .edit().putString(mode,text).apply();
    }
    private String outcome(String mode){
        return getSharedPreferences("gpack_nav_test_results_v061",MODE_PRIVATE)
            .getString(mode,"");
    }

    private String handlerSummary(String mode){
        if(NavigationBridge.GPACK_CLIP_URL.equals(mode))
            return NavigationBridge.hasGpack(this)
                ?"คัดลอกลิงก์ Google Maps → เปิด GPack → วางใน Chrome ภายใน GPack"
                :"ไม่พบ GPack บนเครื่องนี้";
        if(NavigationBridge.NEUSOFT_GEO.equals(mode))
            return "ทดสอบส่ง geo: โดยตรงไป com.neusoft.na.navigation • ไม่ใช่ GPack";
        if((NavigationBridge.GPACK_SHARE_TEXT.equals(mode)
                ||NavigationBridge.GPACK_SHARE_MAPS_URL.equals(mode)
                ||NavigationBridge.GPACK_SEARCH_VIEW.equals(mode))
                &&!NavigationBridge.hasGpack(this))
            return "เครื่องนี้ไม่มี GPack • สำหรับทดลองบนจอรถ BYD";
        if(NavigationBridge.GPACK_CLIP.equals(mode))
            return NavigationBridge.hasGpack(this)
                ?"พบ com.gpack.agent • กดแล้วคัดลอกจุดหมาย + เปิด GPack (ไม่ส่งไปถึง Maps อัตโนมัติ)"
                :"ไม่พบ GPack ใน Android หลัก";
        Intent i=NavigationBridge.intentFor(mode,"Bangkok");
        List<ResolveInfo> apps;
        try{apps=getPackageManager().queryIntentActivities(i,PackageManager.MATCH_DEFAULT_ONLY);}
        catch(Exception e){return "อ่านรายชื่อแอปที่รับคำสั่งไม่ได้";}
        if(apps==null||apps.isEmpty())return "Android หลัก: ยังไม่พบแอปที่ประกาศรับคำสั่งนี้ (ยังทดลองกดได้)";
        StringBuilder s=new StringBuilder("Android หลักพบ "+apps.size()+" ตัวรับ");
        int n=0;for(ResolveInfo r:apps){if(r.activityInfo==null)continue;if(n++>=3)break;s.append("\n• ").append(r.activityInfo.packageName);}
        return s.toString();
    }

    private void openGpackManual(){
        if(destinationInput!=null)destination=destinationInput.getText().toString().trim();
        if(destination.isEmpty()){destinationInput.setError("กรุณาใส่จุดหมาย");return;}
        NavigationBridge.launchUsing(this,NavigationBridge.GPACK_CLIP,destination);
    }

    private List<ResolveInfo> gpackLaunchers(){
        ArrayList<ResolveInfo> result=new ArrayList<>();
        try{
            Intent launcher=new Intent(Intent.ACTION_MAIN);launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> all=getPackageManager().queryIntentActivities(launcher,0);
            for(ResolveInfo r:all){
                if(r.activityInfo==null)continue;
                String pkg=r.activityInfo.packageName;
                CharSequence raw=r.loadLabel(getPackageManager());
                String label=raw==null?"":raw.toString();
                String text=(pkg+" "+label).toLowerCase(Locale.ROOT);
                if(text.contains("gpack")||text.contains("gbox")||text.contains("gspace"))result.add(r);
            }
        }catch(Exception ignored){}
        return result;
    }

    private String report(){
        StringBuilder b=new StringBuilder("BYD Phone Space Navigation Bridge V0.6.2\n");
        b.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" API ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        b.append("Chosen method: ").append(NavigationBridge.label(NavigationBridge.selected(this))).append("\n");
        for(String mode:MODES){
            b.append("\n").append(NavigationBridge.label(mode)).append("\n")
                .append(handlerSummary(mode)).append("\n");
            String result=outcome(mode);
            if(!result.isEmpty())b.append("Actual user test: ").append(result).append("\n");
        }
        List<ResolveInfo> list=gpackLaunchers();b.append("\nGPack-like launcher count: ").append(list.size()).append("\n");
        for(ResolveInfo r:list){if(r.activityInfo!=null){CharSequence label=r.loadLabel(getPackageManager());b.append("• ").append(label).append(" / ").append(r.activityInfo.packageName).append(" / ").append(r.activityInfo.name).append("\n");}}
        Intent speech=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        try{
            List<ResolveInfo> speechApps=getPackageManager().queryIntentActivities(speech,PackageManager.MATCH_DEFAULT_ONLY);
            b.append("Speech recognition handlers (Android host): ").append(speechApps==null?0:speechApps.size()).append("\n");
        }catch(Exception e){b.append("Speech recognition handlers: unknown\n");}
        b.append("\nGPack exported activities (host package, not internal Google Maps):\n");
        try{
            PackageInfo info=getPackageManager().getPackageInfo(NavigationBridge.GPACK_PACKAGE,
                PackageManager.GET_ACTIVITIES);
            int exposed=0;
            if(info.activities!=null)for(ActivityInfo a:info.activities){
                if(!a.exported)continue;
                exposed++;
                if(exposed<=16)b.append(" • ").append(a.name).append("\n");
            }
            b.append("Exported activity count: ").append(exposed).append("\n");
        }catch(Exception e){b.append("Not visible from Android host\n");}
        b.append("GPack discovered package: ").append(NavigationBridge.GPACK_PACKAGE)
            .append(" / visible: ").append(NavigationBridge.hasGpack(this)).append("\n");
        b.append("หมายเหตุ: ตัวรับ intent != ยืนยันว่า Google Maps ภายใน GPack รับจุดหมายแล้ว\n");
        b.append("Fallback: opens GPack but user must launch Maps and paste/search. SHARE and VIEW are experiments; launch success alone does not prove handoff.");
        return b.toString();
    }

    private void copy(String s){
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("BYD Phone Space",s));
    }
}
