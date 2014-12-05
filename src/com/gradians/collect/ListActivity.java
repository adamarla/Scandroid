package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class ListActivity extends Activity implements OnItemClickListener,
        ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        
        setupActionBar();
        
        Parcelable[] quizItems = getIntent().getParcelableArrayExtra(TAG);
        String path = getIntent().getStringExtra(NAME_KEY);

        initialize(quizItems, path);
        setTitle(getIntent().getStringExtra(TAG_ID));        
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            Intent intent = new Intent();
            intent.putExtra(TAG_ID, adapter.getDirtys());
            this.setResult(RESULT_OK, intent);
            finish();
        default:
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG_ID, adapter.getDirtys());
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BROWSE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
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
                launchBrowseActivity(quiz);
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
            launchBrowseActivity(quiz);
        }
    }
    
    private void launchBrowseActivity(Quij quiz) {
        Intent browseIntent = new Intent(this.getApplicationContext(), 
            com.gradians.collect.BrowseActivity.class);
        browseIntent.putExtra(TAG, (Parcelable)quiz);
        browseIntent.putExtra(QUIZ_PATH_KEY, quizDir.getPath());
        startActivityForResult(browseIntent, BROWSE_ACTIVITY_REQUEST_CODE);
    }

    private void setUpDownloads(DownloadMonitor dlm, 
        HttpCallsAsyncTask hcat, Quij quiz) {
        File problemsDir = new File(studentDir, PROBLEMS_DIR_NAME);
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
        
        Markers markers = new Markers(studentDir);
        Question[] questions = quiz.getQuestions();
        for (Question question : questions) {
            
            Uri src, dest;
            File image = null;
            String imgLocn = question.getImgLocn();
            String[] scans = question.getScanLocn();
            int[] pageNos = question.getPgMap();
            long fdbkMarker = markers.getFdbkId(question.getId());
            
            // Feedback
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
            
            // Attempt
            if (question.getState() > SENT) {
                for (int i = 0; i < pageNos.length; i++) {
                    image = new File(attemptsDir, 
                        question.getId() + "." + pageNos[i]);
                    if (!image.exists()) {
                        src = Uri.parse(String.format(TMPT_URL, 
                            BANK_HOST_PORT, scans[i]));
                        dest = Uri.fromFile(image);
                        dlm.add(question.getId(), src, dest);
                    }
                }
            } else if (question.getState() == SENT || question.getState() == CAPTURED) {
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
            
            // Solution
            if (question.canSeeSolution(quiz.getType())) {
                for (short i = 0; i < question.getImgSpan(); i++) {
                    image = new File(solutionsDir, 
                        "m." + question.getVersion() + "." + 
                        question.getId() + "." + (i+1));
                    if (question.isDirty()) image.delete();
                    if (!image.exists()) {
                        src = Uri.parse(String.format(SOLN_URL, 
                            BANK_HOST_PORT, imgLocn, (i+1)));
                        dest = Uri.fromFile(image);
                        dlm.add(question.getId(), src, dest);
                    }
                }
            }
            
            // Question (thin)
            image = new File(questionsDir, 
                "m." + question.getVersion() + "." + question.getId());
            if (question.isDirty()) image.delete();
            if (!image.exists()) {
                src = Uri.parse(String.format(MOBL_URL, BANK_HOST_PORT, 
                    imgLocn));
                dest = Uri.fromFile(image);
                dlm.add(question.getId(), src, dest);
            }
            
            // Answer
            if (question.hasCodex()) {
                int[] codices = { 0, 1, 2, 3 };
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
            } else if (question.hasAnswer()) {
                image = new File(answersDir, 
                    question.getVersion() + "." + question.getId());
                if (question.isDirty()) image.delete();
                if (!image.exists()) {
                    src = Uri.parse(String.format(ANSR_URL, BANK_HOST_PORT, 
                        imgLocn.replaceFirst("/[0-3]$", ""), question.getVersion()));
                    dest = Uri.fromFile(image);
                    dlm.add(question.getId(), src, dest);
                }
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
            
            markers.setFdbkId(question.getId(), fdbkMarker);
            question.setDirty(false);
        }
        markers.commit();
    }
    
    private void setUpUploads(Quij quiz) {
        File uploadsDir = new File(quizDir, UPLOAD_DIR_NAME);
        if (!uploadsDir.exists()) return;
        
        ArrayList<Question> toSend = new ArrayList<Question>();
        for (Question question : quiz.getQuestions()) {
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
            uploadIntent.putExtra(TAG_ID, quiz.getType());
            uploadIntent.putExtra(TAG, toSend.toArray(new Question[toSend.size()]));
            startService(uploadIntent);
        }    
    }

    private void initialize(Parcelable[] quizItems, String path) {
        studentDir = new File(path);        
        quizzes = new Quij[quizItems.length];
        for (int i = 0; i < quizItems.length; i++) {
            quizzes[i] = (Quij)quizItems[i];
            setUpUploads(quizzes[i]);
        }
        
        adapter = new QuizListAdapter(this, quizzes);
        ListView lv = (ListView) this.findViewById(R.id.lvQuiz);
        lv.setAdapter(adapter);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lv.setOnItemClickListener(this);        
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
    
    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private Quij[] quizzes;
    private QuizListAdapter adapter;
    
    private int selectedQuizPosition;
    private File studentDir, quizDir;
    ProgressDialog peedee;

    private final String
        SOLN_URL = "http://%s/vault/%s/pg-%d.png",
        TMPT_URL = "http://%s/locker/%s",
        MOBL_URL = "http://%s/vault/%s/mobile.black.png",
        FDBK_URL = "http://%s/tokens/view_fdb.json?id=%s&type=%s",
        HINT_URL = "http://%s/tokens/view_hints.json?id=%s",
        ANSR_URL = "http://%s/vault/%s/%s/codex.png";

}


class QuizListAdapter extends BaseAdapter implements IConstants {
    
    public QuizListAdapter(Context context, Quij[] quizzes) {
        this.inflater = LayoutInflater.from(context);
        this.quizzes = quizzes;
        this.dirtys = new ArrayList<Question>();
    }
        
    public void update(int quizPosn, Question[] questions) {
        Quij quiz = quizzes[quizPosn];
        Question q = null;
        for (int i = 0; i < questions.length; i++) {
            q = quiz.get(i);
            if (questions[i].getState() != q.getState() ||
                questions[i].tried() != q.tried() ||
                questions[i].botAnswer() != q.botAnswer() ||
                questions[i].botSolution() != q.botSolution()) {
                quiz.set(i, questions[i]);
                dirtys.add(questions[i]);
            }
        }
        quiz.determineState();
        notifyDataSetChanged();
    }
    
    public Question[] getDirtys() {
        return dirtys.toArray(new Question[dirtys.size()]);
    }

    @Override
    public int getCount() {
        return quizzes.length;
    }

    @Override
    public Object getItem(int position) {
        return quizzes[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.layout_quiz, parent, false);
        }        
        
        TextView tv = (TextView)convertView.findViewById(R.id.tvQuiz);
        tv.setTag(position);
        
        Quij quiz = (Quij)getItem(position);
        tv.setText(quiz.toString());
        
        TextView tvTotal = (TextView)convertView.findViewById(R.id.tvTotal);
        tvTotal.setText(quiz.getDisplayTotal());
        
        return convertView;
    }

    private ArrayList<Question> dirtys;
    private Quij[] quizzes;
    private LayoutInflater inflater;

}
