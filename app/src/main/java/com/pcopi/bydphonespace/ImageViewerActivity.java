package com.pcopi.bydphonespace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

public class ImageViewerActivity extends Activity {
    public static final String EXTRA_PATH = "path";

    private ZoomImageView image;
    private TextView title;
    private TextView counter;
    private TextView targetInfo;
    private Button navigateButton;
    private List<File> files;
    private int index;
    private boolean animating;

    private final int BG=Color.rgb(3,8,14), TEXT=Color.rgb(242,247,252), MUTED=Color.rgb(157,177,197), CYAN=Color.rgb(77,196,255), GREEN=Color.rgb(93,219,168);
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} 
    private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        files=NavigationLibrary.listAll(this);
        String path=getIntent().getStringExtra(EXTRA_PATH);
        index=findIndex(path);
        build();
        showCurrent();
    }

    private int findIndex(String path){
        if(path==null)return 0;
        for(int i=0;i<files.size();i++) if(path.equals(files.get(i).getAbsolutePath())) return i;
        return 0;
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(10),dp(10),dp(10),dp(10));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button close=small("←");
        close.setOnClickListener(v->finish());
        top.addView(close,new LinearLayout.LayoutParams(dp(56),dp(48)));

        title=text("",16,TEXT,true);
        title.setSingleLine(true);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));

        counter=text("",13,CYAN,true);
        counter.setGravity(Gravity.CENTER);
        top.addView(counter,new LinearLayout.LayoutParams(dp(64),dp(48)));
        root.addView(top);

        image=new ZoomImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setSwipeListener(new ZoomImageView.SwipeListener() {
            @Override public void onSwipeLeft() { swipeTo(+1); }
            @Override public void onSwipeRight() { swipeTo(-1); }
        });
        root.addView(image,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout tools=new LinearLayout(this);
        tools.setPadding(0,dp(8),0,0);
        Button reset=small("รีเซ็ตซูม");
        reset.setOnClickListener(v->image.resetZoom());
        tools.addView(reset,new LinearLayout.LayoutParams(0,dp(46),1));

        Button move=small("ย้ายหมวด");
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(46),1);
        mp.setMargins(dp(8),0,0,0);
        move.setOnClickListener(v->moveCurrent());
        tools.addView(move,mp);

        Button del=small("ลบ");
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(0,dp(46),1);
        dpv.setMargins(dp(8),0,0,0);
        del.setOnClickListener(v->deleteCurrent());
        tools.addView(del,dpv);
        root.addView(tools);

        targetInfo=text("ยังไม่ได้กำหนดจุดหมาย",12.5f,MUTED,false);
        targetInfo.setGravity(Gravity.CENTER);
        targetInfo.setPadding(dp(10),dp(9),dp(10),dp(5));
        root.addView(targetInfo,new LinearLayout.LayoutParams(-1,-2));

        navigateButton=new Button(this);
        navigateButton.setText("นำทางไปที่นี่");
        navigateButton.setAllCaps(false);
        navigateButton.setTextSize(17);
        navigateButton.setTypeface(Typeface.DEFAULT_BOLD);
        navigateButton.setTextColor(Color.rgb(4,22,33));
        navigateButton.setBackground(round(CYAN,18));
        navigateButton.setOnClickListener(v->navigateCurrent());
        navigateButton.setOnLongClickListener(v->{ editTarget(); return true; });
        root.addView(navigateButton,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView hint=text("ปัดซ้าย/ขวาเพื่อเปลี่ยนรูป • กดค้างปุ่มนำทางเพื่อแก้จุดหมาย",11.5f,MUTED,false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0,dp(7),0,0);
        root.addView(hint,new LinearLayout.LayoutParams(-1,-2));

        setContentView(root);
    }

    private File current(){
        if(files==null||files.isEmpty()) return null;
        return files.get(Math.max(0,Math.min(index,files.size()-1)));
    }

    private void showCurrent(){
        File f=current();
        if(f==null){
            title.setText("ไม่มีรูป");
            counter.setText("0 / 0");
            targetInfo.setText("ยังไม่มีรูปในคลัง");
            navigateButton.setEnabled(false);
            image.setImageDrawable(null);
            return;
        }
        navigateButton.setEnabled(true);
        title.setText(f.getName()+"  •  "+NavigationLibrary.thaiCategory(NavigationLibrary.categoryOf(f)));
        counter.setText((index+1)+" / "+files.size());
        image.setImageBitmap(decode(f.getAbsolutePath(),2200));
        image.post(image::resetZoom);
        refreshTargetInfo();
    }

    private void swipeTo(int delta){
        if(animating || files==null || files.size()<2) return;
        animating=true;
        int next=(index+delta+files.size())%files.size();
        float distance=Math.max(dp(80),image.getWidth()*0.18f);
        float outX=delta>0?-distance:distance;
        float inX=-outX;
        image.animate().translationX(outX).alpha(0.15f).setDuration(110).withEndAction(()->{
            index=next;
            showCurrent();
            image.setTranslationX(inX);
            image.setAlpha(0.18f);
            image.animate().translationX(0).alpha(1f).setDuration(160).withEndAction(()->animating=false).start();
        }).start();
    }

    private void refreshTargetInfo(){
        File f=current();
        if(f==null)return;
        NavigationTargetStore.Target t=NavigationTargetStore.get(this,f);
        if(t.isEmpty()){
            targetInfo.setText("ยังไม่ได้กำหนดจุดหมาย • แตะ “นำทางไปที่นี่” เพื่อตั้งค่า");
            targetInfo.setTextColor(MUTED);
        }else{
            targetInfo.setText("● จุดหมาย: "+t.display());
            targetInfo.setTextColor(GREEN);
        }
    }

    private void navigateCurrent(){
        File f=current();
        if(f==null)return;
        NavigationTargetStore.Target target=NavigationTargetStore.get(this,f);
        if(target.isEmpty()){
            showTargetTypeDialog(true);
            return;
        }
        launchNavigation(target);
    }

    private void editTarget(){
        if(current()==null)return;
        showTargetTypeDialog(false);
    }

    private void showTargetTypeDialog(boolean navigateAfterSave){
        String[] options={"ค้นหาด้วยชื่อสถานที่", "กำหนดพิกัด / ปักหมุด", "ยกเลิกจุดหมายที่บันทึกไว้"};
        new AlertDialog.Builder(this)
                .setTitle("กำหนดจุดหมายสำหรับรูปนี้")
                .setItems(options,(d,which)->{
                    if(which==0) showPlaceDialog(navigateAfterSave);
                    else if(which==1) showCoordinateDialog(navigateAfterSave);
                    else {
                        NavigationTargetStore.clear(this,current());
                        refreshTargetInfo();
                        Toast.makeText(this,"ล้างจุดหมายแล้ว",Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("ปิด",null)
                .show();
    }

    private void showPlaceDialog(boolean navigateAfterSave){
        File f=current(); if(f==null)return;
        NavigationTargetStore.Target old=NavigationTargetStore.get(this,f);
        EditText input=new EditText(this);
        input.setHint("เช่น ปั๊ม PTT บ้านติ้ว หรือ วัดพระธาตุ...");
        input.setText(old.isPlace()?old.name:"");
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setPadding(dp(18),dp(12),dp(18),dp(12));
        new AlertDialog.Builder(this)
                .setTitle("ชื่อสถานที่ / จุดหมาย")
                .setMessage("พิมพ์ชื่อที่ Google Maps ค้นหาเจอ แอปจะจำไว้กับรูปนี้")
                .setView(input)
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("บันทึก",null)
                .create()
                .setOnShowListener(dialog->{ });

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("ชื่อสถานที่ / จุดหมาย")
                .setMessage("พิมพ์ชื่อที่ Google Maps ค้นหาเจอ แอปจะจำไว้กับรูปนี้")
                .setView(input)
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("บันทึก",null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();
            if(name.isEmpty()){input.setError("กรุณาใส่ชื่อสถานที่");return;}
            NavigationTargetStore.savePlace(this,f,name);
            dialog.dismiss();
            refreshTargetInfo();
            if(navigateAfterSave) launchNavigation(NavigationTargetStore.get(this,f));
        }));
        dialog.show();
    }

    private void showCoordinateDialog(boolean navigateAfterSave){
        File f=current(); if(f==null)return;
        NavigationTargetStore.Target old=NavigationTargetStore.get(this,f);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),0,dp(18),0);

        EditText lat=new EditText(this);
        lat.setHint("Latitude เช่น 16.8211");
        lat.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        if(old.isCoordinate())lat.setText(old.lat);
        box.addView(lat,new LinearLayout.LayoutParams(-1,dp(56)));

        EditText lng=new EditText(this);
        lng.setHint("Longitude เช่น 100.2659");
        lng.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        if(old.isCoordinate())lng.setText(old.lng);
        box.addView(lng,new LinearLayout.LayoutParams(-1,dp(56)));

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("กำหนดพิกัด / ปักหมุด")
                .setMessage("ใส่ Latitude และ Longitude ของจุดหมาย แล้วแอปจะจำพิกัดนี้ไว้กับรูป")
                .setView(box)
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("บันทึก",null)
                .create();

        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String a=lat.getText().toString().trim();
            String b=lng.getText().toString().trim();
            try{
                double la=Double.parseDouble(a); double lo=Double.parseDouble(b);
                if(la<-90||la>90){lat.setError("Latitude ต้องอยู่ระหว่าง -90 ถึง 90");return;}
                if(lo<-180||lo>180){lng.setError("Longitude ต้องอยู่ระหว่าง -180 ถึง 180");return;}
                NavigationTargetStore.saveCoordinate(this,f,a,b);
                dialog.dismiss();
                refreshTargetInfo();
                if(navigateAfterSave) launchNavigation(NavigationTargetStore.get(this,f));
            }catch(Exception e){Toast.makeText(this,"กรุณากรอกพิกัดให้ถูกต้อง",Toast.LENGTH_SHORT).show();}
        }));
        dialog.show();
    }

    private void launchNavigation(NavigationTargetStore.Target target){
        String query=target.isCoordinate()?target.lat+","+target.lng:target.name;
        try{
            Uri navUri=Uri.parse("google.navigation:q="+Uri.encode(query)+"&mode=d");
            Intent maps=new Intent(Intent.ACTION_VIEW,navUri);
            maps.setPackage("com.google.android.apps.maps");
            startActivity(maps);
        }catch(Exception first){
            try{
                Uri geo=Uri.parse("geo:0,0?q="+Uri.encode(query));
                startActivity(new Intent(Intent.ACTION_VIEW,geo));
            }catch(Exception second){
                Toast.makeText(this,"ไม่พบแอปแผนที่สำหรับนำทาง",Toast.LENGTH_LONG).show();
            }
        }
    }

    private void moveCurrent(){
        File f=current(); if(f==null)return;
        String[] th={"Inbox","เส้นทาง","จุดชาร์จ","สถานที่โปรด"};
        String[] ids=NavigationLibrary.categories();
        new AlertDialog.Builder(this).setTitle("ย้ายรูปไปที่").setItems(th,(d,w)->{
            try{
                File moved=NavigationLibrary.moveToCategory(this,f,ids[w]);
                files=NavigationLibrary.listAll(this);
                index=findIndex(moved.getAbsolutePath());
                showCurrent();
                Toast.makeText(this,"ย้ายไป "+NavigationLibrary.thaiCategory(ids[w])+" แล้ว",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"ย้ายไม่สำเร็จ: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }).setNegativeButton("ยกเลิก",null).show();
    }

    private void deleteCurrent(){
        File f=current(); if(f==null)return;
        new AlertDialog.Builder(this)
                .setTitle("ลบรูปนี้?")
                .setMessage(f.getName()+"\n\nไฟล์จะถูกลบออกจากคลังรูปออฟไลน์ของ BYD Phone Space")
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("ลบ",(d,w)->{
                    if(NavigationLibrary.delete(this,f)){
                        files=NavigationLibrary.listAll(this);
                        if(index>=files.size())index=Math.max(0,files.size()-1);
                        showCurrent();
                        Toast.makeText(this,"ลบแล้ว",Toast.LENGTH_SHORT).show();
                    }else Toast.makeText(this,"ลบไม่สำเร็จ",Toast.LENGTH_SHORT).show();
                }).show();
    }

    private Bitmap decode(String path,int target){
        BitmapFactory.Options o=new BitmapFactory.Options();
        o.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(path,o);
        int sample=1;
        while(o.outWidth/sample>target*2||o.outHeight/sample>target*2)sample*=2;
        o.inJustDecodeBounds=false;
        o.inSampleSize=sample;
        return BitmapFactory.decodeFile(path,o);
    }

    private TextView text(String s,float z,int c,boolean b){
        TextView v=new TextView(this);
        v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));
        v.setPadding(dp(10),0,dp(10),0);
        return v;
    }

    private Button small(String s){
        Button b=new Button(this);
        b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(TEXT);
        b.setBackground(round(Color.rgb(15,28,43),16));
        return b;
    }
}
