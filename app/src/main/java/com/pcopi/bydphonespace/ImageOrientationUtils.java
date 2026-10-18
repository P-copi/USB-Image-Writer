package com.pcopi.bydphonespace;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.File;

/** Orient image previews without rewriting the original photos or losing EXIF/GPS. */
final class ImageOrientationUtils {
    private ImageOrientationUtils(){}

    static Bitmap decode(Context context, File file, int target){
        if(file==null||!file.isFile())return null;
        String path=file.getAbsolutePath();
        BitmapFactory.Options o=new BitmapFactory.Options();
        o.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(path,o);
        if(o.outWidth<=0||o.outHeight<=0)return null;
        int sample=1;
        final int safeTarget=Math.max(120,target);
        while((long)o.outWidth/sample>safeTarget*2L||(long)o.outHeight/sample>safeTarget*2L)sample*=2;
        o.inJustDecodeBounds=false;
        o.inSampleSize=sample;
        Bitmap bitmap=BitmapFactory.decodeFile(path,o);
        if(bitmap==null)return null;
        int exifOrientation=ExifInterface.ORIENTATION_NORMAL;
        try{
            ExifInterface exif=new ExifInterface(path);
            exifOrientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        }catch(Exception ignored){}
        int manual=ImageOrientationStore.rotation(context,file);
        Matrix matrix=new Matrix();
        switch(exifOrientation){
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:matrix.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_180:matrix.postRotate(180);break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:matrix.postScale(1,-1);break;
            case ExifInterface.ORIENTATION_TRANSPOSE:matrix.postRotate(90);matrix.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_90:matrix.postRotate(90);break;
            case ExifInterface.ORIENTATION_TRANSVERSE:matrix.postRotate(-90);matrix.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_270:matrix.postRotate(270);break;
            default:break;
        }
        if(manual!=0)matrix.postRotate(manual);
        if(matrix.isIdentity())return bitmap;
        try{
            Bitmap oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
            if(oriented!=bitmap)bitmap.recycle();
            return oriented;
        }catch(OutOfMemoryError error){
            return bitmap;
        }
    }
}
