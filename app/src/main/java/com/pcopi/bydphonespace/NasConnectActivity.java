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
    private final ExecutorService thumbnails=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final LinkedHashMap<String,NasSmbClient.Entry> selected=new LinkedHashMap<>();
    private final ArrayList<String> parents=new ArrayList<>();
    private final AtomicBoolean cancelImport=new AtomicBoolean(false);

    private NasSmbClient.Link link;
    private String currentUrl;
    private List<NasSmbClient.Entry> currentFiles=new ArrayList<>();
    private EditText lan,tail,user,pass;
    private Spinner mode;
    private TextView status;
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
        ScrollView scroll=new ScrollView(this);LinearLayout root=newRoot(scroll);
        header(root,"SMB2/SMB3 • LAN หรือ Tailscale • Synology Photos ไม่จำเป็น");
        NasSettings saved=NasSettings.load(this);
        LinearLayout card=panel();root.addView(card,cardParams());
        card.addView(tv("เลือกช่องทางการเชื่อมต่อ",17,TEXT,true));
        TextView subtitle=tv("อัตโนมัติจะลอง LAN ก่อน ถ้าเชื่อมต่อไม่ได้จึงลอง Tailscale",12.5f,MUTED,false);
        subtitle.setPadding(0,dp(5),0,dp(10));card.addView(subtitle);
        mode=new Spinner(this);
        String[] choices={"อัตโนมัติ (LAN → Tailscale)","LAN เท่านั้น","Tailscale เท่านั้น"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);
        mode.setAdapter(adapter);mode.setSelection(NasSettings.LAN.equals(saved.mode)?1:NasSettings.TAIL.equals(saved.mode)?2:0);
        card.addView(mode,new LinearLayout.LayoutParams(-1,dp(50)));
        card.addView(label("LAN IP / Hostname"));lan=field("เช่น 192.168.1.100",saved.lan,false);card.addView(lan,fieldParams());
        card.addView(label("Tailscale IP / MagicDNS"));tail=field("เช่น 100.x.y.z",saved.tail,false);card.addView(tail,fieldParams());
        card.addView(label("ชื่อผู้ใช้บน NAS"));user=field("Username",saved.user,false);card.addView(user,fieldParams());
        card.addView(label("รหัสผ่าน NAS (เก็บแบบเข้ารหัส)"));pass=field("Password",saved.pass,true);card.addView(pass,fieldParams());

        status=tv("○  รอทดสอบการเชื่อมต่อ",13,MUTED,true);status.setPadding(dp(5),dp(10),dp(5),dp(8));root.addView(status);
        Button connect=button("เชื่อมต่อและเปิด Shared Folder",true);connect.setOnClickListener(v->connect());root.addView(connect,buttonParams());
        TextView note=tv("ต้องใช้บัญชี NAS ที่มีสิทธิ์อ่าน Shared Folder และเปิด SMB ใน DSM เท่านั้น ไม่ต้องเปิด Synology Photos\n"
                +"หากอยู่ภายนอกบ้าน ต้องเชื่อม Tailscale บนโทรศัพท์/จอรถก่อน",12.5f,MUTED,false);
        note.setPadding(dp(4),dp(18),dp(4),0);root.addView(note);
        setContentView(scroll);
    }

    private void connect(){
        if(loading)return;
        String choice=mode.getSelectedItemPosition()==1?NasSettings.LAN:
                      mode.getSelectedItemPosition()==2?NasSettings.TAIL:NasSettings.AUTO;
        NasSettings settings=new NasSettings(lan.getText().toString(),tail.getText().toString(),
                user.getText().toString(),pass.getText().toString(),choice);
        if(settings.user.isEmpty()||settings.pass.isEmpty()){
            status.setText("กรุณากรอก Username และ Password");return;
        }
        try{settings.save(this);}catch(Exception e){
            status.setText("ไม่สามารถเข้ารหัสและบันทึกรหัสผ่าน NAS ได้: "+e.getMessage());return;
        }
        loading=true;status.setText("กำลังเชื่อม NAS ผ่าน "+(NasSettings.AUTO.equals(choice)?"LAN → Tailscale":choice)+" …");
        io.execute(()->{
            try{
                NasSmbClient.Link connected=NasSmbClient.connect(settings);
                List<NasSmbClient.Entry> rootFiles=NasSmbClient.list(connected,connected.root());
                runOnUiThread(()->{
                    loading=false;link=connected;currentUrl=connected.root();currentFiles=rootFiles;
                    parents.clear();selected.clear();displayed=0;showFiles();
                    Toast.makeText(this,"เชื่อมต่อสำเร็จผ่าน "+connected.route,Toast.LENGTH_SHORT).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{loading=false;status.setText("เชื่อมต่อไม่สำเร็จ: "+NasSmbClient.friendly(e));});
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
