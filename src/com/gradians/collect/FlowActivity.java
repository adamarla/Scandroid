package com.gradians.collect;

import java.io.File;
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
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
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

        adapter = new FlowAdapter(questions, this.getSupportFragmentManager());
        
        feedback = new Feedback[questions.length];
        hints = new Hint[questions.length];
        
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);

        vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        vpHints = (ViewPager)findViewById(R.id.vpHints);

        fdbkIndicator = (CirclePageIndicator)findViewById(R.id.circlesFdbk);
        hintsIndicator = (CirclePageIndicator)findViewById(R.id.circlesHints);
        
        String title;
        int page = 0, fdbkPg = 0;
        if (savedInstanceState == null) {
            title = getIntent().getStringExtra(NAME_KEY);
            price = getIntent().getIntExtra(QUIZ_PRICE_KEY, 0);
            quizDir = new File(getIntent().getStringExtra(TAG));
            vpPreview.setCurrentItem(0);
        } else {
            title = savedInstanceState.getString(NAME_KEY);
            price = savedInstanceState.getInt(QUIZ_PRICE_KEY, 0);
            quizDir = new File(savedInstanceState.getString(TAG));
            page = savedInstanceState.getInt("page");
            vpPreview.setCurrentItem(page);
            adapter.setFlipped(savedInstanceState.getBoolean("flipped"));
            fdbkPg = savedInstanceState.getInt("fdbkIdx", FdbkView.NO_FEEDBACK);
            fdbkShown = savedInstanceState.getBoolean("fdbkShown");
            hintShown = savedInstanceState.getBoolean("hintShown");
        }
        setTitle(title);
        loadHints(quizDir);
        adjustView(page, fdbkPg);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArray(TAG_ID, adapter.getQuestions());
        outState.putInt(QUIZ_PRICE_KEY, price);
        outState.putString(TAG, quizDir.getPath());
        outState.putInt("page", vpPreview.getCurrentItem());
        outState.putBoolean("flipped", adapter.getFlipped());
        outState.putInt("fdbkIdx", fdbkIdx);
        outState.putBoolean("fdbkShown", fdbkShown);
        outState.putBoolean("hintShown", hintShown);
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
        if (fdbkShown) {
            fdbkIdx = position;
            Feedback fdbk = feedback[vpPreview.getCurrentItem()];
            adapter.shift(fdbk.page[position], fdbk.x[position], fdbk.y[position],
                vpPreview.getCurrentItem());
        }
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
        ViewGroup btnBar = (ViewGroup)findViewById(R.id.llBtnBar);
        for (int i = 0; i < btnBar.getChildCount(); i++) {
            btnBar.getChildAt(i).setVisibility(
                btnBar.getChildAt(i).getVisibility() == View.INVISIBLE ?
                        View.VISIBLE : View.INVISIBLE);
        }
        Question q = adapter.getQuestions()[vpPreview.getCurrentItem()];
        if (fdbkShown) {
            vpFdbk.setVisibility(vpFdbk.getVisibility() == View.INVISIBLE ?
                View.VISIBLE : View.INVISIBLE);
            fdbkIndicator.setVisibility(fdbkIndicator.getVisibility() == View.INVISIBLE ?
                View.VISIBLE : View.INVISIBLE);
        } else if (hintShown) {
            vpHints.setVisibility(vpHints.getVisibility() == View.INVISIBLE ?
                View.VISIBLE : View.INVISIBLE);
            hintsIndicator.setVisibility(hintsIndicator.getVisibility() == View.INVISIBLE ?
                View.VISIBLE : View.INVISIBLE);
        } else if (q.getState() == DOWNLOADED) {
            ViewGroup instructionBar = (ViewGroup)findViewById(R.id.llInstruction);
            instructionBar.setVisibility(instructionBar.getVisibility() == View.INVISIBLE ?
                    View.VISIBLE : View.INVISIBLE);
//            for (int i = 0; i < instructionBar.getChildCount(); i++) {
//                instructionBar.getChildAt(i).setVisibility(
//                    instructionBar.getChildAt(i).getVisibility() == View.INVISIBLE ?
//                            View.VISIBLE : View.INVISIBLE);
//            }
        }
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
        String[] paths = null;
        if (flipped) {
            if (question.getState() > SENT) {
                paths = getSolution(question);
            } else if (question.getState() == DOWNLOADED) {
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
        ActionButton btnAction = (ActionButton)this.findViewById(R.id.btnAction);
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
        
        boolean somethingToCapture = !nothingToCapture(questions);
        boolean somethingToUpload = !nothingToUpload(questions);

        btnCamera.setEnabled(somethingToCapture || somethingToUpload);
        btnUpload.setEnabled(somethingToUpload);
        btnAction.setEnabled(true);
        
        Question q = questions[position];
        btnAction.setIsCaptured(q.getState() > DOWNLOADED);
        btnAction.setIsSent(q.getState() > CAPTURED);
        ((TextView)findViewById(R.id.tvName)).setText(q.getName());
        if (adapter.getFlipped()) {
            if (q.getState() > DOWNLOADED) {
                btnAction.setText("Response");
            } else {
                btnAction.setText("Question");
            }
            
            if (q.getState() == GRADED) {
                unrenderFeedback(position);
            }
        } else {
            if (q.getState() > SENT) {
                btnAction.setText("Solution");
            } else if (q.getState() > DOWNLOADED) {
                btnAction.setText("Question");
            } else {
                btnAction.setText("Hint");
                btnAction.setEnabled(hints[position] != null);
            }
            
            if (q.getState() == GRADED) {
                renderFeedback(position, fdbkPosn);
                tvMarks.setText(String.format("%2.1f/%1d", 
                    q.getMarks(), q.getOutOf()));
            } else {
                unrenderFeedback(position);
                tvMarks.setText("");
            }
        }
        int visibility = (q.getState() == DOWNLOADED ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.llInstruction).setVisibility(visibility);
        
        if (hintShown) unrenderHint();
        
        btnLeft.refreshDrawableState();
        btnAction.refreshDrawableState();
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
        adapter.shift(0, FdbkView.NO_FEEDBACK, FdbkView.NO_FEEDBACK, position);
    }
    
    private void renderHint(int qsnPosn, int part) {
        if (hints[qsnPosn].getText(part) == null) return;
        
        hintsAdapter = new LatexAdapter(hints[qsnPosn].getText(part), this);
        
        vpHints.setOffscreenPageLimit(3);
        vpHints.setAdapter(hintsAdapter);
        vpHints.setVisibility(View.VISIBLE);
        findViewById(R.id.llInstruction).setVisibility(View.INVISIBLE);
        hintShown = true;
        
        //Bind the circle indicator to the adapter
        hintsIndicator.setVisibility(View.VISIBLE);
        hintsIndicator.setViewPager(vpHints, 0);
        hintsIndicator.setOnPageChangeListener(this);
        
        onPageSelected(0);
    }
    
    private void unrenderHint() {
        hintShown = false;
        vpHints.setVisibility(View.INVISIBLE);
        hintsIndicator.setVisibility(View.INVISIBLE);
        findViewById(R.id.llInstruction).setVisibility(View.VISIBLE);
    }
    
    private void loadHints(File quizDir) {
        File hintsDir = new File(quizDir, HINTS_DIR_NAME);
        Question[] questions = adapter.getQuestions();
        for (int i = 0; i < hints.length; i++) {
            hints[i] = Hint.load(hintsDir, questions[i]);            
        }
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
        Uri src = Uri.parse(String.format(BILL_URL, WEB_APP_HOST_PORT, ws_id));
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this,
            BILL_WORKSHEET_TASK_RESULT_CODE).execute(new Download[] { download });
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
    private Hint[] hints;
    private File quizDir;

    private ViewPager vpPreview;
    private FlowAdapter adapter;
    
    private int fdbkIdx;
    private boolean fdbkShown, hintShown;
    private ViewPager vpFdbk, vpHints;
    private LatexAdapter fdbkAdapter, hintsAdapter;
    private CirclePageIndicator fdbkIndicator, hintsIndicator;
    
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
        final int scale = activity.getResources().getConfiguration().
            orientation == Configuration.ORIENTATION_LANDSCAPE ? 100 : 80;
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
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setJavaScriptEnabled(true);
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



