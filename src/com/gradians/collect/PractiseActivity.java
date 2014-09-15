package com.gradians.collect;

import java.io.File;

import org.json.simple.JSONArray;

import com.gradians.collect.QuestionManifest.Op;

import android.util.Log;
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
            title = "Fresh Problems";
            break;
        case R.id.btnSelected:
            items = selected;
            title = "My Selections";
            break;
        default: //case R.id.btnSent:
            items = submitted;
            title = "My Submissions";
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
        HugeButton btnOffered, btnSelected, btnSubmitted;
        btnOffered = (HugeButton)findViewById(R.id.btnOffered);
        btnSelected = (HugeButton)findViewById(R.id.btnSelected);
        btnSubmitted = (HugeButton)findViewById(R.id.btnSent);
        
        offered = ((QuestionManifest)manifest).getQsns(LOCKED, LOCKED);
        selected = ((QuestionManifest)manifest).getQsns(CAPTURED, DOWNLOADED);
        submitted = ((QuestionManifest)manifest).getQsns(GRADED, SENT);
        
        btnOffered.setText("<b>Browse</b>\nproblems and select", 
            R.drawable.ic_action_forward);
        btnOffered.setEnabled(offered.length > 0);
        btnSelected.setText("<b>Attempt</b>\nselections and send", 
            R.drawable.ic_action_unread);
        btnSelected.setEnabled(selected.length > 0);
        btnSubmitted.setText("<b>View</b>\nsubmissions and feedback", 
            R.drawable.ic_action_chat);
        btnSubmitted.setEnabled(submitted.length > 0);        
    }
    
    private Quij[] offered, selected, submitted;
    
}

