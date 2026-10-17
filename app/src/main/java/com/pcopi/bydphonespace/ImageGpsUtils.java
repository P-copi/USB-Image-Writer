package com.pcopi.bydphonespace;

import android.media.ExifInterface;

import java.io.File;

public final class ImageGpsUtils {
    private ImageGpsUtils() {}

    public static final class Info {
        public final boolean hasGps;
        public final double lat;
        public final double lng;
        public final String dateTime;

        Info(boolean hasGps, double lat, double lng, String dateTime) {
            this.hasGps = hasGps;
            this.lat = lat;
            this.lng = lng;
            this.dateTime = dateTime == null ? "" : dateTime;
        }

        public String coordinateText() {
            if (!hasGps) return "";
            return String.format(java.util.Locale.US, "%.6f, %.6f", lat, lng);
        }
    }

    public static Info read(File file) {
        if (file == null || !file.exists()) return new Info(false, 0, 0, "");
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            float[] ll = new float[2];
            boolean ok = exif.getLatLong(ll);
            String date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (date == null || date.trim().isEmpty()) date = exif.getAttribute(ExifInterface.TAG_DATETIME);
            return new Info(ok, ok ? ll[0] : 0, ok ? ll[1] : 0, date);
        } catch (Exception e) {
            return new Info(false, 0, 0, "");
        }
    }
}
