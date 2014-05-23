package com.gradians.collect;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

public class FlowFragment extends Fragment implements IConstants {
    
    public static final FlowFragment newInstance(Question question) {
        Bundle bundle = new Bundle(3);
        bundle.putString(GR_PATH_KEY, question.getState() > DOWNLOADED ?
                question.getScanLocn() : question.getImgLocn());
        bundle.putString(NAME_KEY, question.getName());        
        bundle.putString(GR_ID_KEY, question.getGRId());
        FlowFragment pf = new FlowFragment();
        pf.setArguments(bundle);
        return pf;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Bundle bundle = this.getArguments();
        String path = bundle.getString(GR_PATH_KEY);
        String name = bundle.getString(NAME_KEY);
        String id = bundle.getString(GR_ID_KEY);

        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_flow, container, false);
        
        ImageView ivPreview = (ImageView)rootView.findViewById(R.id.ivPreview);
        String imagePath = path;

        int orientation = this.getActivity().getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            ivPreview.setScaleType(ScaleType.CENTER_INSIDE);
        } else {
            ivPreview.setScaleType(ScaleType.FIT_CENTER);
        }
        setImage(ivPreview, imagePath);
        
        TextView tv = (TextView)rootView.findViewById(R.id.tvPreview);
        tv.setText(name);
        tv.setTag(id);
        
        return rootView;
    }
    
    private void setImage(ImageView iv, String path) {
        Bitmap bmap = BitmapFactory.decodeFile(path);
        float bmapAspectRatio = (float)bmap.getWidth()/bmap.getHeight();
        DisplayMetrics dmetrics = this.getActivity().getApplicationContext().
                getResources().getDisplayMetrics();
        bmap = Bitmap.createScaledBitmap(bmap, 
                (int)(dmetrics.widthPixels),
                (int)(dmetrics.widthPixels/bmapAspectRatio), false);
        iv.setImageBitmap(bmap);
    }
    
}

class FlowAdapter extends FragmentStatePagerAdapter implements IConstants {

    public FlowAdapter(Question[] questions, FragmentManager fragmentManager) {
        super(fragmentManager);
        this.questions = questions;
    }
    
    public void update(Question question) {
        this.lastChangedId = question.getGRId();
        this.notifyDataSetChanged();
    }
    
     @Override
    public int getItemPosition(Object object) {
         FlowFragment fragment = (FlowFragment)object;
         String id = fragment.getArguments().getString(GR_ID_KEY);
         return id.equals(lastChangedId) ? 
                 POSITION_NONE : POSITION_UNCHANGED;
    }

    @Override
    public int getCount() {
        return questions.length;
    }

    @Override
    public Fragment getItem(int position) {
        return FlowFragment.newInstance(questions[position]);
    }
    
    private String lastChangedId;
    private Question[] questions;
    
}
