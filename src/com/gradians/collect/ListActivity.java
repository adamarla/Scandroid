package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
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
        
        Parcelable[] quizItems = getIntent().getParcelableArrayExtra(TAG);
        String path = getIntent().getStringExtra(NAME_KEY);

        initialize(quizItems, path, getIntent().getIntExtra(ID_KEY, 0));
        setTitle(getIntent().getStringExtra(TAG_ID));
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG_ID, adapter.getDirtys());
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FLOW_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    Quij quiz = (Quij)adapter.getItem(selectedQuizPosition);
                    String key = categoryId + "." + quiz.getType() + "." + quiz.getId();
                    int bookmark = data.getIntExtra(TAG_ID, 0);
                    if (bookmark != 0) markers.put(key, String.valueOf(bookmark));
                    commit();
                    
                    Parcelable[] parcels = data.getParcelableArrayExtra(TAG);
                    Question[] questions = new Question[parcels.length];
                    for (int i = 0; i < parcels.length; i++)
                        questions[i] = (Question)parcels[i];
                    adapter.update(selectedQuizPosition, questions);
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
        if (requestCode == DOWNLOAD_MONITOR_TASK_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Quij quiz = (Quij)adapter.getItem(selectedQuizPosition);
                launchFlowActivity(quiz);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {        
        Quij quiz = (Quij)adapter.getItem(position);
        selectedQuizPosition = position;

        DownloadMonitor dlm = new DownloadMonitor(this);
        HttpCallsAsyncTask hcat = new HttpCallsAsyncTask(null, 0);
        setUpDownloads(dlm, hcat, quiz);
        if (dlm.getCount() > 0) {
            if (dlm.isNetworkAvailable()) {
                dlm.start("Synchronizing Files", "Please wait...", this);
                hcat.start();
            } else {
                Toast.makeText(getApplicationContext(), 
                    "Sorry, no Internet connection", 
                    Toast.LENGTH_LONG).show();
            }
        } else {
            launchFlowActivity(quiz);
        }
    }
    
    private void launchFlowActivity(Quij quiz) {
        Question[] questions = quiz.getQuestions();
        Intent flowIntent = new Intent(this.getApplicationContext(), 
            com.gradians.collect.FlowActivity.class);   
        flowIntent.putExtra(TAG, questions);
        flowIntent.putExtra(QUIZ_PATH_KEY, quizDir.getPath());
        flowIntent.putExtra(NAME_KEY, quiz.getName());
        flowIntent.putExtra(ID_KEY, quiz.getType());
        String key = categoryId + "." + quiz.getType() + "." + quiz.getId();
        if (markers.containsKey(key))
            flowIntent.putExtra(TAG_ID, Integer.valueOf(markers.getProperty(key)));
        startActivityForResult(flowIntent, FLOW_ACTIVITY_REQUEST_CODE);
    }

    private void setUpDownloads(DownloadMonitor dlm, HttpCallsAsyncTask hcat, Quij quiz) {
        File problemsDir = new File(studentDir, "problems");        
        String dirName = String.valueOf(quiz.getId());
        quizDir = new File(problemsDir, dirName);
        quizDir.mkdir();
        
        File[] dirs = createDirs(quizDir, quiz);
        File questionsDir = dirs[0];
        File attemptsDir = dirs[1];
        File solutionsDir = dirs[2];
        File feedbackDir = dirs[3];
        File hintsDir = dirs[4];
        File answersDir = dirs[5];
        
        Question[] questions = quiz.getQuestions();
        for (Question question : questions) {
            
            Uri src, dest;
            File image = null;
            String imgLocn = question.getImgLocn();
            String[] scans = question.getScanLocn();
            int[] pageNos = question.getPgMap();
            long hintMarker, fdbkMarker;
            String marker = (String)markers.get(question.getId());
            hintMarker = marker == null ? 0 : Long.parseLong(marker.split(",")[0]);
            fdbkMarker = marker == null ? 0 : Long.parseLong(marker.split(",")[1]);
            
            if (question.getState() == GRADED) {
                File fdbk = new File(feedbackDir, question.getId());
                if (fdbkMarker < question.getFdbkMarker()) {
                    fdbk.delete();
                    fdbkMarker = question.getFdbkMarker();
                }
                if (!fdbk.exists()) {
                    String grId = question.getGRId("-");
                    src = Uri.parse(String.format(FDBK_URL, 
                        WEB_APP_HOST_PORT, grId, quiz.getType()));
                    dest = Uri.fromFile(fdbk);
                    hcat.add(null, src, dest);
                }
            }
            
            if (question.getState() > SENT) {
                for (int i = 0; i < pageNos.length; i++) {
                    image = new File(attemptsDir, 
                        question.getId() + "." + pageNos[i]);
                    if (!image.exists()) {
                        src = Uri.parse(String.format(ATMPT_URL, 
                            BANK_HOST_PORT, scans[i]));
                        dest = Uri.fromFile(image);
                        dlm.add(question.getId(), src, dest);
                    }
                }
            }
            
            if (question.canSeeSolution(quiz.getType())) {
                for (short i = 0; i < question.getImgSpan(); i++) {
                    image = new File(solutionsDir, 
                        question.getVersion() + "." + question.getId() + "." + (i+1));
                    if (question.isDirty()) image.delete();
                    if (!image.exists()) {
                        src = Uri.parse(String.format(SOLN_URL, 
                            BANK_HOST_PORT, imgLocn, (i+1)));
                        dest = Uri.fromFile(image);
                        dlm.add(question.getId(), src, dest);
                    }
                }
            } else {
                image = new File(questionsDir, 
                    question.getVersion() + "." + question.getId());
                if (question.isDirty()) image.delete();
                if (!image.exists()) {
                    src = Uri.parse(String.format(QUES_URL, BANK_HOST_PORT, 
                        imgLocn));
                    dest = Uri.fromFile(image);
                    dlm.add(question.getId(), src, dest);
                }
                /* Removing hints for now
                File hint = new File(hintsDir, question.getId());
                if (hintMarker < question.getHintMarker()) {
                    hint.delete();
                    hintMarker = question.getHintMarker();
                }
                if (question.isDirty()) hint.delete();
                if (!hint.exists()) {
                    src = Uri.parse(String.format(HINT_URL, WEB_APP_HOST_PORT, 
                        question.getQsnId()));
                    dest = Uri.fromFile(hint);
                    hcat.add(null, src, dest);
                } */               
            }
            
            if (question.hasCodex()) {
                int[] codices = { 0, 1, 2, 3 };
                answersDir = new File(quizDir, ANSWERS_DIR_NAME);
                for (int codex : codices) {
                    image = new File(answersDir, 
                        codex + "." + question.getId());
                    if (question.isDirty()) image.delete();
                    if (!image.exists()) {
                        src = Uri.parse(String.format(ANSR_URL, BANK_HOST_PORT, 
                            imgLocn.replaceFirst("/[0-3]$", ""), codex));
                        dest = Uri.fromFile(image);
                        dlm.add(question.getId(), src, dest);
                    }
                }                    
            }
            
            if (question.getState() == SENT || question.getState() == CAPTURED) {
                for (int i = 0; i < pageNos.length; i++) {
                    if (pageNos[i] != 0) {
                        image = new File(attemptsDir, 
                            question.getId() + "." + pageNos[i]);
                        if (!image.exists()) { // recapture
                            pageNos[i] = 0;
                            question.setState(DOWNLOADED);
                        }
                    }
                }
                question.setPgMap(pageNos);
            }            
            markers.put(question.getId(), hintMarker + "," + fdbkMarker);
            question.setDirty(false);
        }
        commit();
    }

    private void initialize(Parcelable[] quizItems, String path, int categoryId) {
        studentDir = new File(path);
        
        this.categoryId = categoryId;
        markers = new Properties();
        File filesDir = new File(studentDir, "files");
        File markerFile = new File(filesDir, "markers.txt");
        try {
            markerFile.createNewFile();
            markers.load(new FileInputStream(markerFile));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        
        quizzes = new Quij[quizItems.length];
        for (int i = 0; i < quizItems.length; i++)
            quizzes[i] = (Quij)quizItems[i];
        
        adapter = new QuizListAdapter(this, quizzes);
        ListView lv = (ListView) this.findViewById(R.id.lvQuiz);
        lv.setAdapter(adapter);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lv.setOnItemClickListener(this);        
    }
    
    private void commit() {
        File filesDir = new File(studentDir, "files");
        File markerFile = new File(filesDir, "markers.txt");
        try {
            markers.store(new FileOutputStream(markerFile), null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }        
    }
    
    private File[] createDirs(File quizDir, Quij quiz) {
        String[] names = new String[] {
            QUESTIONS_DIR_NAME, ATTEMPTS_DIR_NAME, SOLUTIONS_DIR_NAME,
            FEEDBACK_DIR_NAME, HINTS_DIR_NAME, ANSWERS_DIR_NAME, 
            UPLOAD_DIR_NAME 
        };
        File[] dirs = new File[names.length];
        for (int i = 0; i < names.length; i++) {
            dirs[i] = new File(quizDir, names[i]);
            dirs[i].mkdir();
        }
        return dirs;
    }

    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }

    private int categoryId;
    private Properties markers;
    private Quij[] quizzes;
    private QuizListAdapter adapter;
    
    private int selectedQuizPosition;
    private File studentDir, quizDir;
    ProgressDialog peedee;

    private final String
        SOLN_URL = "http://%s/vault/%s/pg-%d.jpg",
        ATMPT_URL = "http://%s/locker/%s",
        QUES_URL = "http://%s/vault/%s/notrim.jpg",
        FDBK_URL = "http://%s/tokens/view_fdb.json?id=%s&type=%s",
        HINT_URL = "http://%s/tokens/view_hints.json?id=%s",
        ANSR_URL = "http://%s/vault/%s/%s/codex.png";

}
