package com.pcopi.bydphonespace;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class RouteStore {
    private static final String PREF="byd_routes_v03";
    private static final String KEY="routes";

    public static final class Route {
        public String id;
        public String name;
        public boolean favorite;
        public long updated;
        public long lastOpened;
        public final ArrayList<String> paths=new ArrayList<>();
    }

    private RouteStore() {}

    public static Route create(Context c,String name,List<File> files) {
        Route r=new Route();
        r.id=UUID.randomUUID().toString();
        r.name=(name==null||name.trim().isEmpty())?"เส้นทางใหม่":name.trim();
        r.updated=System.currentTimeMillis();
        if(files!=null) for(File f:files) if(f!=null&&f.exists()) r.paths.add(f.getAbsolutePath());
        ArrayList<Route> all=new ArrayList<>(list(c)); all.add(r); saveAll(c,all); return r;
    }

    public static List<Route> list(Context c) {
        ArrayList<Route> out=readAll(c);
        cleanupMissing(out);
        Collections.sort(out,(a,b)->Long.compare(Math.max(b.updated,b.lastOpened),Math.max(a.updated,a.lastOpened)));
        return out;
    }

    public static List<Route> favorites(Context c) {
        ArrayList<Route> out=new ArrayList<>();
        for(Route r:list(c)) if(r.favorite) out.add(r);
        return out;
    }

    public static List<Route> recent(Context c) {
        ArrayList<Route> out=new ArrayList<>(list(c));
        Collections.sort(out,(a,b)->Long.compare(b.lastOpened,a.lastOpened));
        ArrayList<Route> used=new ArrayList<>();
        for(Route r:out) if(r.lastOpened>0) used.add(r);
        return used;
    }

    public static Route get(Context c,String id) {
        if(id==null)return null;
        for(Route r:list(c)) if(id.equals(r.id)) return r;
        return null;
    }

    public static List<File> files(Context c,String id) {
        Route r=get(c,id); ArrayList<File> out=new ArrayList<>(); if(r==null)return out;
        for(String p:r.paths){File f=new File(p);if(f.exists()&&f.isFile())out.add(f);} return out;
    }

    public static void touchOpened(Context c,String id) {
        ArrayList<Route> all=readAll(c); long now=System.currentTimeMillis();
        for(Route r:all) if(id!=null&&id.equals(r.id)){r.lastOpened=now;r.updated=Math.max(r.updated,now);break;}
        saveAll(c,all);
    }

    public static void toggleFavorite(Context c,String id) {
        ArrayList<Route> all=readAll(c);
        for(Route r:all) if(id!=null&&id.equals(r.id)){r.favorite=!r.favorite;r.updated=System.currentTimeMillis();break;}
        saveAll(c,all);
    }

    public static void rename(Context c,String id,String name) {
        if(name==null||name.trim().isEmpty())return;
        ArrayList<Route> all=readAll(c);
        for(Route r:all) if(id!=null&&id.equals(r.id)){r.name=name.trim();r.updated=System.currentTimeMillis();break;}
        saveAll(c,all);
    }

    public static void delete(Context c,String id) {
        ArrayList<Route> all=readAll(c);
        for(int i=all.size()-1;i>=0;i--)if(id!=null&&id.equals(all.get(i).id))all.remove(i);
        saveAll(c,all);
    }

    public static void moveImagePath(Context c,File oldFile,File newFile) {
        if(oldFile==null||newFile==null)return;
        String oldP=oldFile.getAbsolutePath(),newP=newFile.getAbsolutePath(); boolean changed=false;
        ArrayList<Route> all=readAll(c);
        for(Route r:all){for(int i=0;i<r.paths.size();i++){if(oldP.equals(r.paths.get(i))){r.paths.set(i,newP);r.updated=System.currentTimeMillis();changed=true;}}}
        if(changed)saveAll(c,all);
    }

    public static void removeImagePath(Context c,File file) {
        if(file==null)return; String p=file.getAbsolutePath(); boolean changed=false;
        ArrayList<Route> all=readAll(c);
        for(Route r:all){for(int i=r.paths.size()-1;i>=0;i--)if(p.equals(r.paths.get(i))){r.paths.remove(i);r.updated=System.currentTimeMillis();changed=true;}}
        if(changed)saveAll(c,all);
    }

    private static ArrayList<Route> readAll(Context c) {
        ArrayList<Route> out=new ArrayList<>();
        try{
            String raw=prefs(c).getString(KEY,"[]"); JSONArray a=new JSONArray(raw==null?"[]":raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i); if(o==null)continue; Route r=new Route();
                r.id=o.optString("id",UUID.randomUUID().toString()); r.name=o.optString("name","เส้นทาง"); r.favorite=o.optBoolean("favorite",false); r.updated=o.optLong("updated",0); r.lastOpened=o.optLong("lastOpened",0);
                JSONArray p=o.optJSONArray("paths"); if(p!=null)for(int j=0;j<p.length();j++){String s=p.optString(j,"");if(!s.isEmpty())r.paths.add(s);} out.add(r);
            }
        }catch(Exception ignored){}
        return out;
    }

    private static void saveAll(Context c,List<Route> routes) {
        JSONArray a=new JSONArray();
        try{
            for(Route r:routes){JSONObject o=new JSONObject();o.put("id",r.id);o.put("name",r.name);o.put("favorite",r.favorite);o.put("updated",r.updated);o.put("lastOpened",r.lastOpened);JSONArray p=new JSONArray();for(String s:r.paths)p.put(s);o.put("paths",p);a.put(o);}
        }catch(Exception ignored){}
        prefs(c).edit().putString(KEY,a.toString()).apply();
    }

    private static void cleanupMissing(List<Route> routes) {
        for(Route r:routes)for(int i=r.paths.size()-1;i>=0;i--){File f=new File(r.paths.get(i));if(!f.exists()||!f.isFile())r.paths.remove(i);}
    }

    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
}
