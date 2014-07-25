package com.gradians.collect;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
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
                launchFlowActivity(quiz);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Quij quiz = (Quij)adapter.getItem(position);

        mkdirs(String.valueOf(quiz.getId()));
        recordFdbkMrkrs(quiz);
        
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
        flowIntent.putExtra(TAG, quizDir.getPath());
        flowIntent.putExtra(NAME_KEY, quiz.getName());
        if (quiz.getState() == NOT_YET_BILLED)
            flowIntent.putExtra(QUIZ_PRICE_KEY, quiz.getPrice());
        startActivityForResult(flowIntent, FLOW_ACTIVITY_REQUEST_CODE);
    }
    
    private void setUpDownloads(DownloadMonitor dlm, Quij quiz) {
        String wsId = String.valueOf(quiz.getId());
        Question[] questions = quiz.getQuestions();
        for (Question question : questions) {
            Uri src, dest;
            String id = question.getId();
            String grId = question.getGRId("-");
            File image = null, fdbk = null;            
            String imgLocn = question.getImgLocn();
            String[] scans = question.getScanLocn();
            int[] pageNos = question.getPgMap();
            boolean[] sentState = question.getSentState();
            switch (question.getState()) {
            case GRADED:
                fdbk = new File(feedbackDir, id);
                if (!fdbk.exists()) {
                    src = Uri.parse(String.format(FDBK_URL, WEB_APP_HOST_PORT, grId));
                    dest = Uri.fromFile(new File(feedbackDir, id));
//                    dlm.add(id, src, dest);
                    HttpCallsAsyncTask hcat = new HttpCallsAsyncTask(null, 0);
                    hcat.setDestination(new String[] { (new File(dest.getPath())).toString() });
                    try {
                        hcat.execute(new URL[] { new URL(src.toString()) });
                    } catch (Exception e) { 
                        Log.e(TAG, e.getMessage());
                    }
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
                        pageNos[i] = 0; // to be resent
                        sentState[i] = false;
                        question.setState(DOWNLOADED);
                    }
                }
                question.setSentState(sentState);
            case CAPTURED:
                for (int i = 0; i < pageNos.length; i++) {
                    if (pageNos[i] != 0) {
                        image = new File(answersDir, wsId + "." + pageNos[i]);
                        if (!image.exists()) {
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
            }
        }
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
    
    private void mkdirs(String quizId) {
        (quizDir = new File(studentDir, quizId)).mkdir();
        (questionsDir = new File(quizDir, QUESTIONS_DIR_NAME)).mkdir();
        (answersDir = new File(quizDir, ANSWERS_DIR_NAME)).mkdir();
        (solutionsDir = new File(quizDir, SOLUTIONS_DIR_NAME)).mkdir();
        (feedbackDir = new File(quizDir, FEEDBACK_DIR_NAME)).mkdir();
    }

    private void recordFdbkMrkrs(Quij quiz) {
        Properties fdbkMrkrs = new Properties();
        try {
            File fdbkMrkrsFile = new File(studentDir, "fdbkIds");
            fdbkMrkrsFile.createNewFile(); // creates only if needed
            fdbkMrkrs.load(new FileInputStream(fdbkMrkrsFile));
            if (quiz.getState() < GRADED) return;
            
            String quizId = String.valueOf(quiz.getId());
            long lastFdbkMrkr = Long.parseLong(fdbkMrkrs.getProperty(quizId, 
                String.valueOf(0)));
            if (lastFdbkMrkr < quiz.getFdbkMrkr()) {
                File[] fdbkFiles = feedbackDir.listFiles();
                for (File f : fdbkFiles) {
                    f.delete();
                }
                fdbkMrkrs.setProperty(quizId, String.valueOf(quiz.getFdbkMrkr()));
            }
            fdbkMrkrs.store(new FileOutputStream(fdbkMrkrsFile), null);
        } catch (Exception e) { 
            handleError("Error during fdbkChk", e.getMessage());
        }
    }
    
    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }

    private Quij[] quizzes;
    private QuizListAdapter adapter;
    
    private int selectedQuizPosition;
    private File studentDir, quizDir, questionsDir, answersDir, solutionsDir, feedbackDir;
    ProgressDialog peedee;

    private final String 
        SOLN_URL = "http://%s/vault/%s/pg-%d.jpg",
        ANSR_URL = "http://%s/locker/%s",
        QUES_URL = "http://%s/vault/%s/notrim.jpg",
        FDBK_URL = "http://%s/tokens/view_fdb.json?id=%s";

}
