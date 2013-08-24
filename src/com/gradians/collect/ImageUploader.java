package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.AsyncTask;
import android.util.Log;

public class ImageUploader extends AsyncTask<File, Integer, Void> {

    public static final String TAG = "Scandroid";
    File image;
    
    @Override
    protected Void doInBackground(File... images) {
        
        OutputStream ostream = null;
        PrintWriter opstream = null;
        
        String CRLF = "\r\n";
        String DASH_DASH = "--";
        String boundary =  null;
        
        for (int i = 0; i < images.length; i++) {
            
            boundary =  String.valueOf(System.currentTimeMillis());
            image = images[i];
            
            try {            
                HttpURLConnection httpUrlConnection = null;
                URL url = new URL("http://109.74.201.62:8080/Upload/scan?qqfile=DraftProposal.pdf");
                httpUrlConnection = (HttpURLConnection) url.openConnection();
                httpUrlConnection.setDoOutput(true);

                httpUrlConnection.setRequestMethod("POST");
                httpUrlConnection.setRequestProperty("Connection", "Keep-Alive");
                httpUrlConnection.setRequestProperty("Cache-Control", "no-cache");
                httpUrlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                
                ostream = httpUrlConnection.getOutputStream();
                opstream = new PrintWriter(ostream); 

                opstream.append("--" + boundary).append(CRLF);
                opstream.append("Content-Disposition: form-data; name=\"image\"; filename=\"" + image.getName() + "\"").append(CRLF);
                opstream.append("Content-Type: " + HttpURLConnection.guessContentTypeFromName(image.getName())).append(CRLF);
                opstream.append("Content-Transfer-Encoding: binary").append(CRLF);
                opstream.append(CRLF).flush();
                
                InputStream imgstream = null;
                byte[] buffer = new byte[1024];
                imgstream = new FileInputStream(image);
                for (int length = 0; (length = imgstream.read(buffer)) > 0;) {
                    ostream.write(buffer, 0, length);
                }
                imgstream.close();
                ostream.flush(); // Important! Output cannot be closed. Close of writer will close output as well.
                opstream.append(CRLF).flush(); // CRLF is important! It indicates end of binary boundary.
                
                // End of multipart/form-data.
                opstream.append(DASH_DASH + boundary + DASH_DASH).append(CRLF);            
                opstream.close();
                
                int responseCode = httpUrlConnection.getResponseCode();
                Log.v(TAG, String.format("%s: %s", responseCode, 
                        httpUrlConnection.getResponseMessage()));
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    image.delete();
                }
                
            } catch (Exception e) {
                Log.v(TAG, e.getMessage());
            } finally {
                if (opstream != null) opstream.close();
            }            
        }
        
        return null;
    }

}
