package com.gradians.collect;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements ITaskResult, IConstants, OnClickListener {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final LinearLayout ll = (LinearLayout)this.findViewById(R.id.llMain);
        ViewTreeObserver vto = ll.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() { 
                @SuppressWarnings("deprecation")
                @Override 
                public void onGlobalLayout() {
                    ll.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    setUpMainButtons();
                }
            });
        }
        initialize();
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
        if (manifest != null) 
            try {
                manifest.commit();
            } catch (Exception e) {
                handleError("Oops.. persist file thing failed", e.getMessage());
            }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Question[] selected = manifest.getSelected();
                int frame = data.getIntExtra(TAG, selected.length);
                for (int i = frame; i < selected.length; i++) {
                    manifest.checkUncheck(selected[i].getGRId());
                }
                manifest.saveSelected();
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
                } catch (Exception e) {
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
        } else if (requestCode == PREVIEW_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String[] retakes = data.getStringArrayExtra(TAG);
                for (String ids : retakes) {
                    String[] tokens = ids.split("-");
                    for (int i = 0; i < tokens.length; i += 2) {
                        manifest.checkUncheck(tokens[i+1]);
                    }
                }
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), 
                        "Oops.. image preview failed. Please try again",
                        Toast.LENGTH_SHORT).show();                    
            }
        } 
    }
    
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
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
    public void onClick(View view) {
        manifest.checkUncheck((String)((TextView)view).getTag());
    }
    
    public void initiateDownload(View view) {
        Intent viewPDFsIntent = new Intent(context,
                com.gradians.collect.ViewWorksheetActivity.class);
        Quiz quiz = null;
        String[] name_path_ids = new String[manifest.getGroupCount()];
        for (int i = 0; i < name_path_ids.length; i++) {
            quiz = (Quiz)manifest.getGroup(i);
            name_path_ids[i] = String.format("%s-%s-%s",
                quiz.getName(), quiz.getPath(), quiz.getId());
        }
        viewPDFsIntent.putExtra(TAG_ID, name_path_ids);
        startActivity(viewPDFsIntent);
    }

    public void initiateSend(View view) {
        final File previewDir = this.getDir(IMG_DIR_NAME, MODE_PRIVATE);
        if (previewDir.listFiles().length == 0) {
            Toast.makeText(context, 
                    "Nothing to be submitted", 
                    Toast.LENGTH_SHORT).show();
        } else {            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Ready to submit?")
                   .setMessage("This action is not reversible!");
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           // User clicked OK button
                           Intent uploadIntent = new Intent(context,
                                   com.gradians.collect.ImageUploadService.class);
                           uploadIntent.putExtra(TAG, previewDir.getAbsolutePath());
                           startService(uploadIntent);
                           manifest.sendSaved();
                       }
                   });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           // User cancelled the dialog
                       }
                   });
            AlertDialog dialog = builder.create();
            dialog.show();            
        }
    }
    
    public void initiatePreview(View view) {
        if (manifest.getSaved().length == 0) {
            Toast.makeText(context, 
                    "No images to preview", 
                    Toast.LENGTH_SHORT).show();            
        } else {
            File previewDir = this.getDir(IMG_DIR_NAME, MODE_PRIVATE);
            Intent previewIntent = new Intent(context,
                    com.gradians.collect.PreviewActivity.class);
            previewIntent.putExtra(TAG, previewDir.getPath());
            startActivityForResult(previewIntent, 
                    PREVIEW_ACTIVITY_REQUEST_CODE);
        }
    }

    public void initiateCamera(View view) {
        Question[] selected = manifest.getSelected();
        if (selected.length == 0) {
            Toast.makeText(context, 
                    "No questions selected", 
                    Toast.LENGTH_SHORT).show();
        } else {
            String[] name_ids = new String[selected.length]; int i = 0;
            for (Question q : selected) {
                name_ids[i++] = q.getName() + "-" + q.getGRId();
            }
            Intent takePictureIntent = new Intent(context,
                    com.gradians.collect.CameraActivity.class);
            takePictureIntent.putExtra(TAG, 
                    this.getDir(IMG_DIR_NAME, MODE_PRIVATE).getPath());
            takePictureIntent.putExtra(TAG_ID, name_ids);
            startActivityForResult(takePictureIntent, 
                    CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        }
    }
    
    private void initiateAuthActivity() {
        resetPreferences();
        Intent checkAuthIntent = new Intent(context, 
                com.gradians.collect.LoginActivity.class);
        checkAuthIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(checkAuthIntent, 
                AUTH_ACTIVITY_REQUEST_CODE);
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

    private void initialize() {
        context = getApplicationContext();
        SharedPreferences prefs = getSharedPreferences(TAG, 
                Context.MODE_PRIVATE);
        
        String email = prefs.getString(EMAIL_KEY, null);
        String token = prefs.getString(TOKEN_KEY, null);        
        if (token == null) {
            initiateAuthActivity();
        } else {
            peedee = ProgressDialog.show(this, "Initializing ", 
                    "Please wait...");
            peedee.setIndeterminate(true);
            peedee.setIcon(ProgressDialog.STYLE_SPINNER);
            String urlString = String.format(
                    "http://%s/tokens/verify?email=%s&token=%s",
                    WEB_APP_HOST_PORT, email, token);
            try {
                URL[] urls = { new URL(urlString) };
                new HttpCallsAsyncTask(this, 
                        VERIFY_AUTH_TASK_RESULT_CODE).execute(urls);
            } catch (Exception e) {
                handleError("Auth Check Failed", e.getMessage());
            }
        }
    }
    
    private void setUpMainButtons() {
        ImageButton[] ibtns = new ImageButton[4];
        ibtns[0] = (ImageButton)this.findViewById(R.id.btnWs);
        ibtns[1] = (ImageButton)this.findViewById(R.id.btnClick);
        ibtns[2] = (ImageButton)this.findViewById(R.id.btnPreview);
        ibtns[3] = (ImageButton)this.findViewById(R.id.btnSend);
        LayoutParams lp;
        for (ImageButton ibtn : ibtns) {            
            lp = (LayoutParams)ibtn.getLayoutParams();
            lp.height = ibtn.getMeasuredWidth();
            ibtn.setLayoutParams(lp);
        }
    }

    private void setManifest(Manifest manifest) {        
        setTitle(String.format(TITLE, manifest.getName()));
        ExpandableListView elv = (ExpandableListView)this.findViewById(R.id.elvQuiz);
        if (manifest.getGroupCount() > 0) {
            elv.setAdapter(manifest);
            elv.expandGroup(0);
            elv.setChoiceMode(ExpandableListView.CHOICE_MODE_SINGLE);
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
    
    private Manifest manifest;
    private String error, message;
    private Context context;
    private ProgressDialog peedee;
    
    private final String TITLE = "Scanbot - Hello %s !";
    private final String IMG_DIR_NAME = "images";
    
}

