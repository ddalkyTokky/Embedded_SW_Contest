package com.example.googlemapmavsdk;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.net.URL;

public class PhotoActivity extends AppCompatActivity {
//    private ImageView mImage = (ImageView) findViewById(R.id.imageview_photo);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);
//        try {
//            File files = new File(this.getFilesDir().getAbsolutePath() + "/metro.jpg");
//
//            if (files.exists() == true) {
//                Bitmap myBitmap = BitmapFactory.decodeFile(files.getAbsolutePath());
//                mImage.setImageBitmap(myBitmap);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}