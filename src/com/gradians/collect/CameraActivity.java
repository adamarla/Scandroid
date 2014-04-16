package com.gradians.collect;

import java.io.File;
import java.io.FileOutputStream;

import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;

public class CameraActivity extends Activity implements IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        setContentView(R.layout.activity_camera);
        // Show the Up button in the action bar.
        setupActionBar();
        try {
            camera = Camera.open();
        } catch (Exception e) {
            finish();
        }
        File appDir = this.getApplicationContext().getDir(APP_DIR_NAME, MODE_PRIVATE);        
        picture = new File(appDir, this.getIntent().getStringExtra(TAG));
        preview = new CameraPreview(this, camera);
        ((FrameLayout)findViewById(R.id.camera_preview)).addView(preview);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.camera, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        releaseCamera();
    }

    public void captureImage(View view) {
        // get an picture from the camera
        camera.takePicture(null, null, new PictureWriter(picture, this));
        final Intent intent = new Intent();
        setResult(RESULT_OK, intent);
    }
    
    private void releaseCamera() {
        Log.d(TAG, "Release Camera");
        if (camera != null) {
            camera.release();
            camera = null;
        }        
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private File picture;
    private CameraPreview preview;
    private Camera camera;
}

class PictureWriter implements PictureCallback, IConstants {
        
    public PictureWriter(File picture, Activity caller) {
        this.caller = caller;
        this.picture = picture;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onPictureTake() ->");
        try {
            Log.d(TAG, picture.getAbsolutePath());
            FileOutputStream fos = new FileOutputStream(picture);
            fos.write(data);
            fos.close();
          } catch (Exception error) {
              Log.e(TAG, error.getMessage());
          }        
        Log.d(TAG, "onPictureTake() <-");
        caller.finish();
    }
    
    private Activity caller;
    private File picture;

}
