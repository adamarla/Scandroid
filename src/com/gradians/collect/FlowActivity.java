package com.gradians.collect;

import java.io.File;
import java.net.URL;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.scilab.forge.jlatexmath.TeXFormula.TeXIconBuilder;

import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewSwitcher;


public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, ITaskResult, IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow);
        
        String[] name_state_ids = this.getIntent().getStringArrayExtra(TAG_ID);
        studentDir = new File(this.getIntent().getStringExtra(TAG));
        questions = toQuestions(studentDir, name_state_ids);
        adapter = new FlowAdapter(questions, this.getSupportFragmentManager());
        
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);
        vpPreview.setOnPageChangeListener(this);
        
        onPageSelected(0);
    }

    @Override
    protected void onPause() {
        Intent intent = new Intent();
        intent.putExtra(TAG, toNameStateIds(questions));
        this.setResult(RESULT_OK, intent);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(TAG, toNameStateIds(questions));
        this.setResult(RESULT_OK, intent);
        super.onBackPressed();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Uri picture = data.getData();
                int position = vpPreview.getCurrentItem();
                Question q = questions[position];
                q.setImgLocn(picture.getPath());
                q.setState(CAPTURED);
                adapter.markChanged(q.getGRId());
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), 
                        "Oops.. image capture failed. Please try again",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onTaskResult(int requestCode, int resultCode, String resultData) {
        if (requestCode == FETCH_COMMENTS_TASK_RESULT_CODE) {
            String[] comments = null;
            if (resultCode == RESULT_OK) {
                Log.d(TAG, resultData);
                try {
                    comments = getComments(resultData);
                    renderComments(comments);
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }
//                int position = vpPreview.getCurrentItem();
//                Question q = questions[position];
//                Intent intent = new Intent(this.getApplicationContext(), 
//                        com.gradians.collect.FeedbackActivity.class);
//                intent.putExtra(TAG_ID, q.getImgLocn());
//                intent.putExtra(TAG, comments);
//                startActivity(intent);
            }
        }
    }
    
    private void renderComments(String[] comments) {
        final String latex = comments[0];
        final WebView webView = (WebView)findViewById(R.id.wvFeedback);        
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.loadDataWithBaseURL("", "<html><head><script type='text/x-mathjax-config'>" 
                + "MathJax.Hub.Config({ "
                + "showMathMenu: false, " 
                + "jax: ['input/TeX','output/HTML-CSS'], " 
                + "extensions: ['tex2jax.js'], "
                + "TeX: { extensions: ['AMSmath.js','AMSsymbols.js'," 
                + "'noErrors.js','noUndefined.js'] } "
                + "});</script>" 
                + "<script type='text/javascript' " 
                + "src='http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS_HTML'"
                + "></script><span id='math'>\\["
                + latex
                + "\\]</span></head><body></body></html>", 
                "text/html", "utf-8", "");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {                
                super.onPageFinished(view, url);
                if (!url.contains(ANSWERS_DIR_NAME)) return;
                webView.loadUrl("javascript:MathJax.Hub.Queue(['Typeset', MathJax.Hub]);");                
            }
        });
    }
    
    private String[] getComments(String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
        JSONArray comments = (JSONArray)respObject.get("comments");
        String[] texts = new String[comments.size()];
        for (int i = 0; i < comments.size(); i++) {
            JSONObject comment = (JSONObject)comments.get(i);
            texts[i] = (String)comment.get("comment");
        }
        return texts;
    }

    @Override
    public void onPageScrollStateChanged(int arg0) { }
    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) { }
    @Override
    public void onPageSelected(int position) {
        char state = questions[position].getState();
        ImageButton btnAction1 = (ImageButton)this.findViewById(R.id.btnAction1);
        ImageButton btnAction2 = (ImageButton)this.findViewById(R.id.btnAction2);
        switch (state) {
        case WAITING:
        case SENT:
            btnAction1.setImageResource(android.R.drawable.ic_menu_info_details);
            btnAction2.setVisibility(View.INVISIBLE);
            break;
        case DOWNLOADED:
            btnAction1.setImageResource(android.R.drawable.ic_menu_camera);
            btnAction2.setVisibility(View.INVISIBLE);
            break;
        case CAPTURED:
            btnAction1.setImageResource(android.R.drawable.ic_menu_upload);
            btnAction2.setVisibility(android.R.drawable.ic_menu_delete);
            break;
        }
    }
    
    public void takeAction1(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        Question q = questions[currentIndex];
        switch (q.getState()) {
        case DOWNLOADED:
            takePicture(q);
            break;
        case CAPTURED:
            uploadPicture(q);
            break;
        case WAITING:
        case SENT:
            fetchComments(q);
        }        
    }
    
    public void takeAction2(View view) {
        int currentIndex = vpPreview.getCurrentItem();
        Question q = questions[currentIndex];
        switch (q.getState()) {
        case CAPTURED:
            q.setState(DOWNLOADED);
            adapter.markChanged(q.getGRId());
            adapter.notifyDataSetChanged();
            takePicture(q);
            break;
        case DOWNLOADED:
        case WAITING:
        case SENT:
        }
    }
    
    public void adjustZoom(View view) {
        zoom = (zoom + 1)%3;
        ViewSwitcher vsFlow = (ViewSwitcher)this.findViewById(R.id.vsPreview);        
        switch (zoom) {
        case 0:
            vsFlow.showPrevious();
            break;
        default:
            int currentIndex = vpPreview.getCurrentItem();
            Question q = questions[currentIndex];
            Bitmap bmap = BitmapFactory.decodeFile(q.getImgLocn());
            float bmapAspectRatio = (float)bmap.getWidth()/bmap.getHeight();
            DisplayMetrics dmetrics = this.getApplicationContext().
                    getResources().getDisplayMetrics();            
            float scalingFactor = 1.0f;
            if (bmap.getWidth() < dmetrics.widthPixels*scalingFactor) {
                scalingFactor = (zoom == 1) ? 1.0f : 1.25f;// for small/low density screens
            } else {
                scalingFactor = (zoom == 1) ? 1.25f : 1.75f;// for large/high density screens
            }
            bmap = Bitmap.createScaledBitmap(bmap, 
                    (int)(dmetrics.widthPixels*scalingFactor),
                    (int)(dmetrics.widthPixels*scalingFactor/bmapAspectRatio), false);
            ImageView iv = (ImageView)this.findViewById(R.id.ivFullPreview);
            iv.setImageBitmap(bmap);
            if (zoom == 1) vsFlow.showNext();
        }
    }
    
    private void uploadPicture(final Question q) {
        final String path = q.getImgLocn();
        final String GRId = q.getGRId();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ready to send?")
               .setMessage("This action is not reversible!");
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // User clicked OK button
                       Intent uploadIntent = new Intent(getApplicationContext(),
                               com.gradians.collect.ImageUploadService.class);
                       uploadIntent.putExtra(TAG, path);
                       //startService(uploadIntent);
                       q.setState(SENT);
                       adapter.markChanged(GRId);
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
    
    private void takePicture(Question q) {
        String name_id = q.getName() + "-" + q.getGRId();
        Intent takePictureIntent = new Intent(this.getApplicationContext(),
                com.gradians.collect.CameraActivity.class);
        takePictureIntent.putExtra(TAG, 
                (new File(studentDir, ANSWERS_DIR_NAME)).getPath());
        takePictureIntent.putExtra(TAG_ID, name_id);
        startActivityForResult(takePictureIntent, 
                CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }
    
    private void fetchComments(Question q) {
        String urlString = String.format(
                "http://%s/view/fdb.json?id=31886&sandbox=false&a=null",
                WEB_APP_HOST_PORT);
        try {
            URL[] urls = { new URL(urlString) };
            new HttpCallsAsyncTask(this,
                    FETCH_COMMENTS_TASK_RESULT_CODE).execute(urls);
        } catch (Exception e) {
            Intent intent = new Intent();
            intent.putExtra(TAG, e.getMessage());
            this.setResult(RESULT_FIRST_USER, intent);
            this.finish();
        }
    }
    
    private String[] toNameStateIds(Question[] questions) {
        String[] name_state_ids = new String[questions.length];
        for (int i = 0; i < questions.length; i++) {
            name_state_ids[i] = String.format("%s-%s-%s",
                    questions[i].getName(), 
                    questions[i].getState(), 
                    questions[i].getGRId());
        }
        return name_state_ids;
    }
    
    private Question[] toQuestions(File studentDir, String[] name_state_ids) {
        Question[] questions = new Question[name_state_ids.length];
        String name, id; char state;
        for (int i = 0; i < questions.length; i++) {
            
            String[] tokens = name_state_ids[i].split("-");
            name = tokens[0];
            state = tokens[1].charAt(0);
            id = tokens[2];
            
            Uri image = null;
            File parentDir = null;
            switch (state) {
            case WAITING:
                break;
            case DOWNLOADED:
                parentDir = new File(studentDir, QUESTIONS_DIR_NAME);
                break;
            case CAPTURED:
            case SENT:
                parentDir = new File(studentDir, ANSWERS_DIR_NAME);
                break;
            }
            image = Uri.fromFile(new File(parentDir, id));
            
            questions[i] = new Question(name, id, image.getPath());
            questions[i].setState(state);            
        }
        return questions;
    }
        
    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private Question[] questions;
    private File studentDir;
    private ViewPager vpPreview;
    private FlowAdapter adapter;
    private int zoom;
    
}
