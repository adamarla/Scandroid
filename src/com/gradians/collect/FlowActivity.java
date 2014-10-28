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
        
        int page = 0, fdbkPg = 0;
        if (savedInstanceState == null) {
            vpPreview.setCurrentItem(0);
            initialPageScroll = true;
            bookmark = getIntent().getIntExtra(TAG_ID, 0);
        } else {
            adapter.setFlipped(savedInstanceState.getBoolean(FLIPPED_KEY));
            fdbkPg = savedInstanceState.getInt(FDBK_IDX_KEY, NO_FEEDBACK);
            fdbkShown = savedInstanceState.getBoolean(FDBK_SHOWN_KEY);
            hintShown = savedInstanceState.getBoolean(HINT_SHOWN_KEY);
            page = savedInstanceState.getInt(PAGE_KEY);
            vpPreview.setCurrentItem(page);
        }
        
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        balance = prefs.getInt(BALANCE_KEY, 0);
        
        setTitle(getIntent().getStringExtra(NAME_KEY));
        loadHints(quizDir);
        adjustView(page, fdbkPg);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArray(TAG, adapter.getQuestions());
        outState.putString(QUIZ_PATH_KEY, quizDir.getPath());
        outState.putInt(PAGE_KEY, vpPreview.getCurrentItem());
        outState.putBoolean(FLIPPED_KEY, adapter.getFlipped());
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
                
                adapter.update(vpPreview.getCurrentItem());
                adjustView(vpPreview.getCurrentItem(), 0);                
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
                    balance = ((Long)respObject.get(BALANCE_KEY)).intValue();
                    
                    SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
                    Editor edit = prefs.edit();
                    edit.putInt(BALANCE_KEY, balance);
                    edit.commit();
                    
                    int currentIndex = vpPreview.getCurrentItem();
                    Question qsn = adapter.getQuestions()[currentIndex];
                    if (op.equals(BUY_ANS)) {
                        qsn.setBotAns(true);
                        dlCode = DL_ANS;
                        triggerDownloads(qsn);
                    } else if (op.equals(BUY_SOLN)) {
                        qsn.setBotSoln(true);
                        dlCode = DL_SOLN;
                        triggerDownloads(qsn);
                    } else { // op == GUESS
                        qsn.setGuess(guess);
                        displayOptions(qsn);
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
                    displayAnswers(true);
                    break;
                case DL_ANS:
                    displayAnswers(false);
                    break;
                case DL_SOLN:
                    int currentIndex = vpPreview.getCurrentItem();
                    adapter.update(currentIndex);
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
    
    public void upload(View view) {        
        // prompt for uploading only if something is there
        final Question[] questions = adapter.getQuestions();
        if (nothingToUpload(questions)) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Upload now?");
        builder.setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    triggerUploads(questions);                    
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
    
    public void onOptionsClick(View v) {
        int index = vpPreview.getCurrentItem();
        Question q = adapter.getQuestions()[index];
        if (q.getState() == CAPTURED) {
            activateCamera(q);
        } else {
            displayOptions(q);
        }
    }
    
    public String[] getPaths(Question question, boolean flipped) {
        String[] paths = null;        
        if (flipped) {
            if (question.canSeeSolution(type)) {
                paths = getSolution(question);
            } else {
                paths = getQuestion(question);
            }
        } else {
            if (question.hasScan()) {
                paths = getAttempt(question);
            } else if (question.botSolution()) {
                paths = getSolution(question);
            } else  {
                paths = getQuestion(question);
            }
        }        
        return paths;
    }
    
    public Feedback getFeedback() {
        return feedback[vpPreview.getCurrentItem()];
    }
    
    private void adjustView(int position, int fdbkPosn) {
        Button btnFlip = (Button)this.findViewById(R.id.btnFlip);
        Button btnHint = (Button)this.findViewById(R.id.btnHint);
        Button btnCamera = (Button)this.findViewById(R.id.btnCamera);
        Button btnUpload = (Button)this.findViewById(R.id.btnUpload);
        TextView tvMarks = (TextView)findViewById(R.id.tvMarks);
        TextView tvName = (TextView)findViewById(R.id.tvName);
        
        unrenderHint();
        unrenderFeedback(position);
        
        Question[] questions = adapter.getQuestions();        
        Question q = questions[position];
        
        tvName.setText(String.format("%s of %s", position+1, questions.length));
        tvMarks.setText("");
        btnFlip.setEnabled(q.hasScan());
        
        btnHint.setText("Hint");
        btnHint.setEnabled(false);
        
        btnCamera.setText("Options");
        
        btnUpload.setEnabled(false);
        
        if (!q.hasScan() && !q.botSolution()) {
            instructionShown = true;
            findViewById(R.id.llInstruction).setVisibility(View.VISIBLE);
            btnHint.setEnabled(hints[position] != null);
        } else {
            instructionShown = false;
            findViewById(R.id.llInstruction).setVisibility(View.INVISIBLE);
        }
        
        if (adapter.getFlipped()) {
            // Looking at printed text (question or solution)
            if (q.hasScan()) {
                btnFlip.setText("My Attempt");
            } else {
                if (q.botSolution())
                    btnFlip.setText("Solution");
                else {
                    btnFlip.setText("Question");
//                    btnCamera.setEnabled(true);
                }
            }
        } else {
            // Looking at hand written text (answer)
            if (instructionShown) { // so hide instructions
                if (q.hasScan() || q.botSolution()) {
                    instructionShown = false;
                    findViewById(R.id.llInstruction).setVisibility(View.INVISIBLE);
                    btnHint.setEnabled(false);
                }
            }
            
            if (q.canSeeSolution(type)) {
                btnFlip.setText("Solution");
            } else {
                btnFlip.setText("Question");
                btnUpload.setEnabled(q.getState() == CAPTURED);
//                btnCamera.setEnabled(true);
            }
            
            if (q.getState() == GRADED) {
                renderFeedback(position, fdbkPosn);
                tvMarks.setText(getQuantFdbk(q));
            }
        }
        
        if (q.getState() == CAPTURED) {
            btnCamera.setText("Re-Capture");
        }        

        btnFlip.refreshDrawableState();
        btnUpload.refreshDrawableState();
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
    
    private void activateCamera(final Question qsn) {
        final File answersDir = new File(quizDir, ATTEMPTS_DIR_NAME);
        
        if (qsn.getState() == CAPTURED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Discard?");
            builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {                   
                        int[] map = qsn.getPgMap();
                        for (int i = 0; i < map.length; i++) {
                            if (map[i] == 0) continue;
                            (new File(answersDir, qsn.getId() + "." + map[i])).delete();
                            map[i] = 0;
                        }
                        qsn.setPgMap(map);
                        qsn.setState(DOWNLOADED);
                        launchCameraActivity(qsn);                        
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
        } else {
            launchCameraActivity(qsn);
        }        
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
    
    private void displayOptions(final Question qsn) {
        class OptionItem {            
            public OptionItem(int textId, String price, int icon) {
                this.textId = textId;
                this.price = price;
                this.icon = icon;
            }            
            public String price;            
            public int textId, icon;
        }
        
        ArrayList<OptionItem> chooseFrom = new ArrayList<OptionItem>();
        // guess option
        int icon = android.R.drawable.ic_btn_speak_now;
        int textId = R.string.option_chk_answer;
        String price = "Free";
        if (qsn.hasCodex() && qsn.tried()) {
            price = "Used";
            icon = qsn.getVersion() == qsn.getGuess() ?
                R.drawable.ic_action_accept:
                R.drawable.ic_action_cancel;
            textId = qsn.getVersion() == qsn.getGuess() ?
                R.string.option_chk_right:
                R.string.option_chk_wrong;
        }
        chooseFrom.add(new OptionItem(textId, price, icon));
        // feedback option
        chooseFrom.add(new OptionItem(R.string.option_get_feedback, "Free",
            android.R.drawable.ic_menu_camera));
        // pay for answer option
        if (qsn.hasCodex() && qsn.botAnswer())
            chooseFrom.add(new OptionItem(R.string.option_show_answer, "Bought",
                android.R.drawable.ic_menu_directions));
        else
            chooseFrom.add(new OptionItem(R.string.option_show_answer, "5₲",
                android.R.drawable.ic_menu_directions));
        // pay for solution option
        if (qsn.botSolution())
            chooseFrom.add(new OptionItem(R.string.option_show_solution, "Bought",
                android.R.drawable.ic_menu_compass));
        else
            chooseFrom.add(new OptionItem(R.string.option_show_solution, "20₲",
                android.R.drawable.ic_menu_compass));

        final OptionItem[] options = chooseFrom.toArray(new OptionItem[chooseFrom.size()]);
        ListAdapter optionsAdapter = new ArrayAdapter<OptionItem>(
            this, R.layout.layout_quiz,
            android.R.id.text1, options) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                //User super class to create the View
                LayoutInflater inflater = LayoutInflater.from(getContext());
                if(convertView == null) {
                    convertView = inflater.inflate(R.layout.layout_quiz, parent, false);
                }
                
                TextView tv = (TextView)convertView.findViewById(R.id.tvQuiz);
                tv.setText(getItem(position).textId);
                if (!isEnabled(position)) {
                    tv.setTextColor(getResources().getColor(R.color.gray));
                } else {
                    tv.setTextColor(getResources().getColor(R.color.white));
                }                
                
                TextView tvTotal = (TextView)convertView.findViewById(R.id.tvTotal);
                tvTotal.setText(options[position].price);
                if (!isEnabled(position)) {
                    tvTotal.setBackground(getResources().getDrawable(
                        R.drawable.label_quiz_counts_disabled));
                } else {
                    tvTotal.setBackground(getResources().getDrawable(
                        R.drawable.label_quiz_counts));
                }

                //Put the image on the TextView
                tv.setCompoundDrawablesWithIntrinsicBounds(options[position].icon, 0, 0, 0);

                //Add margin between image and text (support various screen densities)
                int dp5 = (int) (5 * getResources().getDisplayMetrics().density + 0.5f);
                tv.setCompoundDrawablePadding(dp5);

                return convertView;
            }

            @Override
            public boolean isEnabled(int position) {
                boolean enabled = true;
                OptionItem item = getItem(position);                
                if (type.equals(QSN_TYPE)) {
                    if (item.textId == R.string.option_show_answer) {
                        enabled = qsn.hasAnswer() && (balance >= 5 || qsn.botAnswer());                        
                    } else if (item.textId == R.string.option_show_solution) 
                        enabled = balance >= 20 || qsn.botSolution();
                } else {
                    if (item.textId == R.string.option_show_answer ||
                        item.textId == R.string.option_show_solution)
                        enabled = false;
                }                
                if (item.textId == R.string.option_chk_answer)
                    enabled = qsn.hasCodex();
                if (item.textId == R.string.option_get_feedback)
                    enabled = qsn.getState() == DOWNLOADED;
                return enabled;
            }
            
        };
            
        AlertDialog.Builder builder = new AlertDialog.Builder(this, 
        R.style.RobotoDialogTitleStyle);
        builder.setTitle("Account Balance " + balance + "₲");
        builder.setAdapter(optionsAdapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (options[which].textId) {
                case R.string.option_chk_answer:
                case R.string.option_chk_right:
                case R.string.option_chk_wrong:
                    dlCode = DL_CODEX;
                    triggerDownloads(qsn);
                    break;
                case R.string.option_get_feedback:
                    launchCameraActivity(qsn);
                    break;
                case R.string.option_show_answer:
                    if (qsn.botAnswer()) {
                        dlCode = DL_ANS;
                        triggerDownloads(qsn);
                    } else {
                        recordAction(qsn, BUY_ANS);
                    }
                    break;
                case R.string.option_show_solution:
                    if (qsn.botSolution()) {
                        // do nothing I guess
                    } else {
                        recordAction(qsn, BUY_SOLN);
                    }
                    break;
                default:
                }
            }
        });
        builder.show().getWindow().setBackgroundDrawable(
            new ColorDrawable(Color.TRANSPARENT));
    }
    
    private void displayAnswers(boolean check) {
        final Question qsn = adapter.getQuestions()[vpPreview.getCurrentItem()];
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
                    image = new File(answersDir, qsn.getId() + "-" + version);
                    tv.setText(finalOptions[position]);
                } catch (Exception e) {
                    Log.e(TAG, e.getClass().toString());
                }
                
                int dpx = 0, dipx = 60;
                float density = getApplicationContext().getResources().
                    getDisplayMetrics().density;
                dpx = Math.round((float)dipx * density);
                Bitmap bimg = BitmapFactory.decodeFile(image.getPath());                
                int padX = (dpx - bimg.getHeight() - 10)/2;
                if (padX > 0) iv.setPadding(0, padX, 0, padX);
                
                iv.setImageURI(Uri.fromFile(image));
                
                convertView.setBackgroundColor(Color.WHITE);
                return convertView;
            }
        };
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, 
        R.style.RobotoDialogTitleStyle);
        
        if (check && !qsn.tried()) {
            builder.setTitle("Options");
            builder.setAdapter(optionsAdapter, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    guess = which;
                    recordAction(qsn, GUESS);
                }
            });
        } else {            
            builder.setTitle(check ? "You Picked" : "Correct Answer");
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
                image = new File(dlDir, question.getId() + "-" + codex);
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
                image = new File(dlDir, question.getId() + "." + (i+1));
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
                displayAnswers(true);
                break;
            case DL_ANS:
                displayAnswers(false);
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

    private String[] getQuestion(Question question) {
        File questionsDir = new File(quizDir, QUESTIONS_DIR_NAME);
        String[] paths = new String[] 
            {new File(questionsDir, question.getId()).getPath()};
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
            paths[i] = (new File(solutionsDir, question.getId() + "." + (i+1))).getPath();
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
    private boolean hide, fdbkShown, hintShown, instructionShown;
    private ViewPager vpFdbk, vpHints;
    private LatexAdapter fdbkAdapter, hintsAdapter;
    private CirclePageIndicator fdbkIndicator, hintsIndicator;
    private ProgressDialog peedee;
    
    private final int DL_CODEX = 1, DL_ANS = 2, DL_SOLN = 3;
    private final String OP_KEY = "op";
    private final String 
        BILL_STAB_URL = "http://%s/tokens/record?s=%s&q=%s&v=%s&g=%s&op=%s",
        BILL_ATTEMPT_URL = "http://%s/tokens/record?id=%s&g=%s&op=%s",
        ANSR_URL = "http://%s/vault/%s/%s/codex.png",
        SOLN_URL = "http://%s/vault/%s/pg-%d.jpg";

    private final String FLIPPED_KEY = "flipped", FDBK_SHOWN_KEY = "fdbkShown",
        HINT_SHOWN_KEY = "hintShown", FDBK_IDX_KEY = "fdbkIdx", PAGE_KEY = "page";
    
    private final String GUESS = "guess", GET_FDBK = "grade", BUY_ANS = "answer", 
        BUY_SOLN = "solution";
    
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
