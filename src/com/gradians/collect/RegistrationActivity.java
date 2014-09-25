package com.gradians.collect;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

public class RegistrationActivity extends FragmentActivity implements
        ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // Check that the activity is using the layout version with
        // the fragment_container FrameLayout
        if (findViewById(R.id.flRegistration) != null) {

            // However, if we're being restored from a previous state,
            // then we don't need to do anything and should return or else
            // we could end up with overlapping fragments.
            if (savedInstanceState != null) {
                return;
            }
            displayForm();
        }
    }

    public void action(View view) {
        switch (view.getId()) {
        case R.id.btnRegister:
            register();
            break;
        default:
        }
    }

    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == VALIDATE_INPUTS_TASK_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                peedee.dismiss();
                if (resultData.equals("OK")) {
                    String params = String.format(
                            "student[name]=%s&"
                          + "student[jaal]=&"
                          + "student[mobile]=true&"
                          + "student[account_attributes][email]=%s&"
                          + "student[account_attributes][password]=%s&"
                          + "student[account_attributes][password_confirmation]=%s",
                          inputs[0], inputs[1], inputs[2], inputs[3]);
                      Uri src = Uri.parse(String.format(REGISTER_URL, WEB_APP_HOST_PORT));
                      Download download = new Download(null, src, null);
                      
                      peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
                      peedee.setTitle("Registering user...");
                      peedee.setMessage("please wait");
                      peedee.show();
                      
                      HttpCallsAsyncTask hcat = new HttpCallsAsyncTask(this,
                          REGISTER_TASK_REQUEST_CODE);
                      hcat.setUpdateParams(params);
                      hcat.execute(new Download[] { download });
                      
                } else if (resultData.equals("EMAIL")) {
                    EditText etEmail = (EditText)findViewById(R.id.etEmail);
                    etEmail.requestFocus();
                    etEmail.setError(getString(R.string.error_email_taken));
                } else if (resultData.equals("GROUP_CODE")) {
                    EditText etGroup = (EditText)findViewById(R.id.etGroupCode);
                    etGroup.requestFocus();
                    etGroup.setError(getString(R.string.error_invalid_group_code));
                }
            } else {
                Toast.makeText(getApplicationContext(),
                    "Sorry an error occured during validation. Please try again",
                    Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REGISTER_TASK_REQUEST_CODE) {
            peedee.dismiss();
            if (resultCode == RESULT_OK) {
                if (saveToken(resultData)) {
                    Fragment newFragment = new PostRegistrationFragment();
                    FragmentTransaction transaction =
                            getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.flRegistration, newFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                    
                    if (groupCode == null) {
                        launchMainActivity(true);
                    } else {
                        peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
                        peedee.setTitle("Checking group membership...");
                        peedee.setMessage("please wait");
                        peedee.show();
                        
                        Uri src = Uri.parse(String.format(MATCH_URL, 
                            WEB_APP_HOST_PORT, inputs[1], groupCode));
                        Download download = new Download(null, src, null);
                        HttpCallsAsyncTask hcat = new HttpCallsAsyncTask(this, 
                            MATCH_NAME_TASK_REQUEST_CODE);
                        hcat.execute(new Download[] { download });
                    }
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Sorry an error while saving. Please try again",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(),
                    "Sorry an error occured during registration. Please try again",
                    Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == MATCH_NAME_TASK_REQUEST_CODE) {
            peedee.dismiss();
            if (resultCode == RESULT_OK) {
                JSONParser jsonParser = new JSONParser();
                JSONArray matches = null;
                try {
                    matches = (JSONArray) jsonParser.parse(resultData);
                } catch (Exception e) {}

                if (matches == null || matches.size() == 0) {
                    launchMainActivity(false);
                } else {
                    String[] items = new String[matches.size()];
                    final String[] ids = new String[matches.size()];
                    JSONObject student = null;
                    for (int i = 0; i < matches.size(); i++) {
                        student = (JSONObject)((JSONObject)matches.get(i)).get("student");
                        items[i] = String.format("%s %s", 
                            student.get("first_name"), student.get("last_name"));
                        ids[i] = String.valueOf((Long) student.get("id"));
                    }
                    
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Please confirm your name...");
                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            claim(ids[which]);
                        }
                    });
                    builder.setCancelable(false);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            } else {
                launchMainActivity(false);
            }
        } else if (requestCode == CLAIM_ACCT_TASK_REQUEST_CODE) {
            peedee.dismiss();
            launchMainActivity(resultCode == RESULT_OK);
        }
    }

    private boolean saveToken(String json) {
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = null;
        try {
            jsonObject = (JSONObject) jsonParser.parse(json);
        } catch (Exception e) { }

        if (jsonObject != null) {
            SharedPreferences prefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
            Editor edit = prefs.edit();
            edit.putString(TOKEN_KEY, (String) jsonObject.get(TOKEN_KEY));
            edit.putString(NAME_KEY, (String) jsonObject.get(NAME_KEY));
            edit.putString(EMAIL_KEY, (String) jsonObject.get(EMAIL_KEY));
            return edit.commit();
        } else {
            return false;
        }
    }

    private void launchMainActivity(boolean enrolled) {
        // Launch Home Activity
        Intent mainIntent = new Intent(getApplicationContext(),
            com.gradians.collect.HomeActivity.class);
        mainIntent.putExtra(TAG, enrolled);
        startActivity(mainIntent);

        // Kill Parent Activity
        Intent responseIntent = new Intent();
        setResult(RESULT_FIRST_USER, responseIntent);
        finish();
    }

    private void displayForm() {
        // Create fragment and give it an argument specifying the
        // article it should show
        Fragment newFragment = new RegistrationFormFragment();
        Bundle args = new Bundle();
        newFragment.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(R.id.flRegistration, newFragment);

        // Commit the transaction
        transaction.commit();
    }

    private void register() {

        EditText[] etInputs = new EditText[4];
        etInputs[0] = (EditText) findViewById(R.id.etName);
        etInputs[1] = (EditText) findViewById(R.id.etEmail);
        etInputs[2] = (EditText) findViewById(R.id.etPwd);
        etInputs[3] = (EditText) findViewById(R.id.etPwdConf);

        boolean cancel = false;
        View focusView = null;

        inputs = new String[4];
        for (int i = 0; i < etInputs.length; i++) {
            inputs[i] = etInputs[i].getText().toString();
            if (TextUtils.isEmpty(inputs[i])) {
                etInputs[i].setError(getString(R.string.error_field_required));
                focusView = etInputs[i];
                cancel = true;
            } else if (i == 1 && !inputs[i].contains("@")) {
                etInputs[i].setError(getString(R.string.error_invalid_email));
                focusView = etInputs[i];
                cancel = true;
            } else if (i > 1 && inputs[i].length() < 6) {
                etInputs[i].setError(getString(R.string.error_invalid_password));
                focusView = etInputs[i];
                cancel = true;
            } else if (i == 3 && !inputs[i].equals(inputs[i - 1])) {
                etInputs[i].setError(getString(R.string.error_mismatch_password));
                focusView = etInputs[i];
                cancel = true;
            }
            if (cancel)
                break;
        }

        if (!cancel) {
            EditText etGroupCode = (EditText) findViewById(R.id.etGroupCode);
            if (etGroupCode.getVisibility() == View.VISIBLE) {
                groupCode = etGroupCode.getText().toString();
                if (TextUtils.isEmpty(groupCode)) {
                    etGroupCode.setError(getString(R.string.error_field_required));
                    focusView = etGroupCode;
                    cancel = true;
                }
            }
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Check if email address is taken
            etInputs[1] = (EditText) findViewById(R.id.etEmail);
            final String email = etInputs[1].getText().toString();
            Uri src = Uri.parse(String.format(VALIDATE_URL, 
                WEB_APP_HOST_PORT, email,
                groupCode == null ? "" : "&group_code=" + groupCode));
            Download download = new Download(null, src, null);
            HttpCallsAsyncTask hcat = new HttpCallsAsyncTask(this, 
                VALIDATE_INPUTS_TASK_REQUEST_CODE);
            hcat.execute(new Download[] { download });
            peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
            peedee.setTitle("Validating input...");
            peedee.setMessage("please wait");
            peedee.show();
        }
    }

    private void claim(String id) {
        Uri src = Uri.parse(String.format(CLAIM_URL, 
            WEB_APP_HOST_PORT, inputs[1], id));
        Download download = new Download(null, src, null);
        HttpCallsAsyncTask hcat = new HttpCallsAsyncTask(this, 
            CLAIM_ACCT_TASK_REQUEST_CODE);
        hcat.execute(new Download[] { download });
        peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
        peedee.setTitle("Hang on");
        peedee.setMessage("processing");
        peedee.show();
    }

    private String groupCode;
    private String[] inputs;
    private ProgressDialog peedee;

    private final String REGISTER_URL = "http://%s/register/student";
    private final String VALIDATE_URL = "http://%s/tokens/validate?email=%s%s";
    private final String MATCH_URL = "http://%s/tokens/match?email=%s&sektion=%s";
    private final String CLAIM_URL = "http://%s/tokens/claim?email=%s&target_id=%s";
    
}

class RegistrationFormFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        ViewGroup rootView = (ViewGroup)inflater.inflate(
            R.layout.fragment_registration, container, false);
        rootView.findViewById(R.id.etGroupCode).setVisibility(View.INVISIBLE);

        // Inflate the layout for this fragment
        return rootView;
    }
}

class PostRegistrationFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_post_registration, container,
                false);
    }
}
