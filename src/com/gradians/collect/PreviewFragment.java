package com.gradians.collect;

import java.io.File;

import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class PreviewFragment extends Fragment {
    
    public static final PreviewFragment newInstance(String uriString) {
        Bundle bundle = new Bundle(1);
        bundle.putString("uri", uriString);
        PreviewFragment pf = new PreviewFragment();
        pf.setArguments(bundle);
        return pf;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        String uriString = this.getArguments().getString("uri");
        Uri image = Uri.parse(uriString);
        
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_preview, container, false);
        
        ImageView iv = (ImageView)rootView.findViewById(R.id.ivPreview);
        iv.setImageURI(image);
        
        String id_name = uriString.substring(uriString.lastIndexOf('/')+1);
        String displ_names = "";
        String[] tokens = id_name.split("-");
        for (int i = 0; i < tokens.length; i+=2) {
            displ_names += (tokens[i] + " ");
        }
        displ_names = displ_names.substring(0, displ_names.length()-1);
        
        TextView tv = (TextView)rootView.findViewById(R.id.tvPreview);
        tv.setText(displ_names);
        
        tv.setTag(R.id.preview_key, id_name);
        tv.setTag(R.id.preview_state, KEEP);
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("deleted")) {
                tv.setPaintFlags(tv.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);                
                tv.setTag(R.id.preview_state, DELETE);
            } 
        } 
        return rootView;
    }        
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        String state_tag = this.getView().findViewById(R.id.tvPreview).
                getTag(R.id.preview_state).toString();
        outState.putBoolean("deleted", state_tag.equals(DELETE));
        super.onSaveInstanceState(outState);
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

class PreviewAdapter extends FragmentStatePagerAdapter {

    public PreviewAdapter(File imagesDir, FragmentManager fragmentManager) {
        super(fragmentManager);
        this.images = imagesDir.listFiles();
    }
        
     @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return images.length;
    }

    @Override
    public Fragment getItem(int position) {
        // TODO Auto-generated method stub
        Uri uri = Uri.fromFile(images[position]);
        return PreviewFragment.newInstance(uri.toString());
    }
    
     private File[] images;
}
