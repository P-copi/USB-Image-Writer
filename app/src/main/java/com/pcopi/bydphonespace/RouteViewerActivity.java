package com.pcopi.bydphonespace;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RouteViewerActivity extends Activity {
    public static final String EXTRA_ROUTE_ID="route_id";

    private final int BG=Color.rgb(3,8,14),CARD=Color.rgb(15,28,43),TEXT=Color.rgb(242,247,252),MUTED=Color.rgb(157,177,197),CYAN=Color.rgb(77,196,255),GREEN=Color.rgb(93,219,168),YELLOW=Color.rgb(255,205,92);
    private String routeId;
    private RouteStore.Route route;
    private List<File> files=new ArrayList<>();
    private int index;
    private boolean animating;
    private ZoomImageView image;
    private TextView title,counter,info;
    private Button favorite,navigate;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);routeId=getIntent().getStringExtra(EXTRA_ROUTE_ID);RouteStore.touchOpened(this,routeId);reload();build();showCurrent();}
    @Override protected void onResume(){super.onResume();if(routeId!=null&&title!=null){String old=current()==null?null:current().getAbsolutePath();reload();if(old!=null)index=find(old);showCurrent();}}

    private void reload(){route=RouteStore.get(this,routeId);files=RouteStore.files(this,routeId);if(index>=files.size())index=Math.max(0,files.size()-1);}
    private int find(String p){for(int i=0;i<files.size();i++)if(p.equals(files.get(i).getAbsolutePath()))return i;return Math.max(0,Math.min(index,files.size()-1));}
    private File current(){return files.isEmpty()?null:files.get(Math.max(0,Math.min(index,files.size()-1)));}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(10),dp(10),dp(10));root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=dark("←");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(56),dp(48)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.setPadding(dp(8),0,0,0);title=tv(route==null?"เส้นทาง":route.name,17,TEXT,true);title.setSingleLine(true);TextView sub=tv("ชุดรูปนำทาง • Offline",11.5f,MUTED,false);sub.setPadding(0,dp(2),0,0);names.addView(title);names.addView(sub);top.addView(names,new LinearLayout.LayoutParams(0,dp(52),1));
        favorite=dark(route!=null&&route.favorite?"★":"☆");favorite.setTextColor(route!=null&&route.favorite?YELLOW:MUTED);favorite.setOnClickListener(v->{RouteStore.toggleFavorite(this,routeId);reload();updateFavorite();});top.addView(favorite,new LinearLayout.LayoutParams(dp(56),dp(48)));
        counter=tv("",13,CYAN,true);counter.setGravity(Gravity.CENTER);top.addView(counter,new LinearLayout.LayoutParams(dp(66),dp(48)));root.addView(top);

        image=new ZoomImageView(this);image.setBackgroundColor(Color.BLACK);image.setSwipeListener(new ZoomImageView.SwipeListener(){@Override public void onSwipeLeft(){swipe(+1);}@Override public void onSwipeRight(){swipe(-1);}});root.addView(image,new LinearLayout.LayoutParams(-1,0,1));

        info=tv("",12.5f,MUTED,false);info.setGravity(Gravity.CENTER);info.setPadding(dp(8),dp(8),dp(8),dp(4));root.addView(info);

        LinearLayout tools=new LinearLayout(this);tools.setPadding(0,dp(6),0,0);Button reset=dark("รีเซ็ตซูม");reset.setOnClickListener(v->image.resetZoom());tools.addView(reset,new LinearLayout.LayoutParams(0,dp(46),1));Button manage=dark("ชื่อ / GPS");LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(46),1);mp.setMargins(dp(8),0,0,0);manage.setOnClickListener(v->manageCurrent());tools.addView(manage,mp);root.addView(tools);

        navigate=primary("นำทางไปที่นี่");navigate.setOnClickListener(v->navigateCurrent());LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(58));np.setMargins(0,dp(8),0,0);root.addView(navigate,np);
        TextView hint=tv("ปัดซ้าย/ขวาเพื่อดูรูปตามชุดเส้นทาง",11.5f,MUTED,false);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(7),0,0);root.addView(hint);
        setContentView(root);
    }

    private void updateFavorite(){if(route==null)return;favorite.setText(route.favorite?"★":"☆");favorite.setTextColor(route.favorite?YELLOW:MUTED);title.setText(route.name);}

    private void showCurrent(){
        File f=current();if(f==null){counter.setText("0 / 0");info.setText("เส้นทางนี้ไม่มีรูปแล้ว");navigate.setEnabled(false);image.setImageDrawable(null);return;}
        navigate.setEnabled(true);counter.setText((index+1)+" / "+files.size());image.setImageBitmap(decode(f.getAbsolutePath(),2200));image.post(image::resetZoom);
        NavigationTargetStore.Target t=NavigationTargetStore.get(this,f);ImageGpsUtils.Info exif=ImageGpsUtils.read(f);
        String gps=t.isCoordinate()?"GPS ที่บันทึกไว้":t.isPlace()?"จุดหมาย: "+t.name:exif.hasGps?"GPS จากภาพ (EXIF)":"ยังไม่มี GPS";
        info.setText(f.getName()+"  •  "+gps);info.setTextColor((!t.isEmpty()||exif.hasGps)?GREEN:MUTED);
    }

    private void swipe(int delta){if(animating||files.size()<2)return;animating=true;int next=(index+delta+files.size())%files.size();float d=Math.max(dp(80),image.getWidth()*0.18f);float out=delta>0?-d:d,in=-out;image.animate().translationX(out).alpha(.15f).setDuration(110).withEndAction(()->{index=next;showCurrent();image.setTranslationX(in);image.setAlpha(.18f);image.animate().translationX(0).alpha(1f).setDuration(160).withEndAction(()->animating=false).start();}).start();}

    private void manageCurrent(){File f=current();if(f==null)return;Intent i=new Intent(this,ImageViewerActivity.class);i.putExtra(ImageViewerActivity.EXTRA_PATH,f.getAbsolutePath());startActivity(i);}

    private void navigateCurrent(){
        File f=current();if(f==null)return;NavigationTargetStore.Target t=NavigationTargetStore.get(this,f);
        if(t.isCoordinate()){launch(t.lat+","+t.lng);return;}if(t.isPlace()){launch(t.name);return;}ImageGpsUtils.Info exif=ImageGpsUtils.read(f);if(exif.hasGps){launch(String.format(Locale.US,"%.6f,%.6f",exif.lat,exif.lng));return;}
        Toast.makeText(this,"รูปนี้ยังไม่มี GPS กด “ชื่อ / GPS” เพื่อกำหนดก่อน",Toast.LENGTH_LONG).show();
    }

    private void launch(String q){NavigationBridge.launch(this,q);}
    private Bitmap decode(String path,int target){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);int s=1;while(o.outWidth/s>target*2||o.outHeight/s>target*2)s*=2;o.inJustDecodeBounds=false;o.inSampleSize=s;return BitmapFactory.decodeFile(path,o);}
    private Button dark(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13.5f);b.setTextColor(TEXT);b.setBackground(round(CARD,16));return b;}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(17);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.rgb(4,22,33));b.setBackground(round(CYAN,18));return b;}
}
