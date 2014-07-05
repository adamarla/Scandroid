package com.gradians.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.viewpagerindicator.CirclePageIndicator;

import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, IConstants {

    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow); 
        
        String[] name_state_score_ids = savedInstanceState == null ? 
                getIntent().getStringArrayExtra(TAG_ID) :
                savedInstanceState.getStringArray(TAG_ID);

        price = getIntent().getIntExtra(QUIZ_PRICE_KEY, 0);
        studentDir = new File(getIntent().getStringExtra(TAG));
        feedback = new Feedback[name_state_score_ids.length];
        adapter = new FlowAdapter(toQuestions(name_state_score_ids), 
                this.getSupportFragmentManager());
        
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);

        vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        
        //Bind the circle indicator to the adapter
        CirclePageIndicator questionIndicator = (CirclePageIndicator)findViewById(R.id.circlesQn);
        questionIndicator.setViewPager(vpPreview);

        if (savedInstanceState == null) {
            vpPreview.setCurrentItem(0);
            adjustView(0);            
        } else {
            int page = savedInstanceState.getInt("page");
            vpPreview.setCurrentItem(page);
            adapter.setFlipped(savedInstanceState.getBoolean("flipped"));
            int fdbkPg = savedInstanceState.getInt("fdbkPage", FdbkView.NO_FEEDBACK);
            adjustView(page, fdbkPg);
        }
    }    

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putStringArray(TAG_ID, toNameStateIds(adapter.getQuestions()));
        outState.putInt("page", vpPreview.getCurrentItem());
        outState.putBoolean("flipped", adapter.getFlipped());
        if (!adapter.getFlipped() && feedback[vpPreview.getCurrentItem()] != null) {
            outState.putInt("fdbkPage", fdbkPg);
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
                String[] grIds = data.getStringArrayExtra(TAG_ID);
                if (grIds != null) {
                    Question[] questions = adapter.getQuestions();
                    for (int i = 0; i < grIds.length; i++) {
                        questions[i].setGRId(grIds[i]);
                    }
                }
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
        fdbkPg = position;
        Feedback fdbk = feedback[vpPreview.getCurrentItem()];
        adapter.shift(fdbk.x[position], fdbk.y[position], vpPreview.getCurrentItem());
    }
    
    public void page(View view) {
        int currentItem = vpPreview.getCurrentItem();
        int nextItem = view.getId() == R.id.btnLeft ?
            (--currentItem < 0 ? adapter.getCount()-1 : currentItem):
            (++currentItem == adapter.getCount() ? 0 : currentItem);
        vpPreview.setCurrentItem(nextItem, true);
        fdbkPg = 0;
        adjustView(nextItem);
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
            adjustView(currentIndex, fdbkPg);
        }
    }
    
    public void showHide(View view) {
        ViewGroup btnBar = (ViewGroup)findViewById(R.id.llBtnBar);
        for (int i = 0; i < btnBar.getChildCount(); i++) {
            btnBar.getChildAt(i).setVisibility(
                btnBar.getChildAt(i).getVisibility() == View.INVISIBLE ?
                        View.VISIBLE : View.INVISIBLE);
        }
        if (fdbkShown) {
            vpFdbk.setVisibility(vpFdbk.getVisibility() == View.INVISIBLE ?
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
        if (price > 0)
            takePictureIntent.putExtra(QUIZ_PRICE_KEY, 20);
        startActivityForResult(takePictureIntent,
                ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }
    
    private void uploadPicture(int position) {
        Question q = adapter.getQuestions()[position];
        Intent uploadIntent = new Intent(getApplicationContext(),
            com.gradians.collect.ImageUploadService.class);
        try {
            (new File(uploadsDir, q.getId() + "-" + q.getGRId())).createNewFile();
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
    
    private void adjustView(int position) {
        adjustView(position, 0);
    }
    
    private void adjustView(int position, int fdbkPosn) {
        Drawable img = null;        
        ImageButton btnAction = (ImageButton)this.findViewById(R.id.btnAction);
        Question q = adapter.getQuestions()[position];
        ((TextView)findViewById(R.id.tvName)).setText(q.getName());
        switch (q.getState()) {
        case DOWNLOADED:
            img = getResources().getDrawable(R.drawable.ic_action_camera);
            break;
        case CAPTURED:
        case WAITING:
        case SENT:
        case RECEIVED:
            img = getResources().getDrawable(android.R.drawable.ic_menu_info_details);
            ((TextView)findViewById(R.id.tvMarks)).setText("TBD");
            break;
        case GRADED:
            img = getResources().getDrawable(android.R.drawable.ic_menu_info_details);
            ((TextView)findViewById(R.id.tvMarks)).setText(
                String.format("%2.1f/%1d", q.getMarks(), q.getOutOf()));
        }
        btnAction.setImageDrawable(img);        
        
        if (!adapter.getFlipped() && q.getState() == GRADED) {
            renderFeedback(position, fdbkPosn);
        } else {
            unrenderFeedback(position);
        }
    }
    
    private void renderFeedback(int position, int fdbkPosn) {
        if (feedback[position] == null) {
            feedback[position] = loadFeedback(position);
        }
        if (feedback[position] == null) return;
        
        fdbkAdapter = new FeedbackAdapter(feedback[position], this);
        
        vpFdbk.setOffscreenPageLimit(3);
        vpFdbk.setAdapter(fdbkAdapter);
        vpFdbk.setVisibility(View.VISIBLE);
        fdbkShown = true;
        findViewById(R.id.tvMarks).setVisibility(View.VISIBLE);
        findViewById(R.id.circlesFdbk).setVisibility(View.VISIBLE);
        
        //Bind the circle indicator to the adapter
        fdbkIndicator = (CirclePageIndicator)findViewById(R.id.circlesFdbk);
        fdbkIndicator.setViewPager(vpFdbk, fdbkPosn);
        fdbkIndicator.setOnPageChangeListener(this);
        
        onPageSelected(fdbkPosn);
    }
    
    private void unrenderFeedback(int position) {
        fdbkShown = false;
        vpFdbk.setVisibility(View.INVISIBLE);
        findViewById(R.id.circlesFdbk).setVisibility(View.VISIBLE);
        findViewById(R.id.tvMarks).setVisibility(View.INVISIBLE);
        adapter.shift(FdbkView.NO_FEEDBACK, FdbkView.NO_FEEDBACK, position);
    }
    
    private Feedback loadFeedback(int position) {
        Question question = adapter.getQuestions()[position];
        File latex = new File(feedbackDir, question.getId());
        if (!latex.exists()) return null;
        
        BufferedReader br = null;
        Feedback feedback = null;
        try {
            br = new BufferedReader(new FileReader(latex));
            JSONParser jsonParser = new JSONParser();
            JSONObject respObject = (JSONObject)jsonParser.parse(br.readLine());
            JSONArray comments = (JSONArray)respObject.get(COMMENTS_KEY);
            br.close();
            String[] text = new String[comments.size()];
            int[] x = new int[text.length], y = new int[text.length];
            for (int i = 0; i < comments.size(); i++) {
                JSONObject comment = (JSONObject)comments.get(i);
                text[i] = (String)comment.get(COMMENT_KEY);
                x[i] = ((Long)comment.get(X_POSN_KEY)).intValue();
                y[i] = ((Long)comment.get(Y_POSN_KEY)).intValue();
            }
            feedback = new Feedback(text, x, y);
        } catch (Exception e) { 
            Log.e(TAG, "Error loading feedback");
        }
        return feedback;
    }

    private String[] toNameStateIds(Question[] questions) {
        String[] name_state_ids = new String[questions.length];
        for (int i = 0; i < questions.length; i++) {
            name_state_ids[i] = questions[i].getNameStateScoreId();
        }
        return name_state_ids;
    }
    
    private Question[] toQuestions(String[] name_state_ids) {
        
        questionsDir = new File(studentDir, QUESTIONS_DIR_NAME);
        answersDir = new File(studentDir, ANSWERS_DIR_NAME);
        solutionsDir = new File(studentDir, SOLUTIONS_DIR_NAME);
        feedbackDir = new File(studentDir, FEEDBACK_DIR_NAME);
        uploadsDir = new File(studentDir, UPLOAD_DIR_NAME);
                
        Question[] questions = new Question[name_state_ids.length];
        String name, id, grId, imgLocn, scanLocn, score, outof; short state;
        for (int i = 0; i < questions.length; i++) {
            String[] tokens = name_state_ids[i].split(",");
            name = tokens[0];
            state = Short.parseShort(tokens[1]);
            score = tokens[2];
            outof = tokens[3];
            id = tokens[4];
            grId = tokens[5];
            
            imgLocn = (new File(state >  SENT ? solutionsDir : questionsDir, id)).getPath();
            scanLocn = (new File(state > DOWNLOADED ? answersDir : questionsDir, id)).getPath();
            questions[i] = new Question(name, id, grId, imgLocn);
            questions[i].setState(state);
            questions[i].setScanLocn(scanLocn);
            questions[i].setMarks(state == GRADED ? Float.parseFloat(score) : 0f);
            questions[i].setOutOf(state == GRADED ? Short.parseShort(outof) : 0);
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

    private int price;
    private Feedback[] feedback;
    private File studentDir, questionsDir, answersDir, solutionsDir, feedbackDir, uploadsDir;

    private ViewPager vpPreview;
    private FlowAdapter adapter;
    
    private int fdbkPg;
    private boolean fdbkShown;
    private ViewPager vpFdbk;
    private FeedbackAdapter fdbkAdapter;
    private CirclePageIndicator fdbkIndicator;
    
}

class FlowViewPager extends ViewPager {

    public FlowViewPager(Context context) {
        super(context);
    }

    public FlowViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent arg0) {
        // Never allow swiping to switch between pages
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never allow swiping to switch between pages
        return false;
    }

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
        final int scale = activity.getResources().getConfiguration().
            orientation == Configuration.ORIENTATION_LANDSCAPE ? 100 : 80;
        webView.clearCache(true);
        webView.destroyDrawingCache();
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            webView.getSettings().setDisplayZoomControls(false);
            webView.loadDataWithBaseURL("file:///android_asset/mathjax-svg",
                    String.format(HTML, scale, latex), "text/html", "utf-8", "");
            
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
        + "<script type='text/x-mathjax-config'>"
        +   "MathJax.Hub.Config({ "
        +     "showMathMenu: false,"
        +     "SVG: { "
        +       "scale: %d,"
        +       "font: \"TeX\", "
        +       "linebreaks: { automatic: false, width: \"automatic\" }, "
        +       "styles: { "
        +         "\".MathJax_SVG svg > g, .MathJax_SVG_Display svg > g\": {"
        +           "fill: \"#FFF\","
        +           "stroke: \"#FFF\""
        +         "}"
        +       "}"
        +     "}"
        +   "});"
        + "MathJax.Hub.Register.StartupHook(\"SVG Jax Ready\", function() {"
        +   "var VARIANT = MathJax.OutputJax[\"SVG\"].FONTDATA.VARIANT;"
        +   "VARIANT[\"normal\"].fonts.unshift(\"MathJax_SansSerif\");"
        + "});"
        + "</script>"
        + "<script type='text/javascript' src='file:///android_asset/mathjax-svg/MathJax.js?config=TeX-AMS-MML_SVG'></script>"
//        + "</head><body><span id='math' style='position: absolute; color:white; margin-top:13%%;'>\\[%s\\]</span></body></html>";
        + "</head><body><span id='math' style='position: absolute; color:white;'>\\[%s\\]</span></body></html>";
       
}

