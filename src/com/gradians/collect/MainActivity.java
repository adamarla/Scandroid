package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends Activity implements ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        email = getIntent().getStringExtra(EMAIL_KEY);
        token = getIntent().getStringExtra(TOKEN_KEY);
        
        refresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh)
            refresh();
        else if (item.getItemId() == R.id.action_help)
            launchHelpActivity();
        return super.onOptionsItemSelected(item);
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
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == REFRESH_WS_TASK_REQUEST_CODE) {
            if (peedee != null) peedee.dismiss();
            if (resultCode == RESULT_OK) {
                try {
                    JSONParser jsonParser = new JSONParser();
                    JSONObject respObject = (JSONObject)jsonParser.parse(resultData);                    
                    JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
                    
                    File dir = new File(getFilesDir(), "manifests"); dir.mkdir();
                    File stateFile = new File(dir, email.replace('@', '.'));
                    stateFile.createNewFile();                    
                    
                    manifest = new QuizManifest(stateFile, items);
                    setCounts(manifest);
                    
                    studentDir = new File(getExternalFilesDir(null), email.replace('@', '.'));
                    studentDir.mkdir();
                } catch (Exception e) {
                    handleError("Refresh task failed ", e.getMessage());
                }
            } else {
                Toast.makeText(getApplicationContext(), 
                    "Sorry, cannot refresh, no Internet access right now.", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    
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
    
    private void launchListActivity(Quij[] items) {
        ArrayList<Question> questions = new ArrayList<Question>();
        for (Quij quiz : items) {
            questions.addAll(quiz);
        }
        Intent listIntent = new Intent(getApplicationContext(),
            com.gradians.collect.ListActivity.class);
        listIntent.putExtra(NAME_KEY, studentDir.getPath());
        listIntent.putExtra(TAG, items);
        listIntent.putExtra(TAG_ID, questions.toArray(new Question[questions.size()]));
        startActivityForResult(listIntent, LIST_ACTIVITY_REQUEST_CODE);
    }

    private void launchHelpActivity() {
        Intent helpIntent = new Intent(this.getApplicationContext(),
            com.gradians.collect.HelpActivity.class);
        startActivity(helpIntent);
    }

    private void setCounts(QuizManifest manifest) {
        ((MainButton)findViewById(R.id.btnInbox)).setCount(manifest.getInboxItems().length, 
            "Inbox", R.drawable.ic_action_unread);
        ((MainButton)findViewById(R.id.btnOutbox)).setCount(manifest.getOutboxItems().length, 
            "Outbox", R.drawable.ic_action_sent);
        ((MainButton)findViewById(R.id.btnGraded)).setCount(manifest.getGradedItems().length, 
            "Graded", R.drawable.ic_action_chat);
    }
    
    private void refresh() {
        peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
        peedee.setTitle("Synchronizing");
        peedee.setMessage("Please wait...");
        peedee.setIndeterminate(true);
        peedee.setIcon(ProgressDialog.STYLE_SPINNER);
        peedee.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        peedee.show();
        Uri src = Uri.parse(String.format(REFRESH_WS_URL, WEB_APP_HOST_PORT, email, token));
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this,
            REFRESH_WS_TASK_REQUEST_CODE).execute(new Download[] { download });
    }

    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }
    
    private String email, token;
    private QuizManifest manifest;
    private File studentDir;
    ProgressDialog peedee;
    
    private final String REFRESH_WS_URL = "http://%s/tokens/verify?email=%s&token=%s";

}

