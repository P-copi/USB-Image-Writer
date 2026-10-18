package com.pcopi.bydphonespace;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;

/** User-selected display rotation, kept separate from the source image's EXIF and bytes. */
final class ImageOrientationStore {
    private static final String PREF = "navigation_image_rotation_v1";
    private ImageOrientationStore(){}

    static int rotation(Context context, File file) {
        if(file==null)return 0;
        return normalize(context.getSharedPreferences(PREF,Context.MODE_PRIVATE)
                .getInt(file.getAbsolutePath(),0));
    }

    static int rotate(Context context, File file, int delta) {
        if(file==null)return 0;
        int angle=normalize(rotation(context,file)+delta);
        SharedPreferences.Editor e=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();
        if(angle==0)e.remove(file.getAbsolutePath());
        else e.putInt(file.getAbsolutePath(),angle);
        e.apply();
        return angle;
    }

    static void move(Context context, File oldFile, File newFile) {
        if(oldFile==null||newFile==null)return;
        SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        int angle=rotation(context,oldFile);
        SharedPreferences.Editor e=p.edit().remove(oldFile.getAbsolutePath());
        if(angle!=0)e.putInt(newFile.getAbsolutePath(),angle);
        e.apply();
    }

    static void remove(Context context, File file) {
        if(file!=null)context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(file.getAbsolutePath()).apply();
    }

    private static int normalize(int degrees){return ((degrees%360)+360)%360;}
}
