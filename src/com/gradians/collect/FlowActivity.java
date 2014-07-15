package com.gradians.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.viewpagerindicator.CirclePageIndicator;

import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
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
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, IConstants {

    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow); 
        
        Parcelable[] parcels = savedInstanceState == null ? 
            getIntent().getParcelableArrayExtra(TAG_ID) :
            savedInstanceState.getParcelableArray(TAG_ID);
            
        Question[] questions = new Question[parcels.length];
        for (int i = 0; i < parcels.length; i++)
            questions[i] = (Question)parcels[i];

        File studentDir = new File(getIntent().getStringExtra(TAG));
        setLocalPaths(studentDir, questions);
        price = getIntent().getIntExtra(QUIZ_PRICE_KEY, 0);
        feedback = new Feedback[questions.length];
        adapter = new FlowAdapter(questions, this.getSupportFragmentManager());
        
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);

        vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        
        //Bind the circle indicator to the adapter
        CirclePageIndicator questionIndicator = (CirclePageIndicator)findViewById(R.id.circlesQn);
        questionIndicator.setViewPager(vpPreview);

        if (savedInstanceState == null) {
            vpPreview.setCurrentItem(0);
            adjustView(0, 0);
        } else {
            int page = savedInstanceState.getInt("page");
            vpPreview.setCurrentItem(page);
            adapter.setFlipped(savedInstanceState.getBoolean("flipped"));
            int fdbkPg = savedInstanceState.getInt("fdbkPage", FdbkView.NO_FEEDBACK);
            adjustView(page, fdbkPg);
        }
        
        printMap();
    }    

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArray(TAG_ID, adapter.getQuestions());
        outState.putInt("page", vpPreview.getCurrentItem());
        outState.putBoolean("flipped", adapter.getFlipped());
        if (!adapter.getFlipped() && feedback[vpPreview.getCurrentItem()] != null) {
            outState.putInt("fdbkPage", fdbkIdx);
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
                String[] filePartMap = data.getStringArrayExtra(TAG);
                Question[] questions = adapter.getQuestions();
                String quizId = questions[0].getId().split("\\.")[0];
                for (String fileParts : filePartMap) {
                    Log.d(TAG, fileParts);
                    String pageNo = fileParts.split(":")[0];
                    String[] tokens = fileParts.split(":")[1].split("-");
                    int[] selectedParts = new int[tokens.length];
                    for (int i = 0; i < tokens.length; i++) {
                        selectedParts[i] = Integer.parseInt(tokens[i]);
                    }
                    int offset = 0;
                    for (int j : selectedParts) {
                        for (int i = 0; i < questions.length; i++) {
                            String[] pgIdx = questions[i].getMap().split("-");
                            if (j > offset + pgIdx.length - 1) {
                                offset = pgIdx.length;
                            } else {
                                pgIdx[j-offset] = pageNo;
                                questions[i].setMap(TextUtils.join("-", pgIdx));
                                if (!questions[i].getMap().contains("0"))
                                    questions[i].setState(CAPTURED);
                                break;
                            }
                        }                        
                        try {
                            (new File(uploadsDir, quizId + "." + pageNo)).createNewFile();
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }                    
                }                
                
                printMap();
//                if (grIds != null) {
//                    Question[] questions = adapter.getQuestions();
//                    for (int i = 0; i < grIds.length; i++) {
//                        questions[i].setGRId(grIds[i]);
//                    }
//                }
//                Uri picture = data.getData();
//                int position = vpPreview.getCurrentItem();
//                Question q = adapter.getQuestions()[position];
//                q.setScanLocn(picture.getPath());
//                uploadPicture(position);
//                price = 0; // Successful result means Quiz is purchased(Billed)
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
        fdbkIdx = position;
        Feedback fdbk = feedback[vpPreview.getCurrentItem()];
        adapter.shift(fdbk.page[position], fdbk.x[position], fdbk.y[position], vpPreview.getCurrentItem());
    }
    
    public void page(View view) {
        int currentItem = vpPreview.getCurrentItem();
        int nextItem = view.getId() == R.id.btnLeft ?
            (--currentItem < 0 ? adapter.getCount()-1 : currentItem):
            (++currentItem == adapter.getCount() ? 0 : currentItem);
        vpPreview.setCurrentItem(nextItem, true);
        fdbkIdx = 0;
        adjustView(nextItem, fdbkIdx);
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
            adjustView(currentIndex, fdbkIdx);
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
        Intent takePictureIntent = new Intent(this.getApplicationContext(), 
            com.gradians.collect.CameraActivity.class);
        takePictureIntent.putExtra(TAG, adapter.getQuestions());
        takePictureIntent.putExtra(TAG_ID, position);
        takePictureIntent.putExtra(SCANS_KEY, answersDir.getPath());
        if (price > 0) takePictureIntent.putExtra(QUIZ_PRICE_KEY, 20);
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
            adjustView(position, 0);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }        
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
        adapter.shift(0, FdbkView.NO_FEEDBACK, FdbkView.NO_FEEDBACK, position);
    }
    
    private Feedback loadFeedback(int position) {
        Question question = adapter.getQuestions()[position];
        File latex = new File(feedbackDir, question.getId());
        if (!latex.exists()) return null;
        
        String[] tokens = question.getMap().split("-");
        int[] partOnPg = new int[tokens.length];
        int startPg = Integer.parseInt(tokens[0]);
        for (int i = 0; i < tokens.length; i++) {
            partOnPg[i] = Integer.parseInt(tokens[i]);
            if (partOnPg[i] < startPg) {
                startPg = partOnPg[i];
            }
        }        
        
        BufferedReader br = null;
        Feedback feedback = null;
        try {
            br = new BufferedReader(new FileReader(latex));
            JSONParser jsonParser = new JSONParser();
            JSONObject respObject = (JSONObject)jsonParser.parse(br.readLine());
            JSONArray comments = (JSONArray)respObject.get(COMMENTS_KEY);
            br.close();
            String[] text = new String[comments.size()];
            int[] page = new int[text.length];
            int[] x = new int[text.length]; 
            int[] y = new int[text.length];
            JSONObject firstComment = (JSONObject)comments.get(0);
            int id = ((Long)firstComment.get(ID_KEY)).intValue();            
            int partIdx = 0;
            for (int i = 0; i < comments.size(); i++) {
                JSONObject comment = (JSONObject)comments.get(i);
                text[i] = (String)comment.get(COMMENT_KEY);
                x[i] = ((Long)comment.get(X_POSN_KEY)).intValue();
                y[i] = ((Long)comment.get(Y_POSN_KEY)).intValue();
                if (id != ((Long)comment.get(ID_KEY)).intValue()) {
                    id = ((Long)comment.get(ID_KEY)).intValue();
                    partIdx++;
                }                
                page[i] = partOnPg[partIdx]-startPg;
            }
            feedback = new Feedback(text, page, x, y);
        } catch (Exception e) { 
            Log.e(TAG, "Error loading feedback " + e.getMessage());
        }
        return feedback;
    }

    private String[] toNameStateIds(Question[] questions) {
        String[] name_state_ids = new String[questions.length];
        for (int i = 0; i < questions.length; i++) {
            name_state_ids[i] = questions[i].toString();
        }
        return name_state_ids;
    }
    
    private void setLocalPaths(File studentDir, Question[] questions) {
        
        questionsDir = new File(studentDir, QUESTIONS_DIR_NAME);
        answersDir = new File(studentDir, ANSWERS_DIR_NAME);
        solutionsDir = new File(studentDir, SOLUTIONS_DIR_NAME);
        feedbackDir = new File(studentDir, FEEDBACK_DIR_NAME);
        uploadsDir = new File(studentDir, UPLOAD_DIR_NAME);
        
        for (Question question : questions) {
            String imgLocn = question.getState() > DOWNLOADED ? 
                solutionsDir.getPath() : questionsDir.getPath(); 
            String scanLocn = answersDir.getPath();
            question.setImgLocn(imgLocn);
            question.setScanLocn(scanLocn);
        }
            
//        HashMap<String,Integer> hm = new HashMap<String,Integer>();
//        int index = 1;
//        for (Question question : questions) {
//            String imgLocn, scanLocn;
//            
//            if (question.getState() > SENT) {
//                imgLocn = (new File(solutionsDir, question.getId() + "." + question.getImgSpan())).getPath();
//            } else {
//                imgLocn = (new File(questionsDir, question.getId())).getPath();
//            }
//            
//            if (question.getState() > DOWNLOADED) {                
//                
//                if ((new File(answersDir, question.getId() + ".1")).exists()) {
//                    hm.put(question.getScanLocn(), 1);
//                }                
//                
//                String[] scans = question.getScanLocn().split(",");
//                for (int i = 0; i < scans.length; i++) {
//                    if (!hm.containsKey(scans[i])) {
//                        hm.put(scans[i], index);
//                        index++;
//                    }
//                    scanLocn = (new File(answersDir, question.getId() + "." + hm.get(scans[i]))).getPath();                        
//                }
//                scanLocn = (new File(answersDir, question.getId() + "." + scans.length)).getPath();
//                
//            } else {
//                scanLocn = (new File(questionsDir, question.getId())).getPath();                
//            }
//            
//            question.setImgLocn(imgLocn);
//            question.setScanLocn(scanLocn);
//        }
    }
    
    private void printMap() {
        Question[] qns = adapter.getQuestions();
        for (Question qn : qns) {
            Log.d(TAG, qn.getName() + ": " + qn.getMap());
        }
    }
