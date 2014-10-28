package com.gradians.collect;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
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
        
        Parcelable[] parcels = getIntent().getParcelableArrayExtra(TOPICS_KEY);
        topics = new Topic[parcels.length];
        for (int i = 0; i < parcels.length; i++)
           topics[i] = (Topic)parcels[i];
        potd = getIntent().getStringExtra(PZL_KEY);
        
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        studentDir = new File(prefs.getString(DIR_KEY, null));
        email = prefs.getString(EMAIL_KEY, null);
        token = prefs.getString(TOKEN_KEY, null);
        marker = prefs.getInt(subpath, -1);
        
        refresh();
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
                    updateCounts(manifest);
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
        if (peedee != null)
            peedee.dismiss();
        
        File filesDir = new File(studentDir, "files");
        File cache = new File(filesDir, subpath + ".json");
        try {
            JSONObject respObject = null;
            JSONArray delta, items = null;
            JSONParser jsonParser = new JSONParser();
            
            if (marker < 0) {
                cache.delete();
            }
            
            manifest = getManifest(filesDir, topics);            
            if (cache.exists() && marker > 0) {
                FileReader fr = new FileReader(cache);
                items = (JSONArray)jsonParser.parse(fr);
                Log.d(TAG, "Cache Size: " + items.size());
                manifest.parse(items, false);
            } else {
                items = new JSONArray();
            }
            
            if (resultCode == RESULT_OK) {
                respObject = (JSONObject)jsonParser.parse(resultData);
                delta = (JSONArray)respObject.get(ITEMS_KEY);
                Log.d(TAG, "Delta Size: " + delta.size());
                if (delta.size() > 0) {
                    manifest.parse(delta, true);
                }
                
                marker = ((Long)respObject.get(MARKER_KEY)).intValue();
                SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
                Editor edit = prefs.edit();
                edit.putInt(subpath, marker);
                edit.commit();
                Log.d(TAG, "marker: " + marker);
                if (marker > 0) {
                    FileWriter fw = new FileWriter(cache);
                    fw.write(manifest.toJSONArray());
                    fw.close();                
                }
                
           } else {
                Toast.makeText(getApplicationContext(), 
                    "No Internet, continuing with cached content", 
                    Toast.LENGTH_LONG).show();
            }            
            updateCounts(manifest);
        } catch (NullPointerException npe) {
            handleError("Refresh task failed ", "Null Pointer Exception");
        } catch (Exception e) {
            handleError("Refresh task failed ", e.getMessage());
        }
    }
    
    protected abstract BaseManifest getManifest(File studentDir, 
        Topic[] topics) throws Exception;
    
    protected abstract void updateCounts(BaseManifest manifest);
    
    public abstract void onClick(View v);

    protected void launchListActivity(Quij[] items, String title, int categoryId) {
        Intent listIntent = new Intent(getApplicationContext(),
            com.gradians.collect.ListActivity.class);
        listIntent.putExtra(NAME_KEY, studentDir.getPath());
        listIntent.putExtra(TAG, items);
        listIntent.putExtra(TAG_ID, title);
        listIntent.putExtra(ID_KEY, categoryId);
        startActivityForResult(listIntent, LIST_ACTIVITY_REQUEST_CODE);
    }

    public void launchHelpActivity() {
        Intent helpIntent = new Intent(this.getApplicationContext(),
            com.gradians.collect.HelpActivity.class);
        startActivity(helpIntent);
    }

    protected void refresh() {        
        if (peedee == null) {
            peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
            peedee.setTitle("Synchronizing");
            peedee.setMessage("Please wait...");
            peedee.setIndeterminate(true);
            peedee.setIcon(ProgressDialog.STYLE_SPINNER);
            peedee.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            peedee.show();
        } else if (!peedee.isShowing()) {
            peedee.show();
        }
        Uri src = Uri.parse(String.format(REFRESH_URL, 
            WEB_APP_HOST_PORT, subpath, email, token, marker));
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this, REFRESH_WS_TASK_REQUEST_CODE).
            execute(new Download[] { download });
    }
    
    protected void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }
    
    protected BaseManifest manifest;
    protected ProgressDialog peedee;
    protected String email, token;
    protected int marker;
    protected File studentDir;
    protected String potd;
    protected Topic[] topics;
    protected String subpath;
    
    private int layoutId;
    
    private final String REFRESH_URL = "http://%s/tokens/refresh/%s?email=%s&token=%s&marker=%s";
}

