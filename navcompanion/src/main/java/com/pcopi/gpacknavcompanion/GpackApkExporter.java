package com.pcopi.gpacknavcompanion;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Non-destructive APK-only backup of an installed GPack package.
 *
 * The archive contains base.apk and any split APKs needed for inspection,
 * not GPack's private app data, account database or Google credentials.
 */
final class GpackApkExporter {
    static final String PACKAGE="com.gpack.agent";
    private GpackApkExporter(){}
    static final class Info {
        final String version;
        final List<File> files;
        final List<String> names;
        final long size;
        final String description;
        Info(String version,List<File> files,List<String> names,long size,String description){
            this.version=version;this.files=files;this.names=names;this.size=size;this.description=description;
        }
    }
    static Info inspect(Context context)throws Exception{
        PackageManager pm=context.getPackageManager();
        PackageInfo pkg=pm.getPackageInfo(PACKAGE,0);
        ApplicationInfo app=pm.getApplicationInfo(PACKAGE,0);
        List<File> files=new ArrayList<>();List<String> names=new ArrayList<>();
        if(app.sourceDir!=null){
            files.add(new File(app.sourceDir));names.add("base.apk");
        }
        if(app.splitSourceDirs!=null)for(int i=0;i<app.splitSourceDirs.length;i++){
            String src=app.splitSourceDirs[i];
            if(src!=null){files.add(new File(src));names.add("splits/split-"+(i+1)+".apk");}
        }
        if(files.isEmpty())throw new IllegalStateException("GPack package has no APK paths");
        long total=0;StringBuilder details=new StringBuilder();
        String version=pkg.versionName==null?"unknown":pkg.versionName;
        details.append("Package: ").append(PACKAGE).append("\nVersion: ").append(version).append('\n');
        details.append("APK parts: ").append(files.size()).append('\n');
        for(int i=0;i<files.size();i++){
            File f=files.get(i);
            details.append(names.get(i)).append(" : ").append(f.length()).append(" bytes");
            if(!f.isFile())details.append(" (NOT FOUND)");
            else if(!f.canRead())details.append(" (READ ACCESS UNCERTAIN)");
            details.append('\n');
            if(!f.isFile()||f.length()==0)throw new IllegalStateException("APK not readable: "+names.get(i));
            total+=f.length();
        }
        details.append("Total APK bytes: ").append(total).append('\n');
        details.append("This tool NEVER exports GPack private data, Google accounts or cache.\n");
        return new Info(version,Collections.unmodifiableList(files),Collections.unmodifiableList(names),total,details.toString());
    }
    static String proposedName(Info info){
        String safe=info.version.replaceAll("[^a-zA-Z0-9._-]","_");
        return "GPack-"+safe+"-original-apks.zip";
    }
    interface Progress { void update(String phase); }
    static String writeArchive(Context context,Uri uri,Info info,Progress callback)throws Exception{
        ContentResolver resolver=context.getContentResolver();
        StringBuilder summary=new StringBuilder();
        summary.append("GPack installed-package archive (APK bytes only)\n");
        summary.append(info.description).append('\n');
        byte[] buffer=new byte[65536];
        try(OutputStream output=resolver.openOutputStream(uri,"w")){
            if(output==null)throw new java.io.IOException("Cannot open chosen file");
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output,65536))){
                for(int i=0;i<info.files.size();i++){
                    File src=info.files.get(i);
                    if(callback!=null)callback.update("อ่าน "+info.names.get(i)+" ("+(i+1)+"/"+info.files.size()+")");
                    MessageDigest hash=MessageDigest.getInstance("SHA-256");
                    zip.putNextEntry(new ZipEntry(info.names.get(i)));
                    try(InputStream in=new BufferedInputStream(new FileInputStream(src),65536)){
                        int n;while((n=in.read(buffer))!=-1){
                            zip.write(buffer,0,n);hash.update(buffer,0,n);
                        }
                    }
                    zip.closeEntry();
                    StringBuilder digest=new StringBuilder();
                    for(byte b:hash.digest())digest.append(String.format(java.util.Locale.ROOT,"%02x",b&0xff));
                    summary.append(info.names.get(i)).append(" SHA256: ").append(digest).append('\n');
                }
                zip.putNextEntry(new ZipEntry("APK_INSPECTION_README.txt"));
                zip.write(summary.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return summary.toString();
    }
    static Uri saveToDownloads(Context context,Info info,Progress progress)throws Exception{
        if(Build.VERSION.SDK_INT<29)throw new UnsupportedOperationException("Downloads export requires Android 10+");
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,proposedName(info));
        values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/BYDPhoneSpace");
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        ContentResolver resolver=context.getContentResolver();
        Uri uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new java.io.IOException("Downloads provider refused file creation");
        try{
            writeArchive(context,uri,info,progress);
            ContentValues complete=new ContentValues();
            complete.put(MediaStore.MediaColumns.IS_PENDING,0);
            resolver.update(uri,complete,null,null);
            return uri;
        }catch(Exception e){
            try{resolver.delete(uri,null,null);}catch(Exception ignored){}
            throw e;
        }
    }
}
