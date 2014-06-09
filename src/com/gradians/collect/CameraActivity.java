package com.gradians.collect;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;

public class CameraActivity extends Activity implements IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        setContentView(R.layout.activity_camera);
        
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
        
        File imagesDir = new File(this.getIntent().getStringExtra(TAG));
        String name_id = this.getIntent().getStringExtra(TAG_ID);
        picture = new File(imagesDir, name_id.split("-")[1]);
        
        TextView tv = (TextView)this.findViewById(R.id.tvCameraPreview);
        tv.setText(name_id.split("-")[0]);        
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
        camera.takePicture(null, null,  
                new PictureWriter(this, picture));
    }
    
    public void upload(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bombs away...").setMessage(
                "This action is not reversible!");
        builder.setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User clicked OK button
                    Intent intent = new Intent();
                    intent.setData(Uri.fromFile(picture));
                    setResult(Activity.RESULT_OK, intent);
                    finish();        
                }
            });
        builder.setNegativeButton(android.R.string.cancel,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                }
            });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    public void retake(View view) {
        try {
            camera.stopPreview();
            camera.startPreview();
            findViewById(R.id.btnCapture).setVisibility(View.VISIBLE);
            findViewById(R.id.btnBarConf).setVisibility(View.GONE);
        } catch (Exception e) {
            finish();
        }
    }
    
    public void doneTaking(boolean success) {
        if (!success) {
            releaseCamera();
            // User clicked OK button
            Intent intent = new Intent();
            intent.setData(Uri.fromFile(picture));
            setResult(Activity.RESULT_FIRST_USER, intent);
            finish();            
        } else {
            findViewById(R.id.btnCapture).setVisibility(View.GONE);
            findViewById(R.id.btnBarConf).setVisibility(View.VISIBLE);            
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
        params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
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

    private File picture;
    private Camera camera;
    
    private final int PORTRAIT = 90;
    private final int[][] PREFERRED_SIZE = {{800, 600}, {1280, 960}, {960, 720}, {1600, 1200}};
    
}

class PictureWriter implements PictureCallback {
        
    public PictureWriter(CameraActivity activity, File picture) {
        this.picture = picture;
        this.caller = activity;
    }    

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        try {
            FileOutputStream fos = new FileOutputStream(picture);
            fos.write(data);
            fos.close();
            caller.doneTaking(true);
        } catch (Exception error) {
            Log.e(IConstants.TAG, error.getMessage());
            caller.doneTaking(false);
        }
    }
    
    private CameraActivity caller;
    private File picture;

}
