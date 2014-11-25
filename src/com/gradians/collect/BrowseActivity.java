package com.gradians.collect;

import java.io.File;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BrowseActivity extends Activity implements OnItemClickListener, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        
        setupActionBar();
        
        Parcelable parcel = savedInstanceState == null ? 
            getIntent().getParcelableExtra(TAG) :
            savedInstanceState.getParcelable(TAG);
        quiz = (Quij)parcel;
        
        quizDir = new File(getIntent().getStringExtra(QUIZ_PATH_KEY));
        initialize(quiz);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub        
        outState.putParcelable(TAG, quiz);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            Intent intent = new Intent();
            intent.putExtra(TAG, adapter.questions);
            this.setResult(RESULT_OK, intent);
            finish();
        default:
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG, adapter.questions);
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }
    
    @Override
    public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
        this.position = position;
        Intent flowIntent = null;
        flowIntent = new Intent(this.getApplicationContext(), 
            com.gradians.collect.DetailActivity.class);
        flowIntent.putExtra(TAG, (Question)adapter.getItem(position));
        flowIntent.putExtra(QUIZ_PATH_KEY, quizDir.getPath());
        flowIntent.putExtra(NAME_KEY, quizName);
        flowIntent.putExtra(ID_KEY, quizType);
        flowIntent.putExtra(TAG_ID, position);
        startActivityForResult(flowIntent, ITaskResult.FLOW_ACTIVITY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ITaskResult.FLOW_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Question question = (Question)data.getParcelableExtra(TAG);
                adapter.update(question, position);
                quiz.set(position, question);
            } else if (resultCode != Activity.RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(),
                        "Oops we had an error, you may have lost work :/",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void initialize(Quij quiz) {
        quizName = quiz.getName();
        quizType = quiz.getType();
        setTitle(quizName);
        adapter = new QuestionListAdapter(this, quiz.getQuestions(), quizDir);
        ListView lv = (ListView)this.findViewById(R.id.lvQuestions);
        lv.setAdapter(adapter);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lv.setOnItemClickListener(this);
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

    private Quij quiz;
    private int position;
    private File quizDir;
    private String quizName, quizType;
    private QuestionListAdapter adapter;
    
}

class QuestionListAdapter extends BaseAdapter implements IConstants {
    
    public QuestionListAdapter(Context context, Question[] questions, File quizDir) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.questions = questions;
        this.quizDir = quizDir;
    }
    
    public void update(Question question, int position) {
        Question q = questions[position];
        if (question.getState() != q.getState() ||
            question.tried() != q.tried() ||
            question.botAnswer() != q.botAnswer() ||
            question.botSolution() != q.botSolution()) {
            questions[position] = question;
        }
        notifyDataSetChanged();
    }
    
    @Override
    public int getCount() {
        return questions.length;
    }

    @Override
    public Object getItem(int position) {
        return questions[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.layout_question, parent, false);
        }
        
        ImageView iv = (ImageView)convertView.findViewById(R.id.ivAnswer);
        
        Question question = questions[position];
        File questionsDir = new File(quizDir, QUESTIONS_DIR_NAME);
        File image = new File(questionsDir, 
            "m." + question.getVersion() + "." + question.getId());                        
        Bitmap img = BitmapFactory.decodeFile(image.getPath());
        
        float density = context.getResources().
            getDisplayMetrics().density;
        
        final int HEIGHT = 70, WIDTH = 300, PAD = 5;
        img = Bitmap.createBitmap(img, 0, 0,
            img.getWidth() < WIDTH? img.getWidth() : WIDTH,
            img.getHeight() < HEIGHT ? img.getHeight() : HEIGHT);
        iv.setAdjustViewBounds(true);
        iv.setPadding((int)(density*PAD), (int)(density*PAD),
            img.getWidth() > WIDTH ? 
                (int)(density*PAD) : (int)(density*(WIDTH-img.getWidth()-PAD)),
            (int)(density*PAD));
        iv.setImageBitmap(img);
        
        TextView tvLabel = (TextView)convertView.findViewById(R.id.tvLabel);
        tvLabel.setText(String.format("%s", (position+1))); 
        if (question.tried() || question.botAnswer() ||
            question.botSolution() || question.hasScan()) {
            tvLabel.setBackgroundColor(
                context.getResources().getColor(R.color.blue));
        } else {
            tvLabel.setBackgroundColor(
                context.getResources().getColor(R.color.gray));
        }
        return convertView;
    }

    private Context context;
    private File quizDir;
    protected Question[] questions;
    private LayoutInflater inflater;

}
