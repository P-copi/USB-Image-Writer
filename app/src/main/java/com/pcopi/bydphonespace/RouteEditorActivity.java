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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RouteEditorActivity extends Activity {
    public static final String EXTRA_ROUTE_ID="route_id";
    private String routeId;
    private final int BG=Color.rgb(7,16,27), CARD=Color.rgb(17,31,47), TEXT=Color.rgb(242,247,252), MUTED=Color.rgb(157,177,197), CYAN=Color.rgb(77,196,255), RED=Color.rgb(255,105,105);

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        routeId=getIntent().getStringExtra(EXTRA_ROUTE_ID);
        if(routeId==null){finish();return;}
        build();
    }

    @Override protected void onResume(){super.onResume();if(!isFinishing())build();}

    private void build(){
        RouteStore.Route r=RouteStore.get(this,routeId);
        if(r==null){Toast.makeText(this,"ไม่พบเส้นทาง",Toast.LENGTH_SHORT).show();finish();return;}
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));root.setBackgroundColor(BG);sv.addView(root);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(tv("จัดการเส้นทาง",23,TEXT,true));TextView sub=tv(r.name,13,MUTED,false);sub.setSingleLine(true);sub.setPadding(0,dp(2),0,0);names.addView(sub);top.addView(names,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(16),dp(14),dp(16),dp(14));hero.setBackground(round(Color.rgb(12,29,45),20));
        hero.addView(tv("เรียงลำดับรูปตามการเดินทาง",16,TEXT,true));TextView hint=tv("ใช้ ↑ / ↓ เพื่อเลื่อนรูป • เพิ่มรูปจากคลังได้ • เอาออกจากเส้นทางโดยไม่ลบรูปต้นฉบับ",12.5f,MUTED,false);hint.setPadding(0,dp(5),0,dp(12));hero.addView(hint);
        Button add=primary("＋  เพิ่มรูปจากคลัง");add.setOnClickListener(v->addImages());hero.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));root.addView(hero);

        List<File> files=RouteStore.files(this,routeId);
        TextView count=tv("ลำดับรูป  •  "+files.size()+" รูป",15,MUTED,true);count.setPadding(dp(2),dp(20),0,dp(10));root.addView(count);
        if(files.isEmpty())root.addView(empty("เส้นทางนี้ยังไม่มีรูป","กด “เพิ่มรูปจากคลัง” เพื่อเพิ่มรูปเข้ามา"));
        else for(int i=0;i<files.size();i++)root.addView(row(files,i));

        setContentView(sv);
    }

    private LinearLayout row(List<File> files,int index){
        File f=files.get(index);
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setBackground(round(CARD,18));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(9));card.setLayoutParams(cp);

        TextView no=tv(String.valueOf(index+1),15,CYAN,true);no.setGravity(Gravity.CENTER);no.setBackground(round(Color.rgb(11,43,66),15));card.addView(no,new LinearLayout.LayoutParams(dp(38),dp(38)));

        ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setImageBitmap(decode(f.getAbsolutePath(),220));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(68),dp(68));ip.setMargins(dp(9),0,dp(9),0);card.addView(iv,ip);

        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView name=tv(f.getName(),14,TEXT,true);name.setSingleLine(true);text.addView(name);TextView cat=tv(NavigationLibrary.thaiCategory(NavigationLibrary.categoryOf(f)),11.5f,MUTED,false);cat.setPadding(0,dp(4),0,0);text.addView(cat);card.addView(text,new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);
        Button up=mini("↑");up.setEnabled(index>0);up.setOnClickListener(v->move(index,-1));controls.addView(up,new LinearLayout.LayoutParams(dp(44),dp(36)));
        Button down=mini("↓");down.setEnabled(index<files.size()-1);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(44),dp(36));dpv.setMargins(0,dp(4),0,0);down.setOnClickListener(v->move(index,+1));controls.addView(down,dpv);
        Button remove=mini("×");remove.setTextColor(RED);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(44),dp(36));rp.setMargins(dp(5),0,0,0);remove.setOnClickListener(v->remove(index));
        LinearLayout right=new LinearLayout(this);right.setGravity(Gravity.CENTER_VERTICAL);right.addView(controls);right.addView(remove,rp);card.addView(right);
        return card;
    }

    private void move(int index,int delta){
        ArrayList<File> files=new ArrayList<>(RouteStore.files(this,routeId));int target=index+delta;if(index<0||index>=files.size()||target<0||target>=files.size())return;File a=files.get(index);files.set(index,files.get(target));files.set(target,a);RouteStore.replaceFiles(this,routeId,files);build();
    }

    private void remove(int index){
        ArrayList<File> files=new ArrayList<>(RouteStore.files(this,routeId));if(index<0||index>=files.size())return;File f=files.get(index);
        if(files.size()==1){Toast.makeText(this,"เส้นทางควรมีอย่างน้อย 1 รูป",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("เอารูปออกจากเส้นทาง?").setMessage(f.getName()+"\n\nรูปต้นฉบับยังอยู่ในคลัง ไม่ถูกลบ").setNegativeButton("ยกเลิก",null).setPositiveButton("เอาออก",(d,w)->{files.remove(index);RouteStore.replaceFiles(this,routeId,files);build();}).show();
    }

    private void addImages(){
        List<File> all=NavigationLibrary.listAll(this);List<File> current=RouteStore.files(this,routeId);ArrayList<File> candidates=new ArrayList<>();
        for(File f:all){boolean exists=false;for(File c:current)if(c.getAbsolutePath().equals(f.getAbsolutePath())){exists=true;break;}if(!exists)candidates.add(f);}
        if(candidates.isEmpty()){Toast.makeText(this,"รูปทั้งหมดในคลังอยู่ในเส้นทางนี้แล้ว",Toast.LENGTH_SHORT).show();return;}
        CharSequence[] labels=new CharSequence[candidates.size()];boolean[] checked=new boolean[candidates.size()];for(int i=0;i<candidates.size();i++)labels[i]=candidates.get(i).getName()+"  •  "+NavigationLibrary.thaiCategory(NavigationLibrary.categoryOf(candidates.get(i)));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("เพิ่มรูปจากคลัง").setMultiChoiceItems(labels,checked,(dialog,which,isChecked)->checked[which]=isChecked).setNegativeButton("ยกเลิก",null).setPositiveButton("เพิ่ม",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ArrayList<File> merged=new ArrayList<>(current);int added=0;for(int i=0;i<checked.length;i++)if(checked[i]){merged.add(candidates.get(i));added++;}if(added==0){Toast.makeText(this,"กรุณาเลือกรูป",Toast.LENGTH_SHORT).show();return;}RouteStore.replaceFiles(this,routeId,merged);d.dismiss();Toast.makeText(this,"เพิ่ม "+added+" รูปแล้ว",Toast.LENGTH_SHORT).show();build();}));d.show();
    }

    private Bitmap decode(String path,int target){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);int s=1;while(o.outWidth/s>target*2||o.outHeight/s>target*2)s*=2;o.inJustDecodeBounds=false;o.inSampleSize=s;return BitmapFactory.decodeFile(path,o);}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14.5f);b.setTextColor(Color.rgb(4,22,33));b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(CYAN,17));return b;}
    private Button mini(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(17);b.setTextColor(TEXT);b.setBackground(round(Color.rgb(13,27,42),12));b.setPadding(0,0,0,0);return b;}
    private LinearLayout empty(String t,String s){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(22),dp(30),dp(22),dp(30));c.setBackground(round(Color.rgb(12,25,39),22));TextView a=tv(t,17,TEXT,true);a.setGravity(Gravity.CENTER);c.addView(a);TextView b=tv(s,13,MUTED,false);b.setGravity(Gravity.CENTER);b.setPadding(dp(8),dp(8),dp(8),0);c.addView(b);return c;}
}
