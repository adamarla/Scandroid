package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.viewpagerindicator.CirclePageIndicator;

import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;

public class DetailActivity extends Activity implements ViewPager.OnPageChangeListener, IConstants, ITaskResult {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        
        Parcelable parcel = savedInstanceState == null ? 
            getIntent().getParcelableExtra(TAG) :
            savedInstanceState.getParcelable(TAG);
        question = (Question)parcel;
            
        quizDir = new File(getIntent().getStringExtra(QUIZ_PATH_KEY));
        type = getIntent().getStringExtra(ID_KEY);

        File stateFile = getStateFile(quizDir);
        stateMap = new Properties();
        try {
            stateMap.load(new FileInputStream(stateFile));
        } catch (Exception e) { }

        vpFdbk = (ViewPager)findViewById(R.id.vpFeedback);
        if (getResources().getBoolean(R.bool.isTablet)) {
            LayoutParams lp = (LayoutParams)vpFdbk.getLayoutParams();
            lp.height = 100;
            vpFdbk.setLayoutParams(lp);
        }

        fdbkIndicator = (CirclePageIndicator)findViewById(R.id.circlesFdbk);
        
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        balance = prefs.getInt(BALANCE_KEY, 0);
        
        position = getIntent().getIntExtra(TAG_ID, 0);
        title = getIntent().getStringExtra(NAME_KEY);
        setTitle(title);
        
        showing = QSN;
        adjustView();
        
        if (question.getName().equals("Q"))
            purchase(findViewById(R.id.btnBar));
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(TAG, question);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            if (!type.equals(DBT_TYPE) && showing != QSN) {
                back();
                return true;
            }
            Intent intent = new Intent();
            intent.putExtra(TAG, question);
            this.setResult(RESULT_OK, intent);
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        if (!type.equals(DBT_TYPE) && showing != QSN) {
            back();
            return;
        }
        Intent intent = new Intent();
        intent.putExtra(TAG, question);
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Question q = data.getParcelableExtra(TAG);
                question.setPgMap(q.getPgMap());
                
                if (!question.getPgMap("").contains("0")) {
                    question.setState(CAPTURED);
                    stateMap.put(question.getId(), question.toString());
                } else {
                    question.setState(DOWNLOADED);
                    stateMap.remove(question.getId());
                }
                commit();
                
                back();
                showing = QSN;
                adjustView();
                
