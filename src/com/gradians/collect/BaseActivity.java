package com.gradians.collect;

import java.io.File;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public abstract class BaseActivity extends Activity implements ITaskResult, IConstants {
    
    public BaseActivity(int layoutId, String subpath) {
        super();
        this.layoutId = layoutId;
        this.subpath = subpath;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layoutId);
        
        email = getIntent().getStringExtra(EMAIL_KEY);
        token = getIntent().getStringExtra(TOKEN_KEY);
        
        refresh(subpath, email, token);
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
            refresh(subpath, email, token);
        else if (item.getItemId() == R.id.action_help)
            launchHelpActivity();
        return super.onOptionsItemSelected(item);
    }

    protected abstract void onActivityResult(int requestCode, int resultCode, Intent data);
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == REFRESH_WS_TASK_REQUEST_CODE) {
            if (peedee != null) peedee.dismiss();
            if (resultCode == RESULT_OK) {
                try {
                    studentDir = new File(getExternalFilesDir(null), email.replace('@', '.'));
                    studentDir.mkdir();
                    
                    JSONParser jsonParser = new JSONParser();
                    JSONObject respObject = (JSONObject)jsonParser.parse(resultData);
                    
                    initialize(respObject);
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
    
    protected abstract void initialize(JSONObject respObject) throws Exception;
    
    public abstract void onClick(View v);

    protected void launchListActivity(Quij[] items) {
        Intent listIntent = new Intent(getApplicationContext(),
            com.gradians.collect.ListActivity.class);
        listIntent.putExtra(NAME_KEY, studentDir.getPath());
        listIntent.putExtra(TAG, items);
        startActivityForResult(listIntent, LIST_ACTIVITY_REQUEST_CODE);
    }

    public void launchHelpActivity() {
        Intent helpIntent = new Intent(this.getApplicationContext(),
            com.gradians.collect.HelpActivity.class);
        startActivity(helpIntent);
    }

    private void refresh(String subpath, String email, String token) {
        peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
        peedee.setTitle("Synchronizing");
        peedee.setMessage("Please wait...");
        peedee.setIndeterminate(true);
        peedee.setIcon(ProgressDialog.STYLE_SPINNER);
        peedee.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        peedee.show();
        Uri src = Uri.parse(String.format(REFRESH_URL, WEB_APP_HOST_PORT, subpath, email, token));
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this, REFRESH_WS_TASK_REQUEST_CODE).execute(new Download[] { download });
    }
    
    protected void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }
    
    protected ProgressDialog peedee;
    protected String email, token;
    protected File studentDir;
    
    private int layoutId;
    private String subpath;
    
    private final String REFRESH_URL = "http://%s/%s?email=%s&token=%s";    

}