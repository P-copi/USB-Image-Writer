package com.pcopi.bydphonespace;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

/**
 * Android external navigation bridge.
 * GPack is a separate container; installed GPack != Google Maps being visible to Android host.
 * The app never assumes GPack's internal Maps supports external intents.
 */
public final class NavigationBridge {
    public static final String AUTO = "auto";
    public static final String DIRECT = "direct";
    public static final String GEO = "geo";
    public static final String NAV = "navigation";
    public static final String WEB_DIR = "web_dir";
    public static final String WEB_SEARCH = "web_search";
    public static final String GPACK_CLIP = "gpack_clip";
    public static final String GPACK_SHORTCUT_CLIP = "gpack_shortcut_clip";
    public static final String GPACK_CLIP_URL = "gpack_clip_url";
    public static final String NEUSOFT_GEO = "neusoft_geo";
    public static final String NEUSOFT_PACKAGE = "com.neusoft.na.navigation";
    public static final String GPACK_GEO = "gpack_geo";
    public static final String GPACK_WEB = "gpack_web";
    // Standard Android intents directed at the GPack HOST package, not its internal Google apps.
    public static final String GPACK_SEARCH_VIEW = "gpack_search_view";
    public static final String GPACK_SHARE_TEXT = "gpack_share_text";
    public static final String GPACK_SHARE_MAPS_URL = "gpack_share_maps_url";
    public static final String GPACK_PACKAGE="com.gpack.agent";
    public static final String GPACK_INPUT_ASSIST="gpack_input_assist";
    // Exact AndroidManifest actions found in installed GPack 2.20260321.01.
    // Intent payloads remain experimental until validated on the car.
    public static final String GPACK_NATIVE_NAV="gpack_native_nav";
    public static final String GPACK_NATIVE_GEO="gpack_native_geo";
    public static final String GPACK_NATIVE_LAUNCH="gpack_native_launch";
    public static final String GPACK_NAV_ACTIVITY="com.bratwzi.auto.gpack.activity.LaunchAppActivity";
    public static final String GPACK_NAV_ACTION="com.intent.ACTION_SUPPORT_MAP_NAVIGATION";
    public static final String GPACK_LAUNCH_ACTION="com.gpack.launch";

    private static final String PREFS = "byd_navigation_bridge";
    private static final String KEY_MODE = "confirmed_mode";
    private NavigationBridge() {}

