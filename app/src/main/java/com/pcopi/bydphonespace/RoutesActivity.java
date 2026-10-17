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
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RoutesActivity extends Activity {
    public static final String EXTRA_MODE="mode";
    public static final String EXTRA_OPEN_ROUTE_ID="open_route_id";
    public static final String MODE_ALL="ALL";
    public static final String MODE_FAVORITES="FAVORITES";
    public static final String MODE_RECENT="RECENT";

    private final int BG=Color.rgb(7,16,27),CARD=Color.rgb(17,31,47),TEXT=Color.rgb(242,247,252),MUTED=Color.rgb(157,177,197),CYAN=Color.rgb(77,196,255),GREEN=Color.rgb(93,219,168),YELLOW=Color.rgb(255,205,92);
    private String mode=MODE_ALL;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private TextView tv(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans",b?Typeface.BOLD:Typeface.NORMAL));return v;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        String m=getIntent().getStringExtra(EXTRA_MODE);if(m!=null)mode=m;
        build();
        String open=getIntent().getStringExtra(EXTRA_OPEN_ROUTE_ID);
        if(open!=null){getIntent().removeExtra(EXTRA_OPEN_ROUTE_ID);openRoute(open);}
    }

    @Override protected void onResume(){super.onResume();if(!isFinishing())build();}

    private void build(){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));root.setBackgroundColor(BG);sv.addView(root);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tv("‹",36,TEXT,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(tv("เส้นทางของฉัน",23,TEXT,true));TextView sub=tv("ชุดรูปนำทาง • Offline",12.5f,MUTED,false);sub.setPadding(0,dp(2),0,0);names.addView(sub);top.addView(names,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(0,dp(16),0,dp(12));
        TextView all=tab("ทั้งหมด",MODE_ALL.equals(mode));TextView fav=tab("★ โปรด",MODE_FAVORITES.equals(mode));TextView recent=tab("ล่าสุด",MODE_RECENT.equals(mode));
        all.setOnClickListener(v->{mode=MODE_ALL;build();});fav.setOnClickListener(v->{mode=MODE_FAVORITES;build();});recent.setOnClickListener(v->{mode=MODE_RECENT;build();});
        tabs.addView(all,new LinearLayout.LayoutParams(0,dp(44),1));tabs.addView(fav,new LinearLayout.LayoutParams(0,dp(44),1));tabs.addView(recent,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(tabs);

        Button create=primary("＋  สร้างเส้นทางใหม่");create.setOnClickListener(v->startCreate());root.addView(create,new LinearLayout.LayoutParams(-1,dp(54)));

        List<RouteStore.Route> routes=currentRoutes();
        TextView count=tv(titleForMode()+"  •  "+routes.size()+" เส้นทาง",15,MUTED,true);count.setPadding(dp(2),dp(20),0,dp(10));root.addView(count);
        if(routes.isEmpty()){
            root.addView(empty(MODE_FAVORITES.equals(mode)?"ยังไม่มีเส้นทางโปรด":MODE_RECENT.equals(mode)?"ยังไม่มีเส้นทางที่ใช้งานล่าสุด":"ยังไม่มีเส้นทาง","กด “สร้างเส้นทางใหม่” แล้วเลือกรูปจากคลังหลายรูปมารวมเป็นชุดเดียวได้ รูปเดิมยังอยู่ในคลังเหมือนเดิม"));
        }else{
            for(RouteStore.Route r:routes)root.addView(routeCard(r));
        }
        setContentView(sv);
    }

    private List<RouteStore.Route> currentRoutes(){if(MODE_FAVORITES.equals(mode))return RouteStore.favorites(this);if(MODE_RECENT.equals(mode))return RouteStore.recent(this);return RouteStore.list(this);}
    private String titleForMode(){if(MODE_FAVORITES.equals(mode))return "เส้นทางโปรด";if(MODE_RECENT.equals(mode))return "ใช้งานล่าสุด";return "เส้นทางทั้งหมด";}

    private LinearLayout routeCard(RouteStore.Route r){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(round(CARD,22));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(12));card.setLayoutParams(cp);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        List<File> routeFiles=RouteStore.files(this,r.id);
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackground(round(Color.rgb(10,20,31),16));
        if(!routeFiles.isEmpty())cover.setImageBitmap(decode(routeFiles.get(0).getAbsolutePath(),260));
        head.addView(cover,new LinearLayout.LayoutParams(dp(78),dp(78)));

        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);txt.setPadding(dp(12),0,dp(8),0);
        TextView name=tv(r.name,18,TEXT,true);name.setSingleLine(true);txt.addView(name);
        int gps=gpsCount(r);TextView detail=tv(r.paths.size()+" รูป • "+(gps>0?"GPS "+gps+" จุด":"ยังไม่มี GPS")+" • Offline",12.5f,gps>0?GREEN:MUTED,false);detail.setPadding(0,dp(5),0,0);txt.addView(detail);
        TextView order=tv("เรียงรูปตามลำดับการเดินทาง",11.5f,MUTED,false);order.setPadding(0,dp(5),0,0);txt.addView(order);
        head.addView(txt,new LinearLayout.LayoutParams(0,-2,1));

        TextView star=tv(r.favorite?"★":"☆",28,r.favorite?YELLOW:MUTED,true);star.setGravity(Gravity.CENTER);star.setOnClickListener(v->{RouteStore.toggleFavorite(this,r.id);build();});head.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(head);

        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(12),0,0);
        Button open=dark("เปิดเส้นทาง");open.setOnClickListener(v->openRoute(r.id));actions.addView(open,new LinearLayout.LayoutParams(0,dp(48),1));
        Button nav=primary("นำทาง");LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(48),1);np.setMargins(dp(8),0,0,0);nav.setOnClickListener(v->navigateRoute(r));actions.addView(nav,np);card.addView(actions);

        Button manage=dark("☷  จัดการรูป / ลำดับ");manage.setOnClickListener(v->editRoute(r.id));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(46));mp.setMargins(0,dp(8),0,0);card.addView(manage,mp);

        card.setOnClickListener(v->openRoute(r.id));
        card.setOnLongClickListener(v->{showRouteMenu(r);return true;});
        return card;
    }

    private int gpsCount(RouteStore.Route r){int n=0;for(String p:r.paths){File f=new File(p);if(!f.exists())continue;NavigationTargetStore.Target t=NavigationTargetStore.get(this,f);ImageGpsUtils.Info exif=ImageGpsUtils.read(f);if(!t.isEmpty()||exif.hasGps)n++;}return n;}

    private void startCreate(){
        List<File> files=NavigationLibrary.listAll(this);
        if(files.isEmpty()){Toast.makeText(this,"ยังไม่มีรูปในคลัง กรุณานำเข้ารูปก่อน",Toast.LENGTH_LONG).show();startActivity(new Intent(this,RouteImagesActivity.class));return;}
        EditText input=new EditText(this);input.setHint("เช่น บ้าน → ดอนเจดีย์");input.setSingleLine(true);input.setPadding(dp(18),dp(8),dp(18),dp(8));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("ชื่อเส้นทาง").setMessage("ตั้งชื่อให้อ่านแล้วรู้จุดหมายได้ทันที").setView(input).setNegativeButton("ยกเลิก",null).setPositiveButton("ถัดไป",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("กรุณาใส่ชื่อเส้นทาง");return;}d.dismiss();chooseImagesForRoute(name,files);}));d.show();
    }

    private void chooseImagesForRoute(String name,List<File> files){
        CharSequence[] labels=new CharSequence[files.size()];boolean[] checked=new boolean[files.size()];
        for(int i=0;i<files.size();i++){File f=files.get(i);labels[i]=(i+1)+". "+f.getName()+"  •  "+NavigationLibrary.thaiCategory(NavigationLibrary.categoryOf(f));}
        AlertDialog d=new AlertDialog.Builder(this).setTitle("เลือกรูปในเส้นทาง").setMultiChoiceItems(labels,checked,(dialog,which,isChecked)->checked[which]=isChecked).setNegativeButton("ยกเลิก",null).setPositiveButton("สร้างเส้นทาง",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ArrayList<File> selected=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])selected.add(files.get(i));if(selected.isEmpty()){Toast.makeText(this,"กรุณาเลือกรูปอย่างน้อย 1 รูป",Toast.LENGTH_SHORT).show();return;}RouteStore.Route r=RouteStore.create(this,name,selected);d.dismiss();Toast.makeText(this,"สร้างเส้นทาง “"+r.name+"” แล้ว",Toast.LENGTH_SHORT).show();openRoute(r.id);}));d.show();
    }

    private void openRoute(String id){
        List<File> fs=RouteStore.files(this,id);if(fs.isEmpty()){Toast.makeText(this,"เส้นทางนี้ยังไม่มีรูป",Toast.LENGTH_SHORT).show();return;}
        RouteStore.touchOpened(this,id);Intent i=new Intent(this,RouteViewerActivity.class);i.putExtra(RouteViewerActivity.EXTRA_ROUTE_ID,id);startActivity(i);
    }

    private void editRoute(String id){Intent i=new Intent(this,RouteEditorActivity.class);i.putExtra(RouteEditorActivity.EXTRA_ROUTE_ID,id);startActivity(i);}

    private void navigateRoute(RouteStore.Route r){
        List<File> fs=RouteStore.files(this,r.id);if(fs.isEmpty()){Toast.makeText(this,"เส้นทางนี้ยังไม่มีรูป",Toast.LENGTH_SHORT).show();return;}
        RouteStore.touchOpened(this,r.id);
        for(int i=fs.size()-1;i>=0;i--){File f=fs.get(i);NavigationTargetStore.Target t=NavigationTargetStore.get(this,f);if(t.isCoordinate()){launchNav(t.lat+","+t.lng);return;}if(t.isPlace()){launchNav(t.name);return;}ImageGpsUtils.Info exif=ImageGpsUtils.read(f);if(exif.hasGps){launchNav(String.format(Locale.US,"%.6f,%.6f",exif.lat,exif.lng));return;}}
        Toast.makeText(this,"ยังไม่มี GPS/จุดหมายในเส้นทางนี้ เปิดเส้นทางเพื่อกำหนดให้รูปก่อน",Toast.LENGTH_LONG).show();openRoute(r.id);
    }

    private void launchNav(String q){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(q)+"&mode=d"));i.setPackage("com.google.android.apps.maps");startActivity(i);}catch(Exception e){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(q))));}catch(Exception ignored){Toast.makeText(this,"ไม่พบแอปแผนที่",Toast.LENGTH_SHORT).show();}}}

    private void showRouteMenu(RouteStore.Route r){
        String[] options={"จัดการรูป / ลำดับ","เปลี่ยนชื่อเส้นทาง",r.favorite?"เอาออกจากรายการโปรด":"เพิ่มเป็นรายการโปรด","ลบเส้นทาง"};
        new AlertDialog.Builder(this).setTitle(r.name).setItems(options,(d,w)->{if(w==0)editRoute(r.id);else if(w==1)renameRoute(r);else if(w==2){RouteStore.toggleFavorite(this,r.id);build();}else confirmDelete(r);}).setNegativeButton("ยกเลิก",null).show();
    }

    private void renameRoute(RouteStore.Route r){EditText e=new EditText(this);e.setText(r.name);e.setSelectAllOnFocus(true);e.setSingleLine(true);AlertDialog d=new AlertDialog.Builder(this).setTitle("เปลี่ยนชื่อเส้นทาง").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String n=e.getText().toString().trim();if(n.isEmpty()){e.setError("กรุณาใส่ชื่อ");return;}RouteStore.rename(this,r.id,n);d.dismiss();build();}));d.show();}
    private void confirmDelete(RouteStore.Route r){new AlertDialog.Builder(this).setTitle("ลบเส้นทางนี้?").setMessage(r.name+"\n\nรูปภาพจะไม่ถูกลบออกจากคลัง ลบเฉพาะชุดเส้นทางเท่านั้น").setNegativeButton("ยกเลิก",null).setPositiveButton("ลบ",(d,w)->{RouteStore.delete(this,r.id);build();}).show();}

    private Bitmap decode(String path,int target){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);int s=1;while(o.outWidth/s>target*2||o.outHeight/s>target*2)s*=2;o.inJustDecodeBounds=false;o.inSampleSize=s;return BitmapFactory.decodeFile(path,o);}
    private TextView tab(String s,boolean a){TextView v=tv(s,14,a?Color.rgb(4,22,33):MUTED,true);v.setGravity(Gravity.CENTER);v.setBackground(round(a?CYAN:Color.rgb(13,27,42),18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),0,dp(3),0);v.setLayoutParams(p);return v;}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14.5f);b.setTextColor(Color.rgb(4,22,33));b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(CYAN,17));return b;}
    private Button dark(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(TEXT);b.setBackground(round(Color.rgb(13,27,42),17));return b;}
    private LinearLayout empty(String t,String s){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(22),dp(30),dp(22),dp(30));c.setBackground(round(Color.rgb(12,25,39),22));TextView a=tv(t,17,TEXT,true);a.setGravity(Gravity.CENTER);c.addView(a);TextView b=tv(s,13,MUTED,false);b.setGravity(Gravity.CENTER);b.setPadding(dp(8),dp(8),dp(8),0);c.addView(b);return c;}
}
