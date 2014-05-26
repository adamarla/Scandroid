package com.gradians.collect;

import java.io.File;
import java.net.URL;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

public class SummaryActivity extends Activity implements IConstants, ITaskResult, OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);
        checkAuth();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (manifest != null) 
            try {
                manifest.commit();
            } catch (Exception e) {
                handleError("Oops.. persist file thing failed", e.getMessage());
            }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_sign_out:
            initiateAuthActivity();
          break;
        default:
          break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    manifest = new Manifest(context, 
                            data.getStringExtra(TAG), this);
                    setPreferences(manifest);
                    setManifest(manifest);
                } catch (Exception e) {
                    handleError("Oops, Auth activity request failed", 
                            e.getMessage());
                }                
            } else if (resultCode != Activity.RESULT_CANCELED){
                Toast.makeText(getApplicationContext(),
                        "Oops.. auth check failed. Please try again",
                        Toast.LENGTH_SHORT).show();
            } else {
                this.finish();
            }
        } else if (requestCode == FLOW_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    String[] name_state_ids = data.getStringArrayExtra(TAG);
                    for (String name_state_id : name_state_ids) {
                        short state; String id;
                        String[] tokens = name_state_id.split("-");
                        state = Short.parseShort(tokens[1]);
                        id = tokens[2];
                        manifest.update(id, state);
                    }
                } catch (Exception e) {
                    handleError("Oops, Flow activity request failed", 
                            e.getMessage());
                }
            } else if (resultCode != Activity.RESULT_CANCELED){
                Toast.makeText(getApplicationContext(),
                        "Oops we had an error, you may have lost work :/",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == VERIFY_AUTH_TASK_RESULT_CODE) {
            if (peedee != null) peedee.dismiss();
            if (resultCode == RESULT_OK) {  
                try {
                    manifest = new Manifest(context, resultData, this);
                    setManifest(manifest);
                } catch (Exception e) { 
                    handleError("Oops, Verify auth task failed", e.getMessage());
                }
            } else {
                initiateAuthActivity();
            }
        }
    }    
    
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.tvQuiz) {
            ExpandableListView elv = (ExpandableListView)this.findViewById(R.id.elvQuiz);
            openGroupPosn = (Integer)view.getTag();
            if (elv.isGroupExpanded(openGroupPosn)) {
                elv.collapseGroup(openGroupPosn);
            } else {
                elv.expandGroup(openGroupPosn);
                int groups = manifest.getGroupCount();
                for (int i = 0; i < groups; i++) {
                    if (i != openGroupPosn) {
                        elv.collapseGroup(i);
                    }
                }
            }
        } else {
            launchFlowActivity();
        }
    }
    
    private void launchFlowActivity() {
        Quiz quiz = (Quiz)manifest.getGroup(openGroupPosn);
        Question[] questions = quiz.getQuestions();
        String[] name_state_ids = new String[questions.length];
        for (int i = 0; i < questions.length; i++) {
            name_state_ids[i] = questions[i].getNameStateId();
        }
        Intent flowIntent = new Intent(context, 
                com.gradians.collect.FlowActivity.class);        
        flowIntent.putExtra(TAG_ID, name_state_ids);
        flowIntent.putExtra(TAG, this.studentDir.getPath());
        startActivityForResult(flowIntent, FLOW_ACTIVITY_REQUEST_CODE);
    }
       
    private void triggerDownloads() {
        Log.d(TAG, "triggerDownloads() -->");
        DownloadMonitor dlm = new DownloadMonitor(this);        
        for (int j = 0; j < manifest.getGroupCount(); j++) {
            Quiz quiz = (Quiz)manifest.getGroup(j);
            Question[] questions = quiz.getQuestions();
            for (Question question : questions) {
                Uri src, dest;
                File image = null;
                if (question != null) {
                    switch (question.getState()) {
                    case GRADED:
                    case RECEIVED:
                        image = new File(solutionsDir, question.getGRId()); 
                        if (!image.exists()) {
                            src = Uri.parse(String.format(URL, BANK_HOST_PORT, "vault", 
                                    question.getImgLocn() + "/pg-1.jpg"));
                            dest = Uri.fromFile(image);
                            dlm.add(src, dest);
                        }
                    case SENT:
                        image = new File(answersDir, question.getGRId());
                        if (!image.exists()) {
                            src = Uri.parse(String.format(URL, BANK_HOST_PORT, "locker", 
                                    question.getScanLocn()));
                            dest = Uri.fromFile(image);
                            dlm.add(src, dest);
                        }
                        break;
                    case CAPTURED:
                        image = new File(answersDir, question.getGRId());
                        if (!image.exists()) {
                            question.setState(DOWNLOADED);
                        }
                    case DOWNLOADED:
                    case WAITING:
                        image = new File(questionsDir, question.getGRId());
                        if (!image.exists()) {
                            src = Uri.parse(String.format(URL, BANK_HOST_PORT, "vault", 
                                    question.getImgLocn() + "/notrim.jpg"));
                            dest = Uri.fromFile(image);
                            dlm.add(src, dest);
                        }
                        if (question.getState() == WAITING) {
                            question.setState(DOWNLOADED);
                        }
                    }
                }
            }
        }
        dlm.start("Synchronizing Files", "Please wait...");
    }
        
    private void checkAuth() {
        context = getApplicationContext();
        SharedPreferences prefs = getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);        
        String email = prefs.getString(EMAIL_KEY, null);
        String token = prefs.getString(TOKEN_KEY, null);        
        if (token == null) {
            initiateAuthActivity();
        } else {
            String urlString = String.format(
                    "http://%s/tokens/verify?email=%s&token=%s",
                    WEB_APP_HOST_PORT, email, token);
            try {
                peedee = ProgressDialog.show(this, "Initializing", 
                        "Please wait...");
                peedee.setIndeterminate(true);
                peedee.setIcon(ProgressDialog.STYLE_SPINNER);        
                URL[] urls = { new URL(urlString) };
                new HttpCallsAsyncTask(this, this, 
                        VERIFY_AUTH_TASK_RESULT_CODE).execute(urls);
            } catch (Exception e) {
                handleError("Auth Check Failed", e.getMessage());
            }
        }
    }

    private void initiateAuthActivity() {
        resetPreferences();
        Intent checkAuthIntent = new Intent(context, 
                com.gradians.collect.LoginActivity.class);
        checkAuthIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(checkAuthIntent, AUTH_ACTIVITY_REQUEST_CODE);
    }

    private void setManifest(Manifest manifest) {
        setTitle(String.format("Hi %s", manifest.getName()));
        ExpandableListView elv = (ExpandableListView)this.findViewById(R.id.elvQuiz);
        if (manifest.getGroupCount() > 0) {
            elv.setAdapter(manifest);
            elv.setChoiceMode(ExpandableListView.CHOICE_MODE_SINGLE);
        }
        
        mkdirs(manifest.getEmail());
        triggerDownloads();
    }

    private void setPreferences(Manifest manifest) {
        SharedPreferences prefs = this.getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putString(TOKEN_KEY, manifest.getAuthToken());
        edit.putString(NAME_KEY, manifest.getName());
        edit.putString(EMAIL_KEY, manifest.getEmail());
        edit.commit();       
    }
    
    private void resetPreferences() {
        SharedPreferences prefs = getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();        
    }

    private void mkdirs(String studentDirName) {
        (studentDir = new File(this.getExternalFilesDir(null), studentDirName)).mkdir();
        (questionsDir = new File(studentDir, QUESTIONS_DIR_NAME)).mkdir();
        (answersDir = new File(studentDir, ANSWERS_DIR_NAME)).mkdir();
        (solutionsDir = new File(studentDir, SOLUTIONS_DIR_NAME)).mkdir();
        (new File(studentDir, FEEDBACK_DIR_NAME)).mkdir();
    }
    
    private void handleError(String msg, String err) {
        Log.d(TAG, msg + " " + err);
    }
    
    private File studentDir, questionsDir, answersDir, solutionsDir;    
    private int openGroupPosn = -1;
    
    private Context context;
    private Manifest manifest;
    private ProgressDialog peedee;

    private final String URL = "http://%s/%s/%s";    
}

