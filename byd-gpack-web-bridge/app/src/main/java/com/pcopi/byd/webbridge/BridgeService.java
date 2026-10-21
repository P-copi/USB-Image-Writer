package com.pcopi.byd.webbridge;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class BridgeService extends Service {
  static final int PORT=8767;
  private volatile boolean running=false;
  private ServerSocket socket;
  private Thread accepting;
  private final ExecutorService workers=Executors.newFixedThreadPool(3);
  private SharedPreferences prefs;

  @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("bridge",MODE_PRIVATE);makeForeground();startServer();}
  @Override public int onStartCommand(Intent intent,int flags,int startId){return START_STICKY;}
  @Override public IBinder onBind(Intent intent){return null;}
  private void makeForeground(){
    String channel="byd_web_bridge_v029";
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    if(Build.VERSION.SDK_INT>=26){
      nm.createNotificationChannel(new NotificationChannel(channel,"BYD Web Bridge",NotificationManager.IMPORTANCE_LOW));
    }
    Intent open=new Intent(this,MainActivity.class);
    PendingIntent p=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification notification=new Notification.Builder(this,channel)
       .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("BYD Web Bridge V0.2.9 • พอร์ต 8766")
       .setContentText("127.0.0.1:8767 • เปิดใน Browser ภายใน GPack")
       .setOngoing(true).setContentIntent(p).build();
    startForeground(901,notification);
  }
  private void startServer(){
    if(running)return;
    running=true;
    accepting=new Thread(()->{
      try{
        ServerSocket ss=new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),PORT));
        socket=ss;
        prefs.edit().putString("server","กำลังฟัง 127.0.0.1:"+PORT+" (loopback เท่านั้น)").apply();
        while(running){
          Socket peer=ss.accept();
          workers.execute(()->serve(peer));
        }
      }catch(Exception e){
        if(running)prefs.edit().putString("server","ผิดพลาด: "+e.getClass().getSimpleName()+" "+e.getMessage()).apply();
      }
    },"bridge-listener");
    accepting.start();
  }
  private void serve(Socket client){
    try(Socket s=client){
      s.setSoTimeout(4000);
      BufferedReader br=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
      String line=br.readLine();
      if(line==null || line.length()>2000){respond(s,400,"text/plain; charset=utf-8","Bad request");return;}
      String[] parts=line.split(" ");
      if(parts.length<2 || !"GET".equals(parts[0])){respond(s,405,"text/plain; charset=utf-8","Only GET allowed");return;}
      String host="";int headerLen=0;String h;
      while((h=br.readLine())!=null && !h.isEmpty()){
        headerLen+=h.length();
        if(headerLen>12000){respond(s,431,"text/plain; charset=utf-8","Too many headers");return;}
        if(h.regionMatches(true,0,"Host:",0,5))host=h.substring(5).trim().toLowerCase(Locale.ROOT);
      }
      // Keep requests local and reject forged Host names.
      if(!(host.equals("127.0.0.1:"+PORT)||host.equals("localhost:"+PORT))){
        respond(s,403,"text/plain; charset=utf-8","Host forbidden");return;
      }
      String path=parts[1].split("\\?",2)[0];
      if("/".equals(path) || "/index.html".equals(path)){
        prefs.edit().putInt("visits",prefs.getInt("visits",0)+1).putString("lastSeen",timestamp()).apply();
        respond(s,200,"text/html; charset=utf-8",asset("index.html"));
      }else if("/bridge.js".equals(path)){
        respond(s,200,"text/javascript; charset=utf-8",asset("bridge.js"));
      }else if("/api/state".equals(path)){
        prefs.edit().putInt("polls",prefs.getInt("polls",0)+1).putString("lastSeen",timestamp()).apply();
        respond(s,200,"application/json; charset=utf-8",jsonState());
      }else if("/ping".equals(path)){
        respond(s,200,"text/plain; charset=utf-8","BYD WEB BRIDGE OK");
      }else{
        respond(s,404,"text/plain; charset=utf-8","Not found");
      }
    }catch(Exception ignored){}
  }
  private String timestamp(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());}
  private String asset(String name)throws IOException{
    try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] buf=new byte[8192];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);
      return out.toString("UTF-8");
    }
  }
  private String esc(String s){
    if(s==null)return "";
    return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")
      .replace("<","\\u003c").replace(">","\\u003e").replace("&","\\u0026");
  }
  private String jsonState(){
    String destination=prefs.getString("destination","13.7563310,100.5017650");
    String label=prefs.getString("label","พิกัดตัวอย่าง");
    String link=Uri.encode(destination);
    String search,dir;
    if(destination.startsWith("https://")){
      Uri uri=Uri.parse(destination);String host=uri.getHost();
      if(host!=null&&(host.equals("www.google.com")||host.equals("google.com")||host.equals("maps.google.com")||host.equals("maps.app.goo.gl"))){
        search=destination;dir=destination;
      }else{
        search=mapsSearch(destination);dir=mapsDir(destination);
      }
    }else{
      search=mapsSearch(destination);dir=mapsDir(destination);
    }
    return "{\"connected\":true,\"revision\":"+prefs.getLong("revision",0)
      +",\"sentAtMs\":"+prefs.getLong("sentAtMs",0)
      +",\"destination\":\""+esc(destination)+"\",\"label\":\""+esc(label)+"\""
      +",\"searchUrl\":\""+esc(search)+"\",\"directionsUrl\":\""+esc(dir)+"\""
      +",\"geoUrl\":\""+esc("geo:0,0?q="+link)+"\""
      +",\"navigationUrl\":\""+esc("google.navigation:q="+link+"&mode=d")+"\""
      +",\"feedback\":\""+esc(prefs.getString("feedback","ยังไม่ยืนยัน"))+"\"}";
  }
  static String mapsSearch(String q){return "https://www.google.com/maps/search/?api=1&query="+Uri.encode(q);}
  static String mapsDir(String q){return "https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(q)+"&travelmode=driving&dir_action=navigate";}
  private void respond(Socket s,int status,String contentType,String body)throws IOException{
    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
    OutputStream out=s.getOutputStream();
    String pre="HTTP/1.1 "+status+" "+(status==200?"OK":"Error")+"\r\n"
      +"Content-Type: "+contentType+"\r\n"+"Content-Length: "+bytes.length+"\r\n"
      +"Cache-Control: no-store\r\n"+"X-Content-Type-Options: nosniff\r\n"
      +"X-Frame-Options: DENY\r\n"
      +"Content-Security-Policy: default-src 'self'; connect-src 'self'; script-src 'self'; style-src 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'\r\n"
      +"Referrer-Policy: no-referrer\r\n"+"Connection: close\r\n\r\n";
    out.write(pre.getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
  }
  @Override public void onDestroy(){
    running=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}
    workers.shutdownNow();
    prefs.edit().putString("server","หยุดแล้ว").apply();
    super.onDestroy();
  }
}