package com.gradians.collect;

import java.io.File;

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
        String names = "";        
        String[] tokens = uriString.substring(
                uriString.lastIndexOf('/')+1).split("-");
        for (int i = 0; i < tokens.length; i+=2) {
            names += (tokens[i] + " ");
        }
        names = names.substring(0, names.length()-1);
        TextView tv = (TextView)rootView.findViewById(R.id.tvPreview);
        tv.setText(names);
        return rootView;
    }

}

class PreviewAdapter extends FragmentStatePagerAdapter {

    private File[] images;
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

}
