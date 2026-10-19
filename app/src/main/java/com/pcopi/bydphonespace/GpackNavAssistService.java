package com.pcopi.bydphonespace;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Explicit opt-in, single-purpose bridge to the UI of GPack/virtual Maps.
 * No reads outside the allowed package, no log of the accessibility tree,
 * no global gestures, screenshots or automatic Start Navigation clicks.
 * User has to enable it in system Accessibility settings.
 */
public final class GpackNavAssistService extends AccessibilityService {
    private long lastCheck=0L;
    private boolean triedOpenMaps=false;
    private static final int MAX_NODES=250;

    private static boolean allowed(CharSequence value){
        if(value==null)return false;
        String s=value.toString();
        return "com.gpack.agent".equals(s)||"com.google.android.apps.maps".equals(s);
    }
    private static String normalized(CharSequence s){
        return s==null?"":s.toString().trim().toLowerCase(Locale.ROOT);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||!GpackInputBridge.isArmed(this))return;
        if(!allowed(event.getPackageName()))return;
        long now=SystemClock.uptimeMillis();
        if(now-lastCheck<400)return;
        lastCheck=now;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||!allowed(root.getPackageName()))return;
        try{
            String query=GpackInputBridge.current(this);
            if(query.isEmpty())return;
            ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
            queue.add(root);
            AccessibilityNodeInfo searchLike=null;
            AccessibilityNodeInfo mapsIcon=null;
            int scanned=0;
            while(!queue.isEmpty()&&scanned++<MAX_NODES){
                AccessibilityNodeInfo n=queue.removeFirst();
                if(n==null)continue;
                String nodePackage=normalized(n.getPackageName());
                if(!"com.gpack.agent".equals(nodePackage)
                        &&!"com.google.android.apps.maps".equals(nodePackage))continue;
                if(n.isEditable()&&!n.isPassword()){
                    String content=normalized(n.getContentDescription());
                    String text=normalized(n.getText());
                    String viewId=normalized(n.getViewIdResourceName());
                    String hint=android.os.Build.VERSION.SDK_INT>=26?normalized(n.getHintText()):"";
                    // Prevent accidentally inserting the destination into GPack's
                    // app search, Chrome address bar, or an unrelated guest app.
                    boolean mapsLabel=hint.contains("search here")
                        ||hint.contains("search maps")||hint.contains("google maps")
                        ||hint.contains("ค้นหาที่นี่")||hint.contains("ค้นหาสถานที่")
                        ||content.contains("search here")||content.contains("search maps")
                        ||content.contains("ค้นหาที่นี่")
                        ||text.contains("search here")||text.contains("ค้นหาที่นี่");
                    boolean mapsViewId=viewId.contains("search_omnibox_text_box")
                        ||viewId.contains("search_omnibox_textbox");
                    if(mapsLabel||mapsViewId
                        ||("com.google.android.apps.maps".equals(nodePackage)&&n.isFocused())){
                        searchLike=n;
                    }
                }
                if(mapsIcon==null){
                    String desc=normalized(n.getContentDescription());
                    String text=normalized(n.getText());
                    if(("maps".equals(desc)||"maps".equals(text)||"google maps".equals(desc)
                            ||"google maps".equals(text))&&!n.isEditable()){
                        mapsIcon=n;
                    }
                }
                for(int i=0;i<n.getChildCount();i++){
                    AccessibilityNodeInfo child=n.getChild(i);
                    if(child!=null)queue.addLast(child);
                }
            }
            AccessibilityNodeInfo target=searchLike;
            if(target!=null){
                Bundle data=new Bundle();
                data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,query);
                boolean success=target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data);
                if(success){
                    GpackInputBridge.stop(this,
                        "ใส่จุดหมายในช่องค้นหาแล้ว: "+query
                        +" • กรุณาตรวจผลลัพธ์บน Maps และเลือกนำทางด้วยตัวเอง");
                }else{
                    GpackInputBridge.stop(this,"พบช่องค้นหา แต่ GPack ไม่ยอมรับ ACTION_SET_TEXT");
                }
                triedOpenMaps=false;
                return;
            }
            if(!triedOpenMaps&&mapsIcon!=null){
                AccessibilityNodeInfo click=mapsIcon;
                for(int j=0;j<6&&!click.isClickable()&&click.getParent()!=null;j++){
                    click=click.getParent();
                }
                if(click.isClickable()){
                    triedOpenMaps=true;
                    boolean ok=click.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if(!ok)GpackInputBridge.stop(this,"พบไอคอน Maps แต่กดผ่าน Accessibility ไม่ได้");
                }
            }
        }catch(Exception e){
            GpackInputBridge.stop(this,"การเข้าถึงหน้าค้นหาไม่สำเร็จ: "+e.getClass().getSimpleName());
        }
    }
    @Override public void onInterrupt(){
        triedOpenMaps=false;
    }
}
