package com.multitv.ott.videocompressoranduploadsdk;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileStreamUttils {

    public String getFilePath(Uri src) {
        File source = new File(src.getPath());
        String filename = src.getLastPathSegment();
        File destination = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/CompressSdk/" + filename);

        FileChannel in = null;
        try {
            in = new FileInputStream(source).getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        FileChannel out = null;
        try {
            out = new FileOutputStream(destination).getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            in.transferTo(0, in.size(), out);
        } catch (Exception e) {
            Log.e("FIle :::::", "Read and write error::::" + e.getMessage());
        }
        Log.e("FIle :::::", "Read and write path::::" + destination.getAbsolutePath());
        return destination.getAbsolutePath();
    }
}
