package com.pcopi.byd.gpackprobe;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class TestApkProvider extends ContentProvider {
  private static final String NAME="test-mini-bridge.apk";
  private File file(Uri uri) throws FileNotFoundException {
    if(uri==null||!("/"+NAME).equals(uri.getPath()))throw new FileNotFoundException("Invalid path");
    File f=new File(getContext().getFilesDir(),NAME);
    if(!f.isFile())throw new FileNotFoundException("Embedded APK unavailable");
    return f;
  }
  @Override public boolean onCreate(){return true;}
  @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
  @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
    try{
      File f=file(uri);
      String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
      MatrixCursor c=new MatrixCursor(cols);
      Object[] row=new Object[cols.length];
      for(int i=0;i<cols.length;i++){
        if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=NAME;
        if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();
      }
      c.addRow(row);return c;
    }catch(FileNotFoundException e){return null;}
  }
  @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
    if(!"r".equals(mode))throw new FileNotFoundException("Read only");
    File f=file(uri);
    SharedPreferences pref=getContext().getSharedPreferences("results",Context.MODE_PRIVATE);
    pref.edit().putInt("fileOpenedCount",pref.getInt("fileOpenedCount",0)+1).apply();
    return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
  }
  @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
  @Override public int update(Uri uri,ContentValues values,String s,String[] a){return 0;}
  @Override public int delete(Uri uri,String s,String[] a){return 0;}
}