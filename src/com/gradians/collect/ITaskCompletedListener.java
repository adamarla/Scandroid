package com.gradians.collect;

public interface ITaskCompletedListener {
    
    int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    int AUTH_ACTIVITY_REQUEST_CODE          = 200;
    int VERIFY_AUTH_TASK_RESULT_CODE        = 300;
    int UPLOAD_IMAGE_ACTIVITY_REQUEST_CODE  = 400;

    void onTaskResult(int requestCode, int resultCode, String resultData);
    
}
