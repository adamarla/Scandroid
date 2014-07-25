package com.gradians.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;


public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, ITaskResult, IConstants {

    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow); 
        
        Parcelable[] parcels = savedInstanceState == null ? 
            getIntent().getParcelableArrayExtra(TAG_ID) :
            savedInstanceState.getParcelableArray(TAG_ID);
            
        Question[] questions = new Question[parcels.length];
        for (int i = 0; i < parcels.length; i++) {
            questions[i] = (Question)parcels[i];
        }

        feedback = new Feedback[questions.length];
        adapter = new FlowAdapter(questions, this.getSupportFragmentManager());
        
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);

        vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        
        //Bind the circle indicator to the adapter
        CirclePageIndicator questionIndicator = (CirclePageIndicator)findViewById(R.id.circlesQn);
        questionIndicator.setViewPager(vpPreview);

        String title;
        if (savedInstanceState == null) {
            title = getIntent().getStringExtra(NAME_KEY);
            price = getIntent().getIntExtra(QUIZ_PRICE_KEY, 0);
            quizDir = new File(getIntent().getStringExtra(TAG));
            vpPreview.setCurrentItem(0);
            adjustView(0, 0);
        } else {
            title = savedInstanceState.getString(NAME_KEY);
            price = savedInstanceState.getInt(QUIZ_PRICE_KEY, 0);
            quizDir = new File(savedInstanceState.getString(TAG));
            int page = savedInstanceState.getInt("page");
            vpPreview.setCurrentItem(page);
            adapter.setFlipped(savedInstanceState.getBoolean("flipped"));
            int fdbkPg = savedInstanceState.getInt("fdbkPage", FdbkView.NO_FEEDBACK);
            adjustView(page, fdbkPg);
        }
        setTitle(title);        
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArray(TAG_ID, adapter.getQuestions());
        outState.putInt(QUIZ_PRICE_KEY, price);
        outState.putString(TAG, quizDir.getPath());
        outState.putInt("page", vpPreview.getCurrentItem());
        outState.putBoolean("flipped", adapter.getFlipped());
        if (!adapter.getFlipped() && feedback[vpPreview.getCurrentItem()] != null) {
            outState.putInt("fdbkPage", fdbkIdx);
        }
        super.onSaveInstanceState(outState);
    }
    
    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG, adapter.getQuestions());
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {        
        if (requestCode == ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {                
                Parcelable[] parcels = data.getParcelableArrayExtra(TAG);
                Question[] questions = adapter.getQuestions();                
                for (int i = 0; i < parcels.length; i++) {
                    questions[i].setPgMap(((Question)parcels[i]).getPgMap());
                    if (questions[i].getState() < SENT) {
                        if (!questions[i].getPgMap("-").contains("0"))
                            questions[i].setState(CAPTURED);
                        else
                            questions[i].setState(DOWNLOADED);
                    }
                }                
                adapter.update(vpPreview.getCurrentItem());
                adjustView(vpPreview.getCurrentItem(), 0);
                
                // prompt for uploading only if everything is done
                boolean allDone = true;
                for (Question q : questions) {
                    if (q.getState() < CAPTURED) {
                        allDone = false;
                        break;
                    }
                }
                if (allDone) upload(null);
            }
        }
    }
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == BILL_WORKSHEET_TASK_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    JSONParser jsonParser = new JSONParser();
                    JSONObject respObject = (JSONObject)jsonParser.parse(resultData);
                    JSONArray json = (JSONArray)respObject.get("gr_ids");
                    String[] grIds = new String[json.size()];
                    for (int i = 0; i < grIds.length; i++) {
                        grIds[i] = (String)json.get(i);
                    }
                    
                    Question[] questions = adapter.getQuestions();
                    for (int i = 0; i < questions.length;  i++) {
                        int[] grId = questions[i].getGRId();
                        for (int j = 0; j < grId.length; j++) {
                            grId[j] = Integer.parseInt(grIds[i+j]);
                        }
                        questions[i].setGRId(grId);
                    }
                    price = 0;
                    triggerUploads(questions);
                } catch (Exception e) {
                    Log.e(TAG, "Bill Worksheet failed: " + e.getMessage());
                }
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
        adapter.shift(fdbk.page[position], fdbk.x[position], fdbk.y[position], 
            vpPreview.getCurrentItem());
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
        
    public void flip(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        adapter.flip(currentIndex);
        adjustView(currentIndex, fdbkIdx);
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
            fdbkIndicator.setVisibility(vpFdbk.getVisibility() == View.INVISIBLE ?
                    View.VISIBLE : View.INVISIBLE);
        }
        findViewById(R.id.btnMin).setVisibility(View.VISIBLE);
    }
    
    public void launchCameraActivity(View view) {
        boolean nothingToCapture = true;
        Question[] questions = adapter.getQuestions();
        for (Question q  : questions) {
            if (q.getState() < SENT) {
                nothingToCapture = false;
            }
        }
        if (nothingToCapture) return;
        
        File answersDir = new File(quizDir, ANSWERS_DIR_NAME);
        Intent takePictureIntent = new Intent(this.getApplicationContext(), 
            com.gradians.collect.CameraActivity.class);
        takePictureIntent.putExtra(TAG, adapter.getQuestions());
        takePictureIntent.putExtra(TAG_ID, vpPreview.getCurrentItem());
        takePictureIntent.putExtra(SCANS_KEY, answersDir.getPath());
        startActivityForResult(takePictureIntent, 
            ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }
    
    public void upload(View view) {        
        // prompt for uploading only if someting is there
        final Question[] questions = adapter.getQuestions();
        if (nothingToUpload(questions)) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (price == 0) {
            builder.setTitle("Upload ungraded answers?");
        } else {
            builder.setTitle("Wan't us to look at your work?").setMessage(
                    "Buy this Quiz for " + price + " Gredits");
        }
        builder.setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {                    
                    if (price == 0) {
                        triggerUploads(questions);                    
                    } else {
                        // call to purchase quiz
                        triggerPurchase(questions);                        
                    }
                }
            });
        builder.setNegativeButton(android.R.string.no,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                }
            });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    public String[] getPaths(Question question, boolean flipped) {
        String[] paths;
        if (flipped) {
            if (question.getState() > SENT) {
                paths = getSolution(question);
            } else if (question.getState() == DOWNLOADED) {
                paths = new String[] { "Instruction" };
            } else {
                paths = getQuestion(question);
            }
        } else {
            if (question.getState() > DOWNLOADED) {
                paths = getAnswer(question);
            } else {
                paths = getQuestion(question);
            }
        }
        return paths;
    }

    private void adjustView(int position, int fdbkPosn) {
        Button btnFlip = (Button)this.findViewById(R.id.btnFlip);
        Button btnLeft = (Button)this.findViewById(R.id.btnLeft);
        Button btnRight = (Button)this.findViewById(R.id.btnRight);
        Button btnCamera = (Button)this.findViewById(R.id.btnCamera);
        Button btnUpload = (Button)this.findViewById(R.id.btnUpload);
        TextView tvMarks = (TextView)findViewById(R.id.tvMarks);
        
        Question[] questions = adapter.getQuestions();
        btnLeft.setText(position == 0 ? questions[questions.length-1].getName():
            questions[position-1].getName());
        btnRight.setText(position == questions.length-1 ? questions[0].getName():
            questions[position+1].getName());

        btnCamera.setEnabled(!nothingToCapture(questions));
        btnUpload.setEnabled(!nothingToUpload(questions));
        
        Question q = questions[position];
        ((TextView)findViewById(R.id.tvName)).setText(q.getName());
        if (adapter.getFlipped()) {
            if (q.getState() > DOWNLOADED) {
                btnFlip.setText("Response");
            } else {
                btnFlip.setText("Question");
                btnLeft.setEnabled(false);
                btnRight.setEnabled(false);
                btnCamera.setEnabled(false);
                btnUpload.setEnabled(false);
            }
            
            if (q.getState() == GRADED) {
                unrenderFeedback(position);
            }
        } else {
            if (q.getState() > SENT) {
                btnFlip.setText("Solution");
            } else if (q.getState() > DOWNLOADED) {
                btnFlip.setText("Question");
            } else {
                btnLeft.setEnabled(true);
                btnRight.setEnabled(true);
                btnFlip.setText("Guidelines");
                btnCamera.setEnabled(!nothingToCapture(questions));
                btnUpload.setEnabled(!nothingToUpload(questions));
            }
            
            if (q.getState() == GRADED) {
                renderFeedback(position, fdbkPosn);
                tvMarks.setText(String.format("%2.1f/%1d", q.getMarks(), q.getOutOf()));
            } else {
                unrenderFeedback(position);
                tvMarks.setText("");
            }
        }
        
        btnLeft.refreshDrawableState();
        btnFlip.refreshDrawableState();
        btnUpload.refreshDrawableState();
        btnCamera.refreshDrawableState();
        btnRight.refreshDrawableState();
    }
    
    private boolean nothingToUpload(Question[] questions) {
        boolean nothingToUpload = true;
        for (Question q : questions) {
            if (q.getState() == CAPTURED) {
                nothingToUpload = false;
                break;
            }
        }
        return nothingToUpload;        
    }
    
    private boolean nothingToCapture(Question[] questions) {
        boolean nothingToCapture = true;
        for (Question q : questions) {
            if (q.getState() < CAPTURED) {
                nothingToCapture = false;
                break;
            }
        }
        return nothingToCapture;
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
        findViewById(R.id.circlesFdbk).setVisibility(View.INVISIBLE);
        adapter.shift(0, FdbkView.NO_FEEDBACK, FdbkView.NO_FEEDBACK, position);
    }
    
    private Feedback loadFeedback(int position) {
        Question question = adapter.getQuestions()[position];
        File feedbackDir = new File(quizDir, FEEDBACK_DIR_NAME);
        File latex = new File(feedbackDir, question.getId());
        if (!latex.exists()) return null;
        
        int[] partOnPg = question.getPgMap();
        int highestPg = partOnPg[0];
        for (int i = 0; i < partOnPg.length; i++) {
            if (partOnPg[i] < highestPg) {
                highestPg = partOnPg[i];
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
                page[i] = partOnPg[partIdx]-highestPg;
            }
            feedback = new Feedback(text, page, x, y);
        } catch (Exception e) { 
            Log.e(TAG, "Error loading feedback " + e.getMessage());
        }
        return feedback;
    }
    
    private void triggerUploads(Question[] questions) {        
        SparseArray<ArrayList<Integer>>
            toSend = new SparseArray<ArrayList<Integer>>();
        
        ArrayList<Integer> grIds;
        for (Question q: questions) {
            boolean[] sentState = q.getSentState();                            
            if (q.getState() > CAPTURED) continue;
            
            int[] pgMap = q.getPgMap();
            for (int i = 0; i < pgMap.length; i++) {
                if (pgMap[i] > 0 && !sentState[i]) {
                    // candidate to send
                    grIds = toSend.get(pgMap[i]);
                    if (grIds == null) {
                        grIds = new ArrayList<Integer>();
                        toSend.put(pgMap[i], grIds);
                    }
                    grIds.add(q.getGRId()[i]);
                    sentState[i] = true;
                }
            }                            
            // mark all captured qsns as sent
            q.setSentState(sentState);
            if (!q.getSentState("-").contains("0"))
                q.setState(SENT);
        }
    
        File uploadsDir = new File(quizDir, UPLOAD_DIR_NAME);
        File answersDir = new File(quizDir, ANSWERS_DIR_NAME);
        String quizId = questions[0].getId().split("\\.")[0];
        
        int pg;
        String ids = "";
        String filename;
        for (int i = 0; i < toSend.size(); i++) {
            pg = toSend.keyAt(i);
            ids = TextUtils.join("-", toSend.valueAt(i));
            filename = quizId  + "." + pg + "." + ids;
            try {
                (new File(uploadsDir, filename)).createNewFile();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }
        
        Intent uploadIntent = new Intent(getApplicationContext(),
                com.gradians.collect.ImageUploadService.class);
        uploadIntent.putExtra(TAG_ID, uploadsDir.getPath());
        uploadIntent.putExtra(TAG, answersDir.getPath());
        startService(uploadIntent);
    }
    

    private void triggerPurchase(Question[] questions) {
        String id = questions[0].getId();
        String ws_id = id.substring(0, id.indexOf('.'));
        URL url;
        try {
            url = new URL(String.format(BILL_URL,
                    WEB_APP_HOST_PORT, ws_id));
            URL[] urls = new URL[] { url };
            new HttpCallsAsyncTask(this,
                BILL_WORKSHEET_TASK_RESULT_CODE).execute(urls);        
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }        
    }
    
    private String[] getQuestion(Question question) {
        File questionsDir = new File(quizDir, QUESTIONS_DIR_NAME);
        String[] paths = new String[] 
            {new File(questionsDir, question.getId()).getPath()};
        return paths;
    }
     
    private String[] getAnswer(Question question) {
        String quizId = question.getId().split("\\.")[0];
        int[] pgMap = question.getPgMap();
        HashSet<Integer> pages = new HashSet<Integer>();
        for (int i = 0; i < pgMap.length; i++) {
            if (pgMap[i] != 0) {
                if (!pages.contains(pgMap[i])) {
                    pages.add(pgMap[i]);
                }
            }
        }
        
        File answersDir = new File(quizDir, ANSWERS_DIR_NAME);
        String[] paths = new String[pages.size()];
        int i = 0;
        Iterator<Integer> iter = pages.iterator();
        while (iter.hasNext()) {
            paths[i++] = (new File(answersDir, quizId + "." + iter.next())).getPath();
        }
        return paths;
    }
     
    private String[] getSolution(Question question) {
        File solutionsDir = new File(quizDir, SOLUTIONS_DIR_NAME);
        String[] paths = new String[question.getImgSpan()];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = (new File(solutionsDir, question.getId() + "." + (i+1))).getPath();
        }
        return paths;
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
    private File quizDir;

    private ViewPager vpPreview;
    private FlowAdapter adapter;
    
    private int fdbkIdx;
    private boolean fdbkShown;
    private ViewPager vpFdbk;
    private FeedbackAdapter fdbkAdapter;
    private CirclePageIndicator fdbkIndicator;
    
    private final String BILL_URL = "http://%s/tokens/bill_ws?id=%s";
    
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

