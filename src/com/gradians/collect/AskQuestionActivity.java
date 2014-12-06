package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class AskQuestionActivity extends Activity implements OnItemClickListener, ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ask_question);
        
        Parcelable[] parcels = getIntent().getParcelableArrayExtra(TAG);
        String path = getIntent().getStringExtra(NAME_KEY);
        
        initialize(parcels, path);        
        setUpUploads();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
        default:
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Question q = data.getParcelableExtra(TAG);
                if (!q.getPgMap("").contains("0")) {
                    position = -1;
                    launchDetailActivity(q);
                } 
            }
        } else if (requestCode == DETAIL_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                File questionsDir = new File(doubtsDir, QUESTIONS_DIR_NAME);
                (new File(questionsDir, "0.1")).delete();
                
                Question q = data.getParcelableExtra(TAG);
                if (q.botSolution()) {                    
                    boolean newdoubt = true;
                    Question question;
                    for (int i = 0; i < adapter.getCount(); i++) {
                        question = (Question)adapter.getItem(i); 
                        if (question.getId().equals(q.getId())) {
                            newdoubt = false;
                            break;
                        }
                    }
                    if (newdoubt) {
                        adapter.update(q);
                        findViewById(R.id.tvEmpty).setVisibility(View.GONE);                    
                    }
                }
            }
        }
    }

    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == DOWNLOAD_MONITOR_TASK_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Question q = (Question)adapter.getItem(position);
                launchDetailActivity(q);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
        this.position = position;        
        Question q = (Question)adapter.getItem(position);
        
        File questionsDir = new File(doubtsDir, QUESTIONS_DIR_NAME);
        File solutionsDir = new File(doubtsDir, QUESTIONS_DIR_NAME);        
        DownloadMonitor dlm = new DownloadMonitor(this);
        Uri src, dest;        
        File image = new File(questionsDir, q.getId() + ".1");
        if (!image.exists()) {
            if (q.getImgLocn() == null) return;
            src = Uri.parse(String.format(IMG_URL, 
                BANK_HOST_PORT, q.getImgLocn()));
            dest = Uri.fromFile(image);
            dlm.add(q.getId(), src, dest);
        }
        
        image = new File(solutionsDir, q.getId() + ".1");
        if (!image.exists() &&  q.getScanLocn()[0] != null) {
            src = Uri.parse(String.format(IMG_URL, 
                BANK_HOST_PORT, q.getScanLocn()[0]));
            dest = Uri.fromFile(image);
            dlm.add(q.getId(), src, dest);
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
            launchDetailActivity(q);
        }
    }
    
    public void launchCameraActivity(View view) {        
        File questionsDir = new File(doubtsDir, QUESTIONS_DIR_NAME);        
        Intent takePictureIntent = new Intent(this.getApplicationContext(),
            com.gradians.collect.CameraActivity.class);
        Question newdoubt = new Question("Q", "0", 0, "0", null, (short)0);
        newdoubt.setState(DOWNLOADED);
        takePictureIntent.putExtra(TAG, newdoubt);
        takePictureIntent.putExtra(SCANS_KEY, questionsDir.getPath());
        startActivityForResult(takePictureIntent,
            ITaskResult.CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }
    
    private void launchDetailActivity(Question q) {
        Intent detailIntent = new Intent(this.getApplicationContext(),
            com.gradians.collect.DetailActivity.class);
        detailIntent.putExtra(TAG, q);
        detailIntent.putExtra(QUIZ_PATH_KEY, doubtsDir.getPath());
        detailIntent.putExtra(NAME_KEY, 
            getResources().getString(R.string.title_activity_ask_question));
        detailIntent.putExtra(ID_KEY, DBT_TYPE);
        detailIntent.putExtra(TAG_ID, position);
        startActivityForResult(detailIntent, DETAIL_ACTIVITY_REQUEST_CODE);        
    }
    
    private void setUpUploads() {
        File uploadsDir = new File(doubtsDir, UPLOAD_DIR_NAME);
        if (!uploadsDir.exists()) return;
        
        ArrayList<Question> toSend = new ArrayList<Question>();
        for (Question question : doubts) {
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
            uploadIntent.putExtra(QUIZ_PATH_KEY, doubtsDir.getPath());
            uploadIntent.putExtra(TAG_ID, DBT_TYPE);
            uploadIntent.putExtra(TAG, toSend.toArray(new Question[toSend.size()]));
            startService(uploadIntent);
        }    
    }    
    
    private void initialize(Parcelable[] doubtItems, String path) {
        File studentDir = new File(path);
        File problemsDir = new File(studentDir, PROBLEMS_DIR_NAME);
        doubtsDir = new File(problemsDir, DOUBTS_DIR_NAME);
        doubtsDir.mkdir();        
        (new File(doubtsDir, QUESTIONS_DIR_NAME)).mkdir();
        (new File(doubtsDir, SOLUTIONS_DIR_NAME)).mkdir();
        (new File(doubtsDir, UPLOAD_DIR_NAME)).mkdir();
        
        doubts = new Question[doubtItems.length];
        for (int i = 0; i < doubtItems.length; i++)
            doubts[i] = (Question)doubtItems[i];
        
        adapter = new DoubtsListAdapter(this, doubts);
        ListView lv = (ListView) this.findViewById(R.id.lvDoubts);
        lv.setAdapter(adapter);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lv.setOnItemClickListener(this);
        
        if (doubts.length > 0) 
            findViewById(R.id.tvEmpty).setVisibility(View.GONE);
    }
    
    private int position;
    private Question[] doubts;
    private DoubtsListAdapter adapter;
    
    private File doubtsDir;
    ProgressDialog peedee;

    private final String
        IMG_URL = "http://%s/locker/%s";    
}


class DoubtsListAdapter extends BaseAdapter implements IConstants {
    
    public DoubtsListAdapter(Context context, Question[] doubts) {
        this.inflater = LayoutInflater.from(context);
        this.doubts = new ArrayList<Question>();
        for (Question q : doubts) {
            this.doubts.add(q);
        }
    }
    
    public void update(Question question) {
        doubts.add(question);
        notifyDataSetChanged();
    }
    
    @Override
    public int getCount() {
        return doubts.size();
    }

    @Override
    public Object getItem(int position) {
        return doubts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.layout_doubt, parent, false);
        }
        
        TextView tv = (TextView)convertView.findViewById(R.id.tvLabel);
        tv.setTag(position);
        
        Question question = (Question)getItem(position);
        tv.setText(question.getName());
        
        ImageView ivState = (ImageView)convertView.findViewById(R.id.ivState);
        if (question.getScanLocn()[0] == null)
            ivState.setImageDrawable(null);
        
        return convertView;
    }

    private ArrayList<Question> doubts;
    private LayoutInflater inflater;

}
