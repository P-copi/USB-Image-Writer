package com.pcopi.bydphonespace;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MapPickerActivity extends Activity {
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_LABEL = "label";

    private WebView web;
    private TextView selectedText;
    private Button useButton;
    private double selectedLat = Double.NaN;
    private double selectedLng = Double.NaN;
    private String selectedLabel = "";
    private boolean mapReady = false;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(7,16,27));
        getWindow().setNavigationBarColor(Color.rgb(7,16,27));

        double initialLat = getIntent().getDoubleExtra(EXTRA_LAT, 13.7563);
        double initialLng = getIntent().getDoubleExtra(EXTRA_LNG, 100.5018);
        String initialLabel = getIntent().getStringExtra(EXTRA_LABEL);
        boolean hasInitial = getIntent().hasExtra(EXTRA_LAT) && getIntent().hasExtra(EXTRA_LNG);
        if(hasInitial){
            selectedLat=initialLat;
            selectedLng=initialLng;
            selectedLabel=initialLabel==null?"":initialLabel;
        }

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7,16,27));
        root.setPadding(dp(10),dp(10),dp(10),dp(10));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);
        back.setText("←");
        back.setTextSize(20);
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.rgb(15,28,43));
        back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(dp(56),dp(48)));

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12),0,0,0);
        TextView title=new TextView(this);
        title.setText("เลือกตำแหน่งจากแผนที่");
        title.setTextSize(19);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub=new TextView(this);
        sub.setText("เลื่อนแผนที่ให้จุดที่ต้องการอยู่ตรง + สีขาว");
        sub.setTextSize(12.5f);
        sub.setTextColor(Color.rgb(157,177,197));
        titles.addView(title);
        titles.addView(sub);
        top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top);

        web=new WebView(this);
        WebSettings ws=web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(),"AndroidPicker");
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14),dp(10),dp(14),dp(8));
        info.setBackgroundColor(Color.rgb(10,20,31));

        TextView hint=new TextView(this);
        hint.setText("＋ จุดที่จะบันทึก = ตำแหน่งตรงกลางกากบาทสีขาว");
        hint.setTextSize(12.5f);
        hint.setTextColor(Color.rgb(77,196,255));
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(hint,new LinearLayout.LayoutParams(-1,-2));

        selectedText=new TextView(this);
        selectedText.setTextSize(13.5f);
        selectedText.setTextColor(Color.rgb(242,247,252));
        selectedText.setPadding(0,dp(5),0,0);
        selectedText.setText(hasInitial?formatSelection():"เลื่อนแผนที่เพื่อเลือกตำแหน่ง");
        info.addView(selectedText,new LinearLayout.LayoutParams(-1,-2));
        root.addView(info);

        LinearLayout actions=new LinearLayout(this);
        actions.setPadding(0,dp(7),0,0);

        Button cancel=new Button(this);
        cancel.setText("ยกเลิก");
        cancel.setAllCaps(false);
        cancel.setTextSize(15);
        cancel.setTextColor(Color.WHITE);
        cancel.setBackgroundColor(Color.rgb(15,28,43));
        cancel.setOnClickListener(v->finish());
        actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(56),0.34f));

        useButton=new Button(this);
        useButton.setText("เลือก ณ จุดนี้");
        useButton.setAllCaps(false);
        useButton.setTextSize(17);
        useButton.setTypeface(Typeface.DEFAULT_BOLD);
        useButton.setTextColor(Color.rgb(4,22,33));
        useButton.setBackgroundColor(Color.rgb(77,196,255));
        LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(0,dp(56),0.66f);
        up.setMargins(dp(8),0,0,0);
        actions.addView(useButton,up);
        useButton.setEnabled(false);
        useButton.setOnClickListener(v->confirmCenterPoint());
        root.addView(actions);

        setContentView(root);
        web.loadDataWithBaseURL("https://www.openstreetmap.org/", html(initialLat,initialLng,hasInitial), "text/html", "UTF-8", null);
    }

    private String html(double lat,double lng,boolean hasInitial){
        return "<!doctype html><html><head>"+
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"+
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>"+
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"+
                "<style>"+
                "html,body{margin:0;height:100%;font-family:sans-serif;background:#07101b;color:#fff;overflow:hidden}"+
                "#map{height:100%;width:100%}"+
                ".search{position:absolute;z-index:1000;top:10px;left:10px;right:10px;background:#101d2cee;border-radius:14px;padding:8px;box-shadow:0 4px 16px #0009}"+
                ".row{display:flex;gap:7px}"+
                "input{flex:1;min-width:0;background:#07101b;color:#fff;border:1px solid #38516a;border-radius:10px;padding:10px;font-size:15px;outline:none}"+
                "button{background:#4dc4ff;color:#041621;border:0;border-radius:10px;padding:0 14px;font-weight:bold}"+
                ".results{max-height:170px;overflow:auto}"+
                ".item{padding:9px 6px;border-top:1px solid #2c3b49;font-size:13px;line-height:1.35}"+
                ".hint{font-size:11px;color:#b4c4d3;padding:5px 4px 0}"+
                "#crosshair{position:absolute;z-index:999;left:50%;top:50%;width:56px;height:56px;transform:translate(-50%,-50%);pointer-events:none;filter:drop-shadow(0 1px 3px #000)}"+
                "#crosshair:before{content:'';position:absolute;left:26px;top:8px;width:4px;height:40px;background:white;border-radius:3px}"+
                "#crosshair:after{content:'';position:absolute;left:8px;top:26px;width:40px;height:4px;background:white;border-radius:3px}"+
                "#centerDot{position:absolute;z-index:1000;left:50%;top:50%;width:6px;height:6px;background:#fff;border:2px solid #17212b;border-radius:50%;transform:translate(-50%,-50%);pointer-events:none}"+
                "</style></head><body>"+
                "<div class='search'><div class='row'><input id='q' placeholder='ค้นหาสถานที่ เช่น อนุสรณ์ดอนเจดีย์'><button onclick='searchPlace()'>ค้นหา</button></div>"+
                "<div class='hint'>ค้นหา หรือใช้นิ้วเลื่อนแผนที่ให้สถานที่อยู่ตรง + สีขาว</div><div id='results' class='results'></div></div>"+
                "<div id='map'></div><div id='crosshair'></div><div id='centerDot'></div>"+
                "<script>"+
                "var map=L.map('map',{zoomControl:true}).setView(["+lat+","+lng+"],"+(hasInitial?16:6)+");"+
                "var centerLabel='"+escapeJs(getIntent().getStringExtra(EXTRA_LABEL))+"';"+
                "var reverseTimer=null;"+
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);"+
                "function reportCenter(){var p=map.getCenter();AndroidPicker.centerChanged(p.lat,p.lng,'');}"+
                "map.on('move',function(){centerLabel='';if(reverseTimer)clearTimeout(reverseTimer);reverseTimer=setTimeout(reportCenter,160);});"+
                "map.on('moveend',function(){reportCenter();});"+
                "function searchPlace(){var q=document.getElementById('q').value.trim();if(!q)return;var box=document.getElementById('results');box.innerHTML='<div class=\"item\">กำลังค้นหา…</div>';fetch('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q='+encodeURIComponent(q)).then(r=>r.json()).then(list=>{box.innerHTML='';list.forEach(x=>{var d=document.createElement('div');d.className='item';d.textContent=x.display_name;d.onclick=function(){var a=parseFloat(x.lat),b=parseFloat(x.lon);centerLabel=x.display_name||'';map.setView([a,b],17);AndroidPicker.centerChanged(a,b,centerLabel);box.innerHTML='';document.getElementById('q').blur();};box.appendChild(d);});if(!list.length)box.innerHTML='<div class=\"item\">ไม่พบสถานที่</div>';}).catch(()=>{box.innerHTML='<div class=\"item\">ค้นหาไม่สำเร็จ</div>';});}"+
                "function pickCenter(){var p=map.getCenter();AndroidPicker.pickingStarted(p.lat,p.lng);fetch('https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat='+p.lat+'&lon='+p.lng).then(r=>r.json()).then(j=>{AndroidPicker.confirmPoint(p.lat,p.lng,j.display_name||centerLabel||'');}).catch(()=>{AndroidPicker.confirmPoint(p.lat,p.lng,centerLabel||'');});}"+
                "document.getElementById('q').addEventListener('keyup',function(e){if(e.key==='Enter')searchPlace();});"+
                "setTimeout(function(){AndroidPicker.mapReady();reportCenter();},350);"+
                "</script></body></html>";
    }

    private String escapeJs(String value){
        if(value==null)return "";
        return value.replace("\\","\\\\").replace("'","\\'").replace("\n"," ").replace("\r"," ");
    }

    private void confirmCenterPoint(){
        if(!mapReady){
            Toast.makeText(this,"แผนที่ยังไม่พร้อม กรุณารอสักครู่",Toast.LENGTH_SHORT).show();
            return;
        }
        useButton.setEnabled(false);
        useButton.setText("กำลังอ่านจุด…");
        web.evaluateJavascript("pickCenter();",null);
    }

    public class Bridge {
        @JavascriptInterface public void mapReady(){
            runOnUiThread(()->{
                mapReady=true;
                useButton.setEnabled(true);
            });
        }

        @JavascriptInterface public void centerChanged(double lat,double lng,String label){
            runOnUiThread(()->{
                selectedLat=lat;
                selectedLng=lng;
                if(label!=null&&!label.trim().isEmpty())selectedLabel=label;
                else selectedLabel="";
                selectedText.setText(formatSelection());
            });
        }

        @JavascriptInterface public void pickingStarted(double lat,double lng){
            runOnUiThread(()->{
                selectedLat=lat;
                selectedLng=lng;
                selectedText.setText("กำลังอ่านชื่อบริเวณ…\n"+String.format(Locale.US,"%.6f, %.6f",lat,lng));
            });
        }

        @JavascriptInterface public void confirmPoint(double lat,double lng,String label){
            runOnUiThread(()->{
                selectedLat=lat;
                selectedLng=lng;
                selectedLabel=label==null?"":label;
                selectedText.setText(formatSelection());
                finishWithSelection();
            });
        }
    }

    private String formatSelection(){
        if(Double.isNaN(selectedLat)||Double.isNaN(selectedLng))return "เลื่อนแผนที่เพื่อเลือกตำแหน่ง";
        String coords=String.format(Locale.US,"%.6f, %.6f",selectedLat,selectedLng);
        if(selectedLabel==null||selectedLabel.trim().isEmpty()) return "＋ ตรงกลางแผนที่\n"+coords;
        return "＋ "+selectedLabel+"\n"+coords;
    }

    private void finishWithSelection(){
        if(Double.isNaN(selectedLat)||Double.isNaN(selectedLng)){
            useButton.setText("เลือก ณ จุดนี้");
            useButton.setEnabled(true);
            Toast.makeText(this,"กรุณาเลื่อนแผนที่เลือกจุดก่อน",Toast.LENGTH_SHORT).show();
            return;
        }
        Intent data=new Intent();
        data.putExtra(EXTRA_LAT,selectedLat);
        data.putExtra(EXTRA_LNG,selectedLng);
        data.putExtra(EXTRA_LABEL,selectedLabel==null?"":selectedLabel);
        setResult(RESULT_OK,data);
        finish();
    }

    @Override protected void onDestroy(){
        if(web!=null){
            web.removeJavascriptInterface("AndroidPicker");
            web.destroy();
        }
        super.onDestroy();
    }
}
