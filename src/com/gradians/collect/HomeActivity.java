package com.gradians.collect;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
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

public class HomeActivity extends Activity implements ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        checkAuth();
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
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    String json = data.getStringExtra(TAG);
                    JSONParser jsonParser = new JSONParser();
                    JSONObject respObject = (JSONObject)jsonParser.parse(json);
                    
                    String name, email, token;
                    token = (String)respObject.get(TOKEN_KEY);
                    name = (String)respObject.get(NAME_KEY);
                    email = (String)respObject.get(EMAIL_KEY);
                    int count = ((Long)respObject.get("count")).intValue();
                    
                    setPreferences(email, token);
                    initialize(name, count > 0);
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
        }
    }    

    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == REFRESH_TASK_REQUEST_CODE) {
            if (peedee != null) peedee.dismiss();
            if (resultCode == RESULT_OK) {
                try {
                    JSONParser jsonParser = new JSONParser();
                    JSONObject respObject = (JSONObject)jsonParser.parse(resultData);
                    
                    String name;
                    name = (String)respObject.get(NAME_KEY);
                    respObject.get(QOTD_KEY);
                    int count = ((Long)respObject.get(COUNT_KEY)).intValue();                    
                    initialize(name, count > 0);
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
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        String email = prefs.getString(EMAIL_KEY, null);
        String token = prefs.getString(TOKEN_KEY, null);
        Class<?> cls = null; 
        switch (v.getId()) {
        case R.id.btnQotd:
            cls = com.gradians.collect.QotdActivity.class;
            break;
        case R.id.btnSchool:
            cls = com.gradians.collect.TeacherActivity.class;            
            break;
        case R.id.btnAsk:
            break;
        case R.id.btnBrowse:
        }
        Intent intent = new Intent(getApplicationContext(), cls);
        intent.putExtra(EMAIL_KEY, email);
        intent.putExtra(TOKEN_KEY, token);
        startActivity(intent);
    }
    
    private void checkAuth() {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        String email = prefs.getString(EMAIL_KEY, null);
        String token = prefs.getString(TOKEN_KEY, null);
        if (token == null) {
            initiateAuthActivity();
        } else {
            peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
            peedee.setTitle("Fetching...");
            peedee.setMessage("Please wait...");
            peedee.setIndeterminate(true);
            peedee.setIcon(ProgressDialog.STYLE_SPINNER);
            peedee.show();
            Uri src = Uri.parse(String.format(REFRESH_URL, 
                WEB_APP_HOST_PORT, email, token));
            Download download = new Download(null, src, null);
            new HttpCallsAsyncTask(this, 
                REFRESH_TASK_REQUEST_CODE).execute(new Download[] { download });
        }
    }

    private void initiateAuthActivity() {
        resetPreferences();
        Intent checkAuthIntent = new Intent(this, 
            com.gradians.collect.LoginActivity.class);
        checkAuthIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(checkAuthIntent, AUTH_ACTIVITY_REQUEST_CODE);
    }

    private void initialize(String name, boolean gotSchool) {
        setTitle(String.format("Hi %s", name));
        
        HugeButton qotd, school, ask, browse;
        qotd = (HugeButton)findViewById(R.id.btnQotd);
        school = (HugeButton)findViewById(R.id.btnSchool);
        ask = (HugeButton)findViewById(R.id.btnAsk);
        browse = (HugeButton)findViewById(R.id.btnBrowse);
        
        qotd.setText("Question\nof the\nDay", R.drawable.ic_action_unread);
        school.setText("School Stuff", R.drawable.ic_action_sent);
        school.setEnabled(gotSchool);
        ask.setText("Ask a Ques", R.drawable.ic_action_chat);
        ask.setEnabled(false);
        browse.setText("Browse", R.drawable.ic_action_rotate_right);
        browse.setEnabled(false);
        
        try {
            ((TextView)findViewById(R.id.tvVers)).setText(getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception e) { }
    }

    private void setPreferences(String email, String token) {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putString(EMAIL_KEY, email);
        edit.putString(TOKEN_KEY, token);
        edit.commit();
    }

    private void resetPreferences() {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }
    
    private void handleError(String error, String message) {
        Log.e(TAG, error + " " + message);
    }

    private ProgressDialog peedee;

    private final String REFRESH_URL = "http://%s/tokens/refresh/home?email=%s&token=%s";
    
}

class HugeButton extends RelativeLayout {

    public HugeButton (Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater)context.
            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(R.layout.layout_btn_huge, this, true);
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
