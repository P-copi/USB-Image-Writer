package com.pcopi.bydphonespace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShareImportActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> uris=new ArrayList<>();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        collectUris(getIntent());
        if(uris.isEmpty()){Toast.makeText(this,"ไม่พบรูปที่แชร์มา",Toast.LENGTH_SHORT).show();finish();return;}
        chooseCategory();
    }

    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private void collectUris(Intent i){
        if(i==null)return;
        String action=i.getAction();
        if(Intent.ACTION_SEND.equals(action)){
            Uri u=i.getParcelableExtra(Intent.EXTRA_STREAM); if(u!=null)uris.add(u);
        }else if(Intent.ACTION_SEND_MULTIPLE.equals(action)){
            ArrayList<Uri> list=i.getParcelableArrayListExtra(Intent.EXTRA_STREAM); if(list!=null)uris.addAll(list);
        }
        ClipData cd=i.getClipData();
        if(cd!=null){for(int x=0;x<cd.getItemCount();x++){Uri u=cd.getItemAt(x).getUri();if(u!=null&&!uris.contains(u))uris.add(u);}}
    }

    private void chooseCategory(){
        String[] th={"Inbox","เส้นทาง","จุดชาร์จ","สถานที่โปรด"}; String[] ids=NavigationLibrary.categories();
        new AlertDialog.Builder(this).setTitle("นำเข้า "+uris.size()+" รูปไปที่").setItems(th,(d,w)->importNow(ids[w])).setNegativeButton("ยกเลิก",(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }

    private void importNow(String category){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(50,50,50,50);box.setBackgroundColor(Color.rgb(17,31,47));ProgressBar p=new ProgressBar(this);box.addView(p);TextView t=new TextView(this);t.setText("กำลังคัดลอก "+uris.size()+" รูปเข้า "+NavigationLibrary.thaiCategory(category));t.setTextColor(Color.WHITE);t.setTextSize(16);t.setGravity(Gravity.CENTER);t.setPadding(0,24,0,0);box.addView(t);AlertDialog dialog=new AlertDialog.Builder(this).setView(box).setCancelable(false).create();dialog.show();
        io.execute(()->{int ok=0;String error="";for(Uri u:uris){try{NavigationLibrary.importImage(this,u,category);ok++;}catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();}}int success=ok;String err=error;runOnUiThread(()->{dialog.dismiss();Toast.makeText(this,"นำเข้าสำเร็จ "+success+" / "+uris.size()+" รูป"+(err.isEmpty()?"":"\n"+err),Toast.LENGTH_LONG).show();Intent open=new Intent(this,RouteImagesActivity.class);open.putExtra("mode","RECENT");startActivity(open);finish();});});
    }
}
