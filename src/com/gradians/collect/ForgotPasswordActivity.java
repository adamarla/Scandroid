package com.gradians.collect;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class ForgotPasswordActivity extends Activity implements ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
    }

    
    public void action(View v) {
        EditText etEmail = (EditText)findViewById(R.id.etEmail);
        final String email = etEmail.getText().toString();
        boolean error = false;
        if (TextUtils.isEmpty(email)) {
            etEmail.setError(getString(R.string.error_field_required));
            error = true;
        } else if (!email.contains("@")) {
            etEmail.setError(getString(R.string.error_invalid_email));
            error = true;
        }
        if (error) {
            etEmail.requestFocus();
            return;
        }

        // Check if email address is taken
        Uri src = Uri.parse(String.format(RESET_PWD_URL, 
            WEB_APP_HOST_PORT, email));
        Download download = new Download(null, src, null);
        String params = String.format("account[email]=%s", email);        
        HttpCallsAsyncTask hcat = new HttpCallsAsyncTask(this, 
            RESET_PWD_TASK_REQUEST_CODE);
        hcat.setUpdateParams(params);
        hcat.execute(new Download[] { download });
        peedee = new ProgressDialog(this, R.style.RobotoDialogTitleStyle);
        peedee.setMessage("Re-Sending...");
        peedee.show();
    }

    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        peedee.dismiss();
        if (requestCode == RESET_PWD_TASK_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                EditText etEmail = (EditText)findViewById(R.id.etEmail);
                final String email = etEmail.getText().toString();
                if (resultData.contains("Invalid")) {
                    etEmail.setError(getString(R.string.error_unseen_email));
                    etEmail.requestFocus();
                } else {
                    AlertDialog.Builder builder = null;
                    try {
                        builder = new AlertDialog.Builder(this,
                            R.style.RobotoDialogTitleStyle);        
                    } catch (NoSuchMethodError e) {
                        Log.e(TAG, "Older SDK, using old Builder");
                        builder =  new AlertDialog.Builder(this);
                    }
                    builder.setMessage("Your password has been reset");
                    builder.setPositiveButton(android.R.string.ok,             
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent intent = new Intent();
                                intent.putExtra(EMAIL_KEY, email);
                                setResult(RESULT_FIRST_USER, intent);
                                finish();
                            }
                        });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            } else {
                Toast.makeText(getApplicationContext(),
                    "Sorry, Network Error. Please try again",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private final String RESET_PWD_URL = "http://%s/reset/password";
    private ProgressDialog peedee;    
    
}
