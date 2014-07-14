package com.gradians.collect;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class HelpActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        
        setUpViewPager();
    }
    
    private void setUpViewPager() {
        ViewPager vpHelp = (ViewPager)findViewById(R.id.vpHelp);
        Bitmap[] images = new Bitmap[3];
        images[0] = getBitmapFromAssets("help/step-1.png");
        images[1] = getBitmapFromAssets("help/step-2.png");
        images[2] = getBitmapFromAssets("help/step-3.png");
        HelpViewsAdapter adapter = new HelpViewsAdapter(images, this);
        vpHelp.setAdapter(adapter);
    }
    
    private Bitmap getBitmapFromAssets(String name) {
        AssetManager assetManager = getAssets();
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(assetManager.open(name));
        } catch (Exception e) {
            return null;
        }
        return bitmap;
    }
    
}

class HelpViewsAdapter extends PagerAdapter {
    
    public HelpViewsAdapter(Bitmap[] images, Activity activity) {
        this.images = images;
        this.activity = activity;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        ImageView iv = new ImageView(activity);        
        iv.setImageBitmap(images[position]);
        container.addView(iv);
        return iv;
    }

    @Override
    public int getCount() {
        return images.length;
    }

    @Override
    public boolean isViewFromObject(View arg0, Object arg1) {
        return arg0 == arg1;
    }
    
    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View)object);
    }

    private Activity activity;
    private Bitmap[] images;
    
}