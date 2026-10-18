package com.pcopi.gpacknavcompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Process;

/** Local diagnostic for whether an installer actually asked to read the self APK URI. */
final class ApkHandoffLog {
    private static final String PREF="companion_apk_handoff_v012";
    private ApkHandoffLog(){}
    static void start(Context context,String method){
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString("method",method)
            .putLong("started",System.currentTimeMillis())
            .putInt("metadata",0).putInt("open",0)
            .remove("last_caller").remove("last_event").commit();
    }
    static void note(Context context,String event){
        if(context==null)return;
        int uid=Binder.getCallingUid();
        if(uid==Process.myUid())return; // Own APK check should not count as a GPack read.
        SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        long started=prefs.getLong("started",0);
        long now=System.currentTimeMillis();
        if(started<=0||now<started||now-started>15*60*1000L)return;
        String[] packages=context.getPackageManager().getPackagesForUid(uid);
        String caller=packages==null?"uid:"+uid:java.util.Arrays.toString(packages);
        SharedPreferences.Editor edit=prefs.edit()
            .putString("last_caller",caller)
            .putString("last_event",event);
        if("openFile".equals(event))edit.putInt("open",prefs.getInt("open",0)+1);
        else edit.putInt("metadata",prefs.getInt("metadata",0)+1);
        edit.commit();
    }
    static String report(Context context){
        SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        long since=p.getLong("started",0);
        if(since<=0)return "ยังไม่ได้กดทดลองติดตั้งในรอบนี้";
        StringBuilder out=new StringBuilder();
        out.append("วิธี: ").append(p.getString("method","")).append('\n');
        out.append("ตัวติดตั้งร้องขอข้อมูลไฟล์: ").append(p.getInt("metadata",0)).append(" ครั้ง\n");
        out.append("ตัวติดตั้งร้องขอเปิดอ่าน APK: ").append(p.getInt("open",0)).append(" ครั้ง\n");
        out.append("ผู้เรียกครั้งล่าสุด: ").append(p.getString("last_caller","ยังไม่พบ")).append('\n');
        out.append("ขั้นที่เรียกล่าสุด: ").append(p.getString("last_event","ยังไม่พบ")).append('\n');
        if(p.getInt("open",0)>0){
            out.append("ผล: มีแอปภายนอกร้องขออ่าน APK แล้ว แต่ยังไม่ยืนยันว่าติดตั้งใน GPack\n");
        }else{
            out.append("ผล: ยังไม่พบแอปภายนอกเปิดอ่าน APK ผ่านช่องทางนี้\n");
            out.append("หมายเหตุ: GPack อาจมีวิธีรับไฟล์อื่นที่ตัวตรวจนี้มองไม่เห็น\n");
        }
        return out.toString();
    }
}
