package com.gradians.collect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.net.Uri;
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
import android.widget.TextView;

public class FlowFragment extends Fragment implements IConstants {
    
    public static final FlowFragment newInstance(Question question) {
        Bundle bundle = new Bundle(3);
        bundle.putString(IConstants.NAME_KEY, question.getName());
        bundle.putString(IConstants.GR_ID_KEY, question.getGRId());
        bundle.putString(IConstants.GR_PATH_KEY, question.getImgLocn());
        FlowFragment pf = new FlowFragment();
        pf.setArguments(bundle);
        return pf;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Bundle bundle = this.getArguments();
        String name = bundle.getString(IConstants.NAME_KEY);
        String id = bundle.getString(IConstants.GR_ID_KEY);
        String path = bundle.getString(IConstants.GR_PATH_KEY);

        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_flow, container, false);
        
        ImageView iv = (ImageView)rootView.findViewById(R.id.ivPreview);
        Uri image = Uri.parse(path);
        Bitmap bmap = BitmapFactory.decodeFile(image.getPath());
        float bmapAspectRatio = (float)bmap.getWidth()/bmap.getHeight();
        //iv.setImageURI(image);
        DisplayMetrics dmetrics = this.getActivity().getApplicationContext().
                getResources().getDisplayMetrics();
        bmap = Bitmap.createScaledBitmap(bmap, 
                (int)(dmetrics.widthPixels),
                (int)(dmetrics.widthPixels/bmapAspectRatio), false);
        iv.setImageBitmap(bmap);
        
        TextView tv = (TextView)rootView.findViewById(R.id.tvPreview);
        tv.setText(name);
        tv.setTag(id);
        
        return rootView;
    }        
    
    public void delete() {
        TextView tv = (TextView)this.getView().findViewById(R.id.tvPreview);
        tv.setTag(R.id.preview_state, DELETE);
        tv.setPaintFlags(tv.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);
    }

    public void undelete() {
        TextView tv = (TextView)this.getView().findViewById(R.id.tvPreview);
        tv.setTag(R.id.preview_state, KEEP);
        tv.setPaintFlags(REGULAR_TEXT);
    }
    
    private final String KEEP = "K", DELETE = "D";
    private final int REGULAR_TEXT = 257;
}

class FlowAdapter extends FragmentStatePagerAdapter implements IConstants {

    public FlowAdapter(Question[] questions, FragmentManager fragmentManager) {
        super(fragmentManager);
        this.questions = questions;
    }
    
    public void markChanged(String id) {
        this.lastChangedId = id;
        this.notifyDataSetChanged();
    }
    
     @Override
    public int getItemPosition(Object object) {
         FlowFragment fragment = (FlowFragment) object;
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
        Question question = questions[position];
        return FlowFragment.newInstance(question);
    }
    
    private String lastChangedId;
    private int dirty = -1;
    private Question[] questions;
}
