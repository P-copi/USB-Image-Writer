package com.pcopi.bydphonespace;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

public class ImageViewerActivity extends Activity {
    public static final String EXTRA_PATH = "path";
    private ZoomImageView image;
    private TextView title;
    private List<File> files;
    private int index;

    private final int BG=Color.rgb(3,8,14), TEXT=Color.rgb(242,247,252), MUTED=Color.rgb(157,177,197), CYAN=Color.rgb(77,196,255);
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        files=NavigationLibrary.listAll(this); String path=getIntent().getStringExtra(EXTRA_PATH); index=findIndex(path); build(); showCurrent();
    }

    private int findIndex(String path){ if(path==null)return 0; for(int i=0;i<files.size();i++) if(path.equals(files.get(i).getAbsolutePath())) return i; return 0; }

    private void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG); root.setPadding(dp(10),dp(10),dp(10),dp(10));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); Button close=small("←"); close.setOnClickListener(v->finish()); top.addView(close,new LinearLayout.LayoutParams(dp(56),dp(48))); title=text("",16,TEXT,true); title.setSingleLine(true); top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(top);
        image=new ZoomImageView(this); image.setBackgroundColor(Color.BLACK); root.addView(image,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout tools=new LinearLayout(this); tools.setPadding(0,dp(8),0,0);
        Button reset=small("รีเซ็ตซูม"); reset.setOnClickListener(v->image.resetZoom()); tools.addView(reset,new LinearLayout.LayoutParams(0,dp(46),1));
        Button move=small("ย้ายหมวด"); LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(46),1); mp.setMargins(dp(8),0,0,0); move.setOnClickListener(v->moveCurrent()); tools.addView(move,mp);
        Button del=small("ลบ"); LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(0,dp(46),1); dpv.setMargins(dp(8),0,0,0); del.setOnClickListener(v->deleteCurrent()); tools.addView(del,dpv); root.addView(tools);

        LinearLayout nav=new LinearLayout(this); nav.setPadding(0,dp(8),0,0); Button prev=small("‹  ก่อนหน้า"); Button next=small("ถัดไป  ›"); prev.setOnClickListener(v->{if(files.isEmpty())return;index=(index-1+files.size())%files.size();showCurrent();}); next.setOnClickListener(v->{if(files.isEmpty())return;index=(index+1)%files.size();showCurrent();}); nav.addView(prev,new LinearLayout.LayoutParams(0,dp(52),1)); LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(52),1);np.setMargins(dp(8),0,0,0);nav.addView(next,np); root.addView(nav); setContentView(root);
    }

    private File current(){ if(files==null||files.isEmpty()) return null; return files.get(Math.max(0,Math.min(index,files.size()-1))); }

    private void showCurrent(){
        File f=current(); if(f==null){title.setText("ไม่มีรูป");image.setImageDrawable(null);return;} title.setText(f.getName()+"  •  "+NavigationLibrary.thaiCategory(NavigationLibrary.categoryOf(f))); image.setImageBitmap(decode(f.getAbsolutePath(),2200)); image.post(image::resetZoom);
    }

    private void moveCurrent(){
        File f=current(); if(f==null)return;
        String[] th={"Inbox","เส้นทาง","จุดชาร์จ","สถานที่โปรด"}; String[] ids=NavigationLibrary.categories();
        new AlertDialog.Builder(this).setTitle("ย้ายรูปไปที่").setItems(th,(d,w)->{
            try{
                File moved=NavigationLibrary.moveToCategory(this,f,ids[w]);
                files=NavigationLibrary.listAll(this); index=findIndex(moved.getAbsolutePath()); showCurrent();
                Toast.makeText(this,"ย้ายไป "+NavigationLibrary.thaiCategory(ids[w])+" แล้ว",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"ย้ายไม่สำเร็จ: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }).setNegativeButton("ยกเลิก",null).show();
    }

    private void deleteCurrent(){
        File f=current(); if(f==null)return;
        new AlertDialog.Builder(this).setTitle("ลบรูปนี้?").setMessage(f.getName()+"\n\nไฟล์จะถูกลบออกจากคลังรูปออฟไลน์ของ BYD Phone Space").setNegativeButton("ยกเลิก",null).setPositiveButton("ลบ",(d,w)->{
            if(NavigationLibrary.delete(f)){
                files=NavigationLibrary.listAll(this); if(index>=files.size())index=Math.max(0,files.size()-1); showCurrent(); Toast.makeText(this,"ลบแล้ว",Toast.LENGTH_SHORT).show();
            }else Toast.makeText(this,"ลบไม่สำเร็จ",Toast.LENGTH_SHORT).show();
        }).show();
    }

    private Bitmap decode(String path,int target){ BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);int sample=1;while(o.outWidth/sample>target*2||o.outHeight/sample>target*2)sample*=2;o.inJustDecodeBounds=false;o.inSampleSize=sample;return BitmapFactory.decodeFile(path,o); }
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setGravity(Gravity.CENTER_VERTICAL);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));v.setPadding(dp(10),0,dp(10),0);return v;}
    private Button small(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(TEXT);b.setBackground(round(Color.rgb(15,28,43),16));return b;}
}
