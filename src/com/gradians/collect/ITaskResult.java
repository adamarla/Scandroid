package com.gradians.collect;

public interface ITaskResult {
    
    int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    int AUTH_ACTIVITY_REQUEST_CODE          = 200;
    int REFRESH_TASK_REQUEST_CODE           = 250;
    int REFRESH_WS_TASK_REQUEST_CODE        = 300;
    int BROWSE_ACTIVITY_REQUEST_CODE        = 390;
    int DETAIL_ACTIVITY_REQUEST_CODE        = 400;
    int DOWNLOAD_MONITOR_TASK_REQUEST_CODE  = 500;
    int PURCHASE_TASK_REQUEST_CODE          = 600;
    int LIST_ACTIVITY_REQUEST_CODE          = 700;
    int DOUBTS_ACTIVITY_REQUEST_CODE        = 710;
    int REGISTRATION_ACTIVITY_REQUEST_CODE  = 750;
    int FORGOT_PWD_ACTIVITY_REQUEST_CODE    = 760;
    int REGISTER_TASK_REQUEST_CODE          = 800;
    int VALIDATE_INPUTS_TASK_REQUEST_CODE   = 900;
    int MATCH_NAME_TASK_REQUEST_CODE        = 910;
    int CLAIM_ACCT_TASK_REQUEST_CODE        = 920;
    int RESET_PWD_TASK_REQUEST_CODE         = 950;

    void onTaskResult(int requestCode, int resultCode, String resultData);
    
}
