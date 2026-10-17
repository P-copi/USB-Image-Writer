package com.pcopi.bydphonespace;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class QuickOverlayService extends Service {
    private static final String CHANNEL="byd_phone_space_overlay";
    private WindowManager wm; private View bubble,panel; private WindowManager.LayoutParams bubbleParams,panelParams;
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);startFg();}
    @Override public int onStartCommand(Intent i,int f,int id){if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}if(bubble==null)showBubble();return START_STICKY;} @Override public IBinder onBind(Intent i){return null;}
    private void startFg(){NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"BYD Phone Space",NotificationManager.IMPORTANCE_LOW);c.setDescription("Quick Overlay สำหรับใช้งานร่วมกับการนำทาง");nm.createNotificationChannel(c);}Intent o=new Intent(this,BydPhoneSpaceActivity.class);int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);PendingIntent pi=PendingIntent.getActivity(this,1,o,flags);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setContentTitle("BYD Phone Space").setContentText("Quick Overlay พร้อมใช้งาน").setSmallIcon(R.drawable.ic_launcher_byd_phone_space).setContentIntent(pi).setOngoing(true);startForeground(1701,b.build());}
    private int type(){return Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;}
    private void showBubble(){TextView v=new TextView(this);v.setText("≡");v.setTextSize(29);v.setTextColor(Color.rgb(4,22,33));v.setTypeface(Typeface.DEFAULT_BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(Color.rgb(77,196,255),22));if(Build.VERSION.SDK_INT>=21)v.setElevation(dp(10));bubble=v;bubbleParams=new WindowManager.LayoutParams(dp(58),dp(58),type(),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);bubbleParams.gravity=Gravity.END|Gravity.CENTER_VERTICAL;bubbleParams.x=dp(10);wm.addView(bubble,bubbleParams);final float[] y={0};final int[] py={0};final boolean[] moved={false};v.setOnTouchListener((view,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){y[0]=e.getRawY();py[0]=bubbleParams.y;moved[0]=false;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float d=e.getRawY()-y[0];if(Math.abs(d)>dp(5))moved[0]=true;bubbleParams.y=py[0]+(int)d;try{wm.updateViewLayout(bubble,bubbleParams);}catch(Exception ignored){}if(panel!=null){panelParams.y=bubbleParams.y;try{wm.updateViewLayout(panel,panelParams);}catch(Exception ignored){}}return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(!moved[0])toggle();return true;}return false;});}
    private void toggle(){if(panel==null)showPanel();else hidePanel();}
    private void showPanel(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(15),dp(16),dp(16));box.setBackground(round(Color.rgb(14,29,45),24));if(Build.VERSION.SDK_INT>=21)box.setElevation(dp(14));TextView t=text("BYD Quick Panel",18,Color.WHITE,true);box.addView(t);TextView s=text("Maps ยังทำงานต่อ • ใช้เสร็จพับเก็บ",12.5f,Color.rgb(157,177,197),false);s.setPadding(0,dp(4),0,dp(12));box.addView(s);Button images=pb("▣  รูปภาพนำทาง");images.setOnClickListener(v->{hidePanel();open(RouteImagesActivity.class);});box.addView(images,lp());Button nas=pb("▤  NAS / Tailscale");nas.setOnClickListener(v->{hidePanel();open(NasConnectActivity.class);});box.addView(nas,lp());Button sync=pb("↻  ซิงก์รูปใหม่");sync.setOnClickListener(v->Toast.makeText(this,"V0.1: เตรียม Quick Sync ไว้แล้ว",Toast.LENGTH_SHORT).show());box.addView(sync,lp());Button home=pb("⌂  เปิดหน้า Home");home.setOnClickListener(v->{hidePanel();open(BydPhoneSpaceActivity.class);});box.addView(home,lp());LinearLayout bottom=new LinearLayout(this);Button hide=sb("พับเก็บ");hide.setOnClickListener(v->hidePanel());bottom.addView(hide,new LinearLayout.LayoutParams(0,dp(46),1));Button close=sb("ปิด Overlay");LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1);cp.setMargins(dp(8),0,0,0);close.setOnClickListener(v->stopSelf());bottom.addView(close,cp);box.addView(bottom);panel=box;panelParams=new WindowManager.LayoutParams(dp(286),WindowManager.LayoutParams.WRAP_CONTENT,type(),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);panelParams.gravity=Gravity.END|Gravity.CENTER_VERTICAL;panelParams.x=dp(80);panelParams.y=bubbleParams==null?0:bubbleParams.y;wm.addView(panel,panelParams);}
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;} private Button pb(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.rgb(242,247,252));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(dp(14),0,dp(14),0);b.setBackground(round(Color.rgb(22,39,58),16));return b;} private Button sb(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12.5f);b.setTextColor(Color.rgb(157,177,197));b.setBackground(round(Color.rgb(10,23,37),15));return b;} private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(0,0,0,dp(8));return p;}
    private void open(Class<?> c){Intent i=new Intent(this,c);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);} private void hidePanel(){if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;panelParams=null;}}
    @Override public void onDestroy(){hidePanel();if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;}super.onDestroy();}
}
