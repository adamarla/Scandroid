package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class ImageUploadService extends IntentService implements IConstants {

    public ImageUploadService() {
        super("ImageUploadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        File idDir = new File(intent.getStringExtra(TAG_ID));
        File imageDir = new File(intent.getStringExtra(TAG));
        String[] ids = idDir.list();

        URL url = null;
        HttpURLConnection httpUrlConnection = null;
        OutputStream ostream = null;
        PrintWriter opstream = null;        
        String boundary =  null;
        for (String id : ids) {
            boundary = String.valueOf(System.currentTimeMillis());
            try {
                url = new URL(String.format(URL,
                        BANK_HOST_PORT, id.split("-")[1]));
                Log.i(TAG, url.toString());
                httpUrlConnection = (HttpURLConnection) url.openConnection();
                httpUrlConnection.setDoOutput(true);

                httpUrlConnection.setRequestMethod("POST");
                httpUrlConnection.setRequestProperty("Connection", "Keep-Alive");
                httpUrlConnection.setRequestProperty("Cache-Control", "no-cache");
                httpUrlConnection.setRequestProperty("Content-Type", 
                        "multipart/form-data;boundary=" + boundary);
                
                ostream = httpUrlConnection.getOutputStream();
                opstream = new PrintWriter(ostream); 

                opstream.append("--" + boundary).append(CRLF);
                opstream.append("Content-Disposition: form-data; name=\"image\"; filename=\"" + 
                        id + "\"").append(CRLF);
                opstream.append("Content-Type: " + 
                        HttpURLConnection.guessContentTypeFromName("image.jpeg")).append(CRLF);
                opstream.append("Content-Transfer-Encoding: binary").append(CRLF);
                opstream.append(CRLF).flush();
                
                InputStream imgstream = null;
                byte[] buffer = new byte[1024];
                imgstream = new FileInputStream(new File(imageDir, id.split("-")[0]));
                for (int length = 0; (length = imgstream.read(buffer)) > 0;) {
                    ostream.write(buffer, 0, length);
                }
                imgstream.close();
                ostream.flush(); // Important! Output cannot be closed. Close of writer will close output as well.
                opstream.append(CRLF).flush(); // CRLF is important! It indicates end of binary boundary.
                
                // End of multipart/form-data.
                opstream.append(DASH_DASH + boundary + DASH_DASH).append(CRLF);            
                opstream.close();
                
                if (httpUrlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    (new File(idDir, id)).delete();
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            } finally {
                if (opstream != null) opstream.close();
            }
        }        
    }

    private static final String CRLF = "\r\n", DASH_DASH = "--";
    private static final String URL = "http://%s/Upload/scan?type=GR&id=%s";
    
}
