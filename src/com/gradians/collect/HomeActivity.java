package com.gradians.collect;

import org.json.simple.JSONArray;
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
        int id = item.getItemId();
        if (item.getItemId() == R.id.action_sign_out)
            resetPreferences();
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
                    JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
                    long puzzleId = (Long)respObject.get("pzl_id");
                    int count = ((Long)respObject.get("count")).intValue();
                    
                    initialize(name, count > 0);
                    // TODO - launch QOTD Activity
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
        switch (v.getId()) {
        case R.id.btnQotd:
            break;
        case R.id.btnSchool:
            launchSchoolStuff();
            break;
        case R.id.btnAsk:
            break;
        case R.id.btnBrowse:
        }
    }
    
    private void launchQOTD() {
        
    }
    
    private void launchSchoolStuff() {
        SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
        String email = prefs.getString(EMAIL_KEY, null);
        String token = prefs.getString(TOKEN_KEY, null);
        
        Intent intent = new Intent(getApplicationContext(),
            com.gradians.collect.MainActivity.class);
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
        
        MainButton qotd, school, ask, browse;
        qotd = (MainButton)findViewById(R.id.btnQotd);
        school = (MainButton)findViewById(R.id.btnSchool);
        ask = (MainButton)findViewById(R.id.btnAsk);
        browse = (MainButton)findViewById(R.id.btnBrowse);
        
        qotd.setText("QOTD", R.drawable.ic_action_unread);
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

    private final String REFRESH_URL = "http://%s/tokens/refresh?email=%s&token=%s";
    
}

class MainButton extends RelativeLayout {

    public MainButton (Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater)context.
            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(R.layout.layout_big_btn, this, true);
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
//    
//    @Override
//    protected int[] onCreateDrawableState(int extraSpace) {
//        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
//        if (!active) {
//            mergeDrawableStates(drawableState, STATE_ACTIVE);
//        }
//        return drawableState;
//    }
//    
//    public void setIsActive(boolean b) { active = b; }
//    
//    public boolean isActive() { return active; }
//    
//    private boolean active = true;
//    
//    private static final int[] STATE_ACTIVE = {R.attr.state_active};
    
}
