package com.example.bitmapmanagermoudle.Utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.FileDescriptor;

/**
 * 图片压缩类
 */
 class ImageResizer {
   private final String TAG = "ImageResizer";
   //reqWidth reqHeight 期望的大小
   public Bitmap cpBitmapFromResource(Resources res ,int resId ,int reqWidth,int reqHeight){
      BitmapFactory.Options options = new BitmapFactory.Options();
      //仅仅是测量图片的大小 运算的量级不大
      options.inJustDecodeBounds = true;
      //测量结果保存在options里面
      BitmapFactory.decodeResource(res,resId,options);
      //计算缩略比 option存在计算出来的真实宽高
      options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);
      options.inJustDecodeBounds =false;
      return BitmapFactory.decodeResource(res,resId,options);
   }
   public Bitmap decodeSampleedBitmapFromFileDescriptor(FileDescriptor fd , int reqWidth, int reqHeight){
      BitmapFactory.Options options = new BitmapFactory.Options();
      //仅仅是测量图片的大小 运算的量级不大
      options.inJustDecodeBounds = true;
      //测量结果保存在options里面
      BitmapFactory.decodeFileDescriptor(fd,null,options);
      //计算缩略比 option存在计算出来的真实宽高
      options.inSampleSize = calculateInSampleSize(options,reqWidth,reqHeight);
      options.inJustDecodeBounds =false;
      return  BitmapFactory.decodeFileDescriptor(fd,null,options);
   }
   private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
      if(reqWidth  <= 0 || reqHeight <=0){
         return 1;
      }
      int inSampleSize = 1;
      int realWidth = options.outWidth;
      int realHeight = options.outHeight;
      Log.e(TAG,"w="+realWidth+"h="+realHeight);
      if (reqHeight<realHeight || reqWidth<realWidth){
         final int halfWidth = realWidth/2;
         final int halfHeight = reqHeight/2;
         while ((halfHeight/inSampleSize) >= reqHeight &&(halfWidth/inSampleSize) >= reqWidth){
            inSampleSize *= 2;
         }
      }
      Log.e(TAG,"inSampleSize:"+inSampleSize);
      return inSampleSize;
   }
}
