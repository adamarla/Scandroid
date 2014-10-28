package com.gradians.collect;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;

import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class HttpCallsAsyncTask extends AsyncTask<Download, Void, String> implements IConstants {
    
    public HttpCallsAsyncTask(ITaskResult caller, int requestCode) {
        this.requestCode = requestCode;
        this.caller = caller;
        this.downloads = new ArrayList<Download>();
    }
    
    String[] params;
    public void setUpdateParams(String...params) {
        this.params = params;
    }
    
    public void add(String title, Uri srcUri, Uri destUri) {
        Download download = new Download(title, srcUri, destUri);
        for (Download d : downloads) {
            if (d.equals(download)) return;
        }
        downloads.add(download);
    }
    
    public int getCount() {
        return downloads.size();
    }
    
    ArrayList<Download> downloads;
    public void start() {
        this.execute(downloads.toArray(new Download[downloads.size()]));
    }
    
    @Override
    protected String doInBackground(Download... downloads) {
        String result = "";
        String src, target; 
        for (int i = 0; i < downloads.length; i++) {
            try {
                String charset = Charset.defaultCharset().name();
                src = downloads[i].srcUri.toString();
                target = downloads[i].destUri != null ? downloads[i].destUri.getPath() : null;
                if (target != null) {
                    if ((new File(target)).exists()) continue;
                }
                
                HttpURLConnection conn = (HttpURLConnection)(new URL(src)).openConnection();
                conn.setRequestProperty("Content-Type", 
                        "application/x-www-form-urlencoded;charset=" + charset);
                conn.setRequestProperty("Cache-Control", "no-cache");
                
                if (this.params != null) {
                    conn.setDoOutput(true); // Triggers HTTP POST
                    conn.getOutputStream().write(this.params[i].getBytes(charset));
                    conn.getOutputStream().close();
                }
                
                int responseCode = conn.getResponseCode();
                InputStream istream = conn.getInputStream();
                BufferedReader ireader = new BufferedReader(new InputStreamReader(istream));
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    if (target == null) {
                        result = ireader.readLine();
                    } else {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(target));
                        char[] buf = new char[1024];
                        int read;
                        while ((read = ireader.read(buf, 0, buf.length)) != -1) {
                            writer.write(buf, 0, read);
                        }
                        writer.close();
                    }
                    resultCode = Activity.RESULT_OK;
                } else {
                    resultCode = Activity.RESULT_FIRST_USER;
                    throw new Exception("HTTP Response Code: " + responseCode);
                }
                
            } catch (NullPointerException npe){
                resultCode = Activity.RESULT_CANCELED;
                Log.e(TAG, "Null Pointer Exception");
                result = null;
            } catch (Exception e){
                resultCode = Activity.RESULT_CANCELED;
                Log.e(TAG, e.getClass().getName().toString());
                result = null;
            }
        }
        return result;
    }

    @Override
    protected void onPostExecute(String result) {
        if (caller != null)
            caller.onTaskResult(requestCode, resultCode, result);
    }

    private ITaskResult caller;
    private int requestCode, resultCode;
}
