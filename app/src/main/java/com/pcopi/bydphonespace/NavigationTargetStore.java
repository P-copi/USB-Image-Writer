package com.pcopi.bydphonespace;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

public final class NavigationTargetStore {
    private static final String PREFS = "navigation_targets";
    private NavigationTargetStore() {}

    public static final class Target {
        public final String type;
        public final String name;
        public final String lat;
        public final String lng;

        Target(String type, String name, String lat, String lng) {
            this.type = type == null ? "" : type;
            this.name = name == null ? "" : name;
            this.lat = lat == null ? "" : lat;
            this.lng = lng == null ? "" : lng;
        }

        public boolean isCoordinate() { return "coord".equals(type) && !lat.isEmpty() && !lng.isEmpty(); }
        public boolean isPlace() { return "place".equals(type) && !name.isEmpty(); }
        public boolean isEmpty() { return !isCoordinate() && !isPlace(); }
        public String display() {
            if (isCoordinate()) {
                if (!name.trim().isEmpty()) return name + " • " + lat + ", " + lng;
                return lat + ", " + lng;
            }
            return name;
        }
    }

    private static String key(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Target get(Context context, File file) {
        String k = key(file);
        SharedPreferences p = prefs(context);
        return new Target(
                p.getString(k + ".type", ""),
                p.getString(k + ".name", ""),
                p.getString(k + ".lat", ""),
                p.getString(k + ".lng", "")
        );
    }

    public static void savePlace(Context context, File file, String name) {
        String k = key(file);
        prefs(context).edit()
                .putString(k + ".type", "place")
                .putString(k + ".name", name == null ? "" : name.trim())
                .remove(k + ".lat")
                .remove(k + ".lng")
                .apply();
    }

    public static void saveCoordinate(Context context, File file, String lat, String lng) {
        saveCoordinate(context, file, lat, lng, "");
    }

    public static void saveCoordinate(Context context, File file, String lat, String lng, String label) {
        String k = key(file);
        SharedPreferences.Editor e = prefs(context).edit()
                .putString(k + ".type", "coord")
                .putString(k + ".lat", lat == null ? "" : lat.trim())
                .putString(k + ".lng", lng == null ? "" : lng.trim());
        if (label == null || label.trim().isEmpty()) e.remove(k + ".name");
        else e.putString(k + ".name", label.trim());
        e.apply();
    }

    public static void move(Context context, File from, File to) {
        Target t = get(context, from);
        if (t.isEmpty()) return;
        if (t.isCoordinate()) saveCoordinate(context, to, t.lat, t.lng, t.name);
        else savePlace(context, to, t.name);
        clear(context, from);
    }

    public static void clear(Context context, File file) {
        String k = key(file);
        prefs(context).edit()
                .remove(k + ".type")
                .remove(k + ".name")
                .remove(k + ".lat")
                .remove(k + ".lng")
                .apply();
    }
}
