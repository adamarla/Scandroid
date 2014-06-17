package com.gradians.collect;

public interface ITaskResult {
    
    int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    int AUTH_ACTIVITY_REQUEST_CODE          = 200;
    int VERIFY_AUTH_TASK_RESULT_CODE        = 300;
    int UPLOAD_IMAGE_ACTIVITY_REQUEST_CODE  = 400;
    int PREVIEW_ACTIVITY_REQUEST_CODE       = 500;
    int FLOW_ACTIVITY_REQUEST_CODE          = 600;
    int FETCH_COMMENTS_TASK_RESULT_CODE     = 700;
    int DOWNLOAD_MONITOR_TASK_RESULT_CODE   = 800;
    int BILL_WORKSHEET_TASK_RESULT_CODE     = 900;

    void onTaskResult(int requestCode, int resultCode, String resultData);
    
}
