package com.gradians.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;


public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, IConstants {

    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow); 
        
        String[] name_state_ids = savedInstanceState == null ? 
                getIntent().getStringArrayExtra(TAG_ID) :
                savedInstanceState.getStringArray(TAG_ID);
                
        studentDir = new File(this.getIntent().getStringExtra(TAG));
        feedback = new Feedback[name_state_ids.length];
        adapter = new FlowAdapter(toQuestions(name_state_ids), 
                this.getSupportFragmentManager());
        vpPreview = (FlowViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setOverlay((ViewGroup)this.findViewById(R.id.btnBarViews));
        vpPreview.setAdapter(adapter);
        
        multiMode = true;
        if (savedInstanceState == null) {
            vpPreview.setCurrentItem(0);
            adjustButtons(adapter.getQuestions()[0].getState());
            vpPreview.startFading();
        } else {
            int page = savedInstanceState.getInt("page");
            vpPreview.setCurrentItem(page);
            adjustButtons(adapter.getQuestions()[page].getState());
            
            adapter.setZoomed(savedInstanceState.getBoolean("zoomed"));
            adapter.setFlipped(savedInstanceState.getBoolean("flipped"));
            
            int fdbkPg = savedInstanceState.getInt("fdbkPage", FdbkView.NO_FEEDBACK);
            if (fdbkPg != FdbkView.NO_FEEDBACK) {
                Question q = adapter.getQuestions()[page];
                if (q.getState() == GRADED) {
                    try {
                        renderFeedback(page, fdbkPg);
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }                    
                }                
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putStringArray(TAG_ID, toNameStateIds(adapter.getQuestions()));
        outState.putBoolean("multiMode", multiMode);
        outState.putInt("page", vpPreview.getCurrentItem());
        outState.putBoolean("zoomed", adapter.getZoomed());
        outState.putBoolean("flipped", adapter.getFlipped());
        if (adapter.getFlipped() && feedback[vpPreview.getCurrentItem()] != null) {
            outState.putInt("fdbkPage", vpFdbk.getCurrentItem());            
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        Intent intent = new Intent();
        intent.putExtra(TAG, toNameStateIds(adapter.getQuestions()));
        this.setResult(RESULT_OK, intent);
        super.onPause();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Uri picture = data.getData();
                int position = vpPreview.getCurrentItem();
                Question q = adapter.getQuestions()[position];
                q.setScanLocn(picture.getPath());
                uploadPicture(position);
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), 
                        "Oops.. image capture failed. Please try again",
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
        Feedback fdbk = feedback[vpPreview.getCurrentItem()];
        adapter.shift(fdbk.x[position], fdbk.y[position], vpPreview.getCurrentItem());
    }
    
    public void page(View view) {
        int currentItem = vpPreview.getCurrentItem();
        int nextItem = view.getId() == R.id.btnLeft ?
            (--currentItem < 0 ? adapter.getCount()-1 : currentItem):
            (++currentItem == adapter.getCount() ? 0 : currentItem);            
        unrenderFeedback(nextItem);
        vpPreview.setCurrentItem(nextItem, true);
        vpPreview.startFading();
        adjustButtons(adapter.getQuestions()[nextItem].getState());
    }
    
    public void takeAction(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        takePicture(currentIndex);
    }
    
    public void toggleView(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        switch (view.getId()) {
        case R.id.btnZoom:
            adapter.zoom(currentIndex);
            if (adapter.getZoomed()) {
                enableSingleMode();
            } else {
                if (!adapter.getFlipped()) enableMultiMode();
            }
            break;
        case R.id.btnFlip:
            adapter.flip(currentIndex);
            if (adapter.getFlipped()) {
                enableSingleMode();
                Question q = adapter.getQuestions()[currentIndex];
                if (q.getState() == GRADED) {
                    try {
                        renderFeedback(currentIndex, 0);
                    } catch (Exception e) {
                        Log.e(TAG, e.getClass().toString());                      
                    }                    
                } 
            } else {
                enableMultiMode();
                unrenderFeedback(currentIndex);
            }
        }
    }
    
    private void takePicture(int position) {
        Question q = adapter.getQuestions()[position];
        String name_id = q.getName() + "-" + q.getId();
        Intent takePictureIntent =
                new Intent(this.getApplicationContext(),
                        com.gradians.collect.CameraActivity.class);
        takePictureIntent.putExtra(TAG,
                (new File(studentDir, ANSWERS_DIR_NAME)).getPath());
        takePictureIntent.putExtra(TAG_ID, name_id);
        startActivityForResult(takePictureIntent,
                ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }
    
    private void uploadPicture(int position) {
        final Question q = adapter.getQuestions()[position];
        Intent uploadIntent = new Intent(getApplicationContext(),
            com.gradians.collect.ImageUploadService.class);
        try {
            (new File(uploadsDir, q.getId())).createNewFile();
            uploadIntent.putExtra(TAG_ID, uploadsDir.getPath());
            uploadIntent.putExtra(TAG, answersDir.getPath());
            //startService(uploadIntent); TODO: uncomment this!
            q.setState(SENT);
            adapter.update(position);
            onPageSelected(position);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }        
    }
    
    private Feedback loadFeedback(int position) throws Exception {
        Question question = adapter.getQuestions()[position];
        File feedback = new File(feedbackDir, question.getId());
        if (!feedback.exists()) return null;
        
        BufferedReader br = new BufferedReader(new FileReader(feedback));        
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject = (JSONObject)jsonParser.parse(br.readLine());
        JSONArray comments = (JSONArray)respObject.get("comments");
        br.close();
        String[] text = new String[comments.size()];
        int[] x = new int[text.length], y = new int[text.length];
        for (int i = 0; i < comments.size(); i++) {
            JSONObject comment = (JSONObject)comments.get(i);
            text[i] = (String)comment.get("comment");
            x[i] = ((Long)comment.get("x")).intValue();
            y[i] = ((Long)comment.get("y")).intValue();
        }
        return new Feedback(text, x, y);
    }

    private void renderFeedback(int position, int index) throws Exception {
        Log.d(TAG, "renderFeedback -- > " + position);
        if (feedback[position] == null) {
            feedback[position] = loadFeedback(position);
        }
        if (feedback[position] == null) return;
        
        findViewById(R.id.vpFeedback).setVisibility(View.VISIBLE);
        fdbkAdapter = new FeedbackAdapter(feedback[position], this);
        vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        vpFdbk.setVisibility(View.VISIBLE);
        vpFdbk.setOffscreenPageLimit(3);        
        LayoutParams lp = (LayoutParams)vpFdbk.getLayoutParams();
        DisplayMetrics dmetrics = this.getApplicationContext().
                getResources().getDisplayMetrics();                                
        lp.height = (int)dmetrics.heightPixels/10;
        vpFdbk.setLayoutParams(lp);        
        vpFdbk.setAdapter(fdbkAdapter);
        vpFdbk.setOnPageChangeListener(this);
        adapter.shift(feedback[position].x[index], feedback[position].y[index], position);
    }
    
    private void unrenderFeedback(int position) {
        findViewById(R.id.vpFeedback).setVisibility(View.GONE);
        adapter.shift(FdbkView.NO_FEEDBACK, FdbkView.NO_FEEDBACK, position);
    }
    
    private void enableSingleMode() {
        multiMode = false;
        findViewById(R.id.btnAction).setVisibility(View.GONE);
        findViewById(R.id.btnLeft).setVisibility(View.GONE);
        findViewById(R.id.btnRight).setVisibility(View.GONE);
    }
    
    private void enableMultiMode() {
        multiMode = true;
        findViewById(R.id.btnLeft).setVisibility(View.VISIBLE);
        findViewById(R.id.btnRight).setVisibility(View.VISIBLE);
    }
    
    private void adjustButtons(int state) {
        Button btnAction = (Button)this.findViewById(R.id.btnAction);
        switch (state) {
        case DOWNLOADED:
            btnAction.setVisibility(View.VISIBLE);
            break;
        case CAPTURED:
        case WAITING:
        case SENT:
        case RECEIVED:
        case GRADED:
            btnAction.setVisibility(View.GONE);
        }
    }
    
    private String[] toNameStateIds(Question[] questions) {
        String[] name_state_ids = new String[questions.length];
        for (int i = 0; i < questions.length; i++) {
            name_state_ids[i] = questions[i].getNameStateId();
        }
        return name_state_ids;
    }
    
    private Question[] toQuestions(String[] name_state_ids) {
        DownloadMonitor dlm = new DownloadMonitor(this);
        
        questionsDir = new File(studentDir, QUESTIONS_DIR_NAME);
        answersDir = new File(studentDir, ANSWERS_DIR_NAME);
        solutionsDir = new File(studentDir, SOLUTIONS_DIR_NAME);
        feedbackDir = new File(studentDir, FEEDBACK_DIR_NAME);
        uploadsDir = new File(studentDir, UPLOAD_DIR_NAME);
                
        Question[] questions = new Question[name_state_ids.length];
        String name, id, grId, imgLocn = null, scanLocn = null; short state;
        for (int i = 0; i < questions.length; i++) {
            
            String[] tokens = name_state_ids[i].split(",");
            name = tokens[0];
            state = Short.parseShort(tokens[1]);
            id = tokens[2];
            grId = tokens[3];
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
                if (!(new File(feedbackDir, id)).exists())
                    dlm.add(id, Uri.parse(String.format(FDBK_URL, WEB_APP_HOST_PORT, grId)), 
                        Uri.fromFile(new File(feedbackDir, id)));
                break;
            default:
            }
            questions[i] = new Question(name, id, grId, imgLocn);
            questions[i].setState(state);
            questions[i].setScanLocn(scanLocn);
        }
        dlm.start("Retreiving Feedback", "Please wait...");
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

    private Feedback[] feedback;
    private File studentDir, questionsDir, answersDir, solutionsDir, feedbackDir, uploadsDir;

    private FlowViewPager vpPreview;
    private FlowAdapter adapter;
    
    private ViewPager vpFdbk;
    private FeedbackAdapter fdbkAdapter;

    private boolean multiMode;

    private final String FDBK_URL = "http://%s/tokens/view_fdb.json?id=%s";

}

class FlowViewPager extends ViewPager {

    public FlowViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FlowViewPager(Context context) {
        super(context);
    }
    
    public void setOverlay(ViewGroup group) {
        this.group = group;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent mev) {
        switch (mev.getAction()) {
        case MotionEvent.ACTION_DOWN:
            startFading();
            break;
        default:
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent arg0) {
        return false;
    }
    
    public void startFading() {
        group.setVisibility(View.VISIBLE);
        Animation fadeOut = new AlphaAnimation(1, 0);  // opaque (1) transparent (0)
        fadeOut.setInterpolator(new AccelerateInterpolator());
        fadeOut.setStartOffset(2000); // Start fading out after
        fadeOut.setDuration(1000); // Fadeout duration 
        fadeOut.setAnimationListener(new AnimationListener()
        {
                public void onAnimationEnd(Animation animation) 
                {
                    group.setVisibility(View.GONE);
                }
                public void onAnimationRepeat(Animation animation) {}
                public void onAnimationStart(Animation animation) {}
        });
        group.startAnimation(fadeOut);
    }

    private ViewGroup group;    
}

class Feedback {
    public Feedback(String[] text, int[] x, int[] y) {
        this.text = text; this.x = x; this.y = y;
    }
    public int[] x, y;
    public String[] text;
}

class FeedbackAdapter extends PagerAdapter {
    
    public FeedbackAdapter(Feedback feedback, Activity activity) {
        this.feedback = feedback;
        this.activity = activity;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        final String latex = feedback.text[position];
        final WebView webView = new WebView(activity);
        //webView.setBackgroundResource(R.drawable.dialog);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            webView.getSettings().setDisplayZoomControls(false);
        webView.loadDataWithBaseURL("http://cdn.mathjax.org", 
                String.format(HTML, latex), "text/html", "utf-8", "");        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                    webView.loadUrl("javascript:MathJax.Hub.Queue(['Typeset', MathJax.Hub]);");
                else
                    webView.evaluateJavascript("MathJax.Hub.Queue(['Typeset', MathJax.Hub]);", null);
            }
        });
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
    
    private final String HTML = "<html><head>" 
            + "<script type='text/x-mathjax-config'>" 
            + "MathJax.Hub.Config({showMathMenu: false, " 
            + "jax: ['input/TeX','output/HTML-CSS'], " 
            + "extensions: ['tex2jax.js'], "
            + "TeX: { extensions: ['AMSmath.js','AMSsymbols.js','noErrors.js','noUndefined.js'] } });</script>" 
            + "<script type='text/javascript' src='http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS_HTML'></script>"
            + "<span id='math' style='color:white; margin:0 auto;'>\\[%s\\]</span></head><body></body></html>";
    
}