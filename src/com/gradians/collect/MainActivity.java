package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkAuth();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sign_out)
            initiateAuthActivity();
        else if (item.getItemId() == R.id.action_refresh)
            checkAuth();
        else if (item.getItemId() == R.id.action_help)
            launchHelpActivity();
        return super.onOptionsItemSelected(item);
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    File dir = new File(getFilesDir(), "manifests"); dir.mkdir();
                    manifest = new QuizManifest(dir, data.getStringExtra(TAG));
                    setPreferences(manifest);
                    initialize(manifest);
                } catch (Exception e) {
                    handleError("Oops, Auth activity request failed",
                            e.getMessage());
                }
            } else if (resultCode != Activity.RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(),
                        "Oops.. auth check failed. Please try again",
                        Toast.LENGTH_SHORT).show();
            } else {
                this.finish();
            }
        } else if (requestCode == LIST_ACTIVITY_REQUEST_CODE) {
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
                    handleError("Oops, Flow activity request failed",
                            e.getMessage());
                }
            } else if (resultCode != Activity.RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(),
                        "Oops we had an error, you may have lost work :/",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == VERIFY_AUTH_TASK_RESULT_CODE) {
            if (peedee != null) peedee.dismiss();
            if (resultCode == RESULT_OK) {
                try {
                    File dir = new File(getFilesDir(), "manifests"); dir.mkdir(); 
                    manifest = new QuizManifest(dir, resultData);
                    initialize(manifest);
                } catch (Exception e) {
                    handleError("Oops, Verify auth task failed", e.getMessage());
                }
            } else {
                Toast.makeText(getApplicationContext(), 
                    "Sorry, cannot refresh, no Internet access right now.", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    
    public void onClick(View v) {
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

    private void initialize(QuizManifest manifest) {
        setTitle(String.format("Hi %s", manifest.getName()));        
        try {
            ((TextView)findViewById(R.id.tvVers)).setText(getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName);
        } catch (NameNotFoundException e) { }
        setCounts(manifest);
        (studentDir = new File(getExternalFilesDir(null), 
            manifest.getEmail().replace('@', '.'))).mkdir();
    }
    
    private void checkAuth() {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        String email = prefs.getString(EMAIL_KEY, null);
        String token = prefs.getString(TOKEN_KEY, null);
        if (token == null) {
            initiateAuthActivity();
        } else {
            peedee = ProgressDialog.show(this, "Initializing", "Please wait...");
            peedee.setIndeterminate(true);
            peedee.setIcon(ProgressDialog.STYLE_SPINNER);
            Uri src = Uri.parse(String.format(VERIFY_URL, WEB_APP_HOST_PORT, email, token));
            Download download = new Download(null, src, null);
            new HttpCallsAsyncTask(this,
                VERIFY_AUTH_TASK_RESULT_CODE).execute(new Download[] { download });
        }
    }

    private void initiateAuthActivity() {
        resetPreferences();
        Intent checkAuthIntent = new Intent(this, 
            com.gradians.collect.LoginActivity.class);
        checkAuthIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(checkAuthIntent, AUTH_ACTIVITY_REQUEST_CODE);
    }

    private void setPreferences(QuizManifest manifest) {
        SharedPreferences prefs =
                this.getSharedPreferences(TAG, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putString(TOKEN_KEY, manifest.getAuthToken());
        edit.putString(NAME_KEY, manifest.getName());
        edit.putString(EMAIL_KEY, manifest.getEmail());
        edit.commit();
    }

    private void resetPreferences() {
        SharedPreferences prefs =
                getSharedPreferences(TAG, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    private void setCounts(QuizManifest manifest) {
        ((MainButton)findViewById(R.id.btnInbox)).setCount(manifest.getInboxItems().length, 
            "Inbox", R.drawable.ic_action_unread);
        ((MainButton)findViewById(R.id.btnOutbox)).setCount(manifest.getOutboxItems().length, 
            "Outbox", R.drawable.ic_action_sent);
        ((MainButton)findViewById(R.id.btnGraded)).setCount(manifest.getGradedItems().length, 
            "Graded", R.drawable.ic_action_chat);
    }
    
    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }

    private QuizManifest manifest;
    private File studentDir;
    ProgressDialog peedee;

    private final String 
        VERIFY_URL = "http://%s/tokens/verify?email=%s&token=%s";

}

class MainButton extends RelativeLayout {

    public MainButton (Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater)context.
            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(R.layout.layout_big_btn, this, true);
    }
    
    public void setCount(int count, String text, int drawable) {
        ((TextView)findViewById(R.id.tvLabel)).setText(text);
        ((TextView)findViewById(R.id.tvCount)).setText(String.valueOf(count));
        ((TextView)findViewById(R.id.tvCount)).setVisibility(View.VISIBLE);
        ((ImageView)findViewById(R.id.ivIcon)).setImageResource(drawable);
    }
    
}
