package com.pcopi.bydphonespace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class NasConnectActivity extends Activity {
    private static final int BG=Color.rgb(7,16,27),CARD=Color.rgb(17,31,47),
            TEXT=Color.rgb(242,247,252),MUTED=Color.rgb(157,177,197),
            CYAN=Color.rgb(77,196,255),GREEN=Color.rgb(93,219,168);

    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnails=Executors.newFixedThreadPool(2);
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final LinkedHashMap<String,NasSmbClient.Entry> selected=new LinkedHashMap<>();
    private final ArrayList<String> parents=new ArrayList<>();
    private final AtomicBoolean cancelImport=new AtomicBoolean(false);

    private NasSmbClient.Link link;
    private String currentUrl;
    private List<NasSmbClient.Entry> currentFiles=new ArrayList<>();
    private EditText lan,tail,user,pass,profileName;
    private Spinner mode;
    private TextView status;
    private String activeProfileId="";
    private final LinkedHashMap<ImageView,NasSmbClient.Entry> thumbViews=new LinkedHashMap<>();
    private final java.util.HashSet<String> thumbQueued=new java.util.HashSet<>();
    private int thumbGeneration=0;
    private boolean importing=false,loading=false;
    private int displayed=0;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable rounded(int color,float r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private TextView tv(String text,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private LinearLayout vertical(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);return r;}
    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(15);
        b.setTextColor(primary?Color.rgb(4,22,33):TEXT);b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(primary?CYAN:Color.rgb(25,44,63),16));return b;
    }
    private LinearLayout.LayoutParams buttonParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(8),0,0);return p;}
    private LinearLayout panel(){
        LinearLayout c=vertical();c.setPadding(dp(15),dp(15),dp(15),dp(15));
        c.setBackground(rounded(CARD,19));return c;
    }
    private LinearLayout.LayoutParams cardParams(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));return p;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        showSetup();
    }
    @Override protected void onDestroy(){
        cancelImport.set(true);io.shutdownNow();thumbnails.shutdownNow();cache.evictAll();super.onDestroy();
    }
    @Override public void onBackPressed(){
        if(importing){Toast.makeText(this,"กำลังนำเข้าอยู่ กดยกเลิกในหน้าความคืบหน้าก่อน",Toast.LENGTH_SHORT).show();return;}
        if(link!=null){
            if(!parents.isEmpty()){
                currentUrl=parents.remove(parents.size()-1);loadCurrent();
            }else{link=null;currentUrl=null;selected.clear();showSetup();}
            return;
        }
        super.onBackPressed();
    }

    private void header(LinearLayout root,String subtitle){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->onBackPressed());row.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titles=vertical();titles.addView(tv("NAS → รูปภาพนำทาง",22,TEXT,true));
        titles.addView(tv(subtitle,12.5f,MUTED,false));row.addView(titles);root.addView(row);
    }
    private LinearLayout newRoot(ScrollView scroll){
        scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        LinearLayout root=vertical();root.setBackgroundColor(BG);
        root.setPadding(dp(18),dp(16),dp(18),dp(30));scroll.addView(root);return root;
    }
    private TextView label(String value){TextView t=tv(value,13,MUTED,true);t.setPadding(dp(4),dp(11),0,dp(5));return t;}
    private EditText field(String hint,String text,boolean secret){
        EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);
        e.setText(text);e.setTextColor(TEXT);e.setTextSize(15);e.setSingleLine(true);
        e.setPadding(dp(14),0,dp(14),0);e.setBackground(rounded(CARD,14));
        if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }
    private LinearLayout.LayoutParams fieldParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));p.setMargins(0,0,0,dp(3));return p;}


    private void showSetup(){
        thumbGeneration++;thumbViews.clear();thumbQueued.clear();
        link=null;currentUrl=null;parents.clear();
        ScrollView scroll=new ScrollView(this);LinearLayout root=newRoot(scroll);
        header(root,"บัญชีที่บันทึกไว้ • SMB2/SMB3 • อัตโนมัติ LAN → Tailscale");
        List<NasProfileStore.Profile> profiles;
        try{profiles=NasProfileStore.load(this);}
        catch(Exception e){
            TextView error=tv("อ่านโปรไฟล์ที่เข้ารหัสไม่ได้: "+e.getMessage(),14,Color.rgb(255,160,140),false);
            error.setPadding(dp(12),dp(16),dp(12),dp(12));root.addView(error);setContentView(scroll);return;
        }
        LinearLayout intro=panel();intro.addView(tv("เลือกผู้ใช้ NAS",19,TEXT,true));
        TextView note=tv("กด “เชื่อมต่อ” จากโปรไฟล์ที่บันทึกไว้ได้ทันที ไม่แสดงชื่อผู้ใช้หรือรหัสผ่านทุกครั้ง\n"
                +"บัญชีแต่ละชุดมี LAN / Tailscale / โหมดอัตโนมัติของตนเอง",13,MUTED,false);
        note.setPadding(0,dp(8),0,0);intro.addView(note);root.addView(intro,cardParams());
        if(profiles.isEmpty()){
            TextView empty=tv("ยังไม่มีผู้ใช้ที่บันทึกไว้ กด “＋ เพิ่มผู้ใช้ NAS” เพื่อเริ่มต้น",14,MUTED,false);
            empty.setPadding(dp(8),dp(20),dp(8),dp(15));root.addView(empty);
        }
        final String last=NasProfileStore.last(this);
        for(NasProfileStore.Profile profile:profiles){
            LinearLayout box=panel();root.addView(box,cardParams());
            String title=profile.label+(last.equals(profile.id)?"  ★ ล่าสุด":"");
            box.addView(tv(title,18,TEXT,true));
            String modeLabel=NasSettings.LAN.equals(profile.settings.mode)?"LAN เท่านั้น":
                    NasSettings.TAIL.equals(profile.settings.mode)?"Tailscale เท่านั้น":"อัตโนมัติ (LAN → Tailscale)";
            TextView sub=tv("SMB • "+modeLabel+"  •  เก็บข้อมูลเข้าสู่ระบบแบบเข้ารหัส",12.5f,MUTED,false);
            sub.setPadding(0,dp(5),0,dp(8));box.addView(sub);
            Button connect=button("เชื่อมต่อ NAS",true);
            connect.setOnClickListener(v->connect(profile));box.addView(connect,buttonParams());
            LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);
            Button edit=button("✎ แก้ไข",false);
            Button delete=button("ลบโปรไฟล์",false);
            LinearLayout.LayoutParams e=new LinearLayout.LayoutParams(0,dp(48),1);e.setMargins(0,dp(7),dp(5),0);
            LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,dp(48),1);d.setMargins(dp(5),dp(7),0,0);
            bar.addView(edit,e);bar.addView(delete,d);box.addView(bar);
            edit.setOnClickListener(v->showProfileEditor(profile));
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("ลบโปรไฟล์ "+profile.label+"?")
                    .setMessage("ลบเฉพาะค่าการเชื่อมต่อและรหัสผ่านที่เก็บไว้ในโทรศัพท์ ไม่ลบข้อมูลใน NAS หรือคลังรูป")
                    .setNegativeButton("ยกเลิก",null)
                    .setPositiveButton("ลบ",(dia,w)->{
                        try{
                            List<NasProfileStore.Profile> updated=NasProfileStore.load(this);
                            updated.removeIf(x->x.id.equals(profile.id));
                            NasProfileStore.save(this,updated);
                            if(NasProfileStore.last(this).equals(profile.id))NasProfileStore.setLast(this,"");
                            showSetup();
                        }catch(Exception ex){Toast.makeText(this,"ลบโปรไฟล์ไม่สำเร็จ: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
                    }).show());
        }
        Button add=button("＋ เพิ่มผู้ใช้ NAS",true);add.setOnClickListener(v->showProfileEditor(null));
        root.addView(add,buttonParams());
        TextView help=tv("แนะนำให้ทดสอบ LAN บนโทรศัพท์ก่อน จากนั้นเลือก AUTO สำหรับเชื่อม LAN และ Tailscale โดยไม่ต้องเปลี่ยนบัญชี\n"
                +"ไม่ต้องเปิด Synology Photos • ห้ามเปิดพอร์ต SMB 445 สู่ Internet สาธารณะ",12.5f,MUTED,false);
        help.setPadding(dp(6),dp(20),dp(6),0);root.addView(help);
        setContentView(scroll);
    }

    private void showProfileEditor(NasProfileStore.Profile old){
        ScrollView scroll=new ScrollView(this);LinearLayout root=newRoot(scroll);
        header(root,old==null?"เพิ่มบัญชีใหม่":"แก้ไขบัญชี • แสดงข้อมูลเฉพาะหน้านี้");
        LinearLayout card=panel();root.addView(card,cardParams());
        NasSettings settings=old==null?new NasSettings("","","","",NasSettings.AUTO):old.settings;
        card.addView(label("ชื่อโปรไฟล์ (แสดงบนหน้าแรก)"));
        profileName=field("เช่น บัญชีบ้าน / Family",old==null?"":old.label,false);card.addView(profileName,fieldParams());
        card.addView(label("เส้นทางเชื่อมต่อ"));
        mode=new Spinner(this);
        String[] choices={"อัตโนมัติ (LAN → Tailscale)","LAN เท่านั้น","Tailscale เท่านั้น"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);
        mode.setAdapter(adapter);mode.setSelection(NasSettings.LAN.equals(settings.mode)?1:NasSettings.TAIL.equals(settings.mode)?2:0);
        card.addView(mode,new LinearLayout.LayoutParams(-1,dp(50)));
        card.addView(label("LAN IP / Hostname"));lan=field("เช่น 192.168.1.100",settings.lan,false);card.addView(lan,fieldParams());
        card.addView(label("Tailscale IP / MagicDNS"));tail=field("เช่น 100.x.y.z",settings.tail,false);card.addView(tail,fieldParams());
        card.addView(label("Username"));user=field("ชื่อผู้ใช้ Synology",settings.user,false);card.addView(user,fieldParams());
        card.addView(label("Password (ปิดบังอักขระ)"));pass=field("รหัสผ่าน Synology",settings.pass,true);card.addView(pass,fieldParams());
        Button save=button("บันทึกโปรไฟล์ (เข้ารหัส)",true);root.addView(save,buttonParams());
        save.setOnClickListener(v->{
            String label=profileName.getText().toString().trim();
            String modeValue=mode.getSelectedItemPosition()==1?NasSettings.LAN:
                    mode.getSelectedItemPosition()==2?NasSettings.TAIL:NasSettings.AUTO;
            NasSettings s=new NasSettings(lan.getText().toString(),tail.getText().toString(),
                    user.getText().toString(),pass.getText().toString(),modeValue);
            if(label.isEmpty()){profileName.setError("กรุณาตั้งชื่อโปรไฟล์");return;}
            if(s.user.isEmpty()){user.setError("กรุณากรอก Username");return;}
            if(s.pass.isEmpty()){pass.setError("กรุณากรอก Password");return;}
            if(s.lan.isEmpty()&&s.tail.isEmpty()){lan.setError("กรุณากรอก LAN หรือ Tailscale");return;}
            try{
                List<NasProfileStore.Profile> list=NasProfileStore.load(this);
                String id=old==null?NasProfileStore.newId():old.id;
                NasProfileStore.Profile saved=new NasProfileStore.Profile(id,label,s);
                if(old!=null)list.removeIf(x->x.id.equals(id));
                list.add(saved);NasProfileStore.save(this,list);
                Toast.makeText(this,"บันทึกผู้ใช้แล้ว",Toast.LENGTH_SHORT).show();showSetup();
            }catch(Exception e){Toast.makeText(this,"บันทึกไม่สำเร็จ: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        });
        Button cancel=button("กลับไปเลือกรายชื่อผู้ใช้",false);
        cancel.setOnClickListener(v->showSetup());root.addView(cancel,buttonParams());
        setContentView(scroll);
    }

    private void connect(NasProfileStore.Profile profile){
        if(loading)return;
        NasSettings settings=profile.settings;
        if(settings.user.isEmpty()||settings.pass.isEmpty()){
            Toast.makeText(this,"ไม่มี Username/Password กรุณาแก้ไขโปรไฟล์",Toast.LENGTH_LONG).show();return;
        }
        loading=true;activeProfileId=profile.id;
        final android.app.ProgressDialog spinner=new android.app.ProgressDialog(this);
        spinner.setMessage("กำลังเชื่อม "+profile.label+" • "+(
            NasSettings.AUTO.equals(settings.mode)?"LAN → Tailscale":settings.mode));
        spinner.setCancelable(false);spinner.show();
        io.execute(()->{
            try{
                NasSmbClient.Link connected=NasSmbClient.connect(settings);
                List<NasSmbClient.Entry> files=NasSmbClient.list(connected,connected.root());
                runOnUiThread(()->{
                    loading=false;spinner.dismiss();
                    link=connected;currentUrl=connected.root();currentFiles=files;
                    parents.clear();selected.clear();displayed=0;
                    NasProfileStore.setLast(this,profile.id);
                    showFiles();
                    Toast.makeText(this,"เชื่อมต่อ "+connected.route+" สำเร็จ",Toast.LENGTH_SHORT).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    loading=false;spinner.dismiss();
                    new AlertDialog.Builder(this).setTitle("เชื่อมต่อ NAS ไม่สำเร็จ")
                        .setMessage(NasSmbClient.friendly(e))
                        .setPositiveButton("ตกลง",null).show();
                });
            }
        });
    }

    private void loadCurrent(){
        if(link==null||loading)return;
        loading=true;
        io.execute(()->{
            try{
                List<NasSmbClient.Entry> files=NasSmbClient.list(link,currentUrl);
                runOnUiThread(()->{loading=false;currentFiles=files;displayed=0;showFiles();});
            }catch(Exception e){
                runOnUiThread(()->{loading=false;Toast.makeText(this,"อ่านโฟลเดอร์ไม่สำเร็จ: "+NasSmbClient.friendly(e),Toast.LENGTH_LONG).show();showFiles();});
            }
        });
    }

    private void enterFolder(NasSmbClient.Entry folder){
        if(loading)return;
        parents.add(currentUrl);currentUrl=folder.url;loadCurrent();
    }

    private void showFiles(){
        if(link==null){showSetup();return;}
        ScrollView scroll=new ScrollView(this);LinearLayout root=newRoot(scroll);
        header(root,"● เชื่อมต่อ "+link.route+" • "+link.host);
        LinearLayout top=panel();root.addView(top,cardParams());
        TextView path=tv(currentUrl==null?"/":currentUrl.replace(link.root(),"/"),14,TEXT,true);path.setTextIsSelectable(true);top.addView(path);
        TextView statusLine=tv("เลือกได้ทั้งไฟล์และโฟลเดอร์ • "+selected.size()+" รายการที่เลือก",12.5f,MUTED,false);
        statusLine.setPadding(0,dp(7),0,dp(7));top.addView(statusLine);
        Button importNow=button("↓ นำเข้ารูปที่เลือก ("+selected.size()+")",true);importNow.setEnabled(!selected.isEmpty());
        importNow.setOnClickListener(v->chooseImportCategory());top.addView(importNow,buttonParams());
        selectedCountView=statusLine;selectedImportButton=importNow;
        Button switchHost=button("เปลี่ยนการเชื่อมต่อ / LAN / Tailscale",false);
        switchHost.setOnClickListener(v->{link=null;selected.clear();parents.clear();showSetup();});top.addView(switchHost,buttonParams());
        if(!parents.isEmpty()){
            Button up=button("‹ กลับโฟลเดอร์ก่อนหน้า",false);
            up.setOnClickListener(v->onBackPressed());root.addView(up,buttonParams());
        }

        if(currentFiles.isEmpty()){
            TextView empty=tv("โฟลเดอร์นี้ไม่มีรายการ หรือบัญชีไม่มีสิทธิ์มองเห็น",14,MUTED,false);
            empty.setPadding(dp(10),dp(24),dp(10),dp(12));root.addView(empty);
        }else{
            TextView total=tv("รายการในโฟลเดอร์ • "+currentFiles.size(),15,TEXT,true);
            total.setPadding(dp(3),dp(18),0,dp(8));root.addView(total);
            LinearLayout items=vertical();root.addView(items);
            renderBatch(items);
        }
        setContentView(scroll);
    }

    private void renderBatch(LinearLayout items){
        int end=Math.min(currentFiles.size(),displayed+50);
        for(int i=displayed;i<end;i++)addFileRow(items,currentFiles.get(i));
        displayed=end;
        if(displayed<currentFiles.size()){
            Button more=button("แสดงเพิ่มเติมอีก "+Math.min(50,currentFiles.size()-displayed)+" รายการ",false);
            items.addView(more,buttonParams());
            more.setOnClickListener(v->{items.removeView(more);renderBatch(items);});
        }
    }
    private void addFileRow(LinearLayout root,NasSmbClient.Entry entry){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),dp(9),dp(8),dp(9));row.setBackground(rounded(CARD,15));
        root.addView(row,cardParams());
        if(entry.directory){
            TextView icon=tv("📁",25,TEXT,false);icon.setGravity(Gravity.CENTER);
            row.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(58)));
        }else if(NasSmbClient.isImage(entry.name)){
            ImageView thumbnail=new ImageView(this);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackground(rounded(Color.rgb(30,51,69),12));
            row.addView(thumbnail,new LinearLayout.LayoutParams(dp(50),dp(50)));
            Bitmap hit=cache.get(entry.url);
            if(hit!=null)thumbnail.setImageBitmap(hit);else loadThumb(thumbnail,entry);
        }else{
            TextView icon=tv("▤",23,MUTED,false);icon.setGravity(Gravity.CENTER);
            row.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(58)));
        }
        LinearLayout body=vertical();body.setPadding(dp(8),0,dp(5),0);
        TextView name=tv(entry.name,14.5f,TEXT,true);name.setMaxLines(2);body.addView(name);
        String extra=entry.directory?"แตะเพื่อเปิดโฟลเดอร์":formatSize(entry.size)+(NasSmbClient.isImage(entry.name)?" • รูปภาพ":" • ไม่ใช่รูป");
        body.addView(tv(extra,11.5f,MUTED,false));
        row.addView(body,new LinearLayout.LayoutParams(0,-2,1));
        if(entry.directory||NasSmbClient.isImage(entry.name)){
            CheckBox check=new CheckBox(this);check.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
            check.setChecked(selected.containsKey(entry.url));row.addView(check,new LinearLayout.LayoutParams(dp(42),dp(50)));
            row.setOnClickListener(v->{if(entry.directory)enterFolder(entry);else check.setChecked(!check.isChecked());});
            // Updates the count/action bar without changing folder position.
            check.setOnCheckedChangeListener((v,checked)->{
                if(checked)selected.put(entry.url,entry);else selected.remove(entry.url);
                showSelectionCount();
            });
        }
    }
    private TextView selectedCountView;private Button selectedImportButton;
    private void showSelectionCount(){
        if(selectedCountView!=null)selectedCountView.setText("เลือกได้ทั้งไฟล์และโฟลเดอร์ • "+selected.size()+" รายการที่เลือก");
        if(selectedImportButton!=null){selectedImportButton.setText("↓ นำเข้ารูปที่เลือก ("+selected.size()+")");selectedImportButton.setEnabled(!selected.isEmpty());}
    }

    private void loadThumb(ImageView image,NasSmbClient.Entry entry){
        if(entry.size>12L*1024*1024||entry.size<1)return;
        image.setTag(entry.url);
        thumbnails.execute(()->{
            try(InputStream stream=NasSmbClient.open(link,entry);
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[64*1024];int count;long total=0;
                while((count=stream.read(buf))>0){
                    total+=count;if(total>12L*1024*1024)return;
                    out.write(buf,0,count);
                }
                byte[] data=out.toByteArray();
                BitmapFactory.Options opts=new BitmapFactory.Options();
                opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,opts);
                int scale=1;
                while(opts.outWidth/scale>220||opts.outHeight/scale>220)scale*=2;
                opts.inJustDecodeBounds=false;opts.inSampleSize=scale;
                Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts);
                if(bitmap!=null){cache.put(entry.url,bitmap);runOnUiThread(()->{
                    if(entry.url.equals(image.getTag()))image.setImageBitmap(bitmap);
                });}
            }catch(Exception ignored){}
        });
    }

    private void chooseImportCategory(){
        if(selected.isEmpty()){Toast.makeText(this,"เลือกไฟล์หรือโฟลเดอร์ก่อน",Toast.LENGTH_SHORT).show();return;}
        String[] names={"Inbox","เส้นทาง","จุดชาร์จ","สถานที่โปรด"};
        String[] categories=NavigationLibrary.categories();
        new AlertDialog.Builder(this).setTitle("นำเข้าจาก NAS ไปยังหมวด")
                .setItems(names,(dialog,which)->{
                    String category=categories[which];
                    new AlertDialog.Builder(this).setTitle("ยืนยันการคัดลอก")
                        .setMessage("จะคัดลอกเฉพาะไฟล์ภาพจาก "+selected.size()+" รายการที่เลือก เข้าหมวด “"+names[which]+"”\n"
                            +"หากเลือกทั้งโฟลเดอร์จะอ่านโฟลเดอร์ย่อยด้วย (ไม่ลบหรือย้ายไฟล์ต้นฉบับใน NAS)")
                        .setNegativeButton("ยกเลิก",null)
                        .setPositiveButton("เริ่มนำเข้า",(d,w)->importSelection(category)).show();
                }).setNegativeButton("ยกเลิก",null).show();
    }

    private void importSelection(String category){
        if(importing)return;
        importing=true;cancelImport.set(false);
        ArrayList<NasSmbClient.Entry> sources=new ArrayList<>(selected.values());
        final SharedPreferences ledger=getSharedPreferences("nas_photo_import_history",MODE_PRIVATE);
        LinearLayout body=vertical();body.setPadding(dp(24),dp(20),dp(24),dp(15));
        ProgressBar spin=new ProgressBar(this);body.addView(spin);
        TextView progress=tv("กำลังเตรียมนำเข้ารูป...",14,TEXT,true);
        progress.setPadding(0,dp(13),0,dp(10));progress.setGravity(Gravity.CENTER);body.addView(progress);
        Button cancel=button("ยกเลิกการนำเข้า",false);body.addView(cancel,buttonParams());
        AlertDialog dialog=new AlertDialog.Builder(this).setView(body).setCancelable(false).create();
        cancel.setOnClickListener(v->{cancelImport.set(true);progress.setText("กำลังยกเลิก หลังอ่านไฟล์ปัจจุบัน...");});
        dialog.show();
        io.execute(()->{
            final int[] imported={0},skipped={0},scanned={0};
            final String[] lastError={""};
            final java.util.HashSet<String> seen=new java.util.HashSet<>();
            boolean stopped=false;
            try{
                for(NasSmbClient.Entry item:sources){
                    if(cancelImport.get())break;
                    NasSmbClient.walkImages(link,item,cancelImport,file->{
                        if(!seen.add(file.url))return;
                        String key=fingerprint(file.url+"|"+file.size+"|"+file.modified+"|"+category);
                        String existing=ledger.getString(key,"");
                        if(!existing.isEmpty()&&new File(existing).isFile()&&new File(existing).length()==file.size){
                            skipped[0]++;return;
                        }
                        runOnUiThread(()->progress.setText("กำลังนำเข้า: "+file.name+"\nสำเร็จ "+imported[0]+" • ข้ามไฟล์เดิม "+skipped[0]));
                        try(InputStream stream=NasSmbClient.open(link,file)){
                            File copied=NavigationLibrary.importNasImage(this,file.name,stream,category,file.size,cancelImport);
                            ledger.edit().putString(key,copied.getAbsolutePath()).apply();
                            imported[0]++;
                        }
                    },scanned);
                }
            }catch(InterruptedException e){stopped=true;}
            catch(Exception e){lastError[0]=NasSmbClient.friendly(e);}
            boolean wasStopped=stopped||cancelImport.get();
            runOnUiThread(()->{
                importing=false;dialog.dismiss();selected.clear();showFiles();
                String summary="นำเข้าสำเร็จ "+imported[0]+" รูป\nข้ามไฟล์ที่นำเข้าแล้ว "+skipped[0]+" รูป"
                        +(wasStopped?"\nผู้ใช้ยกเลิกการทำงาน":"")
                        +(!lastError[0].isEmpty()?"\nข้อผิดพลาด: "+lastError[0]:"");
                new AlertDialog.Builder(this).setTitle("ผลการนำเข้าจาก NAS")
                    .setMessage(summary).setNegativeButton("อยู่หน้า NAS",null)
                    .setPositiveButton("เปิดคลังรูป",(d,w)->{
                        Intent i=new Intent(this,RouteImagesActivity.class);
                        i.putExtra("mode","RECENT");startActivity(i);
                    }).show();
            });
        });
    }

    private String fingerprint(String s)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder();for(byte v:hash)b.append(String.format(Locale.US,"%02x",v&255));return b.toString();
    }
    private String formatSize(long size){
        if(size<1024)return size+" B";
        if(size<1024*1024)return String.format(Locale.US,"%.1f KB",size/1024.0);
        return String.format(Locale.US,"%.1f MB",size/(1024.0*1024.0));
    }
}
