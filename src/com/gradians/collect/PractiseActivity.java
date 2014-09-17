package com.gradians.collect;

import java.io.File;

import org.json.simple.JSONArray;

import android.view.View;

public class PractiseActivity extends BaseActivity {
    

    public PractiseActivity() {
        super(R.layout.activity_practise, "qsns");
    }
    
    @Override
    public void onClick(View v) {
        if (manifest == null) return;
        Quij[] items = null;
        String title = null;
        switch (v.getId()) {
        case R.id.btnOffered:
            items = offered;
            title = "Solve problems";
            break;
        default: //case R.id.btnSent:
            items = submitted;
            title = "Review feedback";
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
        HugeButton btnOffered, btnSubmitted;
        btnOffered = (HugeButton)findViewById(R.id.btnOffered);
        btnSubmitted = (HugeButton)findViewById(R.id.btnSent);
        
        offered = ((QuestionManifest)manifest).getQsns(SENT, LOCKED, false);
        submitted = ((QuestionManifest)manifest).getQsns(GRADED, RECEIVED, false);
        
        btnOffered.setText(R.string.practise_button_browse,
            R.drawable.ic_action_unread);
        btnOffered.setEnabled(offered.length > 0);
        btnSubmitted.setText(R.string.practise_button_feedback, 
            R.drawable.ic_action_chat);
        btnSubmitted.setEnabled(submitted.length > 0);
    }
    
    private Quij[] offered, submitted;
    
}

