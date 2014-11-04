package com.gradians.collect;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout.LayoutParams;

public class FlowFragment extends Fragment implements IConstants {
    
    public static final FlowFragment newInstance(Question question,
            int fdbkIdx, int page) {
        Bundle bundle = new Bundle(3);
        bundle.putParcelable(TAG, question);
        bundle.putInt(FDBK_MRKR_KEY, fdbkIdx);
        bundle.putInt("page", page);
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
        final int fdbkIdx = bundle.getInt(FDBK_MRKR_KEY);
        final int page = bundle.getInt("page");
        ViewGroup rootView = (ViewGroup)inflater.
            inflate(R.layout.fragment_flow, container, false);
        String[] paths = activity.getPaths(question);
        
        final WebView solnView = new DullWebView(activity);
        solnView.clearCache(true);
        solnView.setBackgroundColor(Color.WHITE);
        solnView.getSettings().setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            solnView.getSettings().setDisplayZoomControls(false);
        solnView.getSettings().setDefaultTextEncodingName("utf-8");
        solnView.getSettings().setUseWideViewPort(true);
        solnView.getSettings().setJavaScriptEnabled(true);
        solnView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        StringBuilder sbHtml = new StringBuilder();
        if (paths[0].contains(ATTEMPTS_DIR_NAME)) {
            sbHtml.append(HDR_ANS);
        } else {
            sbHtml.append(HDR);
        }
        String styleImgWidth = "";
        for (int i = 0; i < paths.length; i++) {
            sbHtml.append(String.format(PARENT_DIV, i));
            if (paths[i].contains(ATTEMPTS_DIR_NAME)) {
                styleImgWidth = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ? "width: 100%%; " : "";
            }
            sbHtml.append(String.format(IMG_DIV, Uri.fromFile(new File(paths[i])).toString(), styleImgWidth));
            if (i == page && fdbkIdx != FlowActivity.NO_FEEDBACK) {
                Feedback fdbk = activity.getFeedback();
                for (int j = 0; j < fdbk.x.length; j++) {
                    String div = j == fdbkIdx ? MARKER_DIV : NON_MARKER_DIV;
                    sbHtml.append(String.format(div, fdbk.y[j], fdbk.x[j], j+1));
                }
            }
            sbHtml.append(PARENT_DIV_CLS);
        }
        sbHtml.append(FTR);
        solnView.loadDataWithBaseURL(null, sbHtml.toString(), "text/html", "utf-8", null);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        solnView.setLayoutParams(lp);
        rootView.addView(solnView);
        
        if (fdbkIdx != FlowActivity.NO_FEEDBACK) {
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
         
    private final String PARENT_DIV = "<div id='pg%s' style='position: relative; '>";
    private final String PARENT_DIV_CLS = "</div>";
    private final String IMG_DIV = "<img src='%s' style='%s'/>";
    private final String MARKER_DIV = "<div style='font-size: 11px ; text-align : center ; width: 15px ; border-radius : 10px ; padding: 4px ; color: white ; position:absolute; top:%s%%; left: %s%%; background: #F88017;'>%s</div>";
    private final String NON_MARKER_DIV = "<div style='font-size: 11px ; text-align : center ; width: 15px ; border-radius : 10px ; padding: 4px ; color: white ; position:absolute; top:%s%%; left: %s%%; background: #676767;'>%s</div>";
    private final String HDR = "<html><head></head><body>";
    private final String HDR_ANS = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></meta></head><body>";
    private final String FTR = "</body></html>"; 
    
}

/**
 * This class has been extended only to disable Double Tap
 * 
 * Found it on here
 * http://stackoverflow.com/questions/20951935/how-to-disable-doubletap-zoom-in-android-webview
 * 
 */
class DullWebView extends WebView {
        
    private GestureDetector gestureDetector;
    private AtomicBoolean mPreventAction = new AtomicBoolean(false);
    
    public DullWebView(Context context) {
        super(context);        
        gestureDetector = new GestureDetector(context, new GestureListener());
     }

     public DullWebView(Context context, AttributeSet attrs) {
         super(context, attrs);
         gestureDetector = new GestureDetector(context, new GestureListener());
     }

     public DullWebView(Context context, AttributeSet attrs, int defStyle) {
         super(context, attrs, defStyle);
         gestureDetector = new GestureDetector(context, new GestureListener());
     }

     public DullWebView(Context context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
         super(context, attrs, defStyle, privateBrowsing);
         gestureDetector = new GestureDetector(context, new GestureListener());
     }
     
     @Override
     public boolean onTouchEvent(MotionEvent event) {
         int index = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
         int pointId = event.getPointerId(index);

         // just use one (first) finger, prevent double tap with two and more fingers
         if (pointId == 0){
             gestureDetector.onTouchEvent(event);

             if (mPreventAction.get()){
                 mPreventAction.set(false);
                 return true;
             }

             return super.onTouchEvent(event);
         } else {
             return true;
         }
     }
     
     private class GestureListener extends GestureDetector.SimpleOnGestureListener {
         @Override
         public boolean onDoubleTap(MotionEvent e) {
             mPreventAction.set(true);
             return true;
         }
         @Override
         public boolean onDoubleTapEvent(MotionEvent e) {
             mPreventAction.set(true);
             return true;
         }
         @Override
         public boolean onSingleTapConfirmed(MotionEvent e) {
             // onSingleTapConfirmed will only be called after the detector is confident
             // that the user's first tap is not followed by a second tap leading to a double-tap gesture
             return false; // it doesn't matter for the return value here as we only check mPreventAction above
         }
         @Override
         public boolean onSingleTapUp(MotionEvent e) {
             mPreventAction.set(true); // this is the key! this would block double tap to zoom to fire
             return false;
         }
     }
     
}

class FlowAdapter extends FragmentStatePagerAdapter implements IConstants {

    public FlowAdapter(Question[] questions, FragmentManager fragmentManager) {
        super(fragmentManager);
        this.questions = questions;
        fdbkIdx = FlowActivity.NO_FEEDBACK;
    }
    
    public void shift(int pg, int idx, int position) {
        page = pg;
        fdbkIdx = idx;
        update(position);
    }
    
    public void update(int position) {
        lastChangedId = questions[position].getId();
        notifyDataSetChanged();
    }
    
    public Question[] getQuestions() {
        return questions;
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
            fdbkIdx, page);
    }
    
    private int page, fdbkIdx;
    private String lastChangedId;
    private Question[] questions;
    
}
