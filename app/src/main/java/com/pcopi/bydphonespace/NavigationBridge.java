package com.pcopi.bydphonespace;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

/**
 * Routes locations to an Android intent receiver. Does not access or alter GPack internals.
 * A preferred route is persisted only after the user confirms it worked on the car.
 */
public final class NavigationBridge {
    public static final String AUTO = "auto";
    public static final String DIRECT = "direct";
    public static final String GEO = "geo";
    public static final String NAV = "navigation";
    public static final String WEB_DIR = "web_dir";
    public static final String WEB_SEARCH = "web_search";

    private static final String PREFS = "byd_navigation_bridge";
    private static final String KEY_MODE = "confirmed_mode";

    private NavigationBridge() {}

    public static String selected(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, AUTO);
    }

    public static void saveConfirmed(Context context, String mode) {
        if (!isValid(mode)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode).apply();
    }

    public static void reset(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_MODE).apply();
    }

    private static boolean isValid(String mode) {
        return DIRECT.equals(mode) || GEO.equals(mode) || NAV.equals(mode)
                || WEB_DIR.equals(mode) || WEB_SEARCH.equals(mode);
    }

    public static String label(String mode) {
        if (DIRECT.equals(mode)) return "Google Maps ตรง (แพ็กเกจมาตรฐาน)";
        if (GEO.equals(mode)) return "geo: ส่งชื่อหรือพิกัด";
        if (NAV.equals(mode)) return "google.navigation: แบบไม่ล็อกแพ็กเกจ";
        if (WEB_DIR.equals(mode)) return "Google Maps URL: เส้นทาง";
        if (WEB_SEARCH.equals(mode)) return "Google Maps URL: ค้นหาจุดหมาย";
        return "อัตโนมัติแบบเดิม";
    }

    public static Intent intentFor(String mode, String destination) {
        String q = destination == null ? "" : destination.trim();
        Intent intent;
        if (DIRECT.equals(mode) || NAV.equals(mode)) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(q) + "&mode=d"));
            if (DIRECT.equals(mode)) intent.setPackage("com.google.android.apps.maps");
        } else if (GEO.equals(mode)) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)));
        } else if (WEB_SEARCH.equals(mode)) {
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q)));
        } else {
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(q) + "&travelmode=driving"));
        }
        return intent;
    }

    public static boolean launch(Context context, String destination) {
        if (destination == null || destination.trim().isEmpty()) {
            Toast.makeText(context, "ยังไม่ได้เลือกจุดหมาย", Toast.LENGTH_SHORT).show();
            return false;
        }
        String mode = selected(context);
        if (!AUTO.equals(mode)) {
            try {
                context.startActivity(intentFor(mode, destination));
                return true;
            } catch (Exception e) {
                Toast.makeText(context, "ตัวนำทางที่เคยเลือกใช้ไม่ได้แล้ว กรุณาทดสอบวิธีเชื่อม Maps ใหม่", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        try {
            context.startActivity(intentFor(DIRECT, destination));
            return true;
        } catch (Exception ignored) {}
        try {
            context.startActivity(intentFor(GEO, destination));
            return true;
        } catch (Exception ignored) {}
        Toast.makeText(context, "Android หลักไม่พบแอปนำทาง ลองเมนู ทดสอบเชื่อม Maps / GPack", Toast.LENGTH_LONG).show();
        return false;
    }
}
