package com.gradians.collect;

import java.io.File;

import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends Activity {    

    @Override
    protected void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appDir = new File(getExternalFilesDir(null), "Scandroid");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                //uploadImageSMTP();                
                Toast.makeText(getApplicationContext(), 
                        "Processing...", 
                        Toast.LENGTH_SHORT).show();
                uploadImageHTTP();
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
            } else {
                // Image capture failed, advise user
                Toast.makeText(getApplicationContext(), 
                        "Oops.. image capture failed. Please, try again.", 
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
            
        }        
    }
    
    public void initiateCameraActivity(View view) {
        
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            image = createImageFile();
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
            startActivityForResult(takePictureIntent, 
                    CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), 
                    "Ooops... error initiating camera! Please try again", 
                    Toast.LENGTH_LONG).show();
        }
    }
    
    public void initiateAuthActivity() {
        
        try {
            Intent checkAuthIntent = new Intent(this, RegistrationActivity.class);
            startActivityForResult(checkAuthIntent, AUTH_ACTIVITY_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), 
                    "Ooops... Sorry, our bad :/", 
                    Toast.LENGTH_LONG).show();
        }
    }
    
    private void uploadImageSMTP() {
        // Image captured and saved to fileUri specified in the Intent
        Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND); 
        emailIntent.setType("application/image");
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, 
                new String[]{"akshay@gradians.com"}); 
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                "Incoming...");
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, 
                "Sent from my smart Phone!"); 
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(image));
        startActivityForResult(Intent.createChooser(emailIntent, "Send"), 
                SEND_EMAIL_ACTIVITY_REQUEST_CODE);
    }
    
    private void uploadImageHTTP() {
        File[] images = appDir.listFiles();
        new ImageUploader().execute(images);
    }

    private File createImageFile() throws Exception {
        if (!appDir.exists()) appDir.mkdir();
        
        String imageFileName = "SCAN_" + System.currentTimeMillis() + ".jpg";
        return new File(appDir, imageFileName);
    }
    
    private File image, appDir;
    
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int SEND_EMAIL_ACTIVITY_REQUEST_CODE = 200;
    private static final int AUTH_ACTIVITY_REQUEST_CODE = 300;
    private static final String TAG = "Scandroid";
    
    
}
