package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;

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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


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
        quizDir = new File(getIntent().getStringExtra(QUIZ_PATH_KEY));
        type = getIntent().getStringExtra(ID_KEY);

        adapter = new FlowAdapter(questions, this.getSupportFragmentManager());
        File stateFile = new File(quizDir.getParent(), STATE_FILE);
        try {
            stateMap.load(new FileInputStream(stateFile));
        } catch (Exception e) { }

        feedback = new Feedback[questions.length];
        hints = new Hint[questions.length];
        
        vpPreview = (FlowViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);
        vpPreview.setOnPageChangeListener(this);
        vpPreview.setActivity(this);        

        vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        vpHints = (ViewPager)findViewById(R.id.vpHints);

        fdbkIndicator = (CirclePageIndicator)findViewById(R.id.circlesFdbk);
        hintsIndicator = (CirclePageIndicator)findViewById(R.id.circlesHints);        
        
        int page = 0, fdbkPg = 0;
        if (savedInstanceState == null) {
            price = getIntent().getIntExtra(QUIZ_PRICE_KEY, 0);
            vpPreview.setCurrentItem(0);
        } else {
            price = savedInstanceState.getInt(QUIZ_PRICE_KEY, 0);
            adapter.setFlipped(savedInstanceState.getBoolean(FLIPPED_KEY));
            fdbkPg = savedInstanceState.getInt(FDBK_IDX_KEY, NO_FEEDBACK);
            fdbkShown = savedInstanceState.getBoolean(FDBK_SHOWN_KEY);
            hintShown = savedInstanceState.getBoolean(HINT_SHOWN_KEY);
            page = savedInstanceState.getInt(PAGE_KEY);
            vpPreview.setCurrentItem(page);
        }
        
        setTitle(getIntent().getStringExtra(NAME_KEY));
        loadHints(quizDir);
        adjustView(page, fdbkPg);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArray(TAG_ID, adapter.getQuestions());
        outState.putInt(QUIZ_PRICE_KEY, price);
        outState.putString(TAG, quizDir.getPath());
        outState.putInt(PAGE_KEY, vpPreview.getCurrentItem());
        outState.putBoolean(FLIPPED_KEY, adapter.getFlipped());
        outState.putInt(FDBK_IDX_KEY, fdbkIdx);
        outState.putBoolean(FDBK_SHOWN_KEY, fdbkShown);
        outState.putBoolean(FDBK_SHOWN_KEY, hintShown);
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
                        if (!questions[i].getPgMap("").contains("0"))
                            questions[i].setState(CAPTURED);
                        else
                            questions[i].setState(DOWNLOADED);
                    }
                    commit(questions[i]);
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
        if (requestCode == BILL_WORKSHEET_TASK_REQUEST_CODE) {
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
            } else {
                Toast.makeText(getApplicationContext(), 
                    "Sorry, no Internet connection", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }    

    @Override
    public void onPageScrollStateChanged(int arg0) { }
    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) { }
    @Override
    public void onPageSelected(int position) {
        if (pageSwipe) {
            pageSwipe = false;
            fdbkIdx = 0;
            hide = true; showHide(null);
            int currentItem = vpPreview.getCurrentItem();
            adjustView(currentItem, fdbkIdx);
        } else {
            fdbkIdx = position;
            Feedback fdbk = feedback[vpPreview.getCurrentItem()];
            adapter.shift(fdbk.page[position],
                fdbk.x[position], fdbk.y[position],
                vpPreview.getCurrentItem());
        }
    }
    
    public void toggleHint(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        if (hintShown) {
            unrenderHint();
        } else {
            ArrayList<String> partsWithHints = new ArrayList<String>();
            Hint h = hints[currentIndex];
            int[] pgMap = adapter.getQuestions()[currentIndex].getPgMap();
            for (int i = 0; i < h.subparts; i++) {
                if (h.getText(i) != null && pgMap[i] == 0) {
                    partsWithHints.add("Part " + (char)((int)'A'+i));
                }
            }
            if (h.subparts > 1 && partsWithHints.size() > 1) {
                final int qsnIdx = currentIndex;
                final String[] parts = partsWithHints.toArray(
                    new String[partsWithHints.size()]);
                AlertDialog.Builder builder = new AlertDialog.Builder(this, 
                    R.style.RobotoDialogTitleStyle);
                builder.setTitle("Show hints for...")
                    .setItems(parts, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            int partIdx = (int)parts[which].charAt(5)-(int)'A';
                            renderHint(qsnIdx, partIdx);
                        }
                });
                builder.show().getWindow().
                    setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            } else if (h.subparts > 1 && partsWithHints.size() == 1) {
                renderHint(currentIndex,
                    ((int)partsWithHints.get(0).charAt(5)-(int)'A'));
            } else if (partsWithHints.size() == 1) {
                renderHint(currentIndex, 0);
            }
        }        
    }
    
    public void flip(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        adapter.flip(currentIndex);
        adjustView(currentIndex, fdbkIdx);
    }
        
    public void action(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        Question question = adapter.getQuestions()[currentIndex];
        if (question.getState() > DOWNLOADED) {            
            adapter.flip(currentIndex);
            adjustView(currentIndex, fdbkIdx);
        } else {
            if (hintShown) {
                unrenderHint();
            } else {
                ArrayList<String> partsWithHints = new ArrayList<String>();
                Hint h = hints[currentIndex];
                for (int i = 0; i < h.subparts; i++) {
                    if (h.getText(i) != null) {
                        partsWithHints.add("Part " + (char)((int)'A'+i));
                    }
                }
                if (h.subparts > 1 && partsWithHints.size() > 1) {
                    final int qsnIdx = currentIndex;
                    final String[] parts = partsWithHints.toArray(
                        new String[partsWithHints.size()]);
                    AlertDialog.Builder builder = new AlertDialog.Builder(this, 
                        R.style.RobotoDialogTitleStyle);
                    builder.setTitle("Show hints for...")
                        .setItems(parts, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                int partIdx = (int)parts[which].charAt(5)-(int)'A';
                                renderHint(qsnIdx, partIdx);
                            }
                    });
                    builder.show().getWindow().
                        setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                } else if (h.subparts > 1 && partsWithHints.size() == 1) {
                    renderHint(currentIndex, 
                        ((int)partsWithHints.get(0).charAt(0)-(int)'a'));
                } else if (partsWithHints.size() == 1) {
                    renderHint(currentIndex, 0);
                }
            }
        }
    }
    
    public void showHide(View view) {
        hide = !hide;
        int visibility = hide ? View.INVISIBLE : View.VISIBLE;
        findViewById(R.id.llBtnBar).setVisibility(visibility);
        
        if (fdbkShown) {
            vpFdbk.setVisibility(visibility);
            fdbkIndicator.setVisibility(visibility);
        } else if (hintShown) {
            vpHints.setVisibility(visibility);
            hintsIndicator.setVisibility(visibility);
        } else if (instructionShown) {
            findViewById(R.id.llInstruction).setVisibility(visibility);
        }
    }
    
    public void launchCameraActivity(View view) {
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
            builder.setTitle("Want us to look at your work?").setMessage(
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
        String[] paths = null;
        if (flipped) {
            if (question.getState() > SENT) {
                paths = getSolution(question);
            } else {
                paths = getQuestion(question);
            }
        } else {
            if (question.getState() > DOWNLOADED) {
                paths = getAnswer(question);
            } else {
                boolean hasScan = question.getPgMap("").matches(".*[1-9].*");
                if (hasScan) {
                    paths = getAnswer(question);
                } else {                    
                    paths = getQuestion(question);
                }
            }
        }
        return paths;
    }
    
    private void adjustView(int position, int fdbkPosn) {
        Button btnFlip = (Button)this.findViewById(R.id.btnFlip);
        Button btnHint = (Button)this.findViewById(R.id.btnHint);
        Button btnCamera = (Button)this.findViewById(R.id.btnCamera);
        Button btnUpload = (Button)this.findViewById(R.id.btnUpload);
        TextView tvMarks = (TextView)findViewById(R.id.tvMarks);
        TextView tvName = (TextView)findViewById(R.id.tvName);
        
        Question[] questions = adapter.getQuestions();
        
        boolean somethingToCapture = !nothingToCapture(questions);
        boolean somethingToUpload = !nothingToUpload(questions);

        btnCamera.setEnabled(somethingToCapture || somethingToUpload);
        btnUpload.setEnabled(somethingToUpload);
        
        Question q = questions[position];
        tvName.setText(String.format("%s of %s", position+1, questions.length));
        
        boolean hasScan = q.getPgMap("").matches(".*[1-9].*");
        btnFlip.setEnabled(hasScan);
        
        unrenderHint();
        unrenderFeedback(position);
        tvMarks.setText("");
        btnHint.setEnabled(false);
        instructionShown = false;
        findViewById(R.id.llInstruction).setVisibility(View.INVISIBLE);

        btnHint.setText("Hint");
        if (adapter.getFlipped()) {
            if (hasScan) {
                btnFlip.setText("Answer");
            } else {
                btnFlip.setText("Question");
            }
            
            if (q.getState() == DOWNLOADED) {
                instructionShown = true;
                findViewById(R.id.llInstruction).setVisibility(View.VISIBLE);
                btnHint.setEnabled(hints[position] != null && price == 0);
            }
        } else {
            if (q.getState() > SENT) {
                btnFlip.setText("Solution");
            } else {
                btnFlip.setText("Question");
            } 
            
            if (q.getState() == GRADED) {
                renderFeedback(position, fdbkPosn);
                tvMarks.setText(String.format("%2.1f/%1d", 
                    q.getMarks(), q.getOutOf()));
            } else if (q.getState() == DOWNLOADED && !hasScan) {
                instructionShown = true;
                findViewById(R.id.llInstruction).setVisibility(View.VISIBLE);
                btnHint.setEnabled(hints[position] != null && price == 0);
            }
        }
                
        btnFlip.refreshDrawableState();
        btnUpload.refreshDrawableState();
        btnCamera.refreshDrawableState();
    }
    
    private void renderFeedback(int position, int fdbkPosn) {
        if (feedback[position] == null)
            feedback[position] = Feedback.load(quizDir, 
                adapter.getQuestions()[position]);
        if (feedback[position] == null) return;
        
        fdbkAdapter = new LatexAdapter(feedback[position].text, this);
        
        vpFdbk.setOffscreenPageLimit(3);
        vpFdbk.setAdapter(fdbkAdapter);
        vpFdbk.setVisibility(View.VISIBLE);
        fdbkShown = true;
        
        //Bind the circle indicator to the adapter
        fdbkIndicator.setVisibility(View.VISIBLE);
        fdbkIndicator.setViewPager(vpFdbk, fdbkPosn);
        fdbkIndicator.setOnPageChangeListener(this);
        
        onPageSelected(fdbkPosn);
    }
    
    private void unrenderFeedback(int position) {
        fdbkShown = false;
        vpFdbk.setVisibility(View.INVISIBLE);
        fdbkIndicator.setVisibility(View.INVISIBLE);
        adapter.shift(0, NO_FEEDBACK, NO_FEEDBACK, position);
    }
    
    private void renderHint(int qsnPosn, int part) {
        if (hints[qsnPosn].getText(part) == null) return;
        
        hintsAdapter = new LatexAdapter(hints[qsnPosn].getText(part), this);
        
        vpHints.setOffscreenPageLimit(3);
        vpHints.setAdapter(hintsAdapter);
        vpHints.setVisibility(View.VISIBLE);
        findViewById(R.id.llInstruction).setVisibility(View.INVISIBLE);
        hintShown = true;
        instructionShown = false;
        
        //Bind the circle indicator to the adapter
        hintsIndicator.setVisibility(View.VISIBLE);
        hintsIndicator.setViewPager(vpHints, 0);
    }
    
    private void unrenderHint() {
        if (hintShown) {
            hintShown = false;
            instructionShown = true;
            vpHints.setVisibility(View.INVISIBLE);
            hintsIndicator.setVisibility(View.INVISIBLE);
            findViewById(R.id.llInstruction).setVisibility(View.VISIBLE);
        }
    }
    
    private void loadHints(File quizDir) {
        File hintsDir = new File(quizDir, HINTS_DIR_NAME);
        Question[] questions = adapter.getQuestions();
        for (int i = 0; i < hints.length; i++) {
            hints[i] = Hint.load(hintsDir, questions[i]);            
        }
    }
        
    private void triggerUploads(Question[] questions) {
        File uploadsDir = new File(quizDir, UPLOAD_DIR_NAME);        
        ArrayList<Question> toSend = new ArrayList<Question>();
        for (Question q: questions) {
            if (q.getState() == CAPTURED) {
                toSend.add(q);
                int[] pages = q.getPgMap();
                for (int page : pages) {
                    try {
                        (new File(uploadsDir, q.getId() + "." + page)).createNewFile();
                        q.setState(SENT);
                        commit(q);
                    } catch (Exception e) {
                        Log.e(TAG, "Create file in uploadsDir failed: " + e.getMessage());
                    }
                }
            } else if (q.getState() == SENT) {
                // check if really sent
                File upload = new File(uploadsDir, q.getId() + ".1"); 
                if (upload.exists()) {
                    toSend.add(q);
                }
            }
        }
        
        Intent uploadIntent = new Intent(getApplicationContext(),
                com.gradians.collect.ImageUploadService.class);
        uploadIntent.putExtra(QUIZ_PATH_KEY, quizDir.getPath());
        uploadIntent.putExtra(TAG_ID, type);
        uploadIntent.putExtra(TAG, toSend.toArray(new Question[toSend.size()]));
        startService(uploadIntent);
    }    

    private void triggerPurchase(Question[] questions) {
        String id = questions[0].getId();
        String ws_id = id.substring(0, id.indexOf('.'));
        Uri src = Uri.parse(String.format(BILL_URL, WEB_APP_HOST_PORT, ws_id));
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this, 
            BILL_WORKSHEET_TASK_REQUEST_CODE).execute(download);
    }
    
    public void commit(Question q) {
        stateMap.put(q.getId(), q.toString());
        File stateFile = new File(quizDir.getParent(), STATE_FILE);
        try {
            stateMap.store(new FileOutputStream(stateFile), null);
        } catch (Exception e) { }
    }
    
    private boolean nothingToUpload(Question[] questions) {
        boolean somethingToUpload = false;
        for (Question q : questions) {
            if (q.getState() == CAPTURED) {
                somethingToUpload = true;
                break;                              
            }            
        }
        return !somethingToUpload;
    }

    private boolean nothingToCapture(Question[] questions) {
        boolean somethingToCapture = false;
        for (Question q : questions) {
            if (q.getState() < CAPTURED && q.getPgMap(",").contains("0")) {
                somethingToCapture = true;
                break;
            }
        }
        return !somethingToCapture;
    }

    private String[] getQuestion(Question question) {
        File questionsDir = new File(quizDir, QUESTIONS_DIR_NAME);
        String[] paths = new String[] 
            {new File(questionsDir, question.getId()).getPath()};
        return paths;
    }

    private String[] getAnswer(Question question) {
        String prefix = question.getId();
        ArrayList<String> paths = new ArrayList<String>();
        File answersDir = new File(quizDir, ANSWERS_DIR_NAME);
        HashSet<Integer> unique = new HashSet<Integer>();
        for (int pg : question.getPgMap()) {
            if (pg != 0 && !unique.contains(pg)) {
                paths.add((new File(answersDir, prefix + "." + pg)).getPath());
                unique.add(pg);
            }
        }
        return paths.toArray(new String[paths.size()]);        
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
        
    protected boolean pageSwipe = true;

    private String type;
    private int price;
    private Feedback[] feedback;
    private Hint[] hints;
    private File quizDir;

    private Properties stateMap;
    private FlowViewPager vpPreview;
    private FlowAdapter adapter;
    
    private int fdbkIdx;
    private boolean hide, fdbkShown, hintShown, instructionShown;
    private ViewPager vpFdbk, vpHints;
    private LatexAdapter fdbkAdapter, hintsAdapter;
    private CirclePageIndicator fdbkIndicator, hintsIndicator;
    
    private final String BILL_URL = "http://%s/tokens/bill_ws?id=%s";
    private final String FLIPPED_KEY = "flipped", FDBK_SHOWN_KEY = "fdbkShown",
        HINT_SHOWN_KEY = "hintShown", FDBK_IDX_KEY = "fdbkIdx", PAGE_KEY = "page";
    
    public static final int NO_FEEDBACK = -1;    
}

