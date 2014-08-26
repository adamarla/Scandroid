package com.gradians.collect;

public interface ITaskResult {
    
    int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    int AUTH_ACTIVITY_REQUEST_CODE          = 200;
    int VERIFY_AUTH_TASK_REQUEST_CODE       = 300;
    int FLOW_ACTIVITY_REQUEST_CODE          = 400;
    int DOWNLOAD_MONITOR_TASK_REQUEST_CODE  = 500;
    int BILL_WORKSHEET_TASK_REQUEST_CODE    = 600;
    int LIST_ACTIVITY_REQUEST_CODE          = 700;
    int REGISTRATION_ACTIVITY_REQUEST_CODE  = 750;
    int REGISTER_TASK_REQUEST_CODE          = 800;
    int VALIDATE_INPUTS_TASK_REQUEST_CODE       = 900;
    int MATCH_NAME_TASK_REQUEST_CODE        = 1000;
    int CLAIM_ACCT_TASK_REQUEST_CODE        = 1100;

    void onTaskResult(int requestCode, int resultCode, String resultData);
    
}
