package com.pcopi.bydphonespace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RouteImagesActivity extends Activity {
    private static final int REQ_IMAGES=6201;
    private final int BG=Color.rgb(7,16,27), CARD=Color.rgb(17,31,47), TEXT=Color.rgb(242,247,252), MUTED=Color.rgb(157,177,197), CYAN=Color.rgb(77,196,255), GREEN=Color.rgb(93,219,168);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String pendingCategory=NavigationLibrary.INBOX;
    private String mode="RECENT";

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;} private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        String requested=getIntent().getStringExtra("mode");
        if(requested!=null)mode=requested;
        boolean openPicker=getIntent().getBooleanExtra("openPicker",false);
        build();
        if(openPicker){
            getIntent().removeExtra("openPicker");
            new Handler(Looper.getMainLooper()).postDelayed(this::chooseCategoryAndImport,220);
        }
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private void build(){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));sv.addView(root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(tv("รูปภาพนำทาง",23,TEXT,true));TextView sub=tv("Local Navigation Library • Offline",12.5f,MUTED,false);sub.setPadding(0,dp(2),0,0);names.addView(sub);bar.addView(names,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);

        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(0,dp(18),0,dp(14));TextView recent=tab("ล่าสุด","RECENT".equals(mode));TextView library=tab("คลังรูป","LIBRARY".equals(mode));TextView nas=tab("จาก NAS","NAS".equals(mode));recent.setOnClickListener(v->{mode="RECENT";build();});library.setOnClickListener(v->{mode="LIBRARY";build();});nas.setOnClickListener(v->{mode="NAS";build();});tabs.addView(recent,new LinearLayout.LayoutParams(0,dp(44),1));tabs.addView(library,new LinearLayout.LayoutParams(0,dp(44),1));tabs.addView(nas,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(tabs);

        if("NAS".equals(mode)){buildNas(root);setContentView(sv);return;}

        LinearLayout sourceCard=new LinearLayout(this);sourceCard.setOrientation(LinearLayout.VERTICAL);sourceCard.setPadding(dp(16),dp(15),dp(16),dp(15));sourceCard.setBackground(round(Color.rgb(12,29,45),20));
        sourceCard.addView(tv("ไฟล์ในเครื่อง / Flash Drive",16,TEXT,true));TextView sourceText=tv("ค้นหารูปจากหน่วยความจำเครื่อง, Downloads, My Files และ USB/Flash Drive ที่เสียบผ่าน OTG ได้โดยตรง",12.5f,MUTED,false);sourceText.setPadding(0,dp(5),0,dp(12));sourceCard.addView(sourceText);
        Button importBtn=button("⌕  ค้นหารูปในเครื่อง / Flash Drive");importBtn.setOnClickListener(v->chooseCategoryAndImport());sourceCard.addView(importBtn,new LinearLayout.LayoutParams(-1,dp(54)));root.addView(sourceCard);

        LinearLayout quick=new LinearLayout(this);quick.setOrientation(LinearLayout.HORIZONTAL);quick.setPadding(0,dp(10),0,0);Button inbox=darkButton("Inbox");inbox.setOnClickListener(v->{mode="LIBRARY";showCategory(NavigationLibrary.INBOX);});quick.addView(inbox,new LinearLayout.LayoutParams(0,dp(50),1));Button all=darkButton("คลังรูปทั้งหมด");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(50),1);ap.setMargins(dp(8),0,0,0);all.setOnClickListener(v->{mode="LIBRARY";build();});quick.addView(all,ap);root.addView(quick);

        List<File> files="RECENT".equals(mode)?NavigationLibrary.recent(this,12):NavigationLibrary.listAll(this);
        TextView count=tv(("RECENT".equals(mode)?"ล่าสุด":"คลังรูป")+"  •  "+files.size()+" รูป",15,MUTED,true);count.setPadding(dp(2),dp(20),0,dp(10));root.addView(count);
        if(files.isEmpty()) root.addView(empty("ยังไม่มีรูปในคลัง","กด “ค้นหารูปในเครื่อง / Flash Drive” แล้วเลือกรูปได้หลายรูป รูปจะถูกคัดลอกเข้าแอปและเปิดได้แบบ Offline")); else addGrid(root,files);

        if("LIBRARY".equals(mode)){
            TextView catTitle=tv("หมวด",15,MUTED,true);catTitle.setPadding(dp(2),dp(22),0,dp(10));root.addView(catTitle);
            root.addView(categoryRow("Inbox",NavigationLibrary.INBOX));root.addView(categoryRow("เส้นทาง",NavigationLibrary.ROUTES));root.addView(categoryRow("จุดชาร์จ",NavigationLibrary.CHARGERS));root.addView(categoryRow("สถานที่โปรด",NavigationLibrary.FAVORITES));
        }
        setContentView(sv);
    }

    private void buildNas(LinearLayout root){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(round(CARD,22));c.addView(tv("▤  NAS Explorer",20,TEXT,true));TextView s=tv("NAS ยังพักไว้ก่อนในช่วงที่เครื่องกำลังแก้ไขครับ\n\nคลังรูปในเครื่องและ Flash Drive ใช้งานได้แยกจาก NAS โดยสมบูรณ์ เมื่อ NAS กลับมาแล้วเราจะเพิ่มปุ่ม “คัดลอกเข้า รูปภาพนำทาง” ตรงนี้",13.5f,MUTED,false);s.setPadding(0,dp(10),0,dp(14));c.addView(s);Button open=button("เปิดการตั้งค่า NAS");open.setOnClickListener(v->startActivity(new Intent(this,NasConnectActivity.class)));c.addView(open,new LinearLayout.LayoutParams(-1,dp(54)));root.addView(c);}

    private void chooseCategoryAndImport(){String[] th={"Inbox","เส้นทาง","จุดชาร์จ","สถานที่โปรด"};String[] ids=NavigationLibrary.categories();new AlertDialog.Builder(this).setTitle("นำเข้าไปที่").setItems(th,(d,w)->{pendingCategory=ids[w];Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(i,REQ_IMAGES);}catch(Exception e){Toast.makeText(this,"ไม่พบตัวเลือกไฟล์ในเครื่อง",Toast.LENGTH_LONG).show();}}).setNegativeButton("ยกเลิก",null).show();}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_IMAGES||resultCode!=RESULT_OK||data==null)return;ArrayList<Uri> uris=new ArrayList<>();ClipData cd=data.getClipData();if(cd!=null){for(int i=0;i<cd.getItemCount();i++)uris.add(cd.getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;showImporting(uris);}

    private void showImporting(ArrayList<Uri> uris){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setGravity(Gravity.CENTER);b.setPadding(dp(24),dp(36),dp(24),dp(36));b.setBackground(round(CARD,20));ProgressBar p=new ProgressBar(this);b.addView(p);TextView t=tv("กำลังคัดลอก "+uris.size()+" รูปเข้า "+NavigationLibrary.thaiCategory(pendingCategory),15,TEXT,true);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(14),0,0);b.addView(t);AlertDialog dialog=new AlertDialog.Builder(this).setView(b).setCancelable(false).create();dialog.show();io.execute(()->{int ok=0;String error="";for(Uri uri:uris){try{NavigationLibrary.importImage(this,uri,pendingCategory);ok++;}catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();}}int success=ok;String err=error;runOnUiThread(()->{dialog.dismiss();Toast.makeText(this,"นำเข้าสำเร็จ "+success+" / "+uris.size()+" รูป"+(err.isEmpty()?"":"\n"+err),Toast.LENGTH_LONG).show();mode="RECENT";build();});});}

    private void addGrid(LinearLayout root,List<File> files){for(int i=0;i<files.size();i+=2){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,0,0,dp(10));row.addView(imageCard(files.get(i)),new LinearLayout.LayoutParams(0,dp(166),1));if(i+1<files.size()){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(166),1);p.setMargins(dp(10),0,0,0);row.addView(imageCard(files.get(i+1)),p);}else{View spacer=new View(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(166),1);p.setMargins(dp(10),0,0,0);row.addView(spacer,p);}root.addView(row);}}

    private LinearLayout imageCard(File f){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(7),dp(7),dp(7),dp(7));c.setBackground(round(CARD,18));ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setImageBitmap(decode(f.getAbsolutePath(),420));c.addView(iv,new LinearLayout.LayoutParams(-1,0,1));TextView n=tv(f.getName(),11.5f,TEXT,true);n.setSingleLine(true);n.setPadding(dp(3),dp(6),dp(3),0);c.addView(n);TextView cat=tv(NavigationLibrary.thaiCategory(NavigationLibrary.categoryOf(f)),10.5f,MUTED,false);cat.setPadding(dp(3),dp(1),dp(3),0);c.addView(cat);c.setOnClickListener(v->{Intent i=new Intent(this,ImageViewerActivity.class);i.putExtra(ImageViewerActivity.EXTRA_PATH,f.getAbsolutePath());startActivity(i);});return c;}

    private LinearLayout categoryRow(String label,String id){int count=NavigationLibrary.listCategory(this,id).size();LinearLayout c=new LinearLayout(this);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(16),dp(12),dp(16),dp(12));c.setBackground(round(CARD,17));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(8));c.setLayoutParams(cp);c.addView(tv(label,15,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));TextView n=tv(count+" รูป",13,CYAN,true);c.addView(n);c.setOnClickListener(v->showCategory(id));return c;}
    private void showCategory(String id){List<File> list=NavigationLibrary.listCategory(this,id);if(list.isEmpty()){Toast.makeText(this,"หมวดนี้ยังไม่มีรูป",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(this,ImageViewerActivity.class);i.putExtra(ImageViewerActivity.EXTRA_PATH,list.get(0).getAbsolutePath());startActivity(i);}
    private Bitmap decode(String path,int target){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);int s=1;while(o.outWidth/s>target*2||o.outHeight/s>target*2)s*=2;o.inJustDecodeBounds=false;o.inSampleSize=s;return BitmapFactory.decodeFile(path,o);}
    private TextView tab(String s,boolean a){TextView v=tv(s,14,a?Color.rgb(4,22,33):MUTED,true);v.setGravity(Gravity.CENTER);v.setBackground(round(a?CYAN:Color.rgb(13,27,42),18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),0,dp(3),0);v.setLayoutParams(p);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.rgb(4,22,33));b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(CYAN,18));return b;}
    private Button darkButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(TEXT);b.setBackground(round(Color.rgb(13,27,42),18));return b;}
    private LinearLayout empty(String t,String s){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(22),dp(30),dp(22),dp(30));c.setBackground(round(Color.rgb(12,25,39),22));TextView a=tv(t,17,TEXT,true);a.setGravity(Gravity.CENTER);c.addView(a);TextView b=tv(s,13,MUTED,false);b.setGravity(Gravity.CENTER);b.setPadding(dp(8),dp(8),dp(8),0);c.addView(b);return c;}
}
