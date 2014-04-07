package com.gradians.collect;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import android.app.Activity;
import android.os.AsyncTask;

public class VerificationTask extends AsyncTask<Void, Void, String> implements IConstants {
    
    public VerificationTask(String email, String token, ITaskCompletedListener taskCompletedListener) {
        this.email = email;
        this.token = token;
        this.taskCompletedListener = taskCompletedListener;
    }
    
    @Override
    protected String doInBackground(Void... params) {
        String result = null;
        try {
            String charset = Charset.defaultCharset().name();
            URL verifyToken = new URL(String.format(BASE_URL,
                    WEB_APP_HOST_PORT,
                    PARAM_EMAIL, email,
                    PARAM_TOKEN, token));
            HttpURLConnection conn = (HttpURLConnection)verifyToken.openConnection();
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charset);
            conn.setRequestProperty("Cache-Control", "no-cache");

            int responseCode = conn.getResponseCode();
            InputStream istream = conn.getInputStream();
            BufferedReader ireader = new BufferedReader(new InputStreamReader(istream));
            if (responseCode == HttpURLConnection.HTTP_OK) {
                result = ireader.readLine();
            }
        } catch (Exception e){ 
        }            
        return result;
    }

    @Override
    protected void onPostExecute(String result) {
        taskCompletedListener.onTaskResult(ITaskCompletedListener.VERIFY_AUTH_TASK_RESULT_CODE,
                result != null ? Activity.RESULT_OK : Activity.RESULT_FIRST_USER, result);
    }

    private String email, token;
    private ITaskCompletedListener taskCompletedListener;
    
    private final String BASE_URL = "http://%s/tokens/verify?%s=%s&%s=%s";
    
    /** POST parameter name for the user's account name */
    private static final String PARAM_EMAIL = "email";
    /** POST parameter name for the user's password */
    private static final String PARAM_TOKEN = "token";    

    
}
