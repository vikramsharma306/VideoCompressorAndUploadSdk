package com.multitv.ott.videocompressoranduploadsdk;

import android.net.Uri;

public interface onVideoCompressAndUploadListener {
        void onVideoProcessStart(Uri uri, String message);

        void onVideoProgressStateChange();

        void onError(String message);

        void onSuccess(String message, String videoUrl);
    }