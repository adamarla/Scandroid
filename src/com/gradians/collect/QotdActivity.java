package com.gradians.collect;

import java.io.File;

import org.json.simple.JSONArray;

import android.R;
import android.util.Log;
import android.view.View;

public class QotdActivity extends BaseActivity {

    public QotdActivity() {
        super(R.layout.activity_qotd, "qsns");
    }

    @Override
    public void onClick(View v) {
        Quij[] items = null;
        String title = null;
        switch (v.getId()) {
        case R.id.btnPast:
            title = "Past Attempts";
            break;
        case R.id.btnToday:
            items = ((QuestionManifest)manifest).getFuzzle(potd);
            title = "Problem of the Day";
            break;
        }
        if (items.length != 0) {
            launchListActivity(items, title);
        }
    }
    
    @Override
    protected BaseManifest getManifest(File studentDir, JSONArray items,
        Topic[] topics) throws Exception {
        return new QuestionManifest(studentDir, items, topics);
    }

    @Override
    protected void updateCounts(BaseManifest manifest) {
        
        HugeButton today, past;
        today = (HugeButton)findViewById(R.id.btnToday);
        past = (HugeButton)findViewById(R.id.btnPast);
        
        if (manifest instanceof StabManifest) {
            Quij[] quizzes = ((StabManifest)manifest).getStabs();
            int ansCount = quizzes.length == 0 ? 0 : 
                quizzes[0].getQuestions().length;
            
            if (ansCount > 0) {
                past.setCount(ansCount, "Past Attempts", R.drawable.ic_action_sent);
                past.setEnabled(true);
            } else {
                past.setText("Past Attempts", R.drawable.ic_action_sent);
                past.setEnabled(false);
            }
        } else {
            today.setText("", R.drawable.ic_action_forward);
            past.setText("Past Attempts", R.drawable.ic_action_sent);
            past.setEnabled(false);
        }
    }
    
}
