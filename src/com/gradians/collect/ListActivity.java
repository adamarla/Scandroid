package com.gradians.collect;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
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
        
        Parcelable[] quizItems;
        Parcelable[] qstnItems;
        String path;
        if (savedInstanceState != null) {
            quizItems = savedInstanceState.getParcelableArray(TAG);
            qstnItems = savedInstanceState.getParcelableArray(TAG_ID);
            path = savedInstanceState.getString(NAME_KEY);
        } else {
            quizItems = getIntent().getParcelableArrayExtra(TAG);
            qstnItems = getIntent().getParcelableArrayExtra(TAG_ID);
            path = getIntent().getStringExtra(NAME_KEY);
        }

        initialize(quizItems, qstnItems, path);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArray(TAG, quizzes);
        ArrayList<Question> questions = new ArrayList<Question>();
        for (Quij quiz : quizzes) {
            questions.addAll(quiz);
        }
        outState.putParcelableArray(TAG_ID, 
            questions.toArray(new Question[questions.size()]));
        outState.putString(NAME_KEY, studentDir.getPath());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        ArrayList<Question> questions = new ArrayList<Question>();
        for (Quij quiz : quizzes) {
            questions.addAll(quiz);
        }
        Intent intent = new Intent();
        intent.putExtra(TAG_ID, questions.toArray(new Question[questions.size()]));
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FLOW_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    Parcelable[] parcels = data.getParcelableArrayExtra(TAG);
                    Question[] questions = new Question[parcels.length];
                    for (int i = 0; i < parcels.length; i++) {
                        questions[i] = (Question)parcels[i];
                    }
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
        if (requestCode == DOWNLOAD_MONITOR_TASK_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                Quij quiz = (Quij)adapter.getItem(selectedQuizPosition);
                launchFlowActivity(quiz, true);
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
            } else {
                Toast.makeText(getApplicationContext(), 
                    "Sorry, no Internet connection", 
                    Toast.LENGTH_LONG).show();
            }
        } else {
            launchFlowActivity(quiz, false);
        }        
    }
    
    private void setUpDownloads(DownloadMonitor dlm, HttpCallsAsyncTask hcat, Quij quiz) {
        String wsId = String.valueOf(quiz.getId());
        
        File quizDir = new File(studentDir, wsId); quizDir.mkdir();
        String[] dirs = new String[] {
            QUESTIONS_DIR_NAME, ANSWERS_DIR_NAME, SOLUTIONS_DIR_NAME,
            FEEDBACK_DIR_NAME, UPLOAD_DIR_NAME, HINTS_DIR_NAME};
        for (String dir : dirs)
            (new File(quizDir, dir)).mkdir();
        
        File questionsDir = new File(quizDir, QUESTIONS_DIR_NAME);
        File answersDir = new File(quizDir, ANSWERS_DIR_NAME);
        File solutionsDir = new File(quizDir, SOLUTIONS_DIR_NAME);
        File feedbackDir = new File(quizDir, FEEDBACK_DIR_NAME);
        File hintsDir = new File(quizDir, HINTS_DIR_NAME);
        
        Properties markers = new Properties();
        File markerFile = new File(studentDir, "markers");
        try {
            markerFile.createNewFile();
            markers.load(new FileInputStream(markerFile));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        ArrayList<Download> jsonReqs = new ArrayList<Download>();
        Question[] questions = quiz.getQuestions();
        for (Question question : questions) {
            Uri src, dest;
            File image = null;
            String imgLocn = question.getImgLocn();
            String[] scans = question.getScanLocn();
            int[] pageNos = question.getPgMap();
            boolean[] sentState = question.getSentState();
            
            long hintMarker, fdbkMarker;
            String marker = (String)markers.get(question.getId());
            hintMarker = marker == null ? 0 : Long.parseLong(marker.split(",")[0]);
            fdbkMarker = marker == null ? 0 : Long.parseLong(marker.split(",")[1]);
            
            switch (question.getState()) {
            case GRADED:
                File fdbk = new File(feedbackDir, question.getId());
                if (fdbkMarker < question.getFdbkMarker()) {
                    fdbk.delete();
                    fdbkMarker = question.getFdbkMarker();
                }
                if (!fdbk.exists()) {
                    String grId = question.getGRId("-");
                    src = Uri.parse(String.format(FDBK_URL, WEB_APP_HOST_PORT, grId));
                    dest = Uri.fromFile(fdbk);
                    jsonReqs.add(new Download(null, src, dest));
                }
            case RECEIVED:
                for (short i = 0; i < question.getImgSpan(); i++) {
                    image = new File(solutionsDir, question.getId() + "." + (i+1));
                    if (!image.exists()) {
                        src = Uri.parse(String.format(SOLN_URL, BANK_HOST_PORT, imgLocn, (i+1)));
                        dest = Uri.fromFile(image);
                        dlm.add(question.getId(), src, dest);
                    }
                }                
                for (int i = 0; i < pageNos.length; i++) {
                    if (pageNos[i] != 0) {
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
                for (int i = 0; i < scans.length; i++) {
                    if (scans[i].equals("") && question.getExaminer() != 0) {
                        pageNos[i] = 0; // re-send
                        sentState[i] = false;
                        question.setState(DOWNLOADED);
                    }
                }
                question.setSentState(sentState);
            case CAPTURED:
                for (int i = 0; i < pageNos.length; i++) {
                    if (pageNos[i] != 0) {
                        image = new File(answersDir, wsId + "." + pageNos[i]);
                        if (!image.exists()) { // recapture
                            pageNos[i] = 0;
                            question.setState(DOWNLOADED);
                        }
                    }
                }
                question.setPgMap(pageNos);
            case DOWNLOADED:
            case WAITING:
                image = new File(questionsDir, question.getId());
                if (!image.exists()) {
                    src = Uri.parse(String.format(QUES_URL, BANK_HOST_PORT, imgLocn));
                    dest = Uri.fromFile(image);
                    dlm.add(question.getId(), src, dest);
                }
                if (question.getState() == WAITING)
                    question.setState(DOWNLOADED);
                
                File hint = new File(hintsDir, question.getId());
                if (hintMarker < question.getHintMarker()) {
                    hint.delete();
                    hintMarker = question.getHintMarker();                    
                }                
                if (!hint.exists()) {
                    src = Uri.parse(String.format(HINT_URL, WEB_APP_HOST_PORT, question.getQsnId()));
                    dest = Uri.fromFile(hint);
                    jsonReqs.add(new Download(null, src, dest));
                }
            }
            markers.put(question.getId(), hintMarker + "," + fdbkMarker);
        }
        
        try {
            markers.store(new FileOutputStream(markerFile), null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        
        if (jsonReqs.size() > 0) {
            hcat.execute(jsonReqs.toArray(new Download[jsonReqs.size()]));
        }
    }

    private void launchFlowActivity(Quij quiz, boolean firstTime) {
        File quizDir = new File(studentDir, String.valueOf(quiz.getId()));
        Question[] questions = quiz.getQuestions();
        Intent flowIntent = new Intent(this.getApplicationContext(), 
            com.gradians.collect.FlowActivity.class);   
        flowIntent.putExtra(TAG_ID, questions);
        flowIntent.putExtra(TAG, quizDir.getPath());
        flowIntent.putExtra(NAME_KEY, quiz.getName());
        flowIntent.putExtra(STATE_KEY, firstTime);
        if (quiz.getState() == NOT_YET_BILLED)
            flowIntent.putExtra(QUIZ_PRICE_KEY, quiz.getPrice());
        startActivityForResult(flowIntent, FLOW_ACTIVITY_REQUEST_CODE);
    }

    private void initialize(Parcelable[] quizItems, Parcelable[] qstnItems, String path) {
        studentDir = new File(path);
        
        quizzes = new Quij[quizItems.length];
        for (int i = 0; i < quizItems.length; i++)
            quizzes[i] = (Quij)quizItems[i];
        
        String quizId = ((Question)qstnItems[0]).getId().split("\\.")[0];
        int quizIdx = 0;
        Question q;
        for (int i = 0; i < qstnItems.length; i++) {
            q = (Question)qstnItems[i];
            if (!quizId.equals(q.getId().split("\\.")[0])) {
                quizId = q.getId().split("\\.")[0];
                quizIdx++;
            }
            quizzes[quizIdx].add(q);
        }
        
        String title;
        switch (quizzes[0].getState()) {
        case GRADED:
            title = "Graded";
            break;
        case NOT_YET_GRADED:
            title = "Outbox";
            break;
        default:
            title = "Inbox";
        }
        setTitle(title);
        
        adapter = new QuizListAdapter(this, quizzes);
        ListView lv = (ListView) this.findViewById(R.id.lvQuiz);
        lv.setAdapter(adapter);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lv.setOnItemClickListener(this);        
    }
    
    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }

    private Quij[] quizzes;
    private QuizListAdapter adapter;
    
    private int selectedQuizPosition;
    private File studentDir;
    ProgressDialog peedee;

    private final String 
        SOLN_URL = "http://%s/vault/%s/pg-%d.jpg",
        ANSR_URL = "http://%s/locker/%s",
        QUES_URL = "http://%s/vault/%s/notrim.jpg",
        FDBK_URL = "http://%s/tokens/view_fdb.json?id=%s",
        HINT_URL = "http://%s/tokens/view_hints?id=%s";

}
