package com.multitv.ott.videocompressoranduploadsdk;

public class VideoUploadSdkCallBackState {
    private String mvideoUrl;
    private boolean misVideoUploaded;
    private OnCustomStateListener onCustomStateListener;

    public interface OnCustomStateListener {
        void onVideoUploadcallBack(String videoUrl, boolean isVideoUploaded);
    }

    private static VideoUploadSdkCallBackState mInstance;


    private VideoUploadSdkCallBackState() {
    }

    public static VideoUploadSdkCallBackState getInstance() {
        if (mInstance == null) {
            mInstance = new VideoUploadSdkCallBackState();
        }
        return mInstance;
    }

    public void setVideoCallBackListener(OnCustomStateListener listener) {
        onCustomStateListener = listener;
    }

    public void uploadVideoCallBackState(String videoUrl, boolean isVideoUploaded) {
        this.mvideoUrl = videoUrl;
        this.misVideoUploaded = isVideoUploaded;
        onCustomStateListener.onVideoUploadcallBack(mvideoUrl, misVideoUploaded);
    }

    public String getUploadVideoUrl() {
        return mvideoUrl;
    }

    public boolean getUploadVideoState() {
        return misVideoUploaded;
    }

}