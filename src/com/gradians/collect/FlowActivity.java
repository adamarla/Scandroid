package com.gradians.collect;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;
import android.widget.ViewSwitcher;


public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow);
        
        String[] name_state_ids = this.getIntent().getStringArrayExtra(TAG_ID);
        studentDir = new File(this.getIntent().getStringExtra(TAG));
        questions = toQuestions(name_state_ids);
        feedback = new Feedback[name_state_ids.length];
        adapter = new FlowAdapter(questions, this.getSupportFragmentManager());
        
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);
        vpPreview.setOnPageChangeListener(this);
        
        slideshow = true;
        onPageSelected(0);
    }

    @Override
    protected void onPause() {
        Intent intent = new Intent();
        intent.putExtra(TAG, toNameStateIds(questions));
        this.setResult(RESULT_OK, intent);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG, toNameStateIds(questions));
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Uri picture = data.getData();
                int position = vpPreview.getCurrentItem();
                Question q = questions[position];
                q.setScanLocn(picture.getPath());
                q.setState(CAPTURED);
                adapter.update(q);
                onPageSelected(position);
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), 
                        "Oops.. image capture failed. Please try again",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == FETCH_COMMENTS_TASK_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    this.findViewById(R.id.vsPreview);
                    parseFeedback(resultData);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), 
                        "Oops.. problems getting feedback (back). Please try again",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onPageScrollStateChanged(int arg0) { }
    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) { }
    @Override
    public void onPageSelected(int position) {
        short state = questions[position].getState();
        ImageButton btnAction1 = (ImageButton)this.findViewById(R.id.btnAction1);
        ImageButton btnAction2 = (ImageButton)this.findViewById(R.id.btnAction2);
        
        Log.d(TAG, questions[position].getGRId() + " " + questions[position].getName() + " " + questions[position].getState());
        
        switch (state) {
        case WAITING:
            btnAction1.setVisibility(View.INVISIBLE);
            btnAction2.setVisibility(View.INVISIBLE);
            break;
        case DOWNLOADED:
            btnAction1.setVisibility(View.VISIBLE);
            btnAction2.setVisibility(View.INVISIBLE);
            btnAction1.setImageResource(android.R.drawable.ic_menu_camera);
            break;
        case CAPTURED:
            btnAction1.setVisibility(View.VISIBLE);
            btnAction2.setVisibility(View.VISIBLE);
            btnAction1.setImageResource(android.R.drawable.ic_menu_upload);
            btnAction2.setImageResource(android.R.drawable.ic_menu_delete);
            break;
        case SENT:
        case RECEIVED:
        case GRADED:
            btnAction1.setVisibility(View.INVISIBLE);
            btnAction2.setVisibility(View.INVISIBLE);
            if (feedback[position] == null) triggerFeedback(position);
        }        
    }
    
    public void takeAction1(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        Question q = questions[currentIndex];
        switch (q.getState()) {
        case DOWNLOADED:
            takePicture(currentIndex);
            break;
        case CAPTURED:
            uploadPicture(currentIndex);
            break;
        default:
        }
    }
    
    public void takeAction2(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        Question q = questions[currentIndex];
        switch (q.getState()) {
        case CAPTURED:
            deletePicture(currentIndex);
            break;        
        default:
        }
    }
    
    public void toggleView(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        Question q = questions[currentIndex];
        switch (view.getId()) {
        case R.id.btnZoom:
            zoom = (zoom + 1)%3;
            break;
        case R.id.btnAlternate:
            toggleMode = !toggleMode;
            break;
        }
        setImage((q.getState() > DOWNLOADED) ?
                (toggleMode ? q.getImgLocn() : q.getScanLocn()): q.getImgLocn());
    }
    
    public void toggleComments(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        Question q = questions[currentIndex];
        Feedback fdbk = feedback[currentIndex];
        if (fdbkOn) {
            this.findViewById(R.id.vpFeedback).setVisibility(View.INVISIBLE);
        } else {
            renderFeedback(fdbk);
        }
        fdbkOn = !fdbkOn;
        setImage(q.getScanLocn(), fdbk);
    }
    
    public void toggleMode(View view) {
        ViewSwitcher vsFlow = (ViewSwitcher)this.findViewById(R.id.vsPreview);        
        if (slideshow) {
            int currentIndex = vpPreview.getCurrentItem();
            Question q = questions[currentIndex];
            setImage(q.getState() > DOWNLOADED ? 
                    (toggleMode ? q.getImgLocn() : q.getScanLocn()): q.getImgLocn());
            suspendSlideshow(q.getState());
            vsFlow.showNext();
        } else {
            vsFlow.showPrevious();
            resumeSlideshow();
        }
        
    }

    private void takePicture(int position) {
        Question q = questions[position];
        String name_id = q.getName() + "-" + q.getGRId();
        Intent takePictureIntent =
                new Intent(this.getApplicationContext(),
                        com.gradians.collect.CameraActivity.class);
        takePictureIntent.putExtra(TAG,
                (new File(studentDir, ANSWERS_DIR_NAME)).getPath());
        takePictureIntent.putExtra(TAG_ID, name_id);
        startActivityForResult(takePictureIntent,
                CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }
    
    private void deletePicture(int position) {
        Question q = questions[position];
        String path = q.getScanLocn();
        if ((new File(path)).delete()) {
            q.setState(DOWNLOADED);
            adapter.update(q);
            onPageSelected(position);
        }
    }

    private void uploadPicture(final int position) {
        final Question q = questions[position];
        final String path = q.getScanLocn();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ready to send?")
               .setMessage("This action is not reversible!");
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // User clicked OK button
                       Intent uploadIntent = new Intent(getApplicationContext(),
                               com.gradians.collect.ImageUploadService.class);
                       uploadIntent.putExtra(TAG, path);
                       //startService(uploadIntent);
                       q.setState(SENT);
                       adapter.update(q);
                       onPageSelected(position);
                   }
               });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // User cancelled the dialog
                   }
               });
        AlertDialog dialog = builder.create();
        dialog.show();            
    }
    
    private void setImage(String locn, Feedback fdbk) {
        Bitmap bmap = BitmapFactory.decodeFile(locn);
        float bmapAspectRatio = (float)bmap.getWidth()/bmap.getHeight();
        DisplayMetrics dmetrics = this.getApplicationContext().
                getResources().getDisplayMetrics();
        float scalingFactor = (zoom == 1) ? 1.25f : 1.75f;
        switch (zoom) {
        case 0:
            scalingFactor = 1.0f;
            break;
        case 1:
            scalingFactor = 1.25f;
            break;
        case 2:
            scalingFactor = 1.5f;
            
        }
        bmap = Bitmap.createScaledBitmap(bmap, 
                (int)(dmetrics.widthPixels*scalingFactor),
                (int)(dmetrics.widthPixels*scalingFactor/bmapAspectRatio), false);
        FdbkView iv = (FdbkView)this.findViewById(R.id.ivFullPreview);
        iv.setScale(scalingFactor);
        if (fdbk != null && fdbkOn) {
            iv.setLocators(fdbk.x, fdbk.y);
        }
        iv.setImageBitmap(bmap);
    }
    private void setImage(String locn) {
        this.setImage(locn, null);
    }
    
    private void resumeSlideshow() {
        slideshow = true;
        this.findViewById(R.id.btnZoom).setVisibility(View.INVISIBLE);
        this.findViewById(R.id.btnInfo).setVisibility(View.INVISIBLE);
        this.findViewById(R.id.btnAlternate).setVisibility(View.INVISIBLE);
        this.findViewById(R.id.vpFeedback).setVisibility(View.INVISIBLE);
        
        onPageSelected(vpPreview.getCurrentItem());
    }

    private void suspendSlideshow(int state) {
        slideshow = false;
        this.findViewById(R.id.btnAction1).setVisibility(View.INVISIBLE);
        this.findViewById(R.id.btnAction2).setVisibility(View.INVISIBLE);
        
        this.findViewById(R.id.btnZoom).setVisibility(View.VISIBLE);
        if (state > DOWNLOADED) {
            this.findViewById(R.id.btnAlternate).setVisibility(View.VISIBLE);
        }        
        if (state == GRADED) {
            this.findViewById(R.id.vpFeedback).setVisibility(View.VISIBLE);
            this.findViewById(R.id.btnInfo).setVisibility(View.VISIBLE);
        }

    }
    
    private void triggerFeedback(int position) {
        Question q = questions[position];
        String urlString = String.format(
                "http://%s/view/fdb.json?id=%s&sandbox=false&a=null",
                WEB_APP_HOST_PORT, q.getGRId());
        try {
            URL[] urls = { new URL(urlString) };
            new HttpCallsAsyncTask(this,
                    FETCH_COMMENTS_TASK_RESULT_CODE).execute(urls);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), 
                    "Problem retreiving comments, pls try again", 
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void parseFeedback(String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
        JSONArray comments = (JSONArray)respObject.get("comments");
        String[] text = new String[comments.size()];
        int[] x = new int[text.length], y = new int[text.length];
        for (int i = 0; i < comments.size(); i++) {
            JSONObject comment = (JSONObject)comments.get(i);
            text[i] = (String)comment.get("comment");
            x[i] = ((Long)comment.get("x")).intValue();
            y[i] = ((Long)comment.get("y")).intValue();
        }
        String id = String.valueOf((Long)respObject.get("a"));
        for (int i = 0; i < questions.length; i++) {
            if (questions[i].getGRId().equals(id)) {
                feedback[i] = new Feedback(text, x, y);
                break;
            }
        }
    }

    private void renderFeedback(Feedback feedback) {
        fdbkAdapter = new FeedbackAdapter(feedback, this);
        ViewPager vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        vpFdbk.setVisibility(View.VISIBLE);
        LayoutParams lp = (LayoutParams)vpFdbk.getLayoutParams();
        DisplayMetrics dmetrics = this.getApplicationContext().
                getResources().getDisplayMetrics();                                
        lp.height = (int)dmetrics.heightPixels/5;
        vpFdbk.setLayoutParams(lp);
        vpFdbk.setAdapter(fdbkAdapter);
    }

    private String[] toNameStateIds(Question[] questions) {
        String[] name_state_ids = new String[questions.length];
        for (int i = 0; i < questions.length; i++) {
            name_state_ids[i] = String.format("%s-%s-%s",
                    questions[i].getName(), 
                    questions[i].getState(), 
                    questions[i].getGRId());
        }
        return name_state_ids;
    }
    
    private Question[] toQuestions(String[] name_state_ids) {
        
        File questionsDir = new File(studentDir, QUESTIONS_DIR_NAME);
        File answersDir = new File(studentDir, ANSWERS_DIR_NAME); 
        File solutionsDir = new File(studentDir, SOLUTIONS_DIR_NAME); 
                
        Question[] questions = new Question[name_state_ids.length];
        String name, id, imgLocn = null, scanLocn = null; short state;
        for (int i = 0; i < questions.length; i++) {
            
            String[] tokens = name_state_ids[i].split("-");
            name = tokens[0];
            state = Short.parseShort(tokens[1]);
            id = tokens[2];
            
            switch (state) {
            case WAITING:
                imgLocn = "file:///android_asset/albert_einstein.jpg";
                scanLocn = "file:///android_asset/albert_einstein.jpg";
                break;
            case DOWNLOADED:
                imgLocn = (new File(questionsDir, id)).getPath();
                scanLocn = "file:///android_asset/albert_einstein.jpg";
                break;
            case CAPTURED:
            case SENT:
                imgLocn = (new File(questionsDir, id)).getPath();
                scanLocn = (new File(answersDir, id)).getPath();
                break;
            case RECEIVED:
            case GRADED:
                imgLocn = (new File(solutionsDir, id)).getPath();
                scanLocn = (new File(answersDir, id)).getPath();
                break;
            default:
            }
            
            questions[i] = new Question(name, id, imgLocn);
            questions[i].setState(state);
            questions[i].setScanLocn(scanLocn);
            
        }
        return questions;
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

    private Question[] questions;
    private Feedback[] feedback;
    private File studentDir;
    private ViewPager vpPreview;
    private FlowAdapter adapter;
    private FeedbackAdapter fdbkAdapter;
    private boolean slideshow, toggleMode, fdbkOn;
    private int zoom;
    
}

class Feedback {
    public Feedback(String[] text, int[] x, int[] y) {
        this.text = text; this.x = x; this.y = y;
    }
    public int[] x, y;
    public String[] text;
}

class FdbkView extends ImageView {

    public FdbkView(Context context) {
        super(context);
        init(context);
    }
    
    public FdbkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);        
    }
    
    public void setScale(float scale) {
        this.scale = scale;
    }
    
    public void setLocators(int[] x, int[] y) {
        this.x = x; this.y = y;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paletteIndex = 0;
        for (int i = 0; i < x.length; i++) {
            canvas.save();
            canvas.scale(scale, scale);     
            canvas.drawBitmap(PALETTE[paletteIndex], 
                    x[i] * X_OFFSET, 
                    y[i] * Y_OFFSET, paint);
            canvas.restore();
            paletteIndex = (paletteIndex + 1) % 3;
        }
    }
    
    private void init(Context context) {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        
        scale = 1.0f;
        x = new int[0];
        y = new int[0];
        AssetManager am = context.getAssets();        
        try {
            PALETTE[0] = BitmapFactory.decodeStream(am.open("parentheses/pink.png"));
            PALETTE[1] = BitmapFactory.decodeStream(am.open("parentheses/green.png"));
            PALETTE[2] = BitmapFactory.decodeStream(am.open("parentheses/black.png"));
        } catch (IOException e) {
            Log.e("gradians", e.getMessage());
        }
    }    
    
    private float scale;
    private int[] x,y;
    private Paint paint;
    
    private final int X_OFFSET = 8, Y_OFFSET = 10;
    private final Bitmap[] PALETTE = new Bitmap[3];
    private static int paletteIndex = 0;
    
}

class FeedbackAdapter extends PagerAdapter {
    
    public FeedbackAdapter(Feedback feedback, Activity activity) {
        this.feedback = feedback;
        this.activity = activity;
        colourIndex = 0;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        final String color = FONT_COLOURS[colourIndex];        
        final String latex = feedback.text[position];
        final WebView webView = new WebView(activity);
        //webView.setBackgroundResource(R.drawable.dialog);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.loadDataWithBaseURL("http://cdn.mathjax.org", "<html><head>" 
                + "<script type='text/x-mathjax-config'>" 
                + "MathJax.Hub.Config({showMathMenu: false, " 
                + "jax: ['input/TeX','output/HTML-CSS'], " 
                + "extensions: ['tex2jax.js'], "
                + "TeX: { extensions: ['AMSmath.js','AMSsymbols.js','noErrors.js','noUndefined.js'] } });</script>" 
                + "<script type='text/javascript' src='http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS_HTML'></script>"
                + "<span id='math' style='color:" + color + "'>\\[" + latex + "\\]</span></head><body></body></html>", 
                "text/html", "utf-8", "");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                    webView.loadUrl("javascript:MathJax.Hub.Queue(['Typeset', MathJax.Hub]);");
                else
                    webView.evaluateJavascript("MathJax.Hub.Queue(['Typeset', MathJax.Hub]);", null);
            }
        });
        colourIndex = (colourIndex + 1) % 3;        
        container.addView(webView);
        return webView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View)object);
    }

    @Override
    public int getCount() {
        return feedback.text.length;
    }

    @Override
    public boolean isViewFromObject(View container, Object object) {
        return container == object;
    }
    
    private Feedback feedback;
    private Activity activity;    
    private final String[] FONT_COLOURS = { "#ff1493", "#228b22", "#000000" };
    
    private static int colourIndex = 0;

}