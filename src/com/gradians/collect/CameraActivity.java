package com.gradians.collect;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
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
        camera.setParameters(configureParams(camera.getParameters()));
        camera.setDisplayOrientation(PORTRAIT);
        
        preview = new CameraPreview(this, camera);
        ((FrameLayout)findViewById(R.id.camera_preview)).addView(preview);
        
        picture = new File(this.getIntent().getStringExtra(TAG));        
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
        super.onPause();
        releaseCamera();
    }

    public void takePicture(View view) {
        // get an picture from the camera
        camera.takePicture(null, null, new PictureWriter(picture, this));
        final Intent intent = new Intent();
        setResult(RESULT_OK, intent);
    }
    
    private void releaseCamera() {
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
    
    private Camera.Parameters configureParams(Camera.Parameters params) {
        params.setRotation(PORTRAIT);
        params.setPictureFormat(ImageFormat.JPEG);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        Size s = getOptimalSize(params);
        params.setPictureSize(s.width, s.height);
        return params;
    }    

    private Size getOptimalSize(Parameters params) {
        Size match = null;
        List<Size> availableSizes = camera.getParameters().getSupportedPictureSizes();
        for (int[] ps : PREFERRED_SIZE) {
            for (Size s : availableSizes) {
                if (ps[0] == s.width && ps[1] == s.height) {
                    match = s;
                    break;
                }
            }            
            if (match != null) break;
        }        
        return match;
    }

    private File picture;
    private CameraPreview preview;
    private Camera camera;
    
    private final int PORTRAIT = 90;
    private final int[][] PREFERRED_SIZE = {{800, 600}, {1280, 960}, {640, 480}};
    
}

class PictureWriter implements PictureCallback, IConstants {
        
    public PictureWriter(File picture, Activity caller) {
        this.caller = caller;
        this.picture = picture;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        try {
            FileOutputStream fos = new FileOutputStream(picture);
            fos.write(data);
            fos.close();
          } catch (Exception error) { 
              Log.e(TAG, error.getMessage());
          }
        caller.finish();
    }
    
    private Activity caller;
    private File picture;

}
