package com.gradians.collect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.HashMap;
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
import android.os.Parcelable;
import android.app.Activity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.support.v4.app.NavUtils;
import android.annotation.TargetApi;
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
        final int position = params.getIntExtra(TAG_ID, 0);
        imagesDir = new File(getIntent().getStringExtra(SCANS_KEY));
        Parcelable[] parcels = params.getParcelableArrayExtra(TAG);
        quizId = ((Question)parcels[0]).getId().split("\\.")[0];
        
        partPgMap = new HashMap<String, Integer>();
        selectedParts = new HashSet<Integer>();
        
        questions = new Question[parcels.length];
        int partsToPosn = 0;
        PartButton btn = null;
        final LinearLayout llButtons = (LinearLayout)this.findViewById(R.id.llSelectorBtns);
        for (int i = 0; i < questions.length; i++) {
            
            questions[i] = (Question)parcels[i];
            int[] pgMap = questions[i].getPgMap();
            boolean[] sent = questions[i].getSentState();
            if (position > i) partsToPosn += pgMap.length;
            for (int j = 0; j < pgMap.length; j++) {
                
                btn = new PartButton(this);
                btn.setTag(i + "." + j);
                btn.setText(pgMap.length == 1 ? questions[i].getName() :
                    questions[i].getName() + (char)((int)'a'+j));
                btn.setTextColor(getResources().getColorStateList(R.drawable.qsn_text_color));
                btn.setBackgroundResource(R.drawable.qsn_background_selector);
//                btn.setTextSize(getResources().getDimension(R.dimen.small_font_size));
                btn.setTextSize(12f);
                LayoutParams lp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                lp.setMargins(2, 0, 2, 0);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(this);

                if (questions[i].getState() > CAPTURED) {
                    btn.setIsSent(true);
                } else {
                    if (pgMap[j] != 0) {
                        if (sent[j]) {
                            btn.setIsSent(true);
                        } else {
                            btn.setIsCaptured(true);
                            partPgMap.put((String)btn.getTag(), pgMap[j]);
                        }
                    }
                }
                btn.refreshDrawableState();
                llButtons.addView(btn);
            }
        }
        
        btnAction = (ImageButton)findViewById(R.id.btnAction);
        btnAction.setEnabled(false);
        
        updateCounts();
        
        final int offset = partsToPosn;
        final HorizontalScrollView hsvSelectors = 
                (HorizontalScrollView)this.findViewById(R.id.hsvSelectorBtns);
        hsvSelectors.post(new Runnable() {
            @Override
            public void run() {
                int scrollTo = hsvSelectors.getWidth()*offset/llButtons.getChildCount();
                hsvSelectors.smoothScrollTo(scrollTo, 0);
            }
        });

    }
    
    @Override
    public void onBackPressed() {
        String tag = null;
        for (int i = 0; i < questions.length; i++) {
            int[] pgMap = questions[i].getPgMap();
            for (int j = 0; j < pgMap.length; j++) {
                tag = i+"."+j;
                if (partPgMap.containsKey(tag)) {
                    pgMap[j] = partPgMap.get(tag);
                }
            }
            questions[i].setPgMap(pgMap);
        }        
        Intent intent = new Intent();
        intent.putExtra(TAG, questions);
        this.setResult(RESULT_OK, intent);
        releaseCamera();
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
        
        if (cameraOn) {
            // capture action
            int openPgNo = 1;            
            String nextName = quizId + "." + openPgNo;
            while ((picture = new File(imagesDir, nextName)).exists()) {
                openPgNo++;
                nextName = quizId + "." + openPgNo;
            }
            camera.takePicture(null, null, new PictureWriter(this, picture));            
        } else {
            // discard action
            String tag;
            int[] map;
            int i,j;
            PartButton pb;
            Iterator<Integer> iter = selectedParts.iterator();
            while (iter.hasNext()) {
                pb = (PartButton)llButtons.getChildAt(iter.next());
                pb.setSelected(false);
                pb.setIsCaptured(false);
                pb.refreshDrawableState();
                
                tag = (String)pb.getTag();
                int pg = partPgMap.get(tag);
                (new File(imagesDir, quizId + "." + pg)).delete();
                partPgMap.remove(pb.getTag());
                i = Integer.parseInt(tag.split("\\.")[0]);
                j = Integer.parseInt(tag.split("\\.")[1]);
                map = questions[i].getPgMap();
                map[j] = 0;
                questions[i].setPgMap(map);
            }
            selectedParts.clear();
            comandeerCamera();
            updateCounts();
        }
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public void onClick(View v) {
        PartButton pb = (PartButton)v;
        if (pb.isSent()) return;
        
        LinearLayout llButtons = (LinearLayout) this.findViewById(R.id.llSelectorBtns);        
        pb.setSelected(!pb.isSelected());
        if (pb.isCaptured()) {
            btnAction.setImageResource(R.drawable.ic_action_rotate_right);
            
            int pg = this.partPgMap.get(pb.getTag());            
            // unselect other buttons
            PartButton other;
            for (int i = 0; i < llButtons.getChildCount(); i++) {
                other = (PartButton)llButtons.getChildAt(i);
                if (!other.isCaptured()) 
                    other.setSelected(false);
                else if (partPgMap.get(other.getTag()) != pg)
                    other.setSelected(false);
                else
                    other.setSelected(pb.isSelected());
                other.refreshDrawableState();
            }
            
            if (pb.isSelected()) {
                btnAction.setEnabled(true);
                cameraOn = false;
                File file = new File(imagesDir, quizId + "." + pg);
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
            btnAction.setImageResource(android.R.drawable.ic_menu_camera);
            
            boolean anySelected = false;
            PartButton other;
            for (int i = 0; i < llButtons.getChildCount(); i++) {
                other = (PartButton)llButtons.getChildAt(i);
                if (other.isSelected()) {
                    if (other.isCaptured()) {
                        other.setSelected(false);
                    } else {
                        anySelected = true;
                    }
                }                
                other.refreshDrawableState();
            }            
            if (anySelected) {
                if (!cameraOn) {
                    cameraOn = true;
                    comandeerCamera();
                }
            }
            btnAction.setEnabled(anySelected);
        }
    }

    protected void doneTaking(boolean success) {
        cameraOn = false;
        if (success) {
            // update selector buttons
            int pg = Integer.parseInt(picture.getName().split("\\.")[1]);
            PartButton pb;
            String tag;
            int i, j;
            int[] map;
            LinearLayout llButtons = (LinearLayout)this.findViewById(R.id.llSelectorBtns);
            for (int spIdx : selectedParts) {
                pb = (PartButton)llButtons.getChildAt(spIdx);
                pb.setIsCaptured(true);
                pb.refreshDrawableState();
                
                tag = (String)pb.getTag();
                partPgMap.put(tag, pg);
                i = Integer.parseInt(tag.split("\\.")[0]);
                j = Integer.parseInt(tag.split("\\.")[1]);
                map = questions[i].getPgMap();
                map[j] = pg;
                questions[i].setPgMap(map);
            }
            selectedParts.clear();
            btnAction.setImageResource(R.drawable.ic_action_rotate_right);
            updateCounts();
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
    }
    
    private void releaseButtons() {
        LinearLayout llButtons = (LinearLayout) this.findViewById(R.id.llSelectorBtns);        
        for (int i = 0; i < llButtons.getChildCount(); i++) {
            llButtons.getChildAt(i).setSelected(false);            
        }        
    }
    
    private void updateCounts() {
        int sentCount = 0, capturedCount = 0;
        PartButton btn;
        LinearLayout llButtons = (LinearLayout)this.findViewById(R.id.llSelectorBtns);
        for (int i = 0; i < llButtons.getChildCount(); i++) {
            btn = (PartButton)llButtons.getChildAt(i);
            if (btn.isSent()) sentCount++;
            if (btn.isCaptured()) capturedCount++;
        }
        
        TextView tvSent = (TextView)findViewById(R.id.tvSentCount);
        tvSent.setText(String.format("Sent %3d", (sentCount)));
        
        TextView tvCaptured = (TextView)findViewById(R.id.tvCapturedCount);
        tvCaptured.setText(String.format("Captured %3d", (capturedCount+sentCount)));
        
        TextView tvTotal = (TextView)findViewById(R.id.tvTotalCount);
        tvTotal.setText(String.format("Total %3d", llButtons.getChildCount()));
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
        if (params.getColorEffect() != null) {
            pref = getOptimalColorEffect();
            if (pref != null) params.setColorEffect(pref);            
        }
        if (params.getFlashMode() != null) {
            pref = getOptimalFlashMode();
            if (pref != null) params.setFlashMode(pref);
        }
        if (params.getWhiteBalance() != null) {
            pref = getOptimalWhiteBalance();
            if (pref != null) params.setWhiteBalance(pref);
        }
        if (params.getSceneMode() != null) {
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

    private Question[] questions;
    private boolean cameraOn;
    private String quizId;
    private File picture, imagesDir;
    
    private HashSet<Integer> selectedParts;
    private HashMap<String, Integer> partPgMap;
    
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
