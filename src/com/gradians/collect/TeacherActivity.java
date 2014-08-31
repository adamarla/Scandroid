package com.gradians.collect;

import java.io.File;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcelable;
import android.view.View;
import android.widget.Toast;

public class TeacherActivity extends BaseActivity {

    public TeacherActivity() {
        super(R.layout.activity_teacher, "tokens/refresh/ws");
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == LIST_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    Parcelable[] parcels = data.getParcelableArrayExtra(TAG_ID);
                    Question[] questions = new Question[parcels.length];
                    for (int i = 0; i < parcels.length; i++) {
                        questions[i] = (Question)parcels[i];
                    }
                    manifest.update(questions);
                    manifest.commit();
                    setCounts(manifest);
                } catch (Exception e) {
                    handleError("List activity failed", e.getMessage());
                }
            } else if (resultCode != Activity.RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(),
                    "Oops, an error, you may have lost work :/",
                    Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void initialize(JSONObject respObject) throws Exception {
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
        
        File dir = new File(studentDir, "manifests"); dir.mkdir();
        File stateFile = new File(dir, this.getClass().getName().toString());
        stateFile.createNewFile();                    
        
        manifest = new QuizManifest(stateFile, items);
        setCounts(manifest);        
    }

    @Override
    public void onClick(View v) {
        if (manifest == null) return;
        Quij[] items = null;
        switch (v.getId()) {
        case R.id.btnInbox:
            items = manifest.getInboxItems();
            break;
        case R.id.btnOutbox:
            items = manifest.getOutboxItems();
            break;
        case R.id.btnGraded:
            items = manifest.getGradedItems();
        }
        if (items.length != 0) {
            launchListActivity(items);
        }
    }
    
    private void setCounts(QuizManifest manifest) {
        HugeButton inbox, outbox, graded;
        inbox = (HugeButton)findViewById(R.id.btnInbox);
        outbox = (HugeButton)findViewById(R.id.btnOutbox);
        graded = (HugeButton)findViewById(R.id.btnGraded);   
        
        inbox.setCount(manifest.getInboxItems().length, 
            "Inbox", R.drawable.ic_action_unread);
        outbox.setCount(manifest.getOutboxItems().length, 
            "Outbox", R.drawable.ic_action_sent);
        graded.setCount(manifest.getGradedItems().length, 
            "Graded", R.drawable.ic_action_chat);
    }
        
    QuizManifest manifest;    
}

