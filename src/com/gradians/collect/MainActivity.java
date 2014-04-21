package com.gradians.collect;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends FragmentActivity implements ITaskCompletedListener, IConstants, OnChildClickListener, 
    OnGroupClickListener, OnClickListener {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initApp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Manifest manifest = getManifest();        
        if (manifest != null) 
            try {
                manifest.commit();
            } catch (Exception e) {
                handleError("Oops.. persist file thing failed", e.getMessage());
            }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Manifest manifest = null;
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Intent uploadIntent = new Intent(context,
                        com.gradians.collect.ImageUploadService.class);
                startService(uploadIntent);
                manifest = getManifest();
                manifest.markAsSent();
                manifest.notifyDataSetChanged();                
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), 
                        "Oops.. image capture failed. Please try again",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    manifest = new Manifest(this, data.getStringExtra(TAG), this);
                    setPreferences(manifest);
                    setManifest(manifest);
                    Toast.makeText(context, 
                            "Select question(s), then take a picture!", 
                            Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    Log.e(TAG, e.getMessage());
                    handleError("Oops, Auth activity request failed", 
                            data.getStringExtra(TAG));
                }                
            } else if (resultCode != Activity.RESULT_CANCELED){
                Toast.makeText(getApplicationContext(),
                        "Oops.. auth check failed. Please try again",
                        Toast.LENGTH_SHORT).show();
            } else {
                this.finish();
            }
        }
    }
    
    public void onTaskResult(int requestCode, int resultCode, String resultData) {        
        Manifest manifest = null;
        if (requestCode == VERIFY_AUTH_TASK_RESULT_CODE) {
            peedee.dismiss();
            if (resultCode == RESULT_OK) {
                try {
                    manifest = new Manifest(this, resultData, this);
                    setManifest(manifest);
                } catch (Exception e) { 
                    handleError("Oops, Verify auth task failed", resultData);
                }
            } else {
                initiateAuthActivity();
            }
        } else if (requestCode == UPLOAD_IMAGE_TASK_RESULT_CODE) {
            peedee.dismiss();
            if (resultCode == RESULT_OK) {
                manifest = getManifest();
                manifest.markAsSent();
                manifest.notifyDataSetChanged();
            } else {
                handleError("Uh-oh, image upload failed", resultData);
            }
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
      case R.id.action_sign_out:
          initiateAuthActivity();
        break;
      default:
        break;
      }
      return super.onOptionsItemSelected(item);
    }    
    
    @Override
    public boolean onGroupClick(ExpandableListView parent, View v,
            int groupPosition, long id) {
        return false;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
            int groupPosition, int childPosition, long id) {
        return false;
    }
    
    @Override
    public void onClick(View v) {
        Manifest manifest = getManifest();
        manifest.checkUncheck(((TextView)v).getText().toString());
        manifest.notifyDataSetChanged();
    }

    public void initiateCameraActivity(View view) {
        Manifest manifest = getManifest();
        String[] selected = manifest.getSelected();        
        if (selected.length == 0) {
            Toast t = Toast.makeText(context, 
                    "Select at least one question to send its solution", 
                    Toast.LENGTH_SHORT);
            t.setGravity(Gravity.CENTER, 0, 0);
            t.show();
            return;
        }        
        String grIDs = "";
        for (String grID : selected) grIDs = grIDs + grID + "-";
        grIDs = grIDs.substring(0, grIDs.length()-1);
        
        String imageFileName = String.format("GR.%s.%s", grIDs, IMG_EXT);
        
        Intent takePictureIntent = new Intent(context,
                com.gradians.collect.CameraActivity.class);
        takePictureIntent.putExtra(TAG, imageFileName);
        startActivityForResult(takePictureIntent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);           
    }
    
    private void initiateAuthActivity() {
        resetPreferences();
        Intent checkAuthIntent = new Intent(context, 
                com.gradians.collect.LoginActivity.class);
        checkAuthIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(checkAuthIntent, AUTH_ACTIVITY_REQUEST_CODE);
    }
    
    private void resetPreferences() {
        SharedPreferences prefs = getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();        
    }

    private void setPreferences(Manifest manifest) {
        SharedPreferences prefs = this.getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);
        Editor edit = prefs.edit();
        edit.putString(TOKEN_KEY, manifest.getAuthToken());
        edit.putString(NAME_KEY, manifest.getName());
        edit.putString(EMAIL_KEY, manifest.getEmail());
        edit.commit();       
    }

    private void initApp() {
        context = getApplicationContext();
        SharedPreferences prefs = getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);
        
        String token = prefs.getString(TOKEN_KEY, null);        
        if (token == null) {
            initiateAuthActivity();
        } else {
            peedee = ProgressDialog.show(this, "Initializing Scanbot", "Please wait...");
            peedee.setIndeterminate(true);
            peedee.setIcon(ProgressDialog.STYLE_SPINNER);
            
            String email = prefs.getString(EMAIL_KEY, null);
            new VerificationTask(email, token, this).execute();
        }
    }
    
    private Manifest getManifest() {
        ExpandableListView elv = (ExpandableListView)this.findViewById(R.id.elvQuiz);
        return (Manifest)elv.getExpandableListAdapter();
    }

    private void setManifest(Manifest manifest) {
        setTitle(String.format(TITLE, manifest.getName()));
        ExpandableListView elv = (ExpandableListView)this.findViewById(R.id.elvQuiz);
        //elv.setOnChildClickListener(this);
        elv.setOnGroupClickListener(this);
        if (manifest.getGroupCount() > 0) {
            elv.setAdapter(manifest);
            elv.expandGroup(0);
            elv.setChoiceMode(ExpandableListView.CHOICE_MODE_MULTIPLE);
        }
    }
    
    private void handleError(String msg, String err) {
        this.error = err; this.message = msg;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Help us, report it..?")
               .setTitle("Oops, we hit an error!");
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // User clicked OK button
                       Intent intent = new Intent(Intent.ACTION_SEND);
                       intent.setType("text/plain");
                       intent.putExtra(Intent.EXTRA_EMAIL, "help@gradians.com");
                       intent.putExtra(Intent.EXTRA_SUBJECT, message);
                       intent.putExtra(Intent.EXTRA_TEXT, error);
                       /* Send it off to the Activity-Chooser */
                       context.startActivity(Intent.createChooser(intent, "Send mail..."));
                   }
               });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // User cancelled the dialog
                       Toast.makeText(context, "Okay, not sending!", Toast.LENGTH_SHORT).show();
                   }
               });
//        AlertDialog dialog = builder.create();
//        dialog.show();
    }
    
    private String error, message;
    private Context context;
    private ProgressDialog peedee;
    
    private final String TITLE = "Scanbot - Hello %s !";

    
}

