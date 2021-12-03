package com.multitv.ott.videocompressoranduploadsdk;

public class VideoUploadSdkCallBackState {
    private String videoUrl;
    private boolean isVideoUploaded;
    private OnCustomStateListener onCustomStateListener;

    public interface OnCustomStateListener {
        void onVideoUploadcallBack(String videoUrl, boolean isVideoUploaded);
    }

    private static VideoUploadSdkCallBackState mInstance;
    private OnCustomStateListener mListener;

    private VideoUploadSdkCallBackState() {
    }

    public static VideoUploadSdkCallBackState getInstance() {
        if (mInstance == null) {
            mInstance = new VideoUploadSdkCallBackState();
        }
        return mInstance;
    }

    public void setVideoCallBackListener(OnCustomStateListener listener) {
        mListener = listener;
    }

    public void uploadVideoCallBackState(String videoUrl, boolean isVideoUploaded) {
        this.videoUrl = videoUrl;
        this.isVideoUploaded = isVideoUploaded;
        onCustomStateListener.onVideoUploadcallBack(videoUrl, isVideoUploaded);
    }

    public String getUploadVideoUrl() {
        return videoUrl;
    }

    public boolean getUploadVideoState() {
        return isVideoUploaded;
    }

}