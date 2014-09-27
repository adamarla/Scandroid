package com.gradians.collect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import android.graphics.ImageFormat;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class CameraActivity extends Activity implements IConstants, OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        
        setupActionBar();
        
        Intent params = getIntent();
        imagesDir = new File(getIntent().getStringExtra(SCANS_KEY));
        question = (Question)params.getParcelableExtra(TAG);
        
        selectedParts = new HashSet<Integer>();
        
        final LinearLayout llButtons = 
            (LinearLayout)this.findViewById(R.id.llSelectorBtns);
        cameraOn = layoutPartButtons(llButtons);
        
        btnAction = (ImageButton)findViewById(R.id.btnAction);
        btnAction.setEnabled(question.getState() == DOWNLOADED);
//        updateCounts();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            Intent intent = new Intent();
            intent.putExtra(TAG, question);
            this.setResult(RESULT_OK, intent);
            releaseCamera();
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG, question);
        this.setResult(RESULT_OK, intent);
        releaseCamera();
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        comandeerCamera();
    }

    @Override
    protected void onPause() {
        releaseCamera();
        releaseButtons();
        super.onPause();
    }

    public void action(View view) {
        LinearLayout llButtons = (LinearLayout) this.findViewById(R.id.llSelectorBtns);
        for (int i = 0; i < llButtons.getChildCount(); i++) {
            PartButton pb = (PartButton)llButtons.getChildAt(i);
            if (pb.isSelected()) {
                selectedParts.add(i);
            }
        }
        if (selectedParts.size() == 0) return;
        
        int[] map;
        int partIdx;
        PartButton pb;
        String name;
        Iterator<Integer> iter = selectedParts.iterator();
        if (cameraOn) {
            // capture action
            int openPgNo = 1;
            name = question.getId() + "." + String.valueOf(openPgNo);
            while ((picture = new File(imagesDir, name)).exists()) {
                openPgNo++;
                name = question.getId() + "." + String.valueOf(openPgNo);
            }
            camera.takePicture(null, null, new PictureWriter(this, picture));
        } else {
            // discard action
            map = question.getPgMap();
            while (iter.hasNext()) {
                pb = (PartButton)llButtons.getChildAt(iter.next());
                pb.setIsCaptured(false);
                
                partIdx = (Integer)pb.getTag();
                name = question.getId() + "." + String.valueOf(map[partIdx]);
                (new File(imagesDir, name)).delete();
                map[partIdx] = 0;
            }
            question.setPgMap(map);
            refreshButtons();
            selectedParts.clear();
            comandeerCamera();
        }
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public void onClick(View v) {
        PartButton pb = (PartButton)v;
        if (pb.isSent()) return;
        
        pb.setSelected(!pb.isSelected());
        pb.refreshDrawableState();
        int[] map = question.getPgMap();
        
        LinearLayout llButtons = (LinearLayout) this.findViewById(R.id.llSelectorBtns);        
        int pbPartIdx = (Integer)pb.getTag();
        if (pb.isCaptured()) {
            
            PartButton anyPart;
            int partIdx;
            for (int i = 0; i < llButtons.getChildCount(); i++) {
                anyPart = (PartButton)llButtons.getChildAt(i);
                partIdx = (Integer)anyPart.getTag();                    
                if ((Integer)pb.getTag() == partIdx) continue;
                
                if (anyPart.isCaptured()) {
                    if (map[partIdx] == map[pbPartIdx]) {
                        anyPart.setSelected(pb.isSelected());
                    } else {
                        anyPart.setSelected(false);
                    }
                    anyPart.refreshDrawableState();
                }
            }
            
            btnAction.setImageResource(R.drawable.ic_action_rotate_right);            
            if (pb.isSelected()) {
                btnAction.setEnabled(true);
                cameraOn = false;                
                File file = new File(imagesDir, question.getId() + "." + map[pbPartIdx]);                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    cameraPreview.setBackground(Drawable.createFromPath(file.getPath()));
                } else {
                    cameraPreview.setBackgroundDrawable(Drawable.createFromPath(file.getPath()));
                }                
            } else {
                btnAction.setEnabled(false);
                comandeerCamera();
            }
            
        } else {
            
            boolean anySelected = false;
            PartButton anyPart;
            int partIdx;
            for (int i = 0; i < llButtons.getChildCount(); i++) {
                anyPart = (PartButton)llButtons.getChildAt(i);
                partIdx = (Integer)anyPart.getTag();                    
                if ((Integer)pb.getTag() == partIdx) continue;
                
                if (anyPart.isCaptured()) {
                    anyPart.setSelected(false);
                    anyPart.refreshDrawableState();
                } else {
                    if (anyPart.isSelected()) {
                        anySelected = true;
                    }
                }
            }
            
            btnAction.setImageResource(android.R.drawable.ic_menu_camera);            
            if (pb.isSelected() || anySelected) {
                if (!cameraOn) {
                    comandeerCamera();
                }
                btnAction.setEnabled(true);
            } else {
                btnAction.setEnabled(false);
            }            
        }
        
    }

    protected void doneTaking(boolean success) {
        cameraOn = false;
        if (success) {
            // update selector buttons
            int posn = picture.getName().lastIndexOf('.')+1;
            int pg = Integer.parseInt(picture.getName().substring(posn));
            PartButton pb;
            int partIdx;
            int[] map = question.getPgMap();
            LinearLayout llButtons = (LinearLayout)this.findViewById(R.id.llSelectorBtns);
            for (int spIdx : selectedParts) {
                pb = (PartButton)llButtons.getChildAt(spIdx);
                pb.setIsCaptured(true);
                
                partIdx = (Integer)pb.getTag();
                map[partIdx] = pg;
            }            
            question.setPgMap(map);
            selectedParts.clear();
            refreshButtons();
            btnAction.setImageResource(R.drawable.ic_action_rotate_right);
        } else {
            releaseCamera();
            Intent intent = new Intent();
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
        
        if (cameraPreview != null) {
            ((FrameLayout)findViewById(R.id.camera_preview)).removeAllViews();
            cameraPreview = null;
        }
    }
    
    @SuppressWarnings("deprecation")
    private void comandeerCamera() {
        if (camera == null) {
            try {
                camera = Camera.open();
                camera.setParameters(configureParams(camera.getParameters()));
                camera.setDisplayOrientation(PORTRAIT);
            } catch (Exception e) {
                Log.e(TAG, e.getClass().getName());
                setResult(RESULT_FIRST_USER);
                finish();
                return;
            }
        }
        
        if (cameraPreview == null) {
            cameraPreview = new CameraPreview(this, camera);
            ((FrameLayout)findViewById(R.id.camera_preview)).
                addView(cameraPreview);
            cameraPreview.setCamera(camera);
        } else {
            camera.stopPreview();
            camera.startPreview();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                cameraPreview.setBackground(null);
            } else {
                cameraPreview.setBackgroundDrawable(null);
            }
        }
        btnAction.setImageResource(android.R.drawable.ic_menu_camera);
        cameraOn = true;
    }
    
    private void releaseButtons() {
        LinearLayout llButtons = (LinearLayout) this.findViewById(R.id.llSelectorBtns);        
        for (int i = 0; i < llButtons.getChildCount(); i++) {
            llButtons.getChildAt(i).setSelected(false);            
        }        
    }
    
    private void refreshButtons() {
        LinearLayout llButtons = (LinearLayout) this.findViewById(R.id.llSelectorBtns);        
        for (int i = 0; i < llButtons.getChildCount(); i++) {
            llButtons.getChildAt(i).refreshDrawableState();            
        }        
    }
    
    private boolean layoutPartButtons(LinearLayout llButtons) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int pixels = (int)(metrics.density*50f + 0.5f);
        
        boolean anySelected = false;
        PartButton btn = null;
        int[] pgMap = question.getPgMap();
        for (int j = 0; j < pgMap.length; j++) {
            
            btn = new PartButton(this);
            btn.setTag(j);
            btn.setText(pgMap.length == 1 ? 
                question.getName() :
                question.getName() + (char)((int)'a'+j));
            btn.setTextColor(getResources().getColorStateList(
                R.drawable.part_btn_text_selector));
            btn.setBackgroundResource(R.drawable.part_btn_selector);
//                btn.setTextSize(getResources().getDimension(R.dimen.small_font_size));
            btn.setTextSize(12f);
            LayoutParams lp = new LayoutParams(pixels, pixels);
            lp.setMargins(2, 0, 2, 0);
            btn.setLayoutParams(lp);
            btn.setIsSent(question.getState() > CAPTURED);
            btn.setIsCaptured(question.getState() > DOWNLOADED);            
            if (question.getState() == DOWNLOADED) {
                btn.setIsCaptured(pgMap[j] != 0);
                btn.setSelected(pgMap[j] == 0);
                anySelected = anySelected ? true : pgMap[j] == 0; 
            }
            btn.setOnClickListener(this);
            llButtons.addView(btn);
        }
        refreshButtons();
        return anySelected;
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
        String pref = null;
        if (params.getSupportedColorEffects() != null) {
            pref = getOptimalColorEffect();
            if (pref != null) params.setColorEffect(pref);            
        }
        if (params.getSupportedFlashModes() != null) {
            pref = getOptimalFlashMode();
            if (pref != null) params.setFlashMode(pref);
        }
        if (params.getSupportedWhiteBalance() != null) {
            pref = getOptimalWhiteBalance();
            if (pref != null) params.setWhiteBalance(pref);
        }
        if (params.getSupportedSceneModes() != null) {
            pref = getOptimalSceneMode();
            if (pref != null) params.setSceneMode(pref);
        }
        params.setRotation(PORTRAIT);
        params.setPictureFormat(ImageFormat.JPEG);
        Size s = getOptimalSize(params);
        params.setPictureSize(s.width, s.height);
        return params;
    }
    
    private String getOptimalWhiteBalance() {
        String[] preferences = { Camera.Parameters.WHITE_BALANCE_TWILIGHT,
            Camera.Parameters.WHITE_BALANCE_SHADE };
        List<String> options = camera.getParameters().getSupportedWhiteBalance();
        for (String pref : preferences) {
            for (String option : options) {
                if (option.equals(pref))
                    return pref;
            }
        }
        return null;
    }
    
    private String getOptimalFlashMode() {
        String[] preferences = { Camera.Parameters.FLASH_MODE_AUTO };
        List<String> options = camera.getParameters().getSupportedFlashModes();
        for (String pref : preferences) {
            for (String option : options) {
                if (option.equals(pref))
                    return pref;
            }
        }
        return null;
    }
    
    private String getOptimalColorEffect() {
        String[] preferences = { Camera.Parameters.EFFECT_MONO,
            Camera.Parameters.EFFECT_WHITEBOARD, Camera.Parameters.EFFECT_POSTERIZE};
        List<String> options = camera.getParameters().getSupportedColorEffects();
        for (String pref : preferences) {
            for (String option : options) {
                if (option.equals(pref))
                    return pref;
            }
        }
        return null;
    }
    
    private String getOptimalSceneMode() {
        String[] preferences = { Camera.Parameters.SCENE_MODE_STEADYPHOTO };
        List<String> options = camera.getParameters().getSupportedSceneModes();
        for (String pref : preferences) {
            for (String option : options) {
                if (option.equals(pref))
                    return pref;
            }
        }
        return null;
    }

    private Size getOptimalSize(Parameters params) {
        Size s = null;
        int delta = Integer.MAX_VALUE, index = 0;
        List<Size> availableSizes = camera.getParameters().getSupportedPictureSizes();
        for (int i = 0; i < availableSizes.size(); i++) {            
            s = availableSizes.get(i);
            if (s.width < PREFERRED_SIZE[0]) continue;
            
            if ((s.width - PREFERRED_SIZE[0]) <= delta &&
                (s.width*3 == s.height * 4)) {
                delta = s.width - PREFERRED_SIZE[0];
                index = i;
            }
        }
        return availableSizes.get(index);
    }

    private Question question;
    private boolean cameraOn;
    private File picture, imagesDir;
    
    private HashSet<Integer> selectedParts;
    
    private Camera camera;
    private CameraPreview cameraPreview;
    private ImageButton btnAction;
    
    private final int PORTRAIT = 90;
    private final int[] PREFERRED_SIZE = {1600, 1200};
}

class PartButton extends Button {
    
    public PartButton(Context context) {
        super(context);
    }
    
    public PartButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 2);
        if (captured) {
            mergeDrawableStates(drawableState, STATE_CAPTURED);
        }
        if (sent) {
            mergeDrawableStates(drawableState, STATE_SENT);
        }
        return drawableState;
    }
    
    public void setIsCaptured(boolean b) { captured = b; }
    public void setIsSent(boolean b) { sent = b; }
    
    public boolean isCaptured() { return captured; }
    public boolean isSent() { return sent; }
    
    private boolean captured = false;
    private boolean sent = false;    
    
    private static final int[] STATE_CAPTURED = {R.attr.state_captured};
    private static final int[] STATE_SENT = {R.attr.state_sent};
    
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
