package com.gradians.collect;

import java.io.File;
import java.util.HashSet;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.summary, menu);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (manifest != null) 
            try {
                requestIds.clear();
                manifest.commit();
                context.unregisterReceiver(downloadCompleteReceiver);
            } catch (IllegalArgumentException iae) { 
                // In case the receiver is not registered                
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
                    manifest = new Manifest(context, data.getStringExtra(TAG), this);
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
            Log.d(TAG, "Returning from FlowActivity --> ");
            if (resultCode == RESULT_OK) {
                try {
                    String[] name_state_ids = data.getStringArrayExtra(TAG);
                    for (String name_state_id : name_state_ids) {
                        char state; String id;
                        String[] tokens = name_state_id.split("-");
                        state = tokens[1].charAt(0);
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
            peedee.dismiss();
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
            String tag = (String)((TextView)view).getTag();
            launchFlowActivity(tag);
        }
    }
    
    private void launchFlowActivity(String tag) {
        Quiz quiz = (Quiz)manifest.getGroup(openGroupPosn);
        Question[] questions = quiz.getQuestions();
        String[] name_state_ids = new String[questions.length];
        for (int i = 0; i < questions.length; i++) {
            name_state_ids[i] = questions[i].getNameStateId();
        }
        Intent flowIntent = new Intent(context, com.gradians.collect.FlowActivity.class);        
        flowIntent.putExtra(TAG_ID, name_state_ids);
        flowIntent.putExtra(TAG, this.studentDir.getPath());
        startActivityForResult(flowIntent, FLOW_ACTIVITY_REQUEST_CODE);
    }
       
    private void triggerDownloads() {        
        boolean somethingToDownload = false;
        for (int j = 0; j < manifest.getGroupCount(); j++) {
            Quiz quiz = (Quiz)manifest.getGroup(j);
            Question[] questions = quiz.getQuestions();
            for (Question question : questions) {
                if (question != null) {
                    String id = question.getGRId();
                    if (!(new File(questionsDir, id)).exists()) {
                        downloadQuestion(question);
                        somethingToDownload = true;
                    } else if (question.getState() == WAITING) {
                        manifest.update(id, DOWNLOADED);
                    }
                }
            }
        }
        
        if (somethingToDownload) {
            peedee = ProgressDialog.show(this, 
                    "Initiating downloads", 
                    "Please wait, this may take a minute...");
            peedee.setIndeterminate(true);
            peedee.setIcon(ProgressDialog.STYLE_SPINNER);
        }
    }
    
    private void downloadQuestion(Question question) {
        DownloadManager.Request request;
        String url = String.format(URL, BANK_HOST_PORT, question.getImgLocn());
        request = new DownloadManager.Request(Uri.parse(url));
        request.setVisibleInDownloadsUi(false);
        request.setTitle(question.getName());
        request.setDestinationUri(Uri.fromFile(new File(questionsDir, question.getGRId())));
        long id = dm.enqueue(request);
        requestIds.add(String.valueOf(id));
    }
    
    private final BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
            if (!requestIds.contains(String.valueOf(id))) {
                return;
            }
            
            DownloadManager dm = (DownloadManager)
                    context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(id);
            Cursor cursor = dm.query(query);

            // it shouldn't be empty, but just in case
            if (!cursor.moveToFirst()) {
                return;
            }            
            
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int uri_index = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                requestIds.remove(String.valueOf(id));
                onDownloadComplete(Uri.parse(cursor.getString(uri_index)).getLastPathSegment());
            }
        }
    };
        
    private void onDownloadComplete(String id) {
        manifest.update(id, DOWNLOADED);
        if (peedee.isShowing() && requestIds.size() == 0) {
            peedee.dismiss();
        }
    }
    
    private void checkAuth() {
        context = getApplicationContext();
        SharedPreferences prefs = getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);
        
        String token = prefs.getString(TOKEN_KEY, null);        
        if (token == null) {
            initiateAuthActivity();
        } else {
            peedee = ProgressDialog.show(this, "Initializing ", 
                    "Please wait...");
            peedee.setIndeterminate(true);
            peedee.setIcon(ProgressDialog.STYLE_SPINNER);
            
            String email = prefs.getString(EMAIL_KEY, null);
            new VerificationTask(email, token, this).execute();
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
        studentDir = new File(this.getExternalFilesDir(null), manifest.getName());
        ExpandableListView elv = (ExpandableListView)this.findViewById(R.id.elvQuiz);
        if (manifest.getGroupCount() > 0) {
            elv.setAdapter(manifest);
            elv.setChoiceMode(ExpandableListView.CHOICE_MODE_SINGLE);
//            elv.expandGroup(0);
        }
        
        mkdirs(manifest.getEmail());
        dm = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        context.registerReceiver(downloadCompleteReceiver, downloadCompleteIntentFilter);
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
    }
    
    private void handleError(String msg, String err) {
        Log.d(TAG, msg + "\n" + err);
    }
    
    private File studentDir, questionsDir, answersDir, solutionsDir;
    
    private int openGroupPosn = -1;
    
    private DownloadManager dm;
    private Context context;
    private ProgressDialog peedee;
    private Manifest manifest;
    
    private final HashSet<String> requestIds = new HashSet<String>();
    private final IntentFilter downloadCompleteIntentFilter = 
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);       
    private final String URL = "http://%s/vault/%s/pg-1.jpg";
        public final int AUTH_ACTIVITY_REQUEST_CODE = 100;

}

