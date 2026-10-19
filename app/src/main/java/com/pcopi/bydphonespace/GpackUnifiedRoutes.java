package com.pcopi.bydphonespace;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;

/**
 * Controlled variants of GPack 2.20260321.01 navigation bridge.
 *
 * The two action names and LaunchAppActivity are taken from the user's
 * installed GPack APK. Accepted extras and URI formats are NOT confirmed.
 * Any result must be validated on the car, not inferred from startActivity().
 */
public final class GpackUnifiedRoutes {
    public static final String GPACK="com.gpack.agent";
    public static final String LAUNCH_ACTIVITY="com.bratwzi.auto.gpack.activity.LaunchAppActivity";
    public static final String NAV_ACTION="com.intent.ACTION_SUPPORT_MAP_NAVIGATION";
    public static final String LAUNCH_ACTION="com.gpack.launch";
    public static final String MAPS_PACKAGE="com.google.android.apps.maps";
    private static final String PREFIX="gpack_unified_nav_";
    public static final int COUNT=10;
    private static final String[] TITLES={
        "01 — NAV action เปล่า ไม่แนบ URI",
        "02 — GPack launch เปล่า ไม่แนบ URI",
        "03 — NAV action + จุดหมายใน Extra",
        "04 — GPack launch + จุดหมายใน Extra",
        "05 — NAV Activity ตรง + จุดหมาย Extra",
        "06 — GPack launch Activity ตรง + Extra",
        "07 — NAV Activity ตรง + google.navigation:q=",
        "08 — NAV Activity ตรง + geo:0,0?q=",
        "09 — GPack launch Activity ตรง + google.navigation:q=",
        "10 — NAV Activity ตรง + google.navigation:place="
    };
    private GpackUnifiedRoutes(){}
    public static String id(int index){
        if(index<0||index>=COUNT)throw new IllegalArgumentException("probe index");
        return PREFIX+index;
    }
    public static int index(String mode){
        if(mode==null||!mode.startsWith(PREFIX))return -1;
        try{
            int n=Integer.parseInt(mode.substring(PREFIX.length()));
            return n>=0&&n<COUNT?n:-1;
        }catch(Exception e){return -1;}
    }
    public static boolean isMode(String mode){return index(mode)>=0;}
    public static boolean carriesDestination(int index){return index>=2&&index<COUNT;}
    public static String title(int index){return TITLES[index];}
    public static Intent build(int index,String destination){
        if(index<0||index>=COUNT)throw new IllegalArgumentException("probe index");
        String q=destination==null?"":destination.trim();
        Intent intent=new Intent((index==1||index==3||index==5||index==8)
                ?LAUNCH_ACTION:NAV_ACTION);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        if(index==4||index==5||index>=6){
            intent.setComponent(new ComponentName(GPACK,LAUNCH_ACTIVITY));
        }else{
            intent.setPackage(GPACK);
        }
        // GPack's public filters do NOT advertise geo: or google.navigation:
        // data schemes. Only explicit-Component probes include URI data.
        if(index==6||index==8){
            intent.setData(Uri.parse("google.navigation:q="+Uri.encode(q)+"&mode=d"));
        }else if(index==7){
            intent.setData(Uri.parse("geo:0,0?q="+Uri.encode(q)));
        }else if(index==9){
            intent.setData(Uri.parse("google.navigation:place="+Uri.encode(q)));
        }
        if(carriesDestination(index)){
            // These are candidate public text extras, not a confirmed GPack API.
            intent.putExtra(Intent.EXTRA_TEXT,q);
            intent.putExtra("destination",q);
            intent.putExtra("query",q);
            intent.putExtra("packageName",MAPS_PACKAGE);
            intent.putExtra("pkgName",MAPS_PACKAGE);
        }
        return intent;
    }
    public static String explain(int index){
        if(index<2)return "ตรวจเพียงว่าระบบเปิด Action ได้หรือไม่; ไม่มีข้อมูลจุดหมายส่งออก";
        if(index<4)return "ส่งจุดหมายเป็น Extras ไม่แนบ URI; ให้ GPack จับคู่ Action เอง";
        if(index<6)return "เรียก LaunchAppActivity ตรงและส่ง Extras โดยไม่แนบ URI";
        return "เรียก Activity ตรงพร้อม URI; GPack อาจไม่รองรับรูปแบบนี้";
    }
}