                if (question.getState() == CAPTURED)
                    triggerUpload();
            }
        }
    }
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == PURCHASE_TASK_REQUEST_CODE) {
            peedee.dismiss();
            if (resultCode == RESULT_OK) {
                try {
                    JSONParser jsonParser = new JSONParser();
                    JSONObject respObject = (JSONObject)jsonParser.parse(resultData);
                    String op = (String)respObject.get(OP_KEY);
                    String id = String.valueOf((Long)respObject.get(ID_KEY));
                    balance = ((Long)respObject.get(BALANCE_KEY)).intValue();
                    
                    SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
                    Editor edit = prefs.edit();
                    edit.putInt(BALANCE_KEY, balance);
                    edit.commit();
                    
                    if (op.equals(BUY_ASK)) {
                        question.setId(id);
                        question.setName((String)respObject.get(NAME_KEY));
                        question.setBotSoln(true);                        
                        question.setState(CAPTURED);
                        adjustView();
                        
                        File questionsDir = new File(quizDir, QUESTIONS_DIR_NAME);
                        File src = new File(questionsDir, "0.1");
                        File target = new File(questionsDir, question.getId() + ".1");
                        src.renameTo(target);
                        
                        triggerUpload();
                        
                        AlertDialog.Builder builder = null;
                        try {
                            builder = new AlertDialog.Builder(this,
                                R.style.RobotoDialogTitleStyle);        
                        } catch (NoSuchMethodError e) {
                            builder =  new AlertDialog.Builder(this);
                        }
                        builder.setMessage("Your question has been received." + 
                            " You'll hear back in 24 hours");
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.show().getWindow().setBackgroundDrawable(
                            new ColorDrawable(Color.TRANSPARENT));
                    } else if (op.equals(BUY_ANS)) {
                        question.setBotAns(true);
                        adjustView();
                        dlCode = DL_ANS;
                        triggerDownloads(question);
                    } else if (op.equals(BUY_SOLN)) {
                        question.setBotSoln(true);
                        adjustView();
                        dlCode = DL_SOLN;
                        triggerDownloads(question);
                    } else { // op == GUESS
                        question.setGuess(guess);
                        adjustView();
                        displayAnswers(question, true);
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Purchase action failed " + e.getMessage());
                }
                
            } else {
                Toast.makeText(getApplicationContext(), 
                    "Sorry, no Internet connection",
                    Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == DOWNLOAD_MONITOR_TASK_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                switch (dlCode) {
                case DL_CODEX:
                    displayAnswers(question, true);
                    break;
                case DL_ANS:
                    displayAnswers(question, false);
                    break;
                case DL_SOLN:
                    showing = SOLN;
                    adjustView();
                    break;
                default:
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
        display(position);
    }
    
    public void purchase(View view) {
        final Question qsn = question;
        int price = 0;
        String action = null;
        if (view.getId() == R.id.btnBar) {
            if (qsn.botSolution()) {
                if (question.getState() == GRADED) {
                    showing = showing == QSN ? SOLN : QSN;
                    adjustView();
                }
                return;
            }
            price = ASK_PRICE;
            action = BUY_ASK;
        } else if (view.getId() == R.id.btnBuyAns) {
            if (qsn.botAnswer() || qsn.botSolution()) {
                dlCode = DL_ANS;
                triggerDownloads(qsn);
                return;
            }
            price = ANS_PRICE;
            action = BUY_ANS;
        } else {
            if (qsn.canSeeSolution(type)) {
                if (showing != SOLN) {
                    showing = SOLN;
                    adjustView();
                }
                return;
            }
            price = SOLN_PRICE;
            action = BUY_SOLN;
        }

        final String finalAction = action;
        AlertDialog.Builder builder = null;
        try {
            builder = new AlertDialog.Builder(this,
                R.style.RobotoDialogTitleStyle);        
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "Older SDK, using old Builder");
            builder =  new AlertDialog.Builder(this);
        }        
        builder.setMessage(String.format(
            "Current Balance %3d ₲\nAfter Purchase   %3d ₲", balance,
            (balance - price)));
        builder.setPositiveButton(R.string.purchase_conf_text,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    recordAction(qsn, finalAction);
                }
            });
        builder.setNegativeButton(R.string.purchase_cancel_text,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
        builder.show().getWindow()
            .setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    public void selfCheck(View v) {
        displayAnswers(question, true);
    }

    public void activateCamera(View view) {
        if (question.getState() == DOWNLOADED) {
            launchCameraActivity(question);
        } else if (question.hasScan()) {
            if (showing == ATMPT)
                showing = QSN;
            else
                showing = ATMPT;
            adjustView();
        }
    }
    
    public void onHiddenClick(View v) {
        Toast.makeText(getApplicationContext(),
            "lib:" + question.getImgLocn(), Toast.LENGTH_LONG).show();
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void display(int position) {
        View canvas = null;
        String[] paths = getPaths(question);
        ScrollView svCanvas = (ScrollView)findViewById(R.id.svCanvas);
        DullWebView dwvCanvas = (DullWebView)findViewById(R.id.wvCanvas);
        if (paths[0].contains(ATTEMPTS_DIR_NAME) ||
            paths[0].contains(DOUBTS_DIR_NAME)) {
            svCanvas.setVisibility(View.GONE);
            dwvCanvas.clearCache(true);
            dwvCanvas.getSettings().setBuiltInZoomControls(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                dwvCanvas.getSettings().setDisplayZoomControls(false);
            dwvCanvas.getSettings().setDefaultTextEncodingName("utf-8");
            dwvCanvas.getSettings().setUseWideViewPort(true);
            dwvCanvas.getSettings().setJavaScriptEnabled(true);
            dwvCanvas.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            
            StringBuilder sbHtml = new StringBuilder();
                sbHtml.append(HEAD).append(BODY);
            String styleImgWidth = "";
            for (int i = 0; i < paths.length; i++) {
                sbHtml.append(String.format(PARENT_DIV, i));
                styleImgWidth = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ? 
                    "width: 100%%; " : "";
                sbHtml.append(String.format(IMG_DIV, 
                    Uri.fromFile(new File(paths[i])).toString(), styleImgWidth));
                if (question.getState() == GRADED && paths[0].contains(ATTEMPTS_DIR_NAME)) {
                    int fdbkOnPg = feedback.page[position];
                    if (i == fdbkOnPg) {
                        for (int j = 0; j < feedback.x.length; j++) {
                            String div = j == position ? MARKER_DIV : NON_MARKER_DIV;
                            sbHtml.append(String.format(div, feedback.y[j], feedback.x[j], j+1));
                        }
                    }
                }
                sbHtml.append(PARENT_DIV_CLS);
            }
            sbHtml.append(FTR);
            dwvCanvas.loadDataWithBaseURL(null, 
                sbHtml.toString(), "text/html", "utf-8", null);
            
            final int posn = position;
            if (question.getState() == GRADED && paths[0].contains(ATTEMPTS_DIR_NAME)) {
                dwvCanvas.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        String s = String.format(JS_SCROLL, posn);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                            ((DullWebView)view).loadUrl("javascript:" + s);
                        } else {
                            ((DullWebView)view).evaluateJavascript(s, null);
                        }                    
                    }
                });
            }
            canvas = dwvCanvas;
        } else {
            dwvCanvas.setVisibility(View.GONE);
            LinearLayout llCanvas = (LinearLayout)findViewById(R.id.llCanvas);
            llCanvas.removeAllViews();
            
            final int MIN_WIDTH = 280, PAD = 5;
            for (String path : paths) {                
                Bitmap bimg = BitmapFactory.decodeFile(path);
                StretchyImageView iv = 
                    new StretchyImageView(getApplicationContext());
                LinearLayout.LayoutParams lp = 
                    new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                iv.setLayoutParams(lp);
                iv.setAdjustViewBounds(true);
                iv.setPadding(PAD, PAD, PAD, PAD);
                
                float density = getApplicationContext().getResources().
                    getDisplayMetrics().density;
                if (bimg.getWidth() < MIN_WIDTH) {
                    int paddingX = MIN_WIDTH - bimg.getWidth();
                    iv.setPadding((int)(paddingX/2*density), (int)(PAD*density), 
                        (int)(paddingX/2*density), (int)(PAD*density));
                }
                iv.setImageBitmap(bimg);
                llCanvas.addView(iv);
            }
            canvas = svCanvas;
        }
        canvas.setVisibility(View.VISIBLE);
    }

    private void adjustView() {
        if (type.equals(DBT_TYPE)) {
            findViewById(R.id.llBtnBar).setVisibility(View.GONE);
            findViewById(R.id.tvName).setVisibility(View.INVISIBLE);
            
            TextView tvMarks = (TextView)findViewById(R.id.tvMarks);
            tvMarks.setText(question.getName());
            tvMarks.setVisibility(View.VISIBLE);
            
            TextView btnBar = (TextView)findViewById(R.id.btnBar);
            if (question.getState() == DOWNLOADED) {
                btnBar.setText(R.string.buy_ask_text);
                tvMarks.setVisibility(View.INVISIBLE);
            } else {
                btnBar.setText(showing == QSN ? 
                    R.string.bot_ask_text : R.string.bot_ask_text_flip);
                btnBar.setEnabled(question.getState() == GRADED);
            }
        } else if (showing == QSN) {
            findViewById(R.id.btnBar).setVisibility(View.GONE);
            
            Button btnSelfChk = (Button)findViewById(R.id.btnSelfChk);
            TextView btnBuyAns = (TextView)findViewById(R.id.btnBuyAns);
            TextView btnBuySoln = (TextView)findViewById(R.id.btnBuySoln);
            Button btnCamera = (Button)findViewById(R.id.btnCamera);

            TextView tvName = (TextView)findViewById(R.id.tvName);            
            tvName.setText(String.format("%s", (position+1))); 
            if (question.tried() || question.botAnswer() ||
                question.botSolution() || question.hasScan()) {
                tvName.setBackgroundColor(getApplicationContext().
                    getResources().getColor(R.color.blue));
            } else {
                tvName.setBackgroundColor(getApplicationContext().
                    getResources().getColor(R.color.gray));
            }            
        
            // Self Check Button
            int icon = R.drawable.ic_action_mic;
            btnSelfChk.setEnabled(question.hasCodex());
            btnSelfChk.setText("Self Check");
            if (question.tried()) {
                icon = question.getGuess() == question.getVersion() ?
                    R.drawable.ic_action_accept : 
                    R.drawable.ic_action_cancel;
            }
            btnSelfChk.setCompoundDrawablesWithIntrinsicBounds
                (0, icon, 0, 0);

            // Answer and Solution Buttons
            btnBuyAns.setText(R.string.buy_answer_text);
            btnBuySoln.setText(R.string.buy_soln_text);
            btnBuyAns.setEnabled(false);
            btnBuySoln.setEnabled(false);
            if (type.equals(QSN_TYPE)) {
                if (question.hasAnswer()) {
                    if (balance >= ANS_PRICE || 
                        question.botAnswer() || question.botSolution())
                        btnBuyAns.setEnabled(true);
                    if (question.botAnswer() || question.botSolution())
                        btnBuyAns.setText(R.string.bot_answer_text);
                }
                if (balance >= SOLN_PRICE || question.botSolution())
                    btnBuySoln.setEnabled(true);
            } else {
                btnBuySoln.setEnabled(question.canSeeSolution(type));
            }
            
            if (question.canSeeSolution(type))
                btnBuySoln.setText(R.string.bot_soln_text);

            // Review (Camera) Button            
            if (question.hasScan())
                btnCamera.setText("See Attempt");
            else
                btnCamera.setText("Get Review");                
            
            btnBuyAns.refreshDrawableState();
            btnBuySoln.refreshDrawableState();
            btnSelfChk.refreshDrawableState();
            btnCamera.refreshDrawableState();
        } else {
            setTitle(String.format("%s %s", title, (position+1)));
            if (showing == SOLN)
                findViewById(R.id.llTopBar).setVisibility(View.GONE);
            findViewById(R.id.llBtnBar).setVisibility(View.GONE);
            findViewById(R.id.tvName).setVisibility(View.INVISIBLE);
            if (question.getState() == GRADED && showing == ATMPT) {
                renderFeedback();
                TextView tvMarks = (TextView)findViewById(R.id.tvMarks);
                tvMarks.setText(getQuantFdbk(question));
                tvMarks.setVisibility(View.VISIBLE);
            }
        }        
        display(0);
    }

    private void back() {
        setTitle(title);
        findViewById(R.id.llTopBar).setVisibility(View.VISIBLE);        
        findViewById(R.id.llBtnBar).setVisibility(View.VISIBLE);
        findViewById(R.id.tvName).setVisibility(View.VISIBLE);
                
        findViewById(R.id.tvMarks).setVisibility(View.INVISIBLE);
        findViewById(R.id.svCanvas).setVisibility(View.GONE);
        findViewById(R.id.wvCanvas).setVisibility(View.GONE);
        unrenderFeedback();
        
        showing = QSN;
        adjustView();
    }

    private String getQuantFdbk(Question q) {
        String quality = "";
        if (type.equals(GR_TYPE)) {
            quality = String.format("%2.1f/%1d", q.getMarks(), q.getOutOf());
        } else {
            switch ((int) q.getMarks()) {
            case 2:
                quality = "Unimpressed";
                break;
            case 3:
                quality = "Mildly impressed";
                break;
            case 4:
                quality = "Reasonably impressed";
                break;
            case 5:
                quality = "Quite impressed";
                break;
            case 6:
                quality = "Very impressed";
                break;
            default:
                quality = " --- ";
            }
        }
        return quality;
    }

    private void renderFeedback() {
        if (feedback == null) {
            File feedbackDir = new File(quizDir, FEEDBACK_DIR_NAME);
            feedback = Feedback.load(feedbackDir, question);
        }
        if (feedback == null) {
            return;
        }

        fdbkAdapter = new LatexAdapter(feedback.text, this);

        vpFdbk.setOffscreenPageLimit(3);
        vpFdbk.setAdapter(fdbkAdapter);
        vpFdbk.setVisibility(View.VISIBLE);

        // Bind the circle indicator to the adapter
        fdbkIndicator.setVisibility(View.VISIBLE);
        fdbkIndicator.setViewPager(vpFdbk, 0);
        fdbkIndicator.setOnPageChangeListener(this);

        onPageSelected(0);
    }

    private void unrenderFeedback() {
        vpFdbk.setVisibility(View.INVISIBLE);
        fdbkIndicator.setVisibility(View.INVISIBLE);
    }

    private void launchCameraActivity(Question q) {
        File answersDir = new File(quizDir, ATTEMPTS_DIR_NAME);
        Intent takePictureIntent =
            new Intent(this.getApplicationContext(),
                com.gradians.collect.CameraActivity.class);
        takePictureIntent.putExtra(TAG, q);
        takePictureIntent.putExtra(SCANS_KEY, answersDir.getPath());
        startActivityForResult(takePictureIntent,
            ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    private void triggerUpload() {
        File uploadsDir = new File(quizDir, UPLOAD_DIR_NAME);
        ArrayList<Question> toSend = new ArrayList<Question>();
        if (question.getState() == CAPTURED) {
            int[] pages = question.getPgMap();
            for (int page : pages) {
                try {
                    String filename = question.getId() + "." + page;
                    (new File(uploadsDir, filename)).createNewFile();
                    toSend.add(question);                                        
                    if (!type.equals(DBT_TYPE)) {
                        question.setState(SENT);
                        stateMap.put(question.getId(), question.toString());
                        commit();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Create file in uploadsDir failed: " 
                        + e.getMessage());
                }
            }
        } else if (question.getState() == SENT) {
            // check if really sent
            File upload = new File(uploadsDir, question.getId() + ".1");
            if (upload.exists()) {
                toSend.add(question);
            }
        }
        
        if (toSend.size() > 0) {
            Intent uploadIntent =
                new Intent(getApplicationContext(),
                    com.gradians.collect.ImageUploadService.class);
            uploadIntent.putExtra(QUIZ_PATH_KEY, quizDir.getPath());
            uploadIntent.putExtra(TAG_ID, type);
            uploadIntent.putExtra(TAG, toSend.toArray(new Question[toSend.size()]));
            startService(uploadIntent);
        }
    }

    private void displayAnswers(final Question qsn, boolean check) {
        ArrayList<String> options = new ArrayList<String>();
        if (check) {
            if (qsn.tried()) {
                options.add("" + (char) ((int) 'A' + qsn.getGuess()));
            } else {
                options.add("A");
                options.add("B");
                options.add("C");
                options.add("D");
            }
        } else {
            options.add("" + (char) ((int) 'A' + qsn.getVersion()));
        }

        final String[] finalOptions =
            options.toArray(new String[options.size()]);
        ListAdapter optionsAdapter =
            new ArrayAdapter<String>(this, R.layout.layout_answer,
                android.R.id.text1, finalOptions) {

                @Override
                public View getView(int position, View convertView,
                    ViewGroup parent) {

                    // User super class to create the View
                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    if (convertView == null) {
                        convertView =
                            inflater.inflate(R.layout.layout_answer, parent,
                                false);
                    }

                    TextView tv = (TextView)convertView.findViewById(R.id.tvOption);
                    ImageView iv = (ImageView)convertView.findViewById(R.id.ivAnswer);

                    File answersDir = new File(quizDir, ANSWERS_DIR_NAME);
                    File image = null;
                    int version = (int)finalOptions[position].charAt(0) - (int)'A';
                    try {
                        image = new File(answersDir, version + "." + qsn.getId());
                        tv.setText(finalOptions[position]);
                    } catch (Exception e) {
                        Log.e(TAG, e.getClass().toString());
                    }

                    final int HEIGHT = 60, PAD = 5;
                    float density = getApplicationContext().getResources().
                        getDisplayMetrics().density;
                    Bitmap bimg = BitmapFactory.decodeFile(image.getPath());
                    iv.setPadding(
                        (int)(PAD*density),
                        bimg.getHeight() > HEIGHT ? 
                            (int)(PAD*density) : 
                            (int)((HEIGHT - bimg.getHeight())/2*density), 
                        (int)(PAD*density), 
                        bimg.getHeight() > HEIGHT ? 
                            (int)(PAD*density) : 
                            (int)((HEIGHT - bimg.getHeight())/2*density)); 
                    iv.setAdjustViewBounds(true);
                    iv.setImageURI(Uri.fromFile(image));

                    convertView.setBackgroundColor(Color.WHITE);
                    return convertView;
                }
            };

        AlertDialog.Builder builder = null;
        try {
            builder = new AlertDialog.Builder(this,
                R.style.RobotoDialogTitleStyle);        
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "Older SDK, using old Builder");
            builder =  new AlertDialog.Builder(this);
        }        

        if (check) {
            if (qsn.tried()) {
                builder.setTitle("You Picked");
                builder.setAdapter(optionsAdapter, null);
            } else {
                builder.setTitle("Options");
                builder.setAdapter(optionsAdapter,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            guess = which;
                            recordAction(qsn, GUESS);
                        }
                    });
            }
        } else {
            builder.setTitle("Correct Answer");
            builder.setAdapter(optionsAdapter, null);
        }
        builder.show().getWindow()
            .setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    private void triggerDownloads(Question question) {
        DownloadMonitor dlm = new DownloadMonitor(this);
        File dlDir = null, image = null;
        int[] codices = null;
        Uri src, dest;
        switch (dlCode) {
        case DL_CODEX:
            codices = new int[] { 0, 1, 2, 3 };
        case DL_ANS:
            if (codices == null)
                codices = new int[] { question.getVersion() };
            dlDir = new File(quizDir, ANSWERS_DIR_NAME);
            for (int codex : codices) {
                image = new File(dlDir, codex + "." + question.getId());
                if (!image.exists()) {
                    src = Uri.parse(String.format(ANSR_URL, BANK_HOST_PORT,
                        question.getImgLocn().replaceFirst("/[0-3]$", ""),
                        codex));
                    dest = Uri.fromFile(image);
                    dlm.add(question.getId(), src, dest);
                }
            }
            break;
        case DL_SOLN:
            dlDir = new File(quizDir, SOLUTIONS_DIR_NAME);
            for (short i = 0; i < question.getImgSpan(); i++) {
                image = new File(dlDir, "m." + question.getVersion() + "."
                    + question.getId() + "." + (i + 1));
                if (!image.exists()) {
                    src = Uri.parse(String.format(SOLN_URL, BANK_HOST_PORT,
                        question.getImgLocn(), (i + 1)));
                    dest = Uri.fromFile(image);
                    dlm.add(question.getId(), src, dest);
                }
            }
        }

        if (dlm.getCount() > 0) {
            if (dlm.isNetworkAvailable()) {
                dlm.start("Synchronizing Files", "Please wait...", this);
            } else {
                Toast.makeText(getApplicationContext(),
                    "Sorry, no Internet connection", Toast.LENGTH_LONG).show();
            }
        } else {
            switch (dlCode) {
            case DL_CODEX:
                displayAnswers(question, true);
                break;
            case DL_ANS:
                displayAnswers(question, false);
                break;
            case DL_SOLN:
                adjustView();
            }
        }
    }

    private void recordAction(Question question, String op) {
        SharedPreferences prefs =
            getSharedPreferences(TAG, Context.MODE_PRIVATE);
        String studentId = prefs.getString(ID_KEY, null);
        Uri src = null;
        if (type.equals(DBT_TYPE)) {
            src = Uri.parse(String.format(BILL_STAB_URL, 
                WEB_APP_HOST_PORT, studentId, "0", "0", "0", op));
        } else if (type.equals(QSN_TYPE)) {
            src = Uri.parse(String.format(BILL_STAB_URL, 
                WEB_APP_HOST_PORT, studentId, question.getQsnId(), 
                question.getVersion(), op.equals(GUESS) ? guess : "-1", op));
        } else {
            src = Uri.parse(String.format(BILL_ATTEMPT_URL, 
                WEB_APP_HOST_PORT, question.getGRId(","), 
                op.equals(GUESS) ? guess : "-1", op));
        }
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this, PURCHASE_TASK_REQUEST_CODE)
            .execute(download);

        peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
        peedee.setMessage(op.equals(GUESS) ? "Checking..." : "Purchasing...");
        peedee.setIndeterminate(true);
        peedee.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        peedee.getWindow().setBackgroundDrawable(
            new ColorDrawable(Color.TRANSPARENT));
        peedee.show();
    }

    private void commit() {
        File stateFile = getStateFile(quizDir);
        try {
            stateMap.store(new FileOutputStream(stateFile), null);
        } catch (Exception e) {
        }
    }

    private String[] getPaths(Question question) {
        String[] paths = null;
        switch (showing) {
        case ATMPT:
            paths = getAttempt(question);
            break;
        case SOLN:
            paths = getSolution(question);
            break;
        default:
            paths = getQuestion(question);
        }
        if (paths.length == 0) {
            paths = getQuestion(question);
        }
        return paths;
    }

    private String[] getQuestion(Question question) {
        File questionsDir = new File(quizDir, QUESTIONS_DIR_NAME);
        File file = null;
        if (type.equals(DBT_TYPE)) {
            file = new File(questionsDir, question.getId() + ".1");
        } else {
            file = new File(questionsDir, "m." + question.getVersion() 
                + "." + question.getId());
        }
        String[] paths = new String[] { file.getPath() };
        return paths;
    }

    private String[] getAttempt(Question question) {
        String prefix = question.getId();
        ArrayList<String> paths = new ArrayList<String>();
        File answersDir = new File(quizDir, ATTEMPTS_DIR_NAME);
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
        String[] paths = null;
        if (type.equals(DBT_TYPE)) {
            paths = new String[1];
            paths[0] = new String((new File(solutionsDir, 
                question.getId() + ".1").getPath()));
        } else {
            paths = new String[question.getImgSpan()];
            for (int i = 0; i < paths.length; i++)
                paths[i] = (new File(solutionsDir, 
                    "m." + question.getVersion() + "." +
                    question.getId() + "." + (i + 1))).getPath();
        }
        return paths;
    }

    private File getStateFile(File quizDir) {
        File filesDir =
            new File(quizDir.getParentFile().getParentFile(), "files");
        return new File(filesDir, STATE_FILE);
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

    private int balance;
    private String type, title;
    private File quizDir;
    private int position;
    private Question question;
    private Feedback feedback;
    
    private Properties stateMap;

    private char showing;
    private int dlCode, guess;
    private ViewPager vpFdbk;
    private LatexAdapter fdbkAdapter;
    private CirclePageIndicator fdbkIndicator;
    private ProgressDialog peedee;

    private final int DL_CODEX = 1, DL_ANS = 2, DL_SOLN = 3;
    private final String OP_KEY   = "op";
    private final String GUESS    = "guess", GET_FDBK = "grade",
        BUY_ANS = "answer", BUY_SOLN = "solution", BUY_ASK = "ask";   
    private final String        
        BILL_STAB_URL = "http://%s/tokens/record?s=%s&q=%s&v=%s&g=%s&op=%s",
        BILL_ATTEMPT_URL = "http://%s/tokens/record?id=%s&g=%s&op=%s",
        ANSR_URL = "http://%s/vault/%s/%s/codex.png",
        SOLN_URL = "http://%s/vault/%s/pg-%d.png";
    private final char ATMPT = 'a', SOLN = 's', QSN = 'q';    

    private final String JS_SCROLL = "document.getElementById('pg%s').scrollIntoView();";
    private final String PARENT_DIV = "<div id='pg%s' style='position: relative; '>";
    private final String PARENT_DIV_CLS = "</div>";
    private final String IMG_DIV = "<img src='%s' style='%s'/>";
    private final String MARKER_DIV = "<div style='font-size: 11px ; "
        + "text-align : center ; width: 15px ; border-radius : 10px ; " 
        + "padding: 4px ; color: white ; position:absolute; top:%s%%; " 
        + "left: %s%%; background: #F88017;'>%s</div>";
    private final String NON_MARKER_DIV = "<div style='font-size: 11px ; " 
        + "text-align : center ; width: 15px ; border-radius : 10px ; " 
        + "padding: 4px ; color: white ; position:absolute; top:%s%%; " 
        + "left: %s%%; background: #676767;'>%s</div>";
    private final String HEAD = "<html><head>" 
        + "<meta name=\"viewport\" " 
        + " content=\"width=device-width, initial-scale=1\"></meta></head><body>";
    private final String BODY = "<body style='margin:0;padding:0;'>";
    private final String FTR = "</body></html>"; 
    
}
