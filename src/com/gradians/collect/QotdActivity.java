package com.gradians.collect;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.content.Intent;
import android.util.Log;
import android.view.View;

public class QotdActivity extends BaseActivity {

    public QotdActivity() {
        super(R.layout.activity_qotd, "tokens/refresh/stab");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onClick(View v) {
        Quij[] items = null;
        switch (v.getId()) {
        case R.id.btnPast:
            if (stabManifest == null) return;
            items = stabManifest.getStabs();
            break;
        case R.id.btnToday:
            break;
        }
        if (items.length != 0) {
            launchListActivity(items);
        }
    }
    
    @Override
    protected void initialize(JSONObject respObject) throws Exception {
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);        
        stabManifest = new StabManifest(items);
        setCounts(stabManifest);
    }
    
    private void setCounts(StabManifest manifest) {
        Quij quiz = manifest.getStabs()[0];
        int ansCount = quiz == null ? 0 : quiz.getQuestions().length;
        
        HugeButton today, past;
        today = (HugeButton)findViewById(R.id.btnToday);
        past = (HugeButton)findViewById(R.id.btnPast);
        
        today.setText("Today's\nQuestion", R.drawable.ic_action_unread);
        if (ansCount > 0) {
            past.setCount(ansCount, "Past Answers", R.drawable.ic_action_sent);
            past.setEnabled(true);
        } else {
            past.setText("Past Answers", R.drawable.ic_action_sent);
            past.setEnabled(true);
        }
    }
    
    StabManifest stabManifest;
    
}
