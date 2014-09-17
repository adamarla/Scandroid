package com.gradians.collect;

import java.io.File;

import org.json.simple.JSONArray;

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
            title = "Past Puzzles";
            items = pastPuzzles;
            break;
        case R.id.btnToday:
            title = "Puzzle of the Day";
            items = potdOffered;
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
        
        potdOffered = ((QuestionManifest)manifest).getFuzzle(potd);
        pastPuzzles = ((QuestionManifest)manifest).getQsns(GRADED, RECEIVED, true);
        
        today.setText(R.string.potd_button_attempt, R.drawable.ic_action_unread);            
        past.setText(R.string.potd_button_prior, R.drawable.ic_action_sent);
        past.setEnabled(pastPuzzles.length > 0);
        
    }
        
    private Quij[] potdOffered, pastPuzzles;
}
