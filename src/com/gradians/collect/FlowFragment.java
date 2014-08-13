package com.gradians.collect;

import java.io.File;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout.LayoutParams;

public class FlowFragment extends Fragment implements IConstants {
    
    public static final FlowFragment newInstance(Question question,
            boolean flipped, int x, int y, int page) {
        Bundle bundle = new Bundle(5);
        bundle.putParcelable(TAG, question);
        bundle.putInt(X_POSN_KEY, x);
        bundle.putInt(Y_POSN_KEY, y);
        bundle.putInt("page", page);
        bundle.putBoolean("flipped", flipped);
        FlowFragment pf = new FlowFragment();
        pf.setArguments(bundle);
        return pf;
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        FlowActivity activity = (FlowActivity)getActivity();
        
        Bundle bundle = getArguments();
        Question question = bundle.getParcelable(TAG);
        final int yPosn = bundle.getInt(Y_POSN_KEY);
        final int page = bundle.getInt("page");
        boolean flipped = bundle.getBoolean("flipped");
        ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.fragment_flow, container, false);
        String[] paths = activity.getPaths(question, flipped);
        
        final WebView solnView = new WebView(activity);
        solnView.setBackgroundColor(Color.WHITE);
        solnView.getSettings().setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            solnView.getSettings().setDisplayZoomControls(false);
        solnView.getSettings().setDefaultTextEncodingName("utf-8");
        solnView.getSettings().setUseWideViewPort(true);
        solnView.getSettings().setJavaScriptEnabled(true);
        
        StringBuilder sbHtml = new StringBuilder();
        sbHtml.append("<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></meta></head><body>");
        for (int i = 0; i < paths.length; i++) {
            sbHtml.append(String.format("<div id='pg%s' style='position: relative;'>", i));
            sbHtml.append(String.format(IMG_DIV, Uri.fromFile(new File(paths[i])).toString()));
            if (i == page && yPosn != FlowActivity.NO_FEEDBACK) {
                sbHtml.append(String.format(BAND_DIV, yPosn));
            }
            sbHtml.append("</div>");
        }
        sbHtml.append("</body></html>");
        solnView.loadDataWithBaseURL(null, sbHtml.toString(), "text/html", "utf-8", null);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        solnView.setLayoutParams(lp);
        rootView.addView(solnView);
        
        if (yPosn != FlowActivity.NO_FEEDBACK) {
            solnView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    String s = String.format("document.getElementById('pg%s').scrollIntoView();", page);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        solnView.loadUrl("javascript:" + s);
                    } else {
                        solnView.evaluateJavascript(s, null);
                    }                    
                }
            });            
        }
        return rootView;
    }
         
    private final String IMG_DIV = "<img src='%s' style='width: 100%%'/>";
    private final String BAND_DIV = "<span id='band' style='position:absolute; top:%s%%; left: 0; opacity: 0.3; background:#676767; width:100%%; height:4%%;'></span>";
    
}

class FlowAdapter extends FragmentStatePagerAdapter implements IConstants {

    public FlowAdapter(Question[] questions, FragmentManager fragmentManager) {
        super(fragmentManager);
        this.questions = questions;        
        xPosn = yPosn = FlowActivity.NO_FEEDBACK;
    }
    
    public void shift(int pg, int x, int y, int position) {
        page = pg;
        xPosn = x;
        yPosn = y;
        update(position);
    }
    
    public void flip(int position) {
        flipped = !flipped;
        update(position);
    }
    
    public void update(int position) {
        lastChangedId = questions[position].getId();
        notifyDataSetChanged();
    }
    
    public Question[] getQuestions() {
        return questions;
    }
    
    public boolean getFlipped() {
        return flipped;
    }
    
    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }
    
    @Override
    public int getItemPosition(Object object) {
        FlowFragment fragment = (FlowFragment)object;
        Question question = fragment.getArguments().getParcelable(TAG);
        return question.getId().equals(lastChangedId) ? 
                POSITION_NONE : POSITION_UNCHANGED;
    }

    @Override
    public int getCount() {
        return questions.length;
    }

    @Override
    public Fragment getItem(int position) {
        return FlowFragment.newInstance(questions[position], 
                flipped, xPosn, yPosn, page);
    }
    
    private int page, xPosn, yPosn;
    private boolean flipped;
    private String lastChangedId;
    private Question[] questions;
    
}
