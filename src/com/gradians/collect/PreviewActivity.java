package com.gradians.collect;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class PreviewActivity extends FragmentActivity implements IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        
        picturesDir  = new File(this.getIntent().getStringExtra(TAG));        
        adapter = new PreviewAdapter(picturesDir, this.getSupportFragmentManager());
        
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);
        
        adapter.notifyDataSetChanged();
        delete = new HashSet<String>();
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
    public void onBackPressed() {
        Iterator<String> it = delete.iterator();
        while (it.hasNext()) {
            (new File(picturesDir, it.next())).delete();
        }
        Intent deletions = new Intent();
        deletions.putExtra(TAG, delete.toArray(new String[delete.size()]));
        this.setResult(RESULT_OK, deletions);
        super.onBackPressed();
    }

    public void retakePicture(View view) {
        int index = vpPreview.getCurrentItem();
        PreviewFragment pf = (PreviewFragment)adapter.instantiateItem(vpPreview, index);
        TextView tv = ((TextView)pf.getView().findViewById(R.id.tvPreview));
        String key = String.valueOf(tv.getTag(R.id.preview_key));
        if (delete.contains(key)) {
            pf.undelete();
            delete.remove(key);
        } else {
            pf.delete();
            delete.add(key);
        }
    }
    
    public void goBack(View view) {
        onBackPressed();
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
    
    private HashSet<String> delete;
    private File picturesDir;    
    private PreviewAdapter adapter;
    private ViewPager vpPreview;

}


