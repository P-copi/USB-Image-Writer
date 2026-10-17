package com.pcopi.bydphonespace;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class NavigationLibrary {
    public static final String INBOX = "Inbox";
    public static final String ROUTES = "Routes";
    public static final String CHARGERS = "Chargers";
    public static final String FAVORITES = "Favorites";

    private NavigationLibrary() {}

    public static File root(Context context) {
        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File base = pictures != null ? pictures : context.getFilesDir();
        File root = new File(base, "BYDPhoneSpace/Navigation");
        if (!root.exists()) root.mkdirs();
        ensureCategories(root);
        return root;
    }

    private static void ensureCategories(File root) {
        new File(root, INBOX).mkdirs();
        new File(root, ROUTES).mkdirs();
        new File(root, CHARGERS).mkdirs();
        new File(root, FAVORITES).mkdirs();
    }

    public static String[] categories() {
        return new String[]{INBOX, ROUTES, CHARGERS, FAVORITES};
    }

    public static String thaiCategory(String category) {
        if (ROUTES.equals(category)) return "เส้นทาง";
        if (CHARGERS.equals(category)) return "จุดชาร์จ";
        if (FAVORITES.equals(category)) return "สถานที่โปรด";
        return "Inbox";
    }

    public static File importImage(Context context, Uri uri, String category) throws Exception {
        File dir = categoryDir(context, category);
        String name = displayName(context, uri);
        if (name == null || name.trim().isEmpty()) name = "image_" + System.currentTimeMillis() + ".jpg";
        name = safeName(name);
        File out = uniqueFile(dir, name);
        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             BufferedInputStream in = raw == null ? null : new BufferedInputStream(raw, 64 * 1024);
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024)) {
            if (in == null) throw new IllegalStateException("ไม่สามารถเปิดไฟล์ต้นทางได้");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) bos.write(buffer, 0, read);
            bos.flush();
        }
        out.setLastModified(System.currentTimeMillis());
        return out;
    }

    public static File moveToCategory(Context context, File source, String category) throws Exception {
        if (source == null || !source.exists()) throw new IllegalStateException("ไม่พบไฟล์");
        File destDir = categoryDir(context, category);
        if (source.getParentFile() != null && source.getParentFile().equals(destDir)) return source;
        File dest = uniqueFile(destDir, source.getName());
        if (source.renameTo(dest)) {
            NavigationTargetStore.move(context, source, dest);
            dest.setLastModified(System.currentTimeMillis());
            return dest;
        }
        try (BufferedInputStream in = new BufferedInputStream(new java.io.FileInputStream(source), 64 * 1024);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest), 64 * 1024)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }
        if (!source.delete()) {
            dest.delete();
            throw new IllegalStateException("ย้ายไฟล์ไม่สำเร็จ");
        }
        NavigationTargetStore.move(context, source, dest);
        dest.setLastModified(System.currentTimeMillis());
        return dest;
    }

    public static boolean delete(Context context, File file) {
        if (file == null || !file.exists()) return false;
        boolean ok = file.delete();
        if (ok) NavigationTargetStore.clear(context, file);
        return ok;
    }

    private static File categoryDir(Context context, String category) {
        if (category == null || category.trim().isEmpty()) category = INBOX;
        File dir = new File(root(context), category);
        dir.mkdirs();
        return dir;
    }

    private static String displayName(Context context, Uri uri) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? null : last;
    }

    private static String safeName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int n = 2;
        while (true) {
            File candidate = new File(dir, base + " (" + n + ")" + ext);
            if (!candidate.exists()) return candidate;
            n++;
        }
    }

    public static List<File> listAll(Context context) {
        ArrayList<File> files = new ArrayList<>();
        File root = root(context);
        File[] dirs = root.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (!dir.isDirectory()) continue;
                File[] children = dir.listFiles();
                if (children == null) continue;
                for (File f : children) if (f.isFile()) files.add(f);
            }
        }
        sortNewest(files);
        return files;
    }

    public static List<File> listCategory(Context context, String category) {
        ArrayList<File> files = new ArrayList<>();
        File dir = new File(root(context), category);
        File[] children = dir.listFiles();
        if (children != null) for (File f : children) if (f.isFile()) files.add(f);
        sortNewest(files);
        return files;
    }

    public static List<File> recent(Context context, int limit) {
        List<File> all = listAll(context);
        if (all.size() <= limit) return all;
        return new ArrayList<>(all.subList(0, limit));
    }

    public static String categoryOf(File file) {
        File p = file == null ? null : file.getParentFile();
        return p == null ? INBOX : p.getName();
    }

    private static void sortNewest(List<File> files) {
        Collections.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
    }
}
