package com.pcopi.bydphonespace;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * A local, user-initiated destination handoff for GPack's real text field.
 * This is not an undocumented GPack API. It only stores the last navigation
 * query and optional short-lived accessibility intent in app-private prefs.
 */
public final class GpackInputBridge {
    private static final String PREF="gpack_input_bridge_v074";
    private static final String DEST="last_destination",ARM="assist_armed_at",STATE="last_observation";
    private static final long TIMEOUT_MS=90_000L;
    private GpackInputBridge(){}

    public static void remember(Context c,String destination){
        if(destination==null)return;
        String q=destination.trim();
        if(q.isEmpty()||q.length()>480)return;
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(DEST,q).apply();
    }
    public static String current(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(DEST,"");
    }
    public static void arm(Context c,String destination){
        remember(c,destination);
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putLong(ARM,System.currentTimeMillis())
            .putString(STATE,"รอ Maps ใน GPack เพื่อใส่จุดหมาย").apply();
    }
    public static boolean isArmed(Context c){
        long t=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(ARM,0);
        long diff=System.currentTimeMillis()-t;
        return t>0&&diff>=0&&diff<TIMEOUT_MS&&!current(c).isEmpty();
    }
    public static void stop(Context c,String outcome){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .remove(ARM).putString(STATE,outcome==null?"หยุด":outcome).apply();
    }
    public static String observation(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        long armedAt=p.getLong(ARM,0);
        String state=p.getString(STATE,"ยังไม่ทดลอง");
        if(armedAt>0&&!isArmed(c))return state+" • หมดเวลารอ";
        return state;
    }
    public static void clear(Context c){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().clear().apply();
    }
}
