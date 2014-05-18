package com.gradians.collect;

import java.io.File;
import java.util.HashSet;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

public class PreviewActivity extends FragmentActivity implements IConstants {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        
        images  = (new File(this.getIntent().getStringExtra(TAG))).listFiles();
        adapter = new PreviewAdapter(images, this.getSupportFragmentManager());
        
        vpPreview = (ViewPager)this.findViewById(R.id.vpPreview);
        vpPreview.setAdapter(adapter);
        
        adapter.notifyDataSetChanged();
        delete = new HashSet<String>();
        
        zoom = false;
    }

    @Override
    public void onBackPressed() {
        for (File image : images) {
            if (delete.contains(image.getName())) image.delete();
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
    
    public void adjustZoom(View view) {
        int position = vpPreview.getCurrentItem();
        ViewSwitcher vsPreview = (ViewSwitcher)this.findViewById(R.id.vsPreview);
        if (zoom) {
            vsPreview.showPrevious();
        } else {
            ImageView iv = (ImageView)this.findViewById(R.id.ivFullPreview);
            Bitmap bmap = BitmapFactory.decodeFile(images[position].getPath());
            float bmapAspectRatio = (float)bmap.getWidth()/bmap.getHeight();
            float scalingFactor = 1.5f;
            DisplayMetrics dmetrics = this.getApplicationContext().
                    getResources().getDisplayMetrics();
            if (bmap.getWidth() < dmetrics.widthPixels*scalingFactor) {
                bmap = Bitmap.createScaledBitmap(bmap, 
                        (int)(dmetrics.widthPixels*scalingFactor),
                        (int)(dmetrics.widthPixels*scalingFactor/bmapAspectRatio), false);
            }
            iv.setImageBitmap(bmap);
            vsPreview.showNext();
        }
        zoom = !zoom;        
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
    
    private boolean zoom;
    private HashSet<String> delete;
    private File[] images;
    private PreviewAdapter adapter;
    private ViewPager vpPreview;

}


