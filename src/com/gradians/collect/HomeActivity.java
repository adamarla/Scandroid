package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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

public class HomeActivity extends Activity implements IConstants, ITaskResult {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Possible work around for market launches. See
        // http://code.google.com/p/android/issues/detail?id=2373
        // for more details. Essentially, the market launches the main activity
        // on top of other activities.
        // we never want this to happen. Instead, we check if we are the root
        // and if not, we finish.
        if (!isTaskRoot()) {
            final Intent intent = getIntent();
            final String intentAction = intent.getAction();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && intentAction != null
                && intentAction.equals(Intent.ACTION_MAIN)) {
                Log.w(TAG, "Main Activity is not the root.  Finishing instead of launching.");
                finish();
                return;
            }
        }
        
        setContentView(R.layout.activity_home);
        checkAuth();

        if (savedInstanceState != null)
            subpath = savedInstanceState.getString("subpath");
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        outState.putString("subpath", subpath);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        if (item.getItemId() == R.id.action_sign_out)
            initiateAuthActivity();
        else if (item.getItemId() == R.id.action_settings) {
            String vers = null;
            try {
                vers = String.format("Scanbot %s", getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName);
            } catch (Exception e) { }
            
            String[] settings = new String[2];
            settings[0] = "Current Balance: " + balance + "₲";
            settings[1] = "Version: " + vers;
            
            AlertDialog.Builder builder = null;
            try {
                builder = new AlertDialog.Builder(this,
                    R.style.RobotoDialogTitleStyle);        
            } catch (NoSuchMethodError e) {
                Log.e(TAG, "Older SDK, using old Builder");
                builder =  new AlertDialog.Builder(this);
            }        
            builder.setTitle("Settings").setItems(settings, null);
            builder.show();
        } else {
            checkAuth();
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == REFRESH_TASK_REQUEST_CODE) {
            if (splashDialog != null) splashDialog.dismiss();
            try {
                if (resultCode == RESULT_OK) {
                    initialize(resultData);
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(getApplicationContext(), 
                        "No Internet :/ soldiering on regardless", 
                        Toast.LENGTH_LONG).show();
                    initialize(retrieveResponse());
                } else if (resultCode == RESULT_FIRST_USER) {
                    this.finish();
                }
            } catch (Exception e) {
                handleError("Oops, Verify auth task failed", e.getMessage());
            }
        } else if (requestCode == REFRESH_WS_TASK_REQUEST_CODE) {
            if (refreshDialog != null) refreshDialog.dismiss();
            File filesDir = new File(studentDir, FILES_DIR_NAME);
            File cache = new File(filesDir, subpath + ".json");
            
            BaseManifest manifest = null;
            int titleId;
            if (subpath.equals(DOUBTS)) {
                manifest = doubtManifest;
                titleId = R.string.title_activity_ask_question;
            } else if (subpath.equals(QUESTIONS)) {
                manifest = practiceManifest;
                titleId = R.string.title_activity_practise;
            } else {
                manifest = schoolStuffManifest;
                titleId = R.string.title_activity_teacher;
            }
            
            Markers markers = new Markers(studentDir);
            int marker = markers.get(subpath) == null ? -1 :                
                Integer.parseInt(markers.get(subpath));
            String title = getApplicationContext().getResources().getString(titleId);
            try {
                JSONObject respObject = null;
                JSONArray delta, items = null;
                JSONParser jsonParser = new JSONParser();
                
                if (marker < 0) {
                    cache.delete();
                }
                
                if (cache.exists()) {
                    FileReader fr = new FileReader(cache);
                    items = (JSONArray)jsonParser.parse(fr);
                    manifest.parse(items, false);
                } else {
                    items = new JSONArray();
                }
                
                if (resultCode == RESULT_OK) {
                    respObject = (JSONObject)jsonParser.parse(resultData);
                    delta = (JSONArray)respObject.get(ITEMS_KEY);
                    if (delta.size() > 0) {
                        manifest.parse(delta, true);
                    }
                    
                    marker = ((Long)respObject.get(MARKER_KEY)).intValue();
                    markers.set(subpath, String.valueOf(marker));
                    markers.commit();
                    
                    FileWriter fw = new FileWriter(cache);
                    fw.write(manifest.toJSONArray());
                    fw.close();                    
               } else {
                    Toast.makeText(getApplicationContext(), 
                        "No Internet, continuing with cached content", 
                        Toast.LENGTH_LONG).show();
                }                
                launchListActivity(manifest.all(), title);
            } catch (NullPointerException npe) {
                handleError("Refresh task failed ", "Null Pointer Exception");
            } catch (Exception e) {
                handleError("Refresh task failed ", e.getMessage());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    String json = data.getStringExtra(TAG);
                    initialize(json);
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
                BaseManifest manifest = subpath.equals(QUESTIONS) ?
                    practiceManifest : schoolStuffManifest;
                try {
                    Parcelable[] parcels = data.getParcelableArrayExtra(TAG_ID);
                    Question[] questions = new Question[parcels.length];
                    for (int i = 0; i < parcels.length; i++) {
                        questions[i] = (Question)parcels[i];
                    }
                    manifest.update(questions);
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

    public void onClick(View v) {
        try {
            switch (v.getId()) {
            case R.id.btnBrowse:
                subpath = QUESTIONS;
                refresh();
                break;
            case R.id.btnSchool:
                subpath = WORKSHEETS;
                refresh();
                break;
            case R.id.btnAsk:
                subpath = DOUBTS;
                refresh();
                break;
            default:
            }
        } catch (Exception e) {
            handleError("Oops, Activity launch failed", e.getMessage());
        }
    }
    
    private void launchListActivity(Quij[] items, String title) {
        if (subpath.equals(DOUBTS)) {
            Intent listIntent = new Intent(getApplicationContext(),
                com.gradians.collect.AskQuestionActivity.class);
            listIntent.putExtra(NAME_KEY, studentDir.getPath());
            listIntent.putExtra(TAG, items[0].getQuestions());
            listIntent.putExtra(TAG_ID, title);
            startActivityForResult(listIntent, DOUBTS_ACTIVITY_REQUEST_CODE);
        } else {
            Intent listIntent = new Intent(getApplicationContext(),
                com.gradians.collect.ListActivity.class);
            listIntent.putExtra(NAME_KEY, studentDir.getPath());
            listIntent.putExtra(TAG, items);
            listIntent.putExtra(TAG_ID, title);
            startActivityForResult(listIntent, LIST_ACTIVITY_REQUEST_CODE);
        }
    }
    
    private void refresh() {
        if (refreshDialog == null)
            refreshDialog = new ProgressDialog(this, 
                R.style.RobotoDialogTitleStyle);
        refreshDialog.setMessage("Synchronizing...");
        refreshDialog.setIndeterminate(true);
        refreshDialog.getWindow().setBackgroundDrawable(
            new ColorDrawable(Color.TRANSPARENT));
        refreshDialog.show();
        Markers markers = new Markers(studentDir);
        String marker = markers.get(subpath) == null ? 
            "-1" : markers.get(subpath);
        Uri src = Uri.parse(String.format(REFRESH_URL, 
            WEB_APP_HOST_PORT, subpath, email, token, marker));
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this, REFRESH_WS_TASK_REQUEST_CODE).
            execute(new Download[] { download });
    }
    
    private void checkAuth() {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        String token = prefs.getString(TOKEN_KEY, null);
        if (token == null) {
            initiateAuthActivity();
        } else {
            String email = prefs.getString(EMAIL_KEY, null);
            initiateVerifActivity(email, token);
        }
    }

    private void initiateAuthActivity() {
        resetPreferences();
        Intent checkAuthIntent = new Intent(this, 
            com.gradians.collect.LoginActivity.class);
        checkAuthIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(checkAuthIntent, 
            AUTH_ACTIVITY_REQUEST_CODE);
    }
    
    @SuppressLint("InflateParams")
    private void initiateVerifActivity(String email, String token) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this,
            R.style.SplashScreenStyle);        
        LayoutInflater inflater = this.getLayoutInflater();
        View splashView = inflater.inflate(R.layout.layout_splash, null);
        builder.setView(splashView);
        splashDialog = builder.create();
        splashDialog.setCancelable(false);
        splashDialog.show();
        
        String refreshUrl = "http://%s/tokens/verify?email=%s&token=%s";
        Uri src = Uri.parse(String.format(refreshUrl, WEB_APP_HOST_PORT, email, token));
        Download download = new Download(null, src, null);
        new HttpCallsAsyncTask(this, REFRESH_TASK_REQUEST_CODE).
            execute(new Download[] { download });
    }

    private void initialize(String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject = (JSONObject)jsonParser.parse(json);
        
        boolean enrolled;
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);
        id = String.valueOf((Long)respObject.get(ID_KEY));
        balance = ((Long)respObject.get(BALANCE_KEY)).intValue();
        enrolled = (Boolean)respObject.get(ENRL_KEY);
        
        studentDir = new File(getExternalFilesDir(null), 
            email.replace('@', '.'));
        studentDir.mkdir();
        (new File(studentDir, PROBLEMS_DIR_NAME)).mkdir();
        (new File(studentDir, FILES_DIR_NAME)).mkdir();
        
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putString(NAME_KEY, name);
        edit.putString(EMAIL_KEY, email);
        edit.putString(TOKEN_KEY, token);
        edit.putString(ID_KEY, id);
        edit.putString(DIR_KEY, studentDir.getPath());
        edit.putInt(BALANCE_KEY, balance);
        edit.commit();        
        setTitle(String.format("Hi %s", name));
        
        HugeButton school, ask, browse;
        school = (HugeButton)findViewById(R.id.btnSchool);
        ask = (HugeButton)findViewById(R.id.btnAsk);
        browse = (HugeButton)findViewById(R.id.btnBrowse);
        
        browse.setText(R.string.home_button_browse, R.drawable.ic_action_rotate_right);
        school.setText(R.string.home_button_teacher, R.drawable.ic_action_sent);
        school.setEnabled(enrolled);
        ask.setText(R.string.home_button_aaq, R.drawable.ic_action_chat);
        ask.setEnabled(true);
        
        topics = getTopics((JSONArray)respObject.get(TOPICS_KEY)); 
        saveResponse(json);
        
        practiceManifest = new QuestionManifest(studentDir, topics);
        schoolStuffManifest = new QuizManifest(studentDir, topics);
        doubtManifest = new DoubtManifest(studentDir, topics);
    }

    private void resetPreferences() {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }
    
    private void saveResponse(String json) throws Exception {
        FileOutputStream fos = 
            openFileOutput(filename, Context.MODE_PRIVATE);
        fos.write(json.getBytes());
        fos.close();
    }
    
    private String retrieveResponse() throws Exception {
        FileInputStream fis = openFileInput(filename);
        StringBuffer sb = new StringBuffer();
        byte[] buffer = new byte[1024];
        int bytesRead = 0;
        while ((bytesRead = fis.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, bytesRead));
        }
        fis.close();
        return sb.toString();
    }
    
    private Topic[] getTopics(JSONArray items) {
        ArrayList<Topic> topics = new ArrayList<Topic>();
        JSONObject item;
        for (int i = 0; i < items.size(); i++) {
            item = (JSONObject)items.get(i);            
            topics.add(new Topic(
                (Long)item.get(ID_KEY),
                (String)item.get(NAME_KEY),
                (Long)item.get(VERT_ID_KEY),
                (String)item.get(VERT_NAME_KEY)));
        }
        return topics.toArray(new Topic[topics.size()]);
    }
    
    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }
    
    private BaseManifest practiceManifest, schoolStuffManifest, doubtManifest;
 
    private File studentDir;
    private String subpath, name, email, token, id;
    private Topic[] topics;
    private int balance;
    private Dialog splashDialog;
    private ProgressDialog refreshDialog;
    private final String filename = "init.json";
    
    private final String QUESTIONS = "qs", WORKSHEETS = "ws", DOUBTS = "dbt";
    private final String REFRESH_URL = 
        "http://%s/tokens/refresh/%s?email=%s&token=%s&marker=%s";    
}

class HugeButton extends RelativeLayout {

    public HugeButton (Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater)context.
            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(R.layout.layout_btn_huge, this, true);
    }
    
    public void setText(int textId, int drawable) {
        ((TextView)findViewById(R.id.tvLabel)).setText(textId);
        ((ImageView)findViewById(R.id.ivIcon)).setImageResource(drawable);
    }
    
    public void setText(String text, int drawable) {
        ((TextView)findViewById(R.id.tvLabel)).setText(text);
        ((ImageView)findViewById(R.id.ivIcon)).setImageResource(drawable);
    }
    
    public void setCount(int count, String text, int drawable) {
        ((TextView)findViewById(R.id.tvLabel)).setText(text);
        ((TextView)findViewById(R.id.tvCount)).setText(String.valueOf(count));
        ((TextView)findViewById(R.id.tvCount)).setVisibility(View.VISIBLE);
        ((ImageView)findViewById(R.id.ivIcon)).setImageResource(drawable);
    }
    
}
