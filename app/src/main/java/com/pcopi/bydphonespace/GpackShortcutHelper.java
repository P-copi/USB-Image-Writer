package com.pcopi.bydphonespace;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.os.Process;
import android.widget.Toast;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional standard Android shortcut-provider bridge.
 *
 * A pinned GPack shortcut belongs to the BYD launcher. We never read its private
 * database or assume Android grants this app shortcut-host privileges.
 *
 * If GPack exposes ACTION_CREATE_SHORTCUT, the user may request one and we can
 * use ONLY an explicit Intent returned by GPack targeting the GPack host package.
 */
final class GpackShortcutHelper {
    static final int REQUEST_CREATE = 7239;
    private static final String PREF="gpack_explicit_map_shortcut_v1";
    private static final String KEY_INTENT="explicit_intent";
    private static final String KEY_CONFIRMED="user_confirmed";
    private GpackShortcutHelper(){}

    static List<ResolveInfo> createShortcutProviders(Context context){
        Intent intent=new Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(NavigationBridge.GPACK_PACKAGE);
        try{return context.getPackageManager().queryIntentActivities(intent,0);}
        catch(Exception e){return new ArrayList<>();}
    }

    static Intent createShortcutRequest(Context context){
        List<ResolveInfo> handlers=createShortcutProviders(context);
        if(handlers.isEmpty())return null;
        ResolveInfo match=handlers.get(0);
        if(match.activityInfo==null)return null;
        // Use the actual provider GPack has publicly registered.
        return new Intent(Intent.ACTION_CREATE_SHORTCUT)
            .setComponent(new ComponentName(match.activityInfo.packageName,match.activityInfo.name));
    }

    static boolean saveFromResult(Context context,Intent reply){
        if(reply==null)return false;
        Intent shortcut;
        try{
            if(android.os.Build.VERSION.SDK_INT>=33){
                shortcut=reply.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT,Intent.class);
            }else{
                shortcut=reply.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            }
        }catch(Exception e){return false;}
        if(!safe(shortcut))return false;
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(KEY_INTENT,shortcut.toUri(Intent.URI_INTENT_SCHEME))
                .putBoolean(KEY_CONFIRMED,false).commit();
    }

    static boolean hasCandidate(Context context){return getStored(context)!=null;}
    static boolean confirmed(Context context){
        return hasCandidate(context)&&context.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getBoolean(KEY_CONFIRMED,false);
    }
    static void confirm(Context context){
        if(hasCandidate(context))
            context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_CONFIRMED,true).apply();
    }
    static void reset(Context context){
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().clear().apply();
    }

    static boolean open(Context context){
        Intent shortcut=getStored(context);
        if(shortcut==null)return false;
        try{
            if(!(context instanceof android.app.Activity))
                shortcut.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(shortcut);
            return true;
        }catch(Exception e){
            Toast.makeText(context,"ทางลัด GPack เรียกจาก BYD Phone Space ไม่สำเร็จ",Toast.LENGTH_LONG).show();
            return false;
        }
    }

    static String report(Context context){
        StringBuilder s=new StringBuilder();
        s.append("GPack Maps shortcut investigation\n");
        PackageManager pm=context.getPackageManager();
        s.append("GPack visible: ").append(NavigationBridge.hasGpack(context)).append('\n');
        List<ResolveInfo> config=createShortcutProviders(context);
        s.append("GPack ACTION_CREATE_SHORTCUT providers: ").append(config.size()).append('\n');
        for(ResolveInfo r:config){
            if(r.activityInfo!=null)s.append("  ").append(r.activityInfo.packageName)
                .append("/").append(r.activityInfo.name).append('\n');
        }
        LauncherApps launcher=(LauncherApps)context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if(launcher!=null){
            boolean granted=false;
            try{granted=launcher.hasShortcutHostPermission();}catch(Exception ignored){}
            s.append("Android shortcut host permission: ").append(granted).append('\n');
            if(granted){
                try{
                    LauncherApps.ShortcutQuery query=new LauncherApps.ShortcutQuery();
                    query.setPackage(NavigationBridge.GPACK_PACKAGE);
                    query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                        |LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                        |LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
                    List<ShortcutInfo> list=launcher.getShortcuts(query,Process.myUserHandle());
                    s.append("GPack shortcuts visible: ").append(list==null?0:list.size()).append('\n');
                    if(list!=null)for(ShortcutInfo sc:list){
                        s.append("  ").append(sc.getId()).append(": ")
                          .append(sc.getShortLabel()).append('\n');
                    }
                }catch(Exception e){
                    s.append("GPack shortcuts unavailable: ").append(e.getClass().getSimpleName()).append('\n');
                }
            }else{
                s.append("Pinned shortcut data belongs to car launcher; Android restricts third-party access.\n");
            }
        }
        try{
            Intent activities=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> targets=pm.queryIntentActivities(activities,0);
            int count=0;
            if(targets!=null)for(ResolveInfo r:targets){
                if(r.activityInfo==null)continue;
                String label=r.loadLabel(pm)==null?"":r.loadLabel(pm).toString();
                String name=label.toLowerCase(java.util.Locale.ROOT);
                if(name.contains("maps")||name.contains("แผนที่")||name.contains("gpack")){
                    if(count++>=12)break;
                    s.append("Host launcher item: ").append(label).append(" / ")
                        .append(r.activityInfo.packageName).append(" / ").append(r.activityInfo.name).append('\n');
                }
            }
        }catch(Exception e){s.append("Launcher items could not be read\n");}
        s.append("GPack shortcut returned by provider: ").append(hasCandidate(context)).append('\n');
        s.append("Shortcut manually confirmed to open Maps: ").append(confirmed(context)).append('\n');
        return s.toString();
    }

    private static Intent getStored(Context context){
        String encoded=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_INTENT,"");
        if(encoded.isEmpty())return null;
        try{
            Intent stored=Intent.parseUri(encoded,Intent.URI_INTENT_SCHEME);
            return safe(stored)?stored:null;
        }catch(URISyntaxException e){return null;}
    }

    private static boolean safe(Intent intent){
        if(intent==null)return false;
        ComponentName component=intent.getComponent();
        if(component==null||!NavigationBridge.GPACK_PACKAGE.equals(component.getPackageName()))return false;
        return intent.getPackage()==null||NavigationBridge.GPACK_PACKAGE.equals(intent.getPackage());
    }
}