//    private Question[] toQuestions(String[] name_state_ids) {
//        
//        questionsDir = new File(studentDir, QUESTIONS_DIR_NAME);
//        answersDir = new File(studentDir, ANSWERS_DIR_NAME);
//        solutionsDir = new File(studentDir, SOLUTIONS_DIR_NAME);
//        feedbackDir = new File(studentDir, FEEDBACK_DIR_NAME);
//        uploadsDir = new File(studentDir, UPLOAD_DIR_NAME);
//                
//        Question[] questions = new Question[name_state_ids.length];
//        String name, id, grId, imgLocn, scanLocn, score, outof; short state;
//        for (int i = 0; i < questions.length; i++) {
//            String[] tokens = name_state_ids[i].split(",");
//            name = tokens[0];
//            state = Short.parseShort(tokens[1]);
//            score = tokens[2];
//            outof = tokens[3];
//            id = tokens[4];
//            grId = tokens[5];
//            
//            imgLocn = (new File(state >  SENT ? solutionsDir : questionsDir, id)).getPath();
//            scanLocn = (new File(state > DOWNLOADED ? answersDir : questionsDir, id)).getPath();
//            questions[i] = new Question(name, id, grId, imgLocn);
//            questions[i].setState(state);
//            questions[i].setScanLocn(scanLocn);
//            questions[i].setMarks(state == GRADED ? Float.parseFloat(score) : 0f);
//            questions[i].setOutOf(state == GRADED ? Short.parseShort(outof) : 0);
//        }
//        return questions;
//    }
//    
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
    private File questionsDir, answersDir, solutionsDir, feedbackDir, uploadsDir;

    private ViewPager vpPreview;
    private FlowAdapter adapter;
    
    private int fdbkIdx;
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
    public Feedback(String[] text, int[] page, int[] x, int[] y) {
        this.text = text; this.page = page; this.x = x; this.y = y;
    }
    public int[] page, x, y;
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
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                    webView.loadUrl("javascript:MathJax.Hub.Queue(['Typeset', MathJax.Hub]);");
                else
                    webView.evaluateJavascript("MathJax.Hub.Queue(['Typeset', MathJax.Hub]);", null);
            }
        });
//        webView.clearCache(true);
//        webView.destroyDrawingCache();
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setJavaScriptEnabled(true);
//        webView.getSettings().setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            webView.getSettings().setDisplayZoomControls(false);
        webView.loadDataWithBaseURL("file:///android_asset/mathjax-svg",
                String.format(HTML, scale, latex), "text/html", "utf-8", "");
            
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
        + "</head><body><span id='math' style='position: absolute; color:white;'>\\[%s\\]</span></body></html>";
       
}

