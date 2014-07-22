package com.gradians.collect;


import java.io.File;
import java.util.ArrayList;

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
        quizzes = new Quij[quizItems.length];
        for (int i = 0; i < quizItems.length; i++) {
            quizzes[i] = (Quij)quizItems[i];
            Log.d(TAG, quizzes[i].getName() + " " + quizzes[i].getState());
        }
        
        Parcelable[] qstnItems = getIntent().getParcelableArrayExtra(TAG_ID);
        this.studentDirPath = getIntent().getStringExtra("studentDir");
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
        
        adapter = new QuizListAdapter(this, quizzes);
        ListView lv = (ListView) this.findViewById(R.id.lvQuiz);
        lv.setAdapter(adapter);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lv.setOnItemClickListener(this);
        
        setUpPaths(studentDirPath);
    }

    @Override
    public void onBackPressed() {
        ArrayList<Question> questions = new ArrayList<Question>();
        for (Quij quiz : quizzes) {
            questions.addAll(quiz);
        }
        Intent intent = new Intent();
        intent.putExtra(TAG, questions.toArray(new Question[questions.size()]));
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
        flowIntent.putExtra(TAG, studentDirPath);
        if (quiz.getState() == NOT_YET_BILLED)
            flowIntent.putExtra(QUIZ_PRICE_KEY, quiz.getPrice());
        startActivityForResult(flowIntent, FLOW_ACTIVITY_REQUEST_CODE);
    }
    
    private void setUpDownloads(DownloadMonitor dlm, Quij quiz) {
        Question[] questions = quiz.getQuestions();
        for (Question question : questions) {
            Uri src, dest;
            String id = question.getId();
            String wsId = id.split("\\.")[0];
            String grId = question.getGRId("-");
            File image = null, fdbk = null;            
            String imgLocn = question.getImgLocn();
            String[] scans = question.getScanLocn();
            int[] pageNos = question.getPgMap();
            switch (question.getState()) {
            case GRADED:
                fdbk = new File(feedbackDir, id);
                if (!fdbk.exists()) {
                    src = Uri.parse(String.format(FDBK_URL, WEB_APP_HOST_PORT, grId));
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
            case CAPTURED:
            case DOWNLOADED:
            case WAITING:
                for (int i = 0; i < pageNos.length; i++) {
                    if (pageNos[i] != 0) {
                        image = new File(answersDir, wsId + "." + pageNos[i]);
                        if (!image.exists()) {
                            pageNos[i] = 0;
                        }
                    }
                }
                question.setPgMap(pageNos);
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

    private void setUpPaths(String studentDirPath) {
        studentDir = new File(studentDirPath);
        questionsDir = new File(studentDir, QUESTIONS_DIR_NAME);
        answersDir = new File(studentDir, ANSWERS_DIR_NAME);
        solutionsDir = new File(studentDir, SOLUTIONS_DIR_NAME);
        feedbackDir = new File(studentDir, FEEDBACK_DIR_NAME);
    }
    
    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }

    private String studentDirPath;
    private Quij[] quizzes;
    private QuizListAdapter adapter;
    
    private int selectedQuizPosition;
    private File studentDir, questionsDir, answersDir, solutionsDir, feedbackDir;
    ProgressDialog peedee;

    private final String 
        SOLN_URL = "http://%s/vault/%s/pg-%d.jpg",
        ANSR_URL = "http://%s/locker/%s",
        QUES_URL = "http://%s/vault/%s/notrim.jpg",
        FDBK_URL = "http://%s/tokens/view_fdb.json?id=%s";

}
