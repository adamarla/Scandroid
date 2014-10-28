package com.gradians.collect;

import java.io.File;

import android.view.View;

public class TeacherActivity extends BaseActivity {
    

    public TeacherActivity() {
        super(R.layout.activity_teacher, "ws");
    }
    
    @Override
    public void onClick(View v) {
        if (manifest == null) return;
        Quij[] items = null;
        String title = null;
        
        switch (v.getId()) {
        case R.id.btnInbox:
            items = ((QuizManifest)manifest).getInboxItems();
            title = "Inbox";
            break;
        case R.id.btnOutbox:
            items = ((QuizManifest)manifest).getOutboxItems();
            title = "Outbox";
            break;
        case R.id.btnGraded:
            items = ((QuizManifest)manifest).getGradedItems();
            title = "Graded";
        }
        
        if (items.length != 0) {
            launchListActivity(items, title, v.getId());
        }
    }    
    
    @Override
    protected BaseManifest getManifest(File studentDir,
        Topic[] topics) throws Exception {
        return new QuizManifest(studentDir, topics);
    }

    @Override
    protected void updateCounts(BaseManifest manifest) {
        HugeButton inbox, outbox, graded;
        inbox = (HugeButton)findViewById(R.id.btnInbox);
        outbox = (HugeButton)findViewById(R.id.btnOutbox);
        graded = (HugeButton)findViewById(R.id.btnGraded);   
        
        inbox.setCount(((QuizManifest)manifest).getInboxItems().length, 
            "Inbox", R.drawable.ic_action_unread);
        outbox.setCount(((QuizManifest)manifest).getOutboxItems().length, 
            "Outbox", R.drawable.ic_action_sent);
        graded.setCount(((QuizManifest)manifest).getGradedItems().length, 
            "Graded", R.drawable.ic_action_chat);
    }
    
}

