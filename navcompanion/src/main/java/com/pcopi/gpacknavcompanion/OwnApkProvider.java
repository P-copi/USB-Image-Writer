package com.pcopi.gpacknavcompanion;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Read-only content URI for the APK installed on this device.
 *
 * The provider is NOT exported. A specific receiver may read the URI only
 * while the user has granted a temporary per-Intent URI permission.
 */
public final class OwnApkProvider extends ContentProvider {
    public static final String AUTHORITY = "com.pcopi.gpacknavcompanion.selfapk";
    public static final Uri URI=Uri.parse("content://"+AUTHORITY+"/installed-companion.apk");
    private static final String MIME="application/vnd.android.package-archive";
    private boolean valid(Uri uri){
        return URI.equals(uri);
    }
    private File ownApk(){
        return new File(getContext().getApplicationInfo().sourceDir);
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){
        return valid(uri)?MIME:null;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!valid(uri)||!"r".equals(mode))throw new FileNotFoundException("Read only companion APK");
        File file=ownApk();
        if(!file.isFile()||file.length()==0)throw new FileNotFoundException("Installed APK missing");
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,
            String[] args,String sortOrder){
        if(!valid(uri))return null;
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor result=new MatrixCursor(columns);
        Object[] values=new Object[columns.length];
        for(int i=0;i<columns.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]="BYD-Nav-Companion.apk";
            else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=ownApk().length();
        }
        result.addRow(values);
        return result;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException("Read only");}
}
