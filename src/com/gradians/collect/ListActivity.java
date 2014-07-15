package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Properties;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class ListActivity extends Activity implements OnItemClickListener,
        ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        checkAuth();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.list, menu);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (manifest != null) {
            try {
                manifest.commit();
            } catch (Exception e) {
                handleError("Oops.. persist file thing failed", e.getMessage());
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sign_out)
            initiateAuthActivity();
        else if (item.getItemId() == R.id.action_refresh)
            checkAuth();
        else if (item.getItemId() == R.id.action_help)
            launchHelpActivity();
        return super.onOptionsItemSelected(item);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    manifest = new QuizManifest(getApplicationContext(), data.getStringExtra(TAG));
                    setPreferences(manifest);
                    setManifest(manifest);
                } catch (Exception e) {
                    handleError("Oops, Auth activity request failed",
                            e.getMessage());
                }
            } else if (resultCode != Activity.RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(),
                        "Oops.. auth check failed. Please try again",
                        Toast.LENGTH_SHORT).show();
            } else {
                this.finish();
            }
        } else if (requestCode == FLOW_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    Parcelable[] parcels = data.getParcelableArrayExtra(TAG);
                    Question[] questions = new Question[parcels.length];
                    for (int i = 0; i < parcels.length; i++) {
                        questions[i] = (Question)parcels[i];
                    }
                    manifest.update(selectedQuizPosition, questions);
                } catch (Exception e) {
                    handleError("Oops, Flow activity request failed",
                            e.getMessage());
                }
            } else if (resultCode != Activity.RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(),
                        "Oops we had an error, you may have lost work :/",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == VERIFY_AUTH_TASK_RESULT_CODE) {
            if (peedee != null) peedee.dismiss();
            if (resultCode == RESULT_OK) {
                try {
                    manifest = new QuizManifest(getApplicationContext(), resultData);
                    setManifest(manifest);
                } catch (Exception e) {
                    handleError("Oops, Verify auth task failed", e.getMessage());
                }
            } else {
                initiateAuthActivity();
            }
        } else if (requestCode == DOWNLOAD_MONITOR_TASK_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                Quij quiz = (Quij) manifest.getItem(selectedQuizPosition);
                launchFlowActivity(quiz);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Quij quiz = (Quij) manifest.getItem(position);

        DownloadMonitor dlm = new DownloadMonitor(this);
        setUpDownloads(dlm, quiz);
        selectedQuizPosition = position;
        
        if (!dlm.start("Synchronizing Files", "Please wait...", this)) {
            launchFlowActivity(quiz);
        }
    }
    
    private void launchFlowActivity(Quij quiz) {
        Question[] questions = quiz.getQuestions();
        Intent flowIntent = new Intent(this.getApplicationContext(), 
            com.gradians.collect.FlowActivity.class);   
        flowIntent.putExtra(TAG_ID, questions);
        flowIntent.putExtra(TAG, this.studentDir.getPath());
        if (quiz.getState() == NOT_YET_BILLED)
            flowIntent.putExtra(QUIZ_PRICE_KEY, quiz.getPrice());
        startActivityForResult(flowIntent, FLOW_ACTIVITY_REQUEST_CODE);
    }
    
    private void launchHelpActivity() {
        Intent helpIntent = new Intent(this.getApplicationContext(),
            com.gradians.collect.HelpActivity.class);
        startActivity(helpIntent);
    }

    private void setManifest(QuizManifest manifest) {
        setTitle(String.format("Hi %s", manifest.getName()));
        ListView lv = (ListView) this.findViewById(R.id.lvQuiz);
        if (manifest.getCount() > 0) {
            lv.setAdapter(manifest);
            lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            lv.setOnItemClickListener(this);
        }
        mkdirs(manifest.getEmail().replace('@', '.'));
        recordFdbkMrkrs(manifest);
    }

    private void setUpDownloads(DownloadMonitor dlm, Quij quiz) {
        Question[] questions = quiz.getQuestions();
        for (Question question : questions) {
            Uri src, dest;
            String id = question.getId();
            String wsId = id.split("\\.")[0];
            String GRId = question.getGRId();
            File image = null, fdbk = null;            
            String imgLocn = question.getImgLocn();
            String[] scans = question.getScanLocn().split(",", -1);
            String map = question.getMap();
            String[] pageNos = map.split("-");
            switch (question.getState()) {
            case GRADED:
                fdbk = new File(feedbackDir, id);
                if (!fdbk.exists()) {
                    src = Uri.parse(String.format(FDBK_URL, WEB_APP_HOST_PORT, GRId));
                    dest = Uri.fromFile(new File(feedbackDir, id));
                    dlm.add(id, src, dest);
                }
            case RECEIVED:
                for (short i = 0; i < question.getImgSpan(); i++) {
                    image = new File(solutionsDir, id + "." + (i+1));
                    if (!image.exists()) {
                        src = Uri.parse(String.format(SOLN_URL, BANK_HOST_PORT, imgLocn, (i+1)));
                        dest = Uri.fromFile(image);
                        dlm.add(question.getId(), src, dest);
                    }
                }                
                for (int i = 0; i < pageNos.length; i++) {
                    if (!pageNos[i].equals("0")) {
                        image = new File(answersDir, wsId + "." + pageNos[i]);
                        if (!image.exists()) {
                            src = Uri.parse(String.format(ANSR_URL, BANK_HOST_PORT, scans[i]));
                            dest = Uri.fromFile(image);
                            dlm.add(question.getId(), src, dest);
                        }                        
                    }                    
                }
                break;
            case SENT:
            case CAPTURED:
            case DOWNLOADED:
            case WAITING:
                String newmap = "";                
                for (int i = 0; i < pageNos.length; i++) {
                    if (!pageNos[i].equals("0")) {
                        image = new File(answersDir, wsId + "." + pageNos[i]);
                        if (!image.exists()) {
                            newmap += "0";
                        } else {
                            newmap += pageNos[i];
                        }
                    } else {
                        newmap += "0";
                    }
                    if (i != pageNos.length-1) newmap += "-";
                }
                question.setMap(newmap);
                image = new File(questionsDir, question.getId());
                if (!image.exists()) {
                    src = Uri.parse(String.format(QUES_URL, BANK_HOST_PORT, imgLocn));
                    dest = Uri.fromFile(image);
                    dlm.add(question.getId(), src, dest);
                }
                if (question.getState() == WAITING)
                    question.setState(DOWNLOADED);
            }
        }
    }

    private void checkAuth() {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        String email = prefs.getString(EMAIL_KEY, null);
        String token = prefs.getString(TOKEN_KEY, null);
        if (token == null) {
            initiateAuthActivity();
        } else {
            String urlString = String.format(VERIFY_URL, WEB_APP_HOST_PORT, email, token);
            try {
                peedee = ProgressDialog.show(this, "Initializing", "Please wait...");
                peedee.setIndeterminate(true);
                peedee.setIcon(ProgressDialog.STYLE_SPINNER);
                URL[] urls = { new URL(urlString) };
                new HttpCallsAsyncTask(this, VERIFY_AUTH_TASK_RESULT_CODE).execute(urls);
            } catch (Exception e) {
                handleError("Auth Check Failed", e.getMessage());
            }
        }
    }

    private void initiateAuthActivity() {
        resetPreferences();
        Intent checkAuthIntent = new Intent(this, 
            com.gradians.collect.LoginActivity.class);
        checkAuthIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(checkAuthIntent, AUTH_ACTIVITY_REQUEST_CODE);
    }

    private void setPreferences(QuizManifest manifest) {
        SharedPreferences prefs =
                this.getSharedPreferences(TAG, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putString(TOKEN_KEY, manifest.getAuthToken());
        edit.putString(NAME_KEY, manifest.getName());
        edit.putString(EMAIL_KEY, manifest.getEmail());
        edit.commit();
    }

    private void resetPreferences() {
        SharedPreferences prefs =
                getSharedPreferences(TAG, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    private void mkdirs(String studentDirName) {
        (studentDir = new File(this.getExternalFilesDir(null), studentDirName)).mkdir();
        (questionsDir = new File(studentDir, QUESTIONS_DIR_NAME)).mkdir();
        (answersDir = new File(studentDir, ANSWERS_DIR_NAME)).mkdir();
        (solutionsDir = new File(studentDir, SOLUTIONS_DIR_NAME)).mkdir();
        (feedbackDir = new File(studentDir, FEEDBACK_DIR_NAME)).mkdir();
        (new File(studentDir, UPLOAD_DIR_NAME)).mkdir();
    }
    
    private void recordFdbkMrkrs(QuizManifest manifest) {
        Properties fdbkMrkrs = new Properties();
        try {
            File fdbkMrkrsFile = new File(feedbackDir, "mrkrs");
            fdbkMrkrsFile.createNewFile(); // creates only if needed
            fdbkMrkrs.load(new FileInputStream(fdbkMrkrsFile));            
            for (int i = 0; i < manifest.getCount(); i++) {
                Quij quiz = (Quij)manifest.getItem(i);
                String quizId = String.valueOf(quiz.getId());
                long lastFdbkMrkr = fdbkMrkrs.getProperty(quizId) == null ?
                        0 : Long.parseLong(fdbkMrkrs.getProperty(quizId));
                if (lastFdbkMrkr < quiz.getFdbkMrkr()) {
                    File[] fdbkFiles = feedbackDir.listFiles();
                    for (File f : fdbkFiles) {
                        if (f.getName().contains(quizId)) f.delete();
                    }
                    fdbkMrkrs.setProperty(quizId, String.valueOf(quiz.getFdbkMrkr()));
                    fdbkMrkrs.store(new FileOutputStream(fdbkMrkrsFile), null);
                }
            }            
        } catch (Exception e) { 
            handleError("Error during fdbkChk", e.getMessage());
        }        
    }

    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }

    private int          selectedQuizPosition;
    private QuizManifest manifest;
    private File         studentDir, questionsDir, answersDir, solutionsDir, feedbackDir;
    ProgressDialog       peedee;

    private final String 
        VERIFY_URL = "http://%s/tokens/verify?email=%s&token=%s",
        SOLN_URL = "http://%s/vault/%s/pg-%d.jpg",
        ANSR_URL = "http://%s/locker/%s",
        QUES_URL = "http://%s/vault/%s/notrim.jpg",
        FDBK_URL = "http://%s/tokens/view_fdb.json?id=%s";

}
