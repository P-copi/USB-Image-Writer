package com.pcopi.bydphonespace;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class ImageViewerActivity extends Activity {
    public static final String EXTRA_PATH = "path";
    private static final int REQ_LOCATION = 7201;
    private static final int REQ_MAP = 7202;

    private ZoomImageView image;
    private TextView title;
    private TextView counter;
    private TextView gpsInfo;
    private TextView targetInfo;
    private Button navigateButton;
    private List<File> files;
    private int index;
    private boolean animating;
    private File pendingLocationFile;
    private String pendingMapPath;

    private final int BG=Color.rgb(3,8,14), CARD=Color.rgb(15,28,43), TEXT=Color.rgb(242,247,252), MUTED=Color.rgb(157,177,197), CYAN=Color.rgb(77,196,255), GREEN=Color.rgb(93,219,168), ORANGE=Color.rgb(255,183,77);
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        files=NavigationLibrary.listAll(this);
        index=findIndex(getIntent().getStringExtra(EXTRA_PATH));
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
        title.setOnClickListener(v->renameCurrent());
        top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));

        counter=text("",13,CYAN,true);
        counter.setGravity(Gravity.CENTER);
        top.addView(counter,new LinearLayout.LayoutParams(dp(64),dp(48)));
        root.addView(top);

        image=new ZoomImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setSwipeListener(new ZoomImageView.SwipeListener(){
            @Override public void onSwipeLeft(){swipeTo(+1);}
            @Override public void onSwipeRight(){swipeTo(-1);}
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

        LinearLayout orientationTools=new LinearLayout(this);
        orientationTools.setPadding(0,dp(6),0,0);
        Button rotateLeft=small("↶ หมุนซ้าย");
        rotateLeft.setOnClickListener(v->rotateCurrent(-90));
        orientationTools.addView(rotateLeft,new LinearLayout.LayoutParams(0,dp(44),1));
        Button rotateRight=small("↷ หมุนขวา");
        LinearLayout.LayoutParams rotationLp=new LinearLayout.LayoutParams(0,dp(44),1);
        rotationLp.setMargins(dp(8),0,0,0);
        rotateRight.setOnClickListener(v->rotateCurrent(90));
        orientationTools.addView(rotateRight,rotationLp);
        root.addView(orientationTools);

        LinearLayout infoCard=new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(14),dp(11),dp(14),dp(11));
        infoCard.setBackground(round(Color.rgb(10,20,31),17));
        LinearLayout.LayoutParams icp=new LinearLayout.LayoutParams(-1,-2);
        icp.setMargins(0,dp(8),0,0);
        root.addView(infoCard,icp);

        gpsInfo=text("",12.8f,MUTED,true);
        gpsInfo.setPadding(0,0,0,dp(4));
        infoCard.addView(gpsInfo,new LinearLayout.LayoutParams(-1,-2));

        targetInfo=text("",12.4f,MUTED,false);
        targetInfo.setPadding(0,0,0,dp(8));
        infoCard.addView(targetInfo,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout manage=new LinearLayout(this);
        Button rename=small("✎ เปลี่ยนชื่อ");
        rename.setOnClickListener(v->renameCurrent());
        manage.addView(rename,new LinearLayout.LayoutParams(0,dp(44),1));

        Button search=small("⌕ ค้นหาสถานที่");
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(44),1);
        sp.setMargins(dp(7),0,0,0);
        search.setOnClickListener(v->showPlaceDialog(false));
        manage.addView(search,sp);

        Button gps=small("📍 เพิ่ม/แก้ GPS");
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,dp(44),1);
        gp.setMargins(dp(7),0,0,0);
        gps.setOnClickListener(v->showGpsOptions());
        manage.addView(gps,gp);
        infoCard.addView(manage,new LinearLayout.LayoutParams(-1,-2));

        navigateButton=new Button(this);
        navigateButton.setText("นำทางไปที่นี่");
        navigateButton.setAllCaps(false);
        navigateButton.setTextSize(17);
        navigateButton.setTypeface(Typeface.DEFAULT_BOLD);
        navigateButton.setTextColor(Color.rgb(4,22,33));
        navigateButton.setBackground(round(CYAN,18));
        navigateButton.setOnClickListener(v->navigateCurrent());
        navigateButton.setOnLongClickListener(v->{showGpsOptions();return true;});
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(58));
        np.setMargins(0,dp(8),0,0);
        root.addView(navigateButton,np);

        TextView hint=text("ปัดซ้าย/ขวาเพื่อเปลี่ยนรูป • แตะชื่อด้านบนเพื่อเปลี่ยนชื่อ",11.5f,MUTED,false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0,dp(7),0,0);
        root.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }

    private File current(){
        if(files==null||files.isEmpty())return null;
        return files.get(Math.max(0,Math.min(index,files.size()-1)));
    }

    private void showCurrent(){
        File f=current();
        if(f==null){
            title.setText("ไม่มีรูป"); counter.setText("0 / 0"); gpsInfo.setText("○ ไม่พบรูป"); targetInfo.setText("");
            navigateButton.setEnabled(false); image.setImageDrawable(null); return;
        }
        navigateButton.setEnabled(true);
        title.setText(f.getName()+"  •  "+NavigationLibrary.thaiCategory(NavigationLibrary.categoryOf(f)));
        counter.setText((index+1)+" / "+files.size());
        image.setImageBitmap(decode(f.getAbsolutePath(),2200));
        image.post(image::resetZoom);
        refreshInfo();
    }

    private void rotateCurrent(int degrees){
        File currentFile=current();
        if(currentFile==null)return;
        ImageOrientationStore.rotate(this,currentFile,degrees);
        image.setImageBitmap(ImageOrientationUtils.decode(this,currentFile,2200));
        image.post(image::resetZoom);
        Toast.makeText(this,"บันทึกการหมุนเฉพาะการแสดงผลแล้ว • รูปต้นฉบับไม่เปลี่ยน",Toast.LENGTH_SHORT).show();
    }

    private void refreshInfo(){
        File f=current(); if(f==null)return;
        ImageGpsUtils.Info exif=ImageGpsUtils.read(f);
        NavigationTargetStore.Target target=NavigationTargetStore.get(this,f);

        if(target.isCoordinate()){
            gpsInfo.setText("● GPS ที่เพิ่มในแอป: "+target.lat+", "+target.lng);
            gpsInfo.setTextColor(GREEN);
        }else if(exif.hasGps){
            gpsInfo.setText("● มี GPS ในภาพ (EXIF): "+exif.coordinateText());
            gpsInfo.setTextColor(GREEN);
        }else{
            gpsInfo.setText("○ ไม่พบ GPS ในภาพนี้");
            gpsInfo.setTextColor(ORANGE);
        }

        if(target.isPlace()){
            targetInfo.setText("จุดหมายจากชื่อสถานที่: "+target.name);
            targetInfo.setTextColor(CYAN);
        }else if(target.isCoordinate()){
            if(target.name!=null && !target.name.trim().isEmpty()){
                targetInfo.setText("จุดที่เลือกจากแผนที่: "+target.name);
                targetInfo.setTextColor(CYAN);
            }else{
                targetInfo.setText("พร้อมนำทางด้วยพิกัดที่บันทึกไว้");
                targetInfo.setTextColor(MUTED);
            }
        }else if(exif.hasGps){
            targetInfo.setText("พร้อมนำทางจาก GPS ที่ติดมากับภาพ");
            targetInfo.setTextColor(MUTED);
        }else{
            targetInfo.setText("ยังไม่ได้กำหนดจุดหมาย • เลือกจากแผนที่ ค้นหาสถานที่ หรือเพิ่ม GPS ได้");
            targetInfo.setTextColor(MUTED);
        }
    }

    private void swipeTo(int delta){
        if(animating||files==null||files.size()<2)return;
        animating=true;
        int next=(index+delta+files.size())%files.size();
        float distance=Math.max(dp(80),image.getWidth()*0.18f);
        float outX=delta>0?-distance:distance, inX=-outX;
        image.animate().translationX(outX).alpha(0.15f).setDuration(110).withEndAction(()->{
            index=next; showCurrent(); image.setTranslationX(inX); image.setAlpha(0.18f);
            image.animate().translationX(0).alpha(1f).setDuration(160).withEndAction(()->animating=false).start();
        }).start();
    }

    private void renameCurrent(){
        File f=current(); if(f==null)return;
        EditText input=new EditText(this);
        input.setSingleLine(true); input.setText(stripExtension(f.getName())); input.setSelectAllOnFocus(true); input.setPadding(dp(18),dp(8),dp(18),dp(8));
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("เปลี่ยนชื่อรูป")
                .setMessage("ตั้งชื่อให้อ่านแล้วรู้จุดหมายได้ทันที เช่น อนุสรณ์ดอนเจดีย์")
                .setView(input).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();
            if(name.isEmpty()){input.setError("กรุณาใส่ชื่อรูป");return;}
            try{
                File renamed=NavigationLibrary.renameImage(this,f,name);
                files=NavigationLibrary.listAll(this); index=findIndex(renamed.getAbsolutePath());
                dialog.dismiss(); showCurrent(); Toast.makeText(this,"เปลี่ยนชื่อแล้ว",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"เปลี่ยนชื่อไม่สำเร็จ: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }

    private String stripExtension(String name){
        int dot=name==null?-1:name.lastIndexOf('.');
        return dot>0?name.substring(0,dot):(name==null?"":name);
    }

    private void showPlaceDialog(boolean navigateAfterSave){
        File f=current(); if(f==null)return;
        NavigationTargetStore.Target old=NavigationTargetStore.get(this,f);
        EditText input=new EditText(this);
        input.setHint("เช่น อนุสรณ์ดอนเจดีย์");
        input.setText(old.isPlace()?old.name:stripExtension(f.getName()));
        input.setSingleLine(false); input.setMinLines(2); input.setPadding(dp(18),dp(10),dp(18),dp(10));
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("ค้นหาชื่อสถานที่")
                .setMessage("บันทึกชื่อเป็นจุดหมาย หรือเปิด Google Maps เพื่อตรวจสอบชื่อก่อน")
                .setView(input).setNegativeButton("ยกเลิก",null).setNeutralButton("ค้นหาใน Maps",null).setPositiveButton("บันทึก",null).create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                String q=input.getText().toString().trim(); if(q.isEmpty()){input.setError("กรุณาใส่ชื่อสถานที่");return;} openMapSearch(q);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String name=input.getText().toString().trim(); if(name.isEmpty()){input.setError("กรุณาใส่ชื่อสถานที่");return;}
                NavigationTargetStore.savePlace(this,f,name); dialog.dismiss(); refreshInfo();
                if(navigateAfterSave)launchNavigation(NavigationTargetStore.get(this,f));
            });
        });
        dialog.show();
    }

    private void openMapSearch(String query){
        try{
            Uri geo=Uri.parse("geo:0,0?q="+Uri.encode(query)); Intent i=new Intent(Intent.ACTION_VIEW,geo); i.setPackage("com.google.android.apps.maps"); startActivity(i);
        }catch(Exception e){
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(query))));}
            catch(Exception ignored){Toast.makeText(this,"ไม่พบแอปแผนที่",Toast.LENGTH_SHORT).show();}
        }
    }

    private void showGpsOptions(){
        File f=current(); if(f==null)return;
        ImageGpsUtils.Info exif=ImageGpsUtils.read(f);
        NavigationTargetStore.Target target=NavigationTargetStore.get(this,f);
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        java.util.ArrayList<Integer> actions=new java.util.ArrayList<>();

        labels.add("🗺 เลือกจากแผนที่ด้วยนิ้ว"); actions.add(0);
        if(exif.hasGps){labels.add("🖼 ใช้ GPS จากภาพ: "+exif.coordinateText());actions.add(1);}
        labels.add("📍 ใช้ตำแหน่งปัจจุบันของเครื่อง");actions.add(2);
        labels.add("🔎 ค้นหาชื่อสถานที่");actions.add(3);
        labels.add("✍ ป้อน Latitude / Longitude");actions.add(4);
        if(!target.isEmpty()){labels.add("ล้าง GPS / จุดหมายที่เพิ่มไว้");actions.add(5);}

        new AlertDialog.Builder(this)
                .setTitle("เพิ่ม / แก้ GPS ให้รูป")
                .setItems(labels.toArray(new String[0]),(d,which)->{
                    int action=actions.get(which);
                    if(action==0){
                        openMapPicker(f);
                    }else if(action==1){
                        NavigationTargetStore.saveCoordinate(this,f,String.format(Locale.US,"%.6f",exif.lat),String.format(Locale.US,"%.6f",exif.lng));
                        refreshInfo(); Toast.makeText(this,"ใช้ GPS จากภาพแล้ว",Toast.LENGTH_SHORT).show();
                    }else if(action==2){
                        pendingLocationFile=f; useCurrentDeviceLocation();
                    }else if(action==3){
                        showPlaceDialog(false);
                    }else if(action==4){
                        showCoordinateDialog(false);
                    }else if(action==5){
                        NavigationTargetStore.clear(this,f); refreshInfo(); Toast.makeText(this,"ล้างจุดหมายที่เพิ่มไว้แล้ว",Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("ยกเลิก",null).show();
    }

    private void openMapPicker(File f){
        Intent i=new Intent(this,MapPickerActivity.class);
        NavigationTargetStore.Target t=NavigationTargetStore.get(this,f);
        ImageGpsUtils.Info exif=ImageGpsUtils.read(f);
        if(t.isCoordinate()){
            try{
                i.putExtra(MapPickerActivity.EXTRA_LAT,Double.parseDouble(t.lat));
                i.putExtra(MapPickerActivity.EXTRA_LNG,Double.parseDouble(t.lng));
                if(t.name!=null&&!t.name.trim().isEmpty())i.putExtra(MapPickerActivity.EXTRA_LABEL,t.name);
            }catch(Exception ignored){}
        }else if(exif.hasGps){
            i.putExtra(MapPickerActivity.EXTRA_LAT,exif.lat);
            i.putExtra(MapPickerActivity.EXTRA_LNG,exif.lng);
        }
        pendingMapPath=f.getAbsolutePath();
        startActivityForResult(i,REQ_MAP);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_MAP && resultCode==RESULT_OK && data!=null){
            double lat=data.getDoubleExtra(MapPickerActivity.EXTRA_LAT,Double.NaN);
            double lng=data.getDoubleExtra(MapPickerActivity.EXTRA_LNG,Double.NaN);
            String label=data.getStringExtra(MapPickerActivity.EXTRA_LABEL);
            if(Double.isNaN(lat)||Double.isNaN(lng))return;
            File f=pendingMapPath==null?current():new File(pendingMapPath);
            if(f==null||!f.exists())f=current();
            if(f==null)return;
            String a=String.format(Locale.US,"%.6f",lat), b=String.format(Locale.US,"%.6f",lng);
            NavigationTargetStore.saveCoordinate(this,f,a,b,label==null?"":label);
            pendingMapPath=null;
            refreshInfo();
            Toast.makeText(this,"บันทึกจุดจากแผนที่แล้ว",Toast.LENGTH_SHORT).show();
        }
    }

    private void useCurrentDeviceLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION); return;
        }
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(lm==null){Toast.makeText(this,"ไม่พบระบบตำแหน่งของเครื่อง",Toast.LENGTH_SHORT).show();return;}
        Location best=null;
        try{best=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}
        try{Location n=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);if(n!=null&&(best==null||n.getTime()>best.getTime()))best=n;}catch(Exception ignored){}
        if(best!=null){saveCurrentLocation(best);return;}
        Toast.makeText(this,"กำลังค้นหาตำแหน่งปัจจุบัน…",Toast.LENGTH_SHORT).show();
        LocationListener listener=new LocationListener(){
            @Override public void onLocationChanged(Location location){try{lm.removeUpdates(this);}catch(Exception ignored){}saveCurrentLocation(location);}
            @Override public void onProviderEnabled(String provider){}
            @Override public void onProviderDisabled(String provider){}
            @Override public void onStatusChanged(String provider,int status,Bundle extras){}
        };
        try{
            if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))lm.requestSingleUpdate(LocationManager.GPS_PROVIDER,listener,Looper.getMainLooper());
            else if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,listener,Looper.getMainLooper());
            else Toast.makeText(this,"กรุณาเปิด GPS/ตำแหน่งของเครื่องก่อน",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"อ่านตำแหน่งไม่สำเร็จ: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void saveCurrentLocation(Location location){
        File f=pendingLocationFile!=null?pendingLocationFile:current(); if(f==null||location==null)return;
        String lat=String.format(Locale.US,"%.6f",location.getLatitude()), lng=String.format(Locale.US,"%.6f",location.getLongitude());
        NavigationTargetStore.saveCoordinate(this,f,lat,lng,"ตำแหน่งปัจจุบันของเครื่อง");
        pendingLocationFile=null; refreshInfo(); Toast.makeText(this,"บันทึกตำแหน่งปัจจุบันแล้ว: "+lat+", "+lng,Toast.LENGTH_LONG).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION){
            boolean granted=false; for(int r:grantResults)if(r==PackageManager.PERMISSION_GRANTED)granted=true;
            if(granted)useCurrentDeviceLocation(); else Toast.makeText(this,"ต้องอนุญาตตำแหน่งเพื่อใช้ GPS ปัจจุบันของเครื่อง",Toast.LENGTH_LONG).show();
        }
    }

    private void navigateCurrent(){
        File f=current(); if(f==null)return;
        NavigationTargetStore.Target target=NavigationTargetStore.get(this,f);
        if(!target.isEmpty()){launchNavigation(target);return;}
        ImageGpsUtils.Info exif=ImageGpsUtils.read(f);
        if(exif.hasGps){launchCoordinates(String.format(Locale.US,"%.6f",exif.lat),String.format(Locale.US,"%.6f",exif.lng));return;}
        showGpsOptions();
    }

    private void showCoordinateDialog(boolean navigateAfterSave){
        File f=current(); if(f==null)return;
        NavigationTargetStore.Target old=NavigationTargetStore.get(this,f);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),0,dp(18),0);
        EditText lat=new EditText(this); lat.setHint("Latitude เช่น 14.634200"); lat.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED); if(old.isCoordinate())lat.setText(old.lat); box.addView(lat,new LinearLayout.LayoutParams(-1,dp(56)));
        EditText lng=new EditText(this); lng.setHint("Longitude เช่น 100.020300"); lng.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED); if(old.isCoordinate())lng.setText(old.lng); box.addView(lng,new LinearLayout.LayoutParams(-1,dp(56)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("กำหนดพิกัด GPS").setMessage("ใส่ Latitude และ Longitude ของจุดหมาย").setView(box).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String a=lat.getText().toString().trim(), b=lng.getText().toString().trim();
            try{
                double la=Double.parseDouble(a),lo=Double.parseDouble(b);
                if(la<-90||la>90){lat.setError("Latitude ต้องอยู่ระหว่าง -90 ถึง 90");return;}
                if(lo<-180||lo>180){lng.setError("Longitude ต้องอยู่ระหว่าง -180 ถึง 180");return;}
                NavigationTargetStore.saveCoordinate(this,f,a,b); dialog.dismiss(); refreshInfo();
                if(navigateAfterSave)launchNavigation(NavigationTargetStore.get(this,f));
            }catch(Exception e){Toast.makeText(this,"กรุณากรอกพิกัดให้ถูกต้อง",Toast.LENGTH_SHORT).show();}
        }));
        dialog.show();
    }

    private void launchNavigation(NavigationTargetStore.Target target){
        if(target.isCoordinate())launchCoordinates(target.lat,target.lng); else launchQuery(target.name);
    }
    private void launchCoordinates(String lat,String lng){launchQuery(lat+","+lng);}
    private void launchQuery(String query){
        NavigationBridge.launch(this,query);
    }

    private void moveCurrent(){
        File f=current(); if(f==null)return;
        String[] th={"Inbox","เส้นทาง","จุดชาร์จ","สถานที่โปรด"}; String[] ids=NavigationLibrary.categories();
        new AlertDialog.Builder(this).setTitle("ย้ายรูปไปที่").setItems(th,(d,w)->{
            try{File moved=NavigationLibrary.moveToCategory(this,f,ids[w]);files=NavigationLibrary.listAll(this);index=findIndex(moved.getAbsolutePath());showCurrent();Toast.makeText(this,"ย้ายไป "+NavigationLibrary.thaiCategory(ids[w])+" แล้ว",Toast.LENGTH_SHORT).show();}
            catch(Exception e){Toast.makeText(this,"ย้ายไม่สำเร็จ: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }).setNegativeButton("ยกเลิก",null).show();
    }

    private void deleteCurrent(){
        File f=current(); if(f==null)return;
        new AlertDialog.Builder(this).setTitle("ลบรูปนี้?").setMessage(f.getName()+"\n\nไฟล์จะถูกลบออกจากคลังรูปออฟไลน์ของ BYD Phone Space").setNegativeButton("ยกเลิก",null).setPositiveButton("ลบ",(d,w)->{
            if(NavigationLibrary.delete(this,f)){files=NavigationLibrary.listAll(this);if(index>=files.size())index=Math.max(0,files.size()-1);showCurrent();Toast.makeText(this,"ลบแล้ว",Toast.LENGTH_SHORT).show();}
            else Toast.makeText(this,"ลบไม่สำเร็จ",Toast.LENGTH_SHORT).show();
        }).show();
    }

    private Bitmap decode(String path,int target){return ImageOrientationUtils.decode(this,new File(path),target);}
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setGravity(Gravity.CENTER_VERTICAL);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));v.setPadding(dp(10),0,dp(10),0);return v;}
    private Button small(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13.3f);b.setTextColor(TEXT);b.setBackground(round(CARD,16));return b;}
}
