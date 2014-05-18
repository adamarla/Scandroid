package com.gradians.collect;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

public class HttpCallsAsyncTask extends AsyncTask<URL, Void, String> implements IConstants {
    
    public HttpCallsAsyncTask(ITaskResult caller, int resultCode) {
        this.resultCode = resultCode;
        this.caller = caller;
    }
    
    @Override
    protected String doInBackground(URL... params) {
        String result = null;
        try {
            String charset = Charset.defaultCharset().name();
            HttpURLConnection conn = (HttpURLConnection) params[0].openConnection();
            conn.setRequestProperty("Content-Type", 
                    "application/x-www-form-urlencoded;charset=" + charset);
            conn.setRequestProperty("Cache-Control", "no-cache");

            int responseCode = conn.getResponseCode();                        
            InputStream istream = conn.getInputStream();
            BufferedReader ireader = new BufferedReader(new InputStreamReader(istream));
            if (responseCode == HttpURLConnection.HTTP_OK) {                
                result = ireader.readLine();
            } else {
                Log.d(TAG, "Response Code " + responseCode);
            }
        } catch (Exception e){
            Log.e(TAG, e.getMessage());
        }            
        return result;
    }

    @Override
    protected void onPostExecute(String result) {        
        caller.onTaskResult(resultCode, result != null ? Activity.RESULT_OK : Activity.RESULT_FIRST_USER, result);
    }

    private ITaskResult caller;
    private int resultCode;
}