class FlowViewPager extends ViewPager {

    public FlowViewPager(Context context) {
        super(context);
    }

    public FlowViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public void setActivity(FlowActivity activity) {
        this.activity = activity;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        activity.pageSwipe = true;
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        activity.pageSwipe = true;
        return super.onTouchEvent(event);
    }

    private FlowActivity activity;
}

class LatexAdapter extends PagerAdapter {
    
    public LatexAdapter(String[] latex, Activity activity) {
        this.latex = latex;
        this.activity = activity;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        final String latexString = latex[position];
        final WebView webView = new WebView(activity);        
        final int scale = activity.getResources().getBoolean(R.bool.isTablet) ? 120 : 80;
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    webView.loadUrl("javascript:MathJax.Hub.Queue(['Typeset', MathJax.Hub]);");
                } else {
                    webView.evaluateJavascript("MathJax.Hub.Queue(['Typeset', MathJax.Hub]);", null);
                }
            }
        });
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            webView.getSettings().setDisplayZoomControls(false);
        webView.loadDataWithBaseURL("file:///android_asset/mathjax-svg",
            String.format(HTML, scale, latexString), "text/html", "utf-8", null);
            
        container.addView(webView);
        return webView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View)object);
    }

    @Override
    public int getCount() {
        return latex.length;
    }

    @Override
    public boolean isViewFromObject(View container, Object object) {
        return container == object;
    }
    
    private String[] latex;
    private Activity activity;
    
    private final String HTML = 
          "<html><head>"
        + "<script type='text/x-mathjax-config'>"
        +   "MathJax.Hub.Config({ "
        +     "showMathMenu: false,"
        +     "jax: [\"input/TeX\", \"output/SVG\"],"
        +     "extensions: [\"tex2jax.js\",\"mml2jax.js\",\"MathMenu.js\",\"MathZoom.js\"],"
        +     "TeX: { extensions: [\"AMSmath.js\", \"AMSsymbols.js\", \"noErrors.js\", \"noUndefined.js\"] }," 
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
        + "<script type='text/javascript' src='file:///android_asset/mathjax-svg/MathJax.js'></script>"
        + "</head><body><span id='math' style='position: absolute; color:white;'>\\[%s\\]</span></body></html>";
       
}

class ActionButton extends Button {
    
    public ActionButton(Context context) {
        super(context);
    }
    
    public ActionButton(Context context, AttributeSet attrs) {
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



