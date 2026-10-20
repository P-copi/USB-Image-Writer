package com.pcopi.byd.minibridge;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  static final String COORD="13.7563310,100.5017650";
  TextView status;
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,20,24,20);
    TextView title=new TextView(this);title.setText("BYD Mini Bridge • ทดสอบภายใน GPack");title.setTextSize(22);root.addView(title);
    TextView explain=new TextView(this);
    explain.setText("ถ้าเห็นหน้านี้แสดงว่า Mini Bridge เปิดได้แล้ว\nทดลองเปิด Maps จากภายใน GPack และดูว่าหมุดพิกัดปรากฏหรือไม่");
    explain.setTextSize(17);root.addView(explain);
    button(root,"A — เปิดหมุดพิกัด (geo)",()->open("geo:0,0?q="+Uri.encode(COORD)));
    button(root,"B — ขอเริ่มนำทาง (google.navigation)",()->open("google.navigation:q="+Uri.encode(COORD)+"&mode=d"));
    button(root,"C — เปิด Maps URL",()->open("https://www.google.com/maps/search/?api=1&query="+Uri.encode(COORD)));
    button(root,"D — เปิด Maps แอปโดยตรง",()->{
      Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(COORD)));
      i.setPackage("com.google.android.apps.maps"); launch(i);
    });
    status=new TextView(this);status.setTextSize(16);root.addView(status);
    setContentView(root);
  }
  void button(LinearLayout l,String s,Runnable r){
    Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);
    b.setOnClickListener(v->r.run());l.addView(b);
  }
  void open(String uri){launch(new Intent(Intent.ACTION_VIEW,Uri.parse(uri)));}
  void launch(Intent i){
    try{startActivity(i);status.setText("คำสั่งส่งออกแล้ว — โปรดยืนยันหมุดบน Maps");}
    catch(Exception e){status.setText("เปิดไม่ได้: "+e.getClass().getSimpleName()+": "+e.getMessage());}
  }
}