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
import android.widget.TextView;
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
        
        ((FrameLayout)findViewById(R.id.camera_preview)).
            addView(new CameraPreview(this, camera));
        
        imagesDir = new File(this.getIntent().getStringExtra(TAG));
        name_ids = this.getIntent().getStringArrayExtra(TAG_ID);     
        frame = 0;
        
        TextView tv = (TextView)this.findViewById(R.id.tvCameraPreview);
        tv.setText(name_ids[frame].split("-")[0]);
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

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG, frame);
        setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    public void takePicture(View view) {
        // get an picture from the camera
        File picture = new File(imagesDir, name_ids[frame]);
        camera.takePicture(null, null, 
                new PictureWriter(this, picture));
    }
    
    public void moveToNext() {
        if (frame == name_ids.length-1) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent);
            finish();
        } else {
            TextView tv = (TextView)this.findViewById(R.id.tvCameraPreview);
            tv.setText(name_ids[++frame].split("-")[0]);
            camera.stopPreview(); camera.startPreview();
        }
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
        
        if (match == null) {
            Size s;
            for (int i = availableSizes.size(); i > 0; i--) {
                s = availableSizes.get(i);
                if (s.width * 3 == s.height * 4) {
                    match = s;
                    break;
                }
            }            
        }
        return match;
    }

    private File imagesDir;
    private String[] name_ids;
    private int frame;
    private Camera camera;
    
    private final int PORTRAIT = 90;
    private final int[][] PREFERRED_SIZE = {{800, 600}, {1280, 960}, {960, 720}, {1600, 1200}};
    
}

class PictureWriter implements PictureCallback, IConstants {
        
    public PictureWriter(CameraActivity activity, File picture) {
        this.picture = picture;
        this.activity = activity;
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
        activity.moveToNext();
    }
    
    private CameraActivity activity;
    private File picture;

}
