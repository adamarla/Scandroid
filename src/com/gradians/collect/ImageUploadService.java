package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
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
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        File imagesDir = this.getApplicationContext().getDir(APP_DIR_NAME, MODE_PRIVATE);
        File[] images = imagesDir.listFiles(new ImageFilter());

        URL url = null;
        HttpURLConnection httpUrlConnection = null;
        OutputStream ostream = null;
        PrintWriter opstream = null;        
        String boundary =  null;
        String[] params = null;
        for (File image : images) {
            
            Log.d(TAG, "uploading file " + image.getName());
            params = image.getName().split("\\.");
            boundary = String.valueOf(System.currentTimeMillis());
            try {
                url = new URL(String.format(URL,
                        BANK_HOST_PORT, params[0], params[1]));
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
                        image.getName() + "\"").append(CRLF);
                opstream.append("Content-Type: " + 
                        HttpURLConnection.guessContentTypeFromName(image.getName())).append(CRLF);
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
                
                if (httpUrlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    image.delete();
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            } finally {
                if (opstream != null) opstream.close();
            }
        }        
    }

    private static final String CRLF = "\r\n", DASH_DASH = "--";
    private static final String URL = "http://%s/Upload/scan?type=%s&id=%s";
    
}

class ImageFilter implements FilenameFilter, IConstants {

    @Override
    public boolean accept(File dir, String filename) {
        return filename.endsWith(IMG_EXT);
    }
    
}