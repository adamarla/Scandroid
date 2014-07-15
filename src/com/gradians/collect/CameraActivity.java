package com.gradians.collect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
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
import android.os.Parcelable;
import android.app.Activity;
import android.app.ProgressDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;

public class CameraActivity extends Activity implements ITaskResult, IConstants, OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        setContentView(R.layout.activity_camera);
        
        setupActionBar();
        
        Intent params = getIntent();
        final int position = params.getIntExtra(TAG_ID, 0);
        imagesDir = new File(getIntent().getStringExtra(SCANS_KEY));
        Parcelable[] parcels = params.getParcelableArrayExtra(TAG);
        quizId = ((Question)parcels[0]).getId().split("\\.")[0];
        
        filePartMap = new ArrayList<String>();
        selectedParts = new ArrayList<Integer>();
        
        Question[] questions = new Question[parcels.length];
        int partsToPosn = 0;
        Button btn = null;
        final LinearLayout llButtons = (LinearLayout)this.findViewById(R.id.llSelectorBtns);
        for (int i = 0; i < questions.length; i++) {            
            questions[i] = (Question)parcels[i];
            String[] pgNos = questions[i].getMap().split("-");
            if (position > i) partsToPosn += pgNos.length;
            for (int j = 0; j < pgNos.length; j++) {
                btn = new Button(this);
                btn.setText(pgNos.length == 1 ? questions[i].getName() :
                    questions[i].getName() + (char)((int)'a'+j));
                btn.setTextColor(R.drawable.qsn_text_color);
                btn.setBackgroundResource(R.drawable.qsn_background);
                btn.setTextSize(14);
                LayoutParams lp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                lp.setMargins(2, 0, 2, 0);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(this);
                
                if (questions[i].getState() > SENT) {
                    btn.setEnabled(false);
                } else if (!pgNos[j].equals("0")) {
                    btn.setEnabled(false);
                } else if (position == i) {
                    btn.setSelected(true);
                }
                llButtons.addView(btn);
            }
        }
        
        final int offset = partsToPosn;
        final HorizontalScrollView hsvSelectors = 
                (HorizontalScrollView)this.findViewById(R.id.hsvSelectorBtns);
        hsvSelectors.post(new Runnable() {
            @Override
            public void run() {
                int scrollTo = hsvSelectors.getWidth() * offset / llButtons.getChildCount();
                hsvSelectors.smoothScrollTo(scrollTo, 0);
            }
        });

    }
    
    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG, filePartMap.toArray(new String[filePartMap.size()]));
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
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
    protected void onResume() {
        super.onResume();
        comandeerCamera();
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
        if (!captured) {
            LinearLayout llButtons = (LinearLayout)this.findViewById(R.id.llSelectorBtns);            
            for (int i = 0; i < llButtons.getChildCount(); i++) {
                if (llButtons.getChildAt(i).isSelected()) {
                    selectedParts.add(i);
                }
            }
            if (selectedParts.size() == 0) return;
            picture = new File(imagesDir, "picture"); 
            camera.takePicture(null, null, new PictureWriter(this, picture));
        } else {
            int newPgNo = imagesDir.list(new AnswerFilesFilter(quizId)).length+1;
            String confirmedName = quizId + "." + newPgNo;
            picture.renameTo(new File(imagesDir, confirmedName));
            filePartMap.add(newPgNo + ":" + TextUtils.join("-", selectedParts));
            
            // update selector buttons
            LinearLayout llButtons = (LinearLayout)this.findViewById(R.id.llSelectorBtns);
            for (int j : selectedParts) {
                ((Button)llButtons.getChildAt(j)).setEnabled(false);
                ((Button)llButtons.getChildAt(j)).setSelected(false);
            }
            selectedParts.clear();
            retake(null);        
        }
    }
    
    @Override
    public void onClick(View v) {
        if (captured) return;
        if (!v.isEnabled()) {
            return;
        } else {
            v.setSelected(!v.isSelected());
        }
    }

    public void retake(View view) {
        try {
            camera.stopPreview();
            camera.startPreview();
            
            Button btnCapture = (Button)findViewById(R.id.btnCapture);
            btnCapture.setText("Capture");
            btnCapture.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_camera, 0);
            
            captured = false;
        } catch (Exception e) {
            finish();
        }
    }
    
    public void doneTaking(boolean success) {
        if (success) {
            captured = true;
            Button btnCapture = (Button)findViewById(R.id.btnCapture);
            btnCapture.setText("Accept");
            btnCapture.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_save, 0);
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
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        
//        if (cameraPreview != null) {
//            ((FrameLayout)findViewById(R.id.camera_preview)).removeAllViews();
//            cameraPreview = null;
//        }
    }
    
    private void comandeerCamera() {
        if (camera == null) {
            try {
                camera = Camera.open();
            } catch (Exception e) {
                finish();
            }
            camera.setParameters(configureParams(camera.getParameters()));
            camera.setDisplayOrientation(PORTRAIT);
        }
        
        if (cameraPreview == null) {
            cameraPreview = new CameraPreview(this, camera);
            ((FrameLayout)findViewById(R.id.camera_preview)).
                addView(cameraPreview);
        } else {
            cameraPreview.setCamera(camera);
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
        params.setColorEffect(Camera.Parameters.EFFECT_MONO);
        params.setRotation(PORTRAIT);
        params.setPictureFormat(ImageFormat.JPEG);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        Size s = getOptimalSize(params);
        params.setPictureSize(s.width, s.height);
        return params;
    }    

    private Size getOptimalSize(Parameters params) {
        Size s = null;
        int delta, index = 0;
        List<Size> availableSizes = camera.getParameters().getSupportedPictureSizes();
        delta = Math.abs(PREFERRED_SIZE[0] - availableSizes.get(0).width);
        for (int i = 0; i < availableSizes.size(); i++) {
            s = availableSizes.get(i);
            if ((Math.abs(PREFERRED_SIZE[0] - s.width) < delta) &&
                (s.width*3 == s.height * 4)) {
                delta = Math.abs(PREFERRED_SIZE[0] - s.width);
                index = i;
            }
        }
        return availableSizes.get(index);
    }
    
    private boolean captured;
    private String quizId;
    private File picture, imagesDir;
    private ArrayList<String> filePartMap;
    
    private Camera camera;
    private CameraPreview cameraPreview;
    private ArrayList<Integer> selectedParts;
    
    private ProgressDialog peedee;
    
    private final int PORTRAIT = 90;
    private final int[] PREFERRED_SIZE = {1280, 960};
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

class AnswerFilesFilter implements FilenameFilter {
    
    String quizId;
    public AnswerFilesFilter(String quizId) {
        this.quizId = quizId;
    }
    
    @Override
    public boolean accept(File dir, String filename) {
        return filename.startsWith(quizId);
    }
    
}
