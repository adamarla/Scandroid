package com.gradians.collect;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
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
    
    public void setDestination(String[] destUrls) {
        this.destUrls = destUrls;
    }
    
    @Override
    protected String doInBackground(URL... params) {
        String result = null;
        
        for (int i = 0; i < params.length; i++) {
            try {
                String charset = Charset.defaultCharset().name();
                HttpURLConnection conn = (HttpURLConnection) params[i].openConnection();
                conn.setRequestProperty("Content-Type", 
                        "application/x-www-form-urlencoded;charset=" + charset);
                conn.setRequestProperty("Cache-Control", "no-cache");

                int responseCode = conn.getResponseCode();                        
                InputStream istream = conn.getInputStream();
                BufferedReader ireader = new BufferedReader(new InputStreamReader(istream));
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    if (destUrls == null) {
                        result = ireader.readLine();
                    } else {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(destUrls[i]));
                        char[] buf = new char[1024];
                        int read;
                        while ((read = ireader.read(buf, 0, buf.length)) != -1) {
                            writer.write(buf, 0, read);
                        }
                        writer.close();
                        result = "";
                    }
                } else {
                    Log.e(TAG, "Response Code " + responseCode);
                }
            } catch (Exception e){
                Log.e(TAG, e.getClass().getName());
            }
        }
        return result;
    }

    @Override
    protected void onPostExecute(String result) {
        if (caller != null)
            caller.onTaskResult(resultCode, result != null ? 
                Activity.RESULT_OK : Activity.RESULT_FIRST_USER, result);
    }

    private String[] destUrls;
    private ITaskResult caller;
    private int resultCode;
}
