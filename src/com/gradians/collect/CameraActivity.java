package com.gradians.collect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
        
        btnAction = (ImageButton)findViewById(R.id.btnAction);
        btnAction.setEnabled(true);
        
        llButtons = (LinearLayout)this.findViewById(R.id.llSelectorBtns);        
        createPartButtons(llButtons);
        
        if (Build.VERSION.SDK_INT < 11) {   // No vertical rotation below this level
            findViewById(R.id.tvVert1).setVisibility(View.INVISIBLE);
            findViewById(R.id.tvVert2).setVisibility(View.INVISIBLE);
        } 
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            if (!question.getPgMap("").contains("0")) {
                confirmUpload();
                return true;
            } else if (question.hasScan()) {
                // some parts complete
                promptIncomplete();
            } else {
                concludeActivity();
            }
        default:
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        if (!question.getPgMap("").contains("0")) {
            // all parts complete
            confirmUpload();
        } else if (question.hasScan()) {
            // some parts complete
            promptIncomplete();
        } else {
            // no picture taken
            concludeActivity();
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        comandeerCamera();
        refreshButtons();
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
            
            final Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
                // don't do anything here, but we need to implement the callback to get 
                // the shutter sound (at least on Galaxy Nexus and Nexus 7)
                public void onShutter() { }
            };
            
            final CameraActivity caller = this;
            if (autofocus) {
                // focus camera            
                Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        camera.takePicture(shutterCallback, null, new PictureWriter(caller, picture));
                    }
                };
                
                try {
                    camera.autoFocus(autoFocusCallback);
                } catch(RuntimeException e) {
                    Log.e("gradians", "autofocus runtime ex " + e.getMessage());
                    camera.takePicture(shutterCallback, null, new PictureWriter(this, picture));
                }
                
            } else {                
                camera.takePicture(shutterCallback, null, new PictureWriter(this, picture));
            }
            
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
            question.setState(DOWNLOADED);
            refreshButtons();
            
            selectedParts.clear();
            comandeerCamera();
        }
    }
    
    @Override
    public void onClick(View v) {
        PartButton pb = (PartButton)v;
        
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
            
            btnAction.setImageResource(R.drawable.ic_action_discard);            
            if (pb.isSelected()) {
                btnAction.setEnabled(true);
                
                File file = new File(imagesDir, question.getId() + "." + map[pbPartIdx]);
                displayImage(file);
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
            
            btnAction.setImageResource(R.drawable.ic_action_camera);            
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
            btnAction.setImageResource(R.drawable.ic_action_discard);
        } else {
            releaseCamera();
            Intent intent = new Intent();
            setResult(Activity.RESULT_FIRST_USER, intent);
            finish();
        }
    }
    
    private void confirmUpload() {
        AlertDialog.Builder builder = null;
        try {
            builder = new AlertDialog.Builder(this,
                R.style.RobotoDialogTitleStyle);        
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "Older SDK, using old Builder");
            builder =  new AlertDialog.Builder(this);
        }        
        builder.setTitle("Upload?");
        builder.setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    releaseCamera();
                    Intent intent = new Intent();
                    intent.putExtra(TAG, question);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });
        builder.setNegativeButton(android.R.string.no,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
        builder.show().getWindow().setBackgroundDrawable(
            new ColorDrawable(Color.TRANSPARENT));
    }
    
    private void promptIncomplete() {
        AlertDialog.Builder builder = null;
        try {
            builder = new AlertDialog.Builder(this,
                R.style.RobotoDialogTitleStyle);        
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "Older SDK, using old Builder");
            builder =  new AlertDialog.Builder(this);
        }        
        builder.setTitle("Incomplete");
        builder.setMessage("Please capture images for all parts of the question");
        builder.setPositiveButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void concludeActivity() {
        releaseCamera();
        Intent intent = new Intent();
        intent.putExtra(TAG, question);
        setResult(RESULT_OK, intent);
        finish();
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
            ((FrameLayout)findViewById(R.id.camera_preview)).addView(cameraPreview);            
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
    
    @SuppressWarnings("deprecation")
    private void displayImage(File file) {
        cameraOn = false;        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            cameraPreview.setBackground(Drawable.createFromPath(file.getPath()));
        } else {
            cameraPreview.setBackgroundDrawable(Drawable.createFromPath(file.getPath()));
        }
    }
    
    private void releaseButtons() {
        LinearLayout llButtons = (LinearLayout) this.findViewById(R.id.llSelectorBtns);        
        for (int i = 0; i < llButtons.getChildCount(); i++) {
            llButtons.getChildAt(i).setSelected(false);
        }
    }
    
    private void refreshButtons() {
        PartButton btn = null;
        int[] pgMap = question.getPgMap();
        LinearLayout llButtons = (LinearLayout) this.findViewById(R.id.llSelectorBtns);        
        for (int i = 0; i < llButtons.getChildCount(); i++) {
            btn = (PartButton)llButtons.getChildAt(i);
            btn.setIsCaptured(pgMap[i] != 0);
            btn.refreshDrawableState();
        }
    }
    
    private void createPartButtons(LinearLayout llButtons) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int pixels = (int)(metrics.density*50f + 0.5f);
        
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
            btn.setSelected(true);
            btn.setTextSize(12f);
            LayoutParams lp = new LayoutParams(pixels, pixels);
            lp.setMargins(2, 0, 2, 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(this);
            llButtons.addView(btn);
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
        String optimal = null;
        List<String> supported = null;
        supported = params.getSupportedFocusModes();
        if (supported != null) {
            optimal = getOptimal(supported, FOCUS_MODE_PREFS);
            if (optimal != null) {
                params.setFocusMode(optimal);
                autofocus = true;
            }
        }
        supported = params.getSupportedColorEffects();
        if (supported != null) {
            optimal = getOptimal(supported, COLOR_EFFECT_PREFS);
            if (optimal != null) params.setColorEffect(optimal);
        }
        supported = params.getSupportedFlashModes();
        if (supported != null) {
            optimal = getOptimal(supported, FLASH_MODE_PREFS);
            if (optimal != null) params.setFlashMode(optimal);            
        }
        supported = params.getSupportedWhiteBalance();
        if (supported != null) {
            optimal = getOptimal(supported, WHITE_BAL_PREFS);
            if (optimal != null) params.setWhiteBalance(optimal);            
        }
        supported = params.getSupportedSceneModes();
        if (supported != null) {
            optimal = getOptimal(supported, SCENE_MODE_PREFS);
            if (optimal != null) params.setSceneMode(optimal);            
        }
        params.setRotation(PORTRAIT);
        params.setPictureFormat(ImageFormat.JPEG);
        Size s = getOptimalSize(params);
        params.setPictureSize(s.width, s.height);
        return params;
    }
    
    private String getOptimal(List<String> options, String[] preferences) {
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
    private boolean cameraOn, autofocus;
    private File picture, imagesDir;
    
    private LinearLayout llButtons;    
    
    private HashSet<Integer> selectedParts;
    
    private Camera camera;
    private CameraPreview cameraPreview;
    private ImageButton btnAction;
    
    private final int PORTRAIT = 90;
    private final int[] PREFERRED_SIZE = {1600, 1200};
    
    private final String[] WHITE_BAL_PREFS = { Parameters.WHITE_BALANCE_TWILIGHT,
        Parameters.WHITE_BALANCE_SHADE };
    private final String[] FLASH_MODE_PREFS = { Parameters.FLASH_MODE_AUTO };
    private final String[] COLOR_EFFECT_PREFS = { Parameters.EFFECT_MONO,
        Parameters.EFFECT_WHITEBOARD, Parameters.EFFECT_POSTERIZE};
    private final String[] SCENE_MODE_PREFS = { Parameters.SCENE_MODE_STEADYPHOTO };
    private final String[] FOCUS_MODE_PREFS = { Parameters.FOCUS_MODE_MACRO, 
        Parameters.FOCUS_MODE_AUTO };
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
        return drawableState;
    }
    
    public void setIsCaptured(boolean b) { captured = b; }
    
    public boolean isCaptured() { return captured; }
    
    private boolean captured = false;
    
    private static final int[] STATE_CAPTURED = {R.attr.state_captured};
    
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
