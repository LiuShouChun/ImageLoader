package com.example.bitmapmanagermoudle.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.example.bitmapmanagermoudle.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片缓存类
 */
public class ImageLoader {
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    private static final int DISK_CACHE_INDEX = 0;
    private static final int BUFF_SIZE = 8 * 1024;
    private static final int TAG_KEY_URI = R.id.imageView_uri;
    private static final int MESSAGE_POST_RESULT = 1 ;
    private final String TAG = "ImageLoader";
    private Context mContext;
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDisLruCache;
    private BufferedInputStream buf_in;
    private BufferedOutputStream buf_out;
    private HttpURLConnection urlConnection;
    private Boolean mIsDiskLruCacheCreated =false;
    private ImageResizer mImageResizer = new ImageResizer();
    private ExecutorService THREADPOOLEXECUTOR = Executors.newFixedThreadPool(4);
    //内置handler
    private Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView mImageView = result.mImageView;
            String uri = (String) mImageView.getTag(TAG_KEY_URI);
            if (uri.equals(result.mUri)){
                mImageView.setImageBitmap(result.mBitMap);
            }
        }
    };


    public static ImageLoader build(Context context){
       return new ImageLoader(context);
    }

    private ImageLoader(Context context) {
        mContext = context.getApplicationContext();
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };
        File file = getDiskCacheDir(mContext, "dirName");
        if (!file.exists()) {
            file.mkdirs();
        }
        if (getUsableSpace(file) > DISK_CACHE_SIZE) {
            //存储卡缓存
            try {
                mDisLruCache = DiskLruCache.open(file, 1, 1, DISK_CACHE_SIZE);
                mIsDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * load bitmap from memory cache or disk cache or network
     * @param uri http url
     * @param reqWidth the width imageview desired
     * @param reqHeight he height imageview desired
     * @return bitmap
     */
     public Bitmap loadBitmap(String uri,int reqWidth,int reqHeight){
         //1.先从内存中取
         Bitmap bitmap = getBitmapFormCache(hashKeyFromUri(uri));
         if (bitmap != null){
             Log.e(TAG,"getBitmapFormCache,url"+uri);
             return bitmap;
         }
         try {
             //2.从磁盘读取
              bitmap = loadBitmapFromDiskCache(uri, reqWidth, reqHeight);
             if (bitmap != null){
                 Log.e(TAG,"loadBitmapFromDiskCache,url"+uri);
                 return bitmap;
             }
             //网络拉取
              bitmap = loadBitmapFromHttp(uri, reqWidth, reqHeight);
             Log.e(TAG,"loadBitmapFromHttp"+uri);
         } catch (IOException e) {
             e.printStackTrace();
         }
         if (bitmap == null&& !mIsDiskLruCacheCreated){
             Log.e(TAG,"disklrucache is not created");
             //网上继续拉取
             bitmap = downloadBitmapFromUrl(uri);
         }
         return bitmap;
     }


    /**
     * @param uriString image net source
     * @param imageView imageView
     * @param reqWeight your expect weight
     * @param reqHeight your expect height
     */
     public void bindBitmap(final String uriString, ImageView imageView, final int reqWeight, final int reqHeight){
         //标记这个imageView是那张图片
         imageView.setTag(TAG_KEY_URI,uriString);
         Bitmap bitmap = getBitmapFormCache(hashKeyFromUri(uriString));
         if (bitmap !=null){
             imageView.setImageBitmap(bitmap);
             //封装到Loader类
             LoaderResult loaderResult = new LoaderResult(imageView, uriString, bitmap);
             mHandler.obtainMessage(MESSAGE_POST_RESULT,loaderResult).sendToTarget();
             return;
         }
         Runnable runnable = new Runnable(){
             @Override
             public void run() {
                 Bitmap bitmap1 = loadBitmap(uriString, reqWeight, reqHeight);
                 if (bitmap1 !=null){

                 }
             }
         };
         //执行线程池
         THREADPOOLEXECUTOR.execute(runnable);
     }

    /**
     * @param uriString mage net source
     * @param imageView imageView
     */
   public  void bindBitmap( String uriString, ImageView imageView) {
      bindBitmap(uriString,imageView,0,0);
   }

    private Bitmap downloadBitmapFromUrl(String uri) {
        Bitmap bitmap = null;
         InputStream is = null;
        try {
            URL url = new URL(uri);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            is = urlConnection.getInputStream();
            bitmap = BitmapFactory.decodeStream(is);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (urlConnection !=null){
                urlConnection.disconnect();
            }
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return bitmap;
    }

    //内存缓存中添加图片
    private void addBitmapToMEmoryCache(String key, Bitmap bitmap) {
        if (mMemoryCache.get(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }
    //从内存中获取图片
    private Bitmap getBitmapFormCache(String key) {
        return mMemoryCache.get(key);
    }
    //磁盘缓存的添加和读取读取功能
    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqheight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can not assess to network form UI thread");
        }
        if (mMemoryCache == null) {
            return null;
        }
        String key = hashKeyFromUri(url);
        DiskLruCache.Editor editor = mDisLruCache.edit(key);
        if (editor != null) {
            //由于前面在DiskLruCache 的open方法中设置了一个节点只能有一个数据，
            // 因此下面的DISK_CACHE_INDEX 常量直接设为0就可以
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadUriToStream(url, outputStream)) {
                //写入到sd卡中
                 editor.commit();
            }else {
                //回滚
                editor.abort();
            }
            mDisLruCache.flush();
        }
        return loadBitmapFromDiskCache(url,reqWidth,reqheight);
    }

    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqheight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("load bitmap can not from UI Thread");
        }
        if (mDisLruCache == null) {
            return null;
        }
        Bitmap bitmap = null;
        String key = hashKeyFromUri(url);
        //磁盘读取
        DiskLruCache.Snapshot snapshot = mDisLruCache.get(key);
        if (snapshot != null) {
            FileInputStream inputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor fd = inputStream.getFD();
            bitmap = mImageResizer.decodeSampleedBitmapFromFileDescriptor(fd, reqWidth, reqheight);
            if (bitmap != null) {
                addBitmapToMEmoryCache(key, bitmap);
            }

        }
        return bitmap;
    }
    //将url转到输入流再转到输出流
    private boolean downloadUriToStream(String urlString, OutputStream outputStream) {
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            buf_in = new BufferedInputStream(urlConnection.getInputStream(), BUFF_SIZE);
            buf_out = new BufferedOutputStream(outputStream, BUFF_SIZE);
            int b;
            while ((b = buf_in.read()) != -1) {
                buf_out.write(b);
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (urlConnection != null){
                urlConnection.disconnect();
            }
            try {
                buf_in.close();
                buf_out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    //uri 转 key
    private String hashKeyFromUri(String url) {
        String key;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(url.getBytes());
            key = bytesToHexString(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            key = String.valueOf(url.hashCode());
            e.printStackTrace();
        }
        return key;
    }

    private String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src != null && src.length > 0) {
            for (int i = 0; i < src.length; ++i) {
                int v = src[i] & 255;
                String hv = Integer.toHexString(v);
                if (hv.length() < 2) {
                    stringBuilder.append(0);
                }

                stringBuilder.append(hv);
            }

            return stringBuilder.toString();
        } else {
            return null;
        }
    }


    //获取存储空间大小
    private long getUsableSpace(File file) {
        long freeSpace = file.getFreeSpace();
        return freeSpace;
    }

    private File getDiskCacheDir(Context context, String uniqueName) {
        //sd卡正常
        boolean state = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (state) {
            //通过Context.getExternalFilesDir()方法可以获取到 SDCard/Android/data/你的应用的包名/files/ 目录，
            // 一般放一些长时间保存的数据
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            //SDCard/android/data/你的应用包名/cache/目录，一般存放临时缓存数据.如果使用上面的方法，当你的应用在
            // 被用户卸载后，SDCard/Android/data/你的应用的包名/ 这个目录下的所有文件都会被删除，不会留下垃圾信息。
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }
  private static class LoaderResult{
         public ImageView mImageView;
         public String mUri;
         public Bitmap mBitMap;
         public LoaderResult(ImageView ImageView,String uri,Bitmap bitmap){
             mImageView = ImageView;
             mUri = uri;
             mBitMap = bitmap;
         }
    }
}
