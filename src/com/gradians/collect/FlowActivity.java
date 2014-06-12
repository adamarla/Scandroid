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
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, IConstants{

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
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);
        
        multiMode = true;
        if (savedInstanceState == null) {
            vpPreview.setCurrentItem(0);
            adjustView(0);
        } else {
            int page = savedInstanceState.getInt("page");
            vpPreview.setCurrentItem(page);
            adapter.setZoomed(savedInstanceState.getBoolean("zoomed"));
            adapter.setFlipped(savedInstanceState.getBoolean("flipped"));
            adjustView(page);            
            
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
        Log.d(TAG, "FlowActivity.onSaveInstanceState");
        outState.putStringArray(TAG_ID, toNameStateIds(adapter.getQuestions()));
        outState.putBoolean("multiMode", multiMode);
        outState.putInt("page", vpPreview.getCurrentItem());
        outState.putBoolean("zoomed", adapter.getZoomed());
        outState.putBoolean("flipped", adapter.getFlipped());
        if (!adapter.getFlipped() && feedback[vpPreview.getCurrentItem()] != null) {
            outState.putInt("fdbkPage", vpFdbk.getCurrentItem());            
        }
        super.onSaveInstanceState(outState);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent();
        String[] nsids = toNameStateIds(adapter.getQuestions());
        intent.putExtra(TAG_ID, nsids);
        this.setResult(RESULT_OK, intent);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        String[] nsids = toNameStateIds(adapter.getQuestions());
        intent.putExtra(TAG_ID, nsids);
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
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
        adjustView(nextItem);
        vpPreview.setCurrentItem(nextItem, true);
    }
    
    public void takeAction(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        Question q = adapter.getQuestions()[currentIndex];
        switch (q.getState()) {
        case DOWNLOADED:
            takePicture(currentIndex);
            break;
        default:
            adapter.flip(currentIndex);
            adjustView(currentIndex);
        }
    }
    
    public void showHide(View view) {
        ViewGroup btnBar = (ViewGroup)findViewById(R.id.llBtnBar);
        for (int i = 0; i < btnBar.getChildCount(); i++) {
            btnBar.getChildAt(i).setVisibility(
                btnBar.getChildAt(i).getVisibility() == View.INVISIBLE ?
                View.VISIBLE : View.INVISIBLE);
        }
        findViewById(R.id.btnMin).setVisibility(View.VISIBLE);
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
        Question q = adapter.getQuestions()[position];
        Intent uploadIntent = new Intent(getApplicationContext(),
            com.gradians.collect.ImageUploadService.class);
        try {
            (new File(uploadsDir, q.getId())).createNewFile();
            uploadIntent.putExtra(TAG_ID, uploadsDir.getPath());
            uploadIntent.putExtra(TAG, answersDir.getPath());
            startService(uploadIntent);
            q.setState(SENT);
            adapter.update(position);
            adjustView(position);
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
        if (feedback[position] == null) {
            feedback[position] = loadFeedback(position);
        }
        if (feedback[position] == null) return;
        
        findViewById(R.id.vpFeedback).setVisibility(View.VISIBLE);
        fdbkAdapter = new FeedbackAdapter(feedback[position], this);
        vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        vpFdbk.setVisibility(View.VISIBLE);
        vpFdbk.setOffscreenPageLimit(3);
        vpFdbk.setAdapter(fdbkAdapter);
        vpFdbk.setOnPageChangeListener(this);
        adapter.shift(feedback[position].x[index], 
                feedback[position].y[index], position);                
    }
    
    private void unrenderFeedback(int position) {
        findViewById(R.id.vpFeedback).setVisibility(View.GONE);
        adapter.shift(FdbkView.NO_FEEDBACK, FdbkView.NO_FEEDBACK, position);
    }
    
    private void adjustView(int position) {
        Question q = adapter.getQuestions()[position];
        ((TextView)findViewById(R.id.btnName)).setText(q.getName());
        if (!adapter.getFlipped()) {
            if (q.getState() == GRADED) {
                try {
                    renderFeedback(position, 0);
                } catch (Exception e) {
                    Log.e(TAG, e.getClass().toString());
                }
            }
        } else {
            unrenderFeedback(position);
        }
        
        ImageButton btnAction = (ImageButton)this.findViewById(R.id.btnAction);
        Drawable img = null;
        switch (q.getState()) {
        case DOWNLOADED:
            img = getResources().getDrawable(R.drawable.ic_action_camera);
            break;
        case CAPTURED:
        case WAITING:
        case SENT:
        case RECEIVED:
        case GRADED:
            img = getResources().getDrawable(android.R.drawable.ic_menu_revert);
        }
        btnAction.setImageDrawable(img);
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
                imgLocn = scanLocn = "file:///android_asset/albert_einstein.jpg";
                break;
            case DOWNLOADED:
                imgLocn = scanLocn = (new File(questionsDir, id)).getPath();
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
        dlm.start("Retreiving Feedback", "Please wait...", null);
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

    private ViewPager vpPreview;
    private FlowAdapter adapter;
    
    private ViewPager vpFdbk;
    private FeedbackAdapter fdbkAdapter;

    private boolean multiMode;

    private final String FDBK_URL = "http://%s/tokens/view_fdb.json?id=%s";

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

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        final String latex = feedback.text[position];
        final WebView webView = new WebView(activity);
        webView.clearCache(false);
        webView.destroyDrawingCache();
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            webView.getSettings().setDisplayZoomControls(false);
        webView.loadDataWithBaseURL("file:///android_asset/mathjax",
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
    
    private final String HTML = 
            "<html><head>"            
//            + "<script type='text/x-mathjax-config'>" 
//            +   "MathJax.Hub.Config({showMathMenu: false, " 
//            +       "jax: ['input/TeX','output/HTML-CSS'], " 
//            +           "extensions: ['tex2jax.js'], "
//            +       "TeX: { extensions: ['AMSmath.js','AMSsymbols.js','noErrors.js','noUndefined.js'] } });"
//            + "</script>"            
            + "<script type='text/x-mathjax-config'>"
            +   "MathJax.Hub.Config({ showMathMenu: false, \"HTML-CSS\": {"
            +       "availableFonts: [\"TeX\"], preferredFont: \"TeX\", webFont: \"TeX\""
            +   "} }); "
            +   "MathJax.Hub.Register.StartupHook(\"HTML-CSS Jax Ready\", function() {"
            +       "var VARIANT = MathJax.OutputJax[\"HTML-CSS\"].FONTDATA.VARIANT;"
            +       "VARIANT[\"normal\"].fonts.unshift(\"MathJax_SansSerif\");});"
            + "</script>"
            
            + "<script type='text/javascript' src='file:///android_asset/mathjax/MathJax.js?config=TeX-AMS_HTML'></script>"
            + "<span id='math' style='color:white; margin:0 auto;'>\\[%s\\]</span></head><body></body></html>";
    
}

