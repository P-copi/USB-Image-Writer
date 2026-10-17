package com.pcopi.bydphonespace;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Gravity;
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
        back.setText("←"); back.setTextSize(20); back.setAllCaps(false);
        back.setTextColor(Color.WHITE); back.setBackgroundColor(Color.rgb(15,28,43));
        back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(dp(56),dp(48)));

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12),0,0,0);
        TextView title=new TextView(this); title.setText("เลือกตำแหน่งจากแผนที่"); title.setTextSize(19); title.setTextColor(Color.WHITE);
        TextView sub=new TextView(this); sub.setText("เลื่อน/ซูมแผนที่ให้จุดหมายอยู่ตรง + สีขาว"); sub.setTextSize(12.5f); sub.setTextColor(Color.rgb(157,177,197));
        titles.addView(title); titles.addView(sub);
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
        info.setPadding(dp(14),dp(10),dp(14),dp(10));
        selectedText=new TextView(this);
        selectedText.setTextSize(13.5f);
        selectedText.setTextColor(Color.rgb(242,247,252));
        selectedText.setText(hasInitial?formatSelection():"เลื่อนแผนที่ให้จุดหมายอยู่ตรง + สีขาว");
        info.addView(selectedText,new LinearLayout.LayoutParams(-1,-2));
        root.addView(info);

        LinearLayout actions=new LinearLayout(this);
        actions.setPadding(0,dp(6),0,0);
        Button cancel=new Button(this); cancel.setText("ยกเลิก"); cancel.setAllCaps(false); cancel.setTextSize(15); cancel.setTextColor(Color.WHITE); cancel.setBackgroundColor(Color.rgb(15,28,43)); cancel.setOnClickListener(v->finish());
        actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(54),1));
        useButton=new Button(this); useButton.setText("เลือก ณ จุดนี้"); useButton.setAllCaps(false); useButton.setTextSize(16); useButton.setTextColor(Color.rgb(4,22,33)); useButton.setBackgroundColor(Color.rgb(77,196,255));
        LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(0,dp(54),1); up.setMargins(dp(8),0,0,0); actions.addView(useButton,up);
        useButton.setEnabled(hasInitial);
        useButton.setOnClickListener(v->finishWithSelection());
        root.addView(actions);

        setContentView(root);
        web.loadDataWithBaseURL("https://www.openstreetmap.org/", html(initialLat,initialLng,hasInitial), "text/html", "UTF-8", null);
    }

    private String html(double lat,double lng,boolean hasInitial){
        String initialLabel=getIntent().getStringExtra(EXTRA_LABEL);
        String safeLabel=initialLabel==null?"":initialLabel.replace("\\","\\\\").replace("'","\\'").replace("\n"," ").replace("\r"," ");
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"+
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>"+
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"+
                "<style>html,body{margin:0;height:100%;font-family:sans-serif;background:#07101b;color:#fff}#map{height:100%;width:100%}.search{position:absolute;z-index:1000;top:10px;left:10px;right:10px;background:#101d2cdd;border-radius:14px;padding:8px;box-shadow:0 4px 16px #0008}.row{display:flex;gap:7px}input{flex:1;background:#07101b;color:#fff;border:1px solid #38516a;border-radius:10px;padding:10px;font-size:15px}button{background:#4dc4ff;color:#041621;border:0;border-radius:10px;padding:0 14px;font-weight:bold}.results{max-height:170px;overflow:auto}.item{padding:9px 6px;border-top:1px solid #2c3b49;font-size:13px}.hint{font-size:11px;color:#b4c4d3;padding:5px 4px 0}#cross{position:absolute;z-index:999;left:50%;top:50%;width:44px;height:44px;transform:translate(-50%,-50%);pointer-events:none;filter:drop-shadow(0 1px 3px #000)}#cross:before{content:'';position:absolute;left:20px;top:4px;width:4px;height:36px;background:#fff;border-radius:2px}#cross:after{content:'';position:absolute;left:4px;top:20px;width:36px;height:4px;background:#fff;border-radius:2px}</style></head><body>"+
                "<div class='search'><div class='row'><input id='q' placeholder='ค้นหาสถานที่ เช่น อนุสรณ์ดอนเจดีย์'><button onclick='searchPlace()'>ค้นหา</button></div><div class='hint'>ค้นหา หรือเลื่อนแผนที่ให้จุดหมายอยู่ตรง + สีขาว</div><div id='results' class='results'></div></div>"+
                "<div id='map'></div><div id='cross'></div><script>"+
                "var currentLabel='"+safeLabel+"';var map=L.map('map').setView(["+lat+","+lng+"],"+(hasInitial?16:6)+");"+
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);"+
                "function reportCenter(){var p=map.getCenter();AndroidPicker.pointSelected(p.lat,p.lng,currentLabel);currentLabel='';}"+
                "map.on('moveend',reportCenter);"+
                "map.on('click',function(e){map.panTo(e.latlng);});"+
                "function searchPlace(){var q=document.getElementById('q').value.trim();if(!q)return;var box=document.getElementById('results');box.innerHTML='<div class=\"item\">กำลังค้นหา…</div>';fetch('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q='+encodeURIComponent(q)).then(r=>r.json()).then(list=>{box.innerHTML='';list.forEach(x=>{var d=document.createElement('div');d.className='item';d.textContent=x.display_name;d.onclick=function(){var a=parseFloat(x.lat),b=parseFloat(x.lon);currentLabel=x.display_name||'';map.setView([a,b],17);box.innerHTML='';};box.appendChild(d);});if(!list.length)box.innerHTML='<div class=\"item\">ไม่พบสถานที่</div>';}).catch(()=>{box.innerHTML='<div class=\"item\">ค้นหาไม่สำเร็จ</div>';});}"+
                "document.getElementById('q').addEventListener('keyup',function(e){if(e.key==='Enter')searchPlace();});"+
                "setTimeout(reportCenter,300);"+
                "</script></body></html>";
    }

    public class Bridge {
        @JavascriptInterface public void pointSelected(double lat,double lng,String label){
            runOnUiThread(()->{
                selectedLat=lat; selectedLng=lng; selectedLabel=label==null?"":label;
                selectedText.setText(formatSelection());
                useButton.setEnabled(true);
            });
        }
    }

    private String formatSelection(){
        String coords=String.format(Locale.US,"%.6f, %.6f",selectedLat,selectedLng);
        if(selectedLabel==null||selectedLabel.trim().isEmpty()) return "＋ "+coords;
        return "＋ "+selectedLabel+"\n"+coords;
    }

    private void finishWithSelection(){
        if(Double.isNaN(selectedLat)||Double.isNaN(selectedLng)){
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
        if(web!=null){web.removeJavascriptInterface("AndroidPicker");web.destroy();}
        super.onDestroy();
    }
}
