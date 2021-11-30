package com.multitv.ott.videocompressoranduploadsdk;

public class VideoUploadSdkCallBackState {

    public interface OnCustomStateListener {
        void stateChanged();
    }

    private static VideoUploadSdkCallBackState mInstance;
    private OnCustomStateListener mListener;

    private VideoUploadSdkCallBackState() {}

    public static VideoUploadSdkCallBackState getInstance() {
        if(mInstance == null) {
            mInstance = new VideoUploadSdkCallBackState();
        }
        return mInstance;
    }

    public void setVideoCallBackListener(OnCustomStateListener listener) {
        mListener = listener;
    }

    public void uploadVideoCallBackState(String videoUrl,boolean isVideoUploaded) {

    }

}