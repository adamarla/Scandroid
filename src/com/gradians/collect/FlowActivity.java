package com.gradians.collect;

import java.io.File;

import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewSwitcher;


public class FlowActivity extends FragmentActivity implements ViewPager.OnPageChangeListener, IConstants {

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
        
        zoom = 0;
        onPageSelected(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.preview, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
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
        if (requestCode == 100) {
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
    public void onPageScrollStateChanged(int arg0) { }
    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) { }
    @Override
    public void onPageSelected(int position) {
        Log.d(TAG, "onPageSelected: " + position);
        char state = questions[position].getState();
        ImageButton btnAction1 = (ImageButton)this.findViewById(R.id.btnAction1);
        ImageButton btnAction2 = (ImageButton)this.findViewById(R.id.btnAction2);
        switch (state) {
        case WAITING:
        case SENT:
            btnAction1.setVisibility(View.INVISIBLE);
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
        Log.d(TAG, "adjustZoom() --> zoom: " + zoom);
        zoom++;
        ViewSwitcher vsFlow = (ViewSwitcher)this.findViewById(R.id.vsPreview);        
        switch (zoom) {
        case 0:
            vsFlow.showPrevious();
            break;
        default:
            int currentIndex = vpPreview.getCurrentItem();
            FlowFragment fragment = (FlowFragment)adapter.getItem(currentIndex);
            Bundle b = fragment.getArguments();
            Bitmap bmap = BitmapFactory.decodeFile(b.getString(GR_PATH_KEY));
            float bmapAspectRatio = (float)bmap.getWidth()/bmap.getHeight();
            float scalingFactor = (zoom == 1) ? 1.25f : 1.75f;
            DisplayMetrics dmetrics = this.getApplicationContext().
                    getResources().getDisplayMetrics();
            if (bmap.getWidth() < dmetrics.widthPixels*scalingFactor) {
                bmap = Bitmap.createScaledBitmap(bmap, 
                        (int)(dmetrics.widthPixels*scalingFactor),
                        (int)(dmetrics.widthPixels*scalingFactor/bmapAspectRatio), false);
            }            
            ImageView iv = (ImageView)this.findViewById(R.id.ivFullPreview);
            iv.setImageBitmap(bmap);
            if (zoom == 2) {
                zoom = -1;
                iv.invalidate();
            } else {
                vsFlow.showNext();
            }
        }
    }
    
    private void uploadPicture(Question q) {
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
                       startService(uploadIntent);
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
        startActivityForResult(takePictureIntent, 100);
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
                parentDir = new File(studentDir, ANSWERS_DIR_NAME);
                break;
            case SENT:
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
