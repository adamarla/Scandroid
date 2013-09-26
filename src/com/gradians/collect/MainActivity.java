package com.gradians.collect;

import java.io.File;

import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.Spinner;
import android.widget.Toast;

public class MainActivity extends FragmentActivity implements ITaskCompletedListener, IConstants, OnChildClickListener {
    
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
//                gradeables.adjust(quizPosn, questionPosn, triggerBtn == R.id.btnStartWS);
                ExpandableListView elv = (ExpandableListView)this.findViewById(R.id.elvQuiz);
                Manifest manifest = (Manifest)elv.getExpandableListAdapter();
                manifest.adjust(groupPosition, childPosition, worksheet);
                setWidgets();
                uploadImageHTTP();
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(),
                        "Oops.. image capture failed. Please, try again",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == AUTH_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Manifest manifest = null;
                String error = null;
                try {
                    gradeables = new Gradeables(data.getStringExtra(TAG));
                    manifest = new Manifest(this, data.getStringExtra(TAG));
                } catch (Exception e) { 
                    error = e.getMessage() + "\n" + data.getStringExtra(TAG);
                }                
                if (error == null) {                    
                    SharedPreferences prefs = this.getSharedPreferences(TAG, 
                            Context.MODE_PRIVATE);
                    Editor edit = prefs.edit();
                    edit.putString(TOKEN_KEY, manifest.getAuthToken());
                    edit.putString(NAME_KEY, manifest.getName());
                    edit.putString(EMAIL_KEY, manifest.getEmail());
                    edit.commit();
                    
                    setWidgets();
                    ((ExpandableListView)this.findViewById(R.id.elvQuiz)).setAdapter(manifest);
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Oops, Scanbot stumbled :/ please try again",
                            Toast.LENGTH_SHORT).show();
                    //TODO: Send error email
                }                
            } else if (resultCode != Activity.RESULT_CANCELED){
                Toast.makeText(getApplicationContext(),
                        "Oops, Scanbot stumbled :/ please try again",
                        Toast.LENGTH_SHORT).show();
            } else {
                this.finish();
            }
        }
    }
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == VERIFY_AUTH_TASK_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                Manifest manifest = null;
                String error = null;
                try {
                    gradeables = new Gradeables(resultData);
                    manifest = new Manifest(this, resultData);
                } catch (Exception e) { 
                    error = e.getMessage() + "\n" + resultData;
                }
                if (error == null) {                    
                    setWidgets();
                    ((ExpandableListView)this.findViewById(R.id.elvQuiz)).setAdapter(manifest);
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Oops, sorry :/ please try again",
                            Toast.LENGTH_SHORT).show();
                    //TODO: Send error email                    
                }
            } else {
                initiateAuthActivity();
            }
        } else if (requestCode == CHOOSE_STATIONARY_TYPE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                File image = null;
                String error = null;
                try {
                    worksheet = resultData.startsWith("QR");                    
                    String imageFileName = resultData + ".jpg";                    
                    if (!appDir.exists()) appDir.mkdir();
                    image = new File(appDir, imageFileName);
                } catch (Exception e) {
                    error = e.getMessage() + "\n" + resultData;
                }
                if (error == null) {
                    Intent takePictureIntent =
                            new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                            Uri.fromFile(image));
                    startActivityForResult(takePictureIntent,
                            CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                } else {
                    Toast.makeText(context,
                            "Ooops, sorry, our bad :/ please try again",
                            Toast.LENGTH_SHORT).show();
                    //TODO: Send error email
                }
            } 
        } else if (requestCode == UPLOAD_IMAGE_TASK_RESULT_CODE) {
            if (resultCode == RESULT_OK) { }             
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
      case R.id.action_sign_out:
          SharedPreferences prefs = getSharedPreferences(TAG, 
                  Context.MODE_PRIVATE);
          Editor edit = prefs.edit();
          edit.clear();
          edit.commit();
          initiateAuthActivity();
        break;
      default:
        break;
      }
      return super.onOptionsItemSelected(item);
    }    
    
    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
            int groupPosition, int childPosition, long id) {
        this.groupPosition = groupPosition; this.childPosition = childPosition;
        Manifest manifest = (Manifest)parent.getExpandableListAdapter();
        Question question = (Question)manifest.getChild(groupPosition, childPosition);
        DialogFragment newFragment = new PickStationaryTypeDialog(this, 
                question.getQRCode(), question.getGRId());
        newFragment.show(getSupportFragmentManager(), "stationaryType");
        return true;
    }

    private void initiateAuthActivity() {
        Intent checkAuthIntent = new Intent(context, 
                com.gradians.collect.LoginActivity.class);
        checkAuthIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(checkAuthIntent, AUTH_ACTIVITY_REQUEST_CODE);
    }

    private void setWidgets() {        
        setTitle("Scanbot - Hello, " + gradeables.getName() + "!");
    }

    private void initApp() {
        appDir = new File(getExternalFilesDir(null), "Scandroid");
        context = getApplicationContext();
        SharedPreferences prefs = getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);
        if (prefs.getString(TOKEN_KEY, null) == null) {
            initiateAuthActivity();
        } else {
            String email = prefs.getString(EMAIL_KEY, null);
            String token = prefs.getString(TOKEN_KEY, null);
            new VerificationTask(email, token, this).execute();
        }
//        ((ListView)this.findViewById(R.id.lvQuiz)).setOnItemClickListener(this);
        ((ExpandableListView)this.findViewById(R.id.elvQuiz)).setOnChildClickListener(this);
    }    

    private void uploadImageHTTP() {
        File[] images = appDir.listFiles();
        new ImageUploadTask(this).execute(images);
    }
    
    private void setSpinner(String[] items, int resourceId) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 
                android.R.layout.simple_spinner_item, items);        
        ((Spinner)findViewById(resourceId)).setAdapter(adapter);
    }
        
    private void setList(String[] items, int resourceId) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 
                android.R.layout.simple_list_item_1, items);        
        ((ExpandableListView)findViewById(resourceId)).setAdapter(adapter);
    }
    
    private Context context;
    private int groupPosition, childPosition;
    private boolean worksheet;
    private Gradeables gradeables;
    private File appDir;
    
}

class PickStationaryTypeDialog extends DialogFragment implements IConstants {
    
    public PickStationaryTypeDialog(ITaskCompletedListener listener, String qrCode, String grId) {
        this.listener = listener;
        this.qrCode = qrCode;
        this.grId = grId;
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {        
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.title_stationary_type)
               .setItems(R.array.stationary_options, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int which) {
                       listener.onTaskResult(ITaskCompletedListener.CHOOSE_STATIONARY_TYPE_REQUEST_CODE, 
                               Activity.RESULT_OK, which == 0 ? 
                                       String.format(FORMAT, PLAINPAPER_PREFIX, grId): 
                                       String.format(FORMAT, WORKSHEET_PREFIX, qrCode));
               }
        });
        return builder.create();
    }
    
    ITaskCompletedListener listener;
    String qrCode, grId;
    final String FORMAT = "%s.%s";
    
}