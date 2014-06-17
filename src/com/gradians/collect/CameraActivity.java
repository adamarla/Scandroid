package com.gradians.collect;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;

public class CameraActivity extends Activity implements ITaskResult, IConstants {

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
        
        File imagesDir = new File(getIntent().getStringExtra(TAG));
        String id = getIntent().getStringExtra(TAG_ID).split("-")[1];
        price = getIntent().getIntExtra(QUIZ_PRICE_KEY, 0);
        picture = new File(imagesDir, id);
        
        String ws_id = id.substring(0, id.indexOf('.'));
        try {
            url = new URL(String.format(BILL_URL, 
                    WEB_APP_HOST_PORT, ws_id));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
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
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == BILL_WORKSHEET_TASK_RESULT_CODE) {
            if (peedee != null) peedee.dismiss();
            if (resultCode == RESULT_OK) {  
                try {
                    JSONParser jsonParser = new JSONParser();
                    JSONObject respObject = (JSONObject)jsonParser.parse(resultData);
                    JSONArray gr_ids = (JSONArray)respObject.get("gr_ids");
                    String[] grIds = new String[gr_ids.size()];
                    for (int i = 0; i < grIds.length; i++) {
                        grIds[i] = (String)gr_ids.get(i);
                    }
                    Intent intent = new Intent();
                    intent.setData(Uri.fromFile(picture));
                    intent.putExtra(TAG_ID, grIds);
                    setResult(Activity.RESULT_OK, intent);
                    finish();        
                } catch (Exception e) { 
                    Log.e(TAG, "Bill Worksheet failed: " + e.getMessage());
                }
            }
        }
    }

    public void capture(View view) {
        if (captured) return;
        camera.takePicture(null, null,  
                new PictureWriter(this, picture));
    }
    
    public void upload(View view) {
        if (!captured) return;
        final ITaskResult handler = this;
        final Context context = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (price == 0) {
            builder.setTitle("Send this question?").setMessage(
                    "This action is not reversible");
        } else {
            builder.setTitle("Wan't us to look at your work?").setMessage(
                    "Buy this Quiz for " + price + " Gredits");
        }
        builder.setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (price == 0) {
                        // User clicked OK button
                        Intent intent = new Intent();
                        intent.setData(Uri.fromFile(picture));
                        setResult(Activity.RESULT_OK, intent);
                        finish();        
                    } else {
                        peedee = ProgressDialog.show(context, "Executing purchase", 
                                "Please wait...");
                        peedee.setIndeterminate(true);
                        peedee.setIcon(ProgressDialog.STYLE_SPINNER);
                        URL[] urls = new URL[] { url };
                        new HttpCallsAsyncTask(handler, 
                                BILL_WORKSHEET_TASK_RESULT_CODE).execute(urls);
                    }
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
            ((ImageButton)findViewById(R.id.btnCapture)).
                setImageResource(android.R.drawable.ic_menu_camera);
            findViewById(R.id.btnCapture).setBackgroundResource(R.drawable.blue_button);
            findViewById(R.id.btnUpload).setBackgroundResource(R.drawable.gray_button);
            findViewById(R.id.btnRetake).setBackgroundResource(R.drawable.gray_button);
            captured = false;
        } catch (Exception e) {
            finish();
        }
    }
    
    public void doneTaking(boolean success) {
        if (success) {
            captured = true;
            findViewById(R.id.btnCapture).setBackgroundResource(R.drawable.gray_button);
            findViewById(R.id.btnUpload).setBackgroundResource(R.drawable.blue_button);
            findViewById(R.id.btnRetake).setBackgroundResource(R.drawable.blue_button);
        } else {
            releaseCamera();
            // User clicked OK button
            Intent intent = new Intent();
            intent.setData(Uri.fromFile(picture));
            setResult(Activity.RESULT_FIRST_USER, intent);
            finish();            
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

    private boolean captured;
    private URL url;
    private int price;
    private File picture;
    private Camera camera;
    
    private ProgressDialog peedee;
    
    private final int PORTRAIT = 90;
    private final int[][] PREFERRED_SIZE = {{800, 600}, {1280, 960}, {960, 720}, {1600, 1200}};    
    private final String BILL_URL = "http://%s/tokens/bill_ws?id=%s";
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
