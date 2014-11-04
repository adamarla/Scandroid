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

import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.view.PagerAdapter;
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
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, ITaskResult, IConstants {

    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow);
        
        Parcelable[] parcels = savedInstanceState == null ? 
            getIntent().getParcelableArrayExtra(TAG) :
            savedInstanceState.getParcelableArray(TAG);            
        Question[] questions = new Question[parcels.length];
        for (int i = 0; i < parcels.length; i++)
            questions[i] = (Question)parcels[i];

        quizDir = new File(getIntent().getStringExtra(QUIZ_PATH_KEY));
        type = getIntent().getStringExtra(ID_KEY);

        adapter = new FlowAdapter(questions, this.getSupportFragmentManager());
        
        File stateFile = getStateFile(quizDir);
        stateMap = new Properties();
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
        if (getResources().getBoolean(R.bool.isTablet)) {
            LayoutParams lp = (LayoutParams)vpFdbk.getLayoutParams();
            lp.height = 100;
            vpFdbk.setLayoutParams(lp);
        }
        vpHints = (ViewPager)findViewById(R.id.vpHints);

        fdbkIndicator = (CirclePageIndicator)findViewById(R.id.circlesFdbk);
        hintsIndicator = (CirclePageIndicator)findViewById(R.id.circlesHints);        
        
        int page = 0;
        if (savedInstanceState == null) {
            fdbkIdx = 0;
            vpPreview.setCurrentItem(0);
            initialPageScroll = true;
            bookmark = getIntent().getIntExtra(TAG_ID, 0);
        } else {
            fdbkIdx = savedInstanceState.getInt(FDBK_IDX_KEY, NO_FEEDBACK);
            fdbkShown = savedInstanceState.getBoolean(FDBK_SHOWN_KEY);
            hintShown = savedInstanceState.getBoolean(HINT_SHOWN_KEY);
            page = savedInstanceState.getInt(PAGE_KEY);
            vpPreview.setCurrentItem(page);
        }
        showing = questions[page].hasScan() ? ATMPT :
            (questions[page].canSeeSolution(type) ? SOLN : QSN);
        
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        balance = prefs.getInt(BALANCE_KEY, 0);
        
        setTitle(getIntent().getStringExtra(NAME_KEY));
        loadHints(quizDir);
        adjustView(page, fdbkIdx);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArray(TAG, adapter.getQuestions());
        outState.putString(QUIZ_PATH_KEY, quizDir.getPath());
        outState.putInt(PAGE_KEY, vpPreview.getCurrentItem());
        outState.putInt(FDBK_IDX_KEY, fdbkIdx);
        outState.putBoolean(FDBK_SHOWN_KEY, fdbkShown);
        outState.putBoolean(FDBK_SHOWN_KEY, hintShown);
        super.onSaveInstanceState(outState);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            Intent intent = new Intent();
            intent.putExtra(TAG, adapter.getQuestions());
            intent.putExtra(TAG_ID, vpPreview.getCurrentItem());
            this.setResult(RESULT_OK, intent);
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG, adapter.getQuestions());
        intent.putExtra(TAG_ID, vpPreview.getCurrentItem());
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Question q = data.getParcelableExtra(TAG);
                Question question = adapter.getQuestions()[vpPreview.getCurrentItem()];
                question.setPgMap(q.getPgMap());                
                
                if (!question.getPgMap("").contains("0")) {
                    question.setState(CAPTURED);
                    stateMap.put(question.getId(), question.toString());                    
                } else {
                    question.setState(DOWNLOADED);
                    stateMap.remove(question.getId());
                }
                commit();
                
                showing = ATMPT;
                adapter.update(vpPreview.getCurrentItem());
                adjustView(vpPreview.getCurrentItem(), 0);
                
                if (question.getState() == CAPTURED)
                    triggerUploads(adapter.getQuestions());
            }
        }
    }
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        int currentIndex = vpPreview.getCurrentItem();
        Question qsn = adapter.getQuestions()[currentIndex];        
        if (requestCode == PURCHASE_TASK_REQUEST_CODE) {
            peedee.dismiss();
            if (resultCode == RESULT_OK) {
                try {
                    JSONParser jsonParser = new JSONParser();
                    JSONObject respObject = (JSONObject)jsonParser.parse(resultData);
                    String op = (String)respObject.get(OP_KEY);
                    balance = ((Long)respObject.get(BALANCE_KEY)).intValue();
                    
                    SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
                    Editor edit = prefs.edit();
                    edit.putInt(BALANCE_KEY, balance);
                    edit.commit();
                    
                    if (op.equals(BUY_ANS)) {
                        qsn.setBotAns(true);
                        adjustView(currentIndex, fdbkIdx);
                        dlCode = DL_ANS;
                        triggerDownloads(qsn);
                    } else if (op.equals(BUY_SOLN)) {
                        qsn.setBotSoln(true);
                        adjustView(currentIndex, fdbkIdx);
                        dlCode = DL_SOLN;
                        triggerDownloads(qsn);
                    } else { // op == GUESS
                        qsn.setGuess(guess);
                        adjustView(currentIndex, fdbkIdx);
                        displayAnswers(qsn, true);
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
                    displayAnswers(qsn, true);
                    break;
                case DL_ANS:
                    displayAnswers(qsn, false);
                    break;
                case DL_SOLN:
                    showing = SOLN;
                    adapter.update(currentIndex);
                    adjustView(currentIndex, fdbkIdx);
                    break;
                default:
                }
            }
        }
    }
    
    @Override
    public void onPageScrollStateChanged(int arg0) { }
    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) { 
        if (initialPageScroll && adapter.getCount() > bookmark) {
            vpPreview.setCurrentItem(bookmark, true);
            initialPageScroll = false;
        }
    }
    @Override
    public void onPageSelected(int position) {
        if (pageSwipe) {            
            pageSwipe = false;
            fdbkIdx = 0;
            hide = true; 
            showHide(null);
            Question qsn = adapter.getQuestions()[position];
            showing = qsn.hasScan() ? ATMPT :
                (qsn.canSeeSolution(type) ? SOLN : QSN);
            adjustView(vpPreview.getCurrentItem(), fdbkIdx);
        } else {
            fdbkIdx = position;
            Feedback fdbk = feedback[vpPreview.getCurrentItem()];
            adapter.shift(fdbk.page[position], position, 
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
    
    public void purchase(View view) {
        final int currentIndex = vpPreview.getCurrentItem();
        final Question qsn = adapter.getQuestions()[currentIndex];
        
        int price = 0;
        if (view.getId() == R.id.btnBuyAns) {
            if (qsn.botAnswer() || qsn.botSolution()) {
                dlCode = DL_ANS;
                triggerDownloads(qsn);
                return;
            }
            price = 5;
        } else {
            if (qsn.canSeeSolution(type)) {
                if (showing != SOLN) {
                    showing = SOLN;
                    adapter.update(currentIndex);
                    adjustView(currentIndex, fdbkIdx);
                }                    
                return;
            }
            price = 20;
        }
        
        final boolean answer = view.getId() == R.id.btnBuyAns;
        AlertDialog.Builder builder = new AlertDialog.Builder(this,
            R.style.RobotoDialogTitleStyle);
        builder.setMessage(
            String.format("Current Balance %3d ₲\nAfter Purchase   %3d ₲", 
                balance , (balance - price)));
        builder.setPositiveButton(R.string.purchase_conf_text,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    recordAction(qsn, answer ? BUY_ANS : BUY_SOLN);
                }
            });
        builder.setNegativeButton(R.string.purchase_cancel_text,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
        builder.show().getWindow().setBackgroundDrawable(
            new ColorDrawable(Color.TRANSPARENT));
    }
        
    public void selfCheck(View v) {
        int currentIndex = vpPreview.getCurrentItem();
        Question question = adapter.getQuestions()[currentIndex];
        displayAnswers(question, true);
    }
        
    public void showHide(View view) {
        hide = !hide;
        int visibility = hide ? View.INVISIBLE : View.VISIBLE;
        findViewById(R.id.llBtnBar).setVisibility(visibility);
        findViewById(R.id.tvName).setVisibility(visibility);
        findViewById(R.id.tvMarks).setVisibility(visibility);
        
        if (fdbkShown) {
            vpFdbk.setVisibility(visibility);
            fdbkIndicator.setVisibility(visibility);
        } else if (hintShown) {
            vpHints.setVisibility(visibility);
            hintsIndicator.setVisibility(visibility);
        }
    }
    
    public void activateCamera(View view) {
        int posn = vpPreview.getCurrentItem();
        Question qsn = adapter.getQuestions()[posn];        
        if (qsn.getState() == DOWNLOADED) {
            launchCameraActivity(qsn);
        } else if (qsn.hasScan()) {
            if (showing == ATMPT) showing = QSN;
            else showing = ATMPT;
            adapter.update(posn);
            adjustView(posn, fdbkIdx);
        }
    }
    
    public String[] getPaths(Question question) {
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

    public Feedback getFeedback() {
        return feedback[vpPreview.getCurrentItem()];
    }
    
    private void adjustView(int position, int fdbkPosn) {
        Button btnSelfChk = (Button)this.findViewById(R.id.btnSelfChk);
        TextView btnBuyAns = (TextView)this.findViewById(R.id.btnBuyAns);
        TextView btnBuySoln = (TextView)this.findViewById(R.id.btnBuySoln);
        Button btnCamera = (Button)this.findViewById(R.id.btnCamera);
        
        TextView tvMarks = (TextView)findViewById(R.id.tvMarks);
        TextView tvName = (TextView)findViewById(R.id.tvName);
        
        unrenderHint();
        unrenderFeedback(position);
        
        Question[] questions = adapter.getQuestions();
        Question q = questions[position];
        
        tvName.setText(String.format("%s of %s", position+1, questions.length));
        tvMarks.setText("");

        // Self Check Button
        int icon = R.drawable.ic_action_mic;
        btnSelfChk.setEnabled(q.hasCodex());
        btnSelfChk.setText("Self Check");
        if (q.tried()) {
            icon = q.getGuess() == q.getVersion() ? 
                R.drawable.ic_action_accept : R.drawable.ic_action_cancel;
        }
        btnSelfChk.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        
        // Answer and Solution Buttons
        btnBuyAns.setText(R.string.buy_answer_text);
        btnBuySoln.setText(R.string.buy_soln_text);
        btnBuyAns.setEnabled(false);
        btnBuySoln.setEnabled(false);
        if (type.equals(QSN_TYPE)) {
            btnBuyAns.setEnabled(q.hasAnswer() && 
                (balance >= 5 || q.botAnswer()));
            if (q.botAnswer()) 
                btnBuyAns.setText(R.string.bot_answer_text);
            btnBuySoln.setEnabled(balance >= 20 || q.botSolution());
        } else {
            btnBuySoln.setEnabled(q.canSeeSolution(type));
        }
        if (q.canSeeSolution(type)) 
            btnBuySoln.setText(R.string.bot_soln_text);
        
        // Review (Camera) Button
        if (q.hasScan()) {            
            btnCamera.setText(showing != ATMPT ?
                "See Attempt" : "See Question");
            if (q.getState() == GRADED && showing == ATMPT) {
                renderFeedback(position, fdbkPosn);
                tvMarks.setText(getQuantFdbk(q));
            }
        } else {
            btnCamera.setText("Get Review");
        }
                
        btnBuyAns.refreshDrawableState();
        btnBuySoln.refreshDrawableState();
        btnSelfChk.refreshDrawableState();
        btnCamera.refreshDrawableState();
    }
    
    private String getQuantFdbk(Question q) {
        String quality = "";
        if (type.equals(GR_TYPE)) {
            quality = String.format("%2.1f/%1d", q.getMarks(), q.getOutOf());
        } else {
            switch((int)q.getMarks()) {
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
    
    private void renderFeedback(int position, int fdbkPosn) {
        if (feedback[position] == null) {
            File feedbackDir = new File(quizDir, FEEDBACK_DIR_NAME);
            feedback[position] = Feedback.load(feedbackDir,
                adapter.getQuestions()[position]);
        }
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
        adapter.shift(0, NO_FEEDBACK, position);
    }
    
    private void renderHint(int qsnPosn, int part) {
        if (hints[qsnPosn].getText(part) == null) return;
        
        hintsAdapter = new LatexAdapter(hints[qsnPosn].getText(part), this);
        
        vpHints.setOffscreenPageLimit(3);
        vpHints.setAdapter(hintsAdapter);
        vpHints.setVisibility(View.VISIBLE);
        hintShown = true;
        
        //Bind the circle indicator to the adapter
        hintsIndicator.setVisibility(View.VISIBLE);
        hintsIndicator.setViewPager(vpHints, 0);
    }
    
    private void unrenderHint() {
        if (hintShown) {
            hintShown = false;
            vpHints.setVisibility(View.INVISIBLE);
            hintsIndicator.setVisibility(View.INVISIBLE);
        }
    }
    
    /* disabled hints for now - Vers 4.5.0 */
    private void loadHints(File quizDir) {
        for (int i = 0; i < hints.length; i++)
            hints[i] = null;
//        File hintsDir = new File(quizDir, HINTS_DIR_NAME);
//        Question[] questions = adapter.getQuestions();
//        for (int i = 0; i < hints.length; i++)
//            hints[i] = Hint.load(hintsDir, questions[i]);
    }    
    
    private void launchCameraActivity(Question q) {
        File answersDir = new File(quizDir, ATTEMPTS_DIR_NAME);
        Intent takePictureIntent = new Intent(this.getApplicationContext(), 
            com.gradians.collect.CameraActivity.class);
        takePictureIntent.putExtra(TAG, q);
        takePictureIntent.putExtra(SCANS_KEY, answersDir.getPath());
        startActivityForResult(takePictureIntent,
            ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);                       
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
                        stateMap.put(q.getId(), q.toString());
                        commit();
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
    
    private void displayAnswers(final Question qsn, boolean check) {
        ArrayList<String> options = new ArrayList<String>();
        if (check) {
            if (qsn.tried()) {
                options.add("" + (char)((int)'A' + qsn.getGuess()));
            } else {
                options.add("A");
                options.add("B");
                options.add("C");
                options.add("D");
            }
        } else {
            options.add("" + (char)((int)'A' + qsn.getVersion()));
        }
        
        final String[] finalOptions = options.toArray(new String[options.size()]);
        ListAdapter optionsAdapter = new ArrayAdapter<String>(
            this, R.layout.layout_answer,
            android.R.id.text1, finalOptions) {
                
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                
                //User super class to create the View
                LayoutInflater inflater = LayoutInflater.from(getContext());
                if(convertView == null) {
                    convertView = inflater.inflate(R.layout.layout_answer, parent, false);
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
                
                int dpx = 0, dipx = 60, imgHeight = 0;
                float density = getApplicationContext().getResources().
                    getDisplayMetrics().density;
                dpx = Math.round((float)dipx * density);
                Bitmap bimg = BitmapFactory.decodeFile(image.getPath());
                imgHeight = Math.round(bimg.getHeight() * density);
                int padX = (dpx - imgHeight - 10)/2;
                if (padX > 0) iv.setPadding(0, padX, 0, padX);
                
                iv.setImageURI(Uri.fromFile(image));
                
                convertView.setBackgroundColor(Color.WHITE);
                return convertView;
            }
        };
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, 
        R.style.RobotoDialogTitleStyle);
        
        if (check) {
            if (qsn.tried()) {
                builder.setTitle("You Picked");
                builder.setAdapter(optionsAdapter, null);
            } else {
                builder.setTitle("Options");
                builder.setAdapter(optionsAdapter, new DialogInterface.OnClickListener() {
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
        builder.show().getWindow().setBackgroundDrawable(
            new ColorDrawable(Color.TRANSPARENT));
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
                image = new File(dlDir,
                    question.getVersion() + "." +  question.getId() + "." + (i+1));
                if (!image.exists()) {
                    src = Uri.parse(String.format(SOLN_URL, 
                        BANK_HOST_PORT, question.getImgLocn(), (i+1)));
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
                    "Sorry, no Internet connection", 
                    Toast.LENGTH_LONG).show();
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
                int currentIndex = vpPreview.getCurrentItem();
                adapter.update(currentIndex);
                adjustView(currentIndex, fdbkIdx);
            }
        }
    }

    private void recordAction(Question question, String op) {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        String studentId = prefs.getString(ID_KEY, null);
        Uri src = null;
        if (type.equals(QSN_TYPE)) {
            src = Uri.parse(String.format(BILL_STAB_URL, WEB_APP_HOST_PORT, 
                studentId, question.getQsnId(), question.getVersion(),
                op.equals(GUESS) ? guess : "-1", op));
        } else {
            src = Uri.parse(String.format(BILL_ATTEMPT_URL, WEB_APP_HOST_PORT, 
                question.getGRId(","), op.equals(GUESS) ? guess : "-1", op));
        }
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this, 
            PURCHASE_TASK_REQUEST_CODE).execute(download);
        
        peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
        peedee.setMessage(op.equals(GUESS) ? "Checking..." : "Purchasing...");
        peedee.setIndeterminate(true);
        peedee.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        peedee.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        peedee.show();        
    }
    
    private void commit() {
        File stateFile = getStateFile(quizDir);
        try {
            stateMap.store(new FileOutputStream(stateFile), null);
        } catch (Exception e) { }
    }
    
    private String[] getQuestion(Question question) {
        File questionsDir = new File(quizDir, QUESTIONS_DIR_NAME);
        String[] paths = new String[] 
            {new File(questionsDir, 
                question.getVersion() + "." + question.getId()).getPath()};
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
        String[] paths = new String[question.getImgSpan()];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = (new File(solutionsDir, 
                question.getVersion() + "." + question.getId() + "." + (i+1))).getPath();
        }
        return paths;
    }
    
    private File getStateFile(File quizDir) {
        File filesDir = new File(quizDir.getParentFile().getParentFile(), "files");
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
    private int bookmark;
    private boolean initialPageScroll = false;
    
    private int balance;
    private String type;
    private int dlCode, guess;
    private Feedback[] feedback;
    private Hint[] hints;
    private File quizDir;

    private Properties stateMap;
    private FlowViewPager vpPreview;
    private FlowAdapter adapter;
    
    private int fdbkIdx;
    private char showing;
    private boolean hide, fdbkShown, hintShown;
    private ViewPager vpFdbk, vpHints;
    private LatexAdapter fdbkAdapter, hintsAdapter;
    private CirclePageIndicator fdbkIndicator, hintsIndicator;
    private ProgressDialog peedee;
    
    private final int DL_CODEX = 1, DL_ANS = 2, DL_SOLN = 3;
    private final String OP_KEY = "op";
    private final String 
        GUESS = "guess", 
        GET_FDBK = "grade", 
        BUY_ANS = "answer", 
        BUY_SOLN = "solution";    
    private final String 
        BILL_STAB_URL = "http://%s/tokens/record?s=%s&q=%s&v=%s&g=%s&op=%s",
        BILL_ATTEMPT_URL = "http://%s/tokens/record?id=%s&g=%s&op=%s",
        ANSR_URL = "http://%s/vault/%s/%s/codex.png",
        SOLN_URL = "http://%s/vault/%s/pg-%d.jpg";
    private final String FDBK_SHOWN_KEY = "fdbkShown",
        HINT_SHOWN_KEY = "hintShown", FDBK_IDX_KEY = "fdbkIdx", 
        PAGE_KEY = "page";
    private final char ATMPT = 'a', SOLN = 's', QSN = 'q';    
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