    public static String selected(Context context) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_MODE,AUTO);
    }

    public static void saveConfirmed(Context context,String mode){
        if(!isValid(mode))return;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_MODE,mode).apply();
    }

    public static void reset(Context context){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY_MODE).apply();
    }

    private static boolean isValid(String mode){
        return GPACK_INPUT_ASSIST.equals(mode)||GpackUnifiedRoutes.isMode(mode)||DIRECT.equals(mode)||GEO.equals(mode)||NAV.equals(mode)
                ||WEB_DIR.equals(mode)||WEB_SEARCH.equals(mode)||GPACK_CLIP.equals(mode)
                ||GPACK_GEO.equals(mode)||GPACK_WEB.equals(mode)
                ||GPACK_SEARCH_VIEW.equals(mode)||GPACK_SHARE_TEXT.equals(mode)
                ||GPACK_SHARE_MAPS_URL.equals(mode)
                ||GPACK_CLIP_URL.equals(mode)||NEUSOFT_GEO.equals(mode)
                ||GPACK_SHORTCUT_CLIP.equals(mode)
                ||GPACK_NATIVE_NAV.equals(mode)||GPACK_NATIVE_GEO.equals(mode)||GPACK_NATIVE_LAUNCH.equals(mode);
    }

    public static String label(String mode){
        if(GPACK_INPUT_ASSIST.equals(mode))return "ใช้ตัวช่วยใส่จุดหมายในช่อง Maps ภายใน GPack";
        if(GpackUnifiedRoutes.isMode(mode))return GpackUnifiedRoutes.title(GpackUnifiedRoutes.index(mode));
        if(GPACK_NATIVE_NAV.equals(mode))return "GPack NAV action + google.navigation: พิกัด";
        if(GPACK_NATIVE_GEO.equals(mode))return "GPack NAV action + geo: พิกัด";
        if(GPACK_NATIVE_LAUNCH.equals(mode))return "GPack launch action + Google Maps";
        if(GPACK_SHORTCUT_CLIP.equals(mode))return "ทางลัด Maps ของ GPack + คัดลอกจุดหมาย";
        if(GPACK_CLIP.equals(mode))return "ใช้ได้แล้ว: คัดลอกจุดหมาย + เปิด GPack";
        if(GPACK_CLIP_URL.equals(mode))return "ลองลิงก์เส้นทาง Maps ผ่าน Chrome ใน GPack";
        if(NEUSOFT_GEO.equals(mode))return "ลองระบบนำทางของรถ BYD (ไม่ใช่ Google Maps)";
        if(GPACK_SHARE_TEXT.equals(mode))return "ทดลองแชร์ชื่อสถานที่ไป GPack";
        if(GPACK_SHARE_MAPS_URL.equals(mode))return "ทดลองแชร์ลิงก์ Maps ไป GPack";
        if(GPACK_SEARCH_VIEW.equals(mode))return "ทดลองส่งลิงก์ค้นหา Maps เข้า GPack";
        if(GPACK_GEO.equals(mode))return "GPack โดยตรง: geo: (ทดสอบ)";
        if(GPACK_WEB.equals(mode))return "GPack โดยตรง: Google Maps URL (ทดสอบ)";
        if(DIRECT.equals(mode))return "Google Maps ตรง (แพ็กเกจมาตรฐาน)";
        if(GEO.equals(mode))return "geo: ส่งชื่อหรือพิกัด";
        if(NAV.equals(mode))return "google.navigation: แบบไม่ล็อกแพ็กเกจ";
        if(WEB_DIR.equals(mode))return "Google Maps URL: เส้นทาง";
        if(WEB_SEARCH.equals(mode))return "Google Maps URL: ค้นหาจุดหมาย";
        return "อัตโนมัติ (Maps ตรง → GPack → ระบบ Android)";
    }

    public static boolean hasGpack(Context context){
        try{context.getPackageManager().getPackageInfo(GPACK_PACKAGE,0);return true;}
        catch(Exception e){return context.getPackageManager().getLaunchIntentForPackage(GPACK_PACKAGE)!=null;}
    }

    public static Intent intentFor(String mode,String destination){
        String q=destination==null?"":destination.trim();
        if(GpackUnifiedRoutes.isMode(mode))return GpackUnifiedRoutes.build(GpackUnifiedRoutes.index(mode),q);
        if(GPACK_NATIVE_NAV.equals(mode)||GPACK_NATIVE_GEO.equals(mode)||GPACK_NATIVE_LAUNCH.equals(mode)){
            Intent remote=new Intent(GPACK_NATIVE_LAUNCH.equals(mode)?GPACK_LAUNCH_ACTION:GPACK_NAV_ACTION);
            remote.setComponent(new ComponentName(GPACK_PACKAGE,GPACK_NAV_ACTIVITY));
            String navUri="google.navigation:q="+Uri.encode(q)+"&mode=d";
            String geoUri="geo:0,0?q="+Uri.encode(q);
            remote.setData(Uri.parse(GPACK_NATIVE_GEO.equals(mode)?geoUri:navUri));
            // Standard public string inputs. GPack-specific extra names are only
            // compatibility hints, not confirmed proprietary API contracts.
            remote.putExtra(Intent.EXTRA_TEXT,q);
            remote.putExtra("destination",q);
            remote.putExtra("query",q);
            remote.putExtra("packageName","com.google.android.apps.maps");
            remote.putExtra("pkgName","com.google.android.apps.maps");
            return remote;
        }
        if(GPACK_CLIP.equals(mode)||GPACK_CLIP_URL.equals(mode)||GPACK_SHORTCUT_CLIP.equals(mode))
            return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(GPACK_PACKAGE);
        if(NEUSOFT_GEO.equals(mode))
            return new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(q)))
                .setPackage(NEUSOFT_PACKAGE);
        if(GPACK_SHARE_TEXT.equals(mode)||GPACK_SHARE_MAPS_URL.equals(mode)){
            String shared=GPACK_SHARE_TEXT.equals(mode)?q:mapsSearchUrl(q);
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,shared);
            send.setPackage(GPACK_PACKAGE);
            return send;
        }
        if(GPACK_SEARCH_VIEW.equals(mode)){
            return new Intent(Intent.ACTION_VIEW,Uri.parse(mapsSearchUrl(q))).setPackage(GPACK_PACKAGE);
        }
        Intent intent;
        if(DIRECT.equals(mode)||NAV.equals(mode)){
            intent=new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(q)+"&mode=d"));
            if(DIRECT.equals(mode))intent.setPackage("com.google.android.apps.maps");
        }else if(GPACK_GEO.equals(mode)||GEO.equals(mode)){
            intent=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(q)));
            if(GPACK_GEO.equals(mode))intent.setPackage(GPACK_PACKAGE);
        }else if(GPACK_WEB.equals(mode)||WEB_DIR.equals(mode)){
            intent=new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(q)+"&travelmode=driving"));
            if(GPACK_WEB.equals(mode))intent.setPackage(GPACK_PACKAGE);
        }else{
            intent=new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query="+Uri.encode(q)));
        }
        return intent;
    }

    public static String mapsSearchUrl(String destination){
        String q=destination==null?"":destination.trim();
        return "https://www.google.com/maps/search/?api=1&query="+Uri.encode(q);
    }

    public static String mapsDirectionsUrl(String destination){
        String q=destination==null?"":destination.trim();
        return "https://www.google.com/maps/dir/?api=1&destination="+
            Uri.encode(q)+"&travelmode=driving";
    }

    /** Opens the known GPack launcher without undocumented internal APIs or permissions. */
    public static boolean openGpack(Context context){
        Intent launcher=context.getPackageManager().getLaunchIntentForPackage(GPACK_PACKAGE);
        if(launcher==null){
            launcher=new Intent(Intent.ACTION_MAIN);
            launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            launcher.setClassName(GPACK_PACKAGE,"com.bratwzi.auto.gpack.activity.SplashActivity");
        }
        try{
            if(!(context instanceof android.app.Activity))launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launcher);
            return true;
        }catch(Exception e){
            Toast.makeText(context,"เปิด GPack จากแอปไม่ได้ ลองกดไอคอน GPack บนจอรถ",Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static boolean launchUsing(Context context,String mode,String destination){
        if(destination==null||destination.trim().isEmpty()){
            Toast.makeText(context,"ยังไม่ได้เลือกจุดหมาย",Toast.LENGTH_SHORT).show();
            return false;
        }
        GpackInputBridge.remember(context,destination);
        if(GPACK_INPUT_ASSIST.equals(mode)){
            ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(
                ClipData.newPlainText("BYD Phone Space destination",destination.trim()));
            GpackInputBridge.arm(context,destination);
            boolean opened=openGpack(context);
            if(!opened)GpackInputBridge.stop(context,"ไม่สามารถเปิด GPack");
            else Toast.makeText(context,"เปิด GPack แล้ว • รอใส่จุดหมายใน Maps หากตัวช่วยไม่พร้อมให้แตะค้นหาและวางข้อความ",Toast.LENGTH_LONG).show();
            return opened;
        }
        if(GPACK_SHORTCUT_CLIP.equals(mode)){
            ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(
                ClipData.newPlainText("จุดหมาย BYD Phone Space",destination.trim()));
            if(GpackShortcutHelper.confirmed(context)&&GpackShortcutHelper.open(context)){
                Toast.makeText(context,"เปิด Maps ผ่านทางลัดแล้ว • แตะค้นหาและวางจุดหมาย",Toast.LENGTH_LONG).show();
                return true;
            }
            Toast.makeText(context,"ยังเรียกทางลัด Maps ไม่ได้ • เปิด GPack ตามวิธีที่ผ่านแล้วแทน",Toast.LENGTH_LONG).show();
            return openGpack(context);
        }
        if(GPACK_CLIP.equals(mode)||GPACK_CLIP_URL.equals(mode)){
            boolean link=GPACK_CLIP_URL.equals(mode);
            String copied=link?mapsDirectionsUrl(destination):destination.trim();
            ClipboardManager cm=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText(link?"ลิงก์เส้นทาง Google Maps":"จุดหมาย BYD Phone Space",copied));
            Toast.makeText(context,link
                    ?"คัดลอกลิงก์แล้ว → ใน GPack เปิด Chrome → แตะช่องที่อยู่ → กดค้าง → วาง → ไป"
                    :"คัดลอกแล้ว → เปิด Maps ใน GPack → แตะค้นหา → กดค้าง → วาง",Toast.LENGTH_LONG).show();
            if(hasGpack(context))return openGpack(context);
            Toast.makeText(context,"ไม่พบ GPack บนเครื่องนี้ • จุดหมายถูกคัดลอกไว้แล้ว",Toast.LENGTH_LONG).show();
            return false;
        }
        try{
            Intent intent=intentFor(mode,destination);
            if(!(context instanceof android.app.Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        }catch(Exception e){
            Toast.makeText(context,"Android ไม่พบตัวรับคำสั่งสำหรับ "+label(mode),Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static boolean launch(Context context,String destination){
        if(destination==null||destination.trim().isEmpty()){
            Toast.makeText(context,"ยังไม่ได้เลือกจุดหมาย",Toast.LENGTH_SHORT).show();
            return false;
        }
        GpackInputBridge.remember(context,destination);
        String mode=selected(context);
        if(!AUTO.equals(mode))return launchUsing(context,mode,destination);
        // GPack Maps is not installed as host Maps, so do not accidentally route to
        // com.neusoft.na.navigation when the user expects Google Maps.
        try{
            Intent direct=intentFor(DIRECT,destination);
            context.startActivity(direct);
            return true;
        }catch(Exception ignored){}
        if(hasGpack(context))return launchUsing(context,GPACK_CLIP,destination);
        try{
            context.startActivity(intentFor(GEO,destination));
            return true;
        }catch(Exception ignored){}
        Toast.makeText(context,"ไม่พบตัวนำทาง กรุณาเปิดการทดสอบ Maps / GPack",Toast.LENGTH_LONG).show();
        return false;
    }
}
