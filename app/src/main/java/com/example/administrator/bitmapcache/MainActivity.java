package com.example.administrator.bitmapcache;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;

import com.example.bitmapmanagermoudle.Utils.ImageLoader;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private LruCache<String, Bitmap> lruCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageLoader imageLoader =  ImageLoader.build(getApplicationContext());
    }

    private void init() {


    }
}
