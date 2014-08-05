package com.gradians.collect;

import java.io.File;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;

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
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        FlowActivity activity = (FlowActivity)getActivity();
        dmetrics = activity.getApplicationContext().getResources().getDisplayMetrics();
        
        Bundle bundle = getArguments();
        Question question = bundle.getParcelable(TAG);
        final int xPosn = bundle.getInt(X_POSN_KEY);
        final int yPosn = bundle.getInt(Y_POSN_KEY);
        final int page = bundle.getInt("page");
        boolean flipped = bundle.getBoolean("flipped");
        ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.fragment_flow, container, false);
        String[] paths = activity.getPaths(question, flipped);
        
        if (paths[0].contains(ANSWERS_DIR_NAME)) {
            LinearLayout llPreview = new LinearLayout(activity);
            llPreview.setOrientation(LinearLayout.VERTICAL);
            FdbkView fdbkView = null;
            llPreview.setBackgroundColor(getResources().getColor(R.color.gray));
            Bitmap b = null;
            for (int i = 0; i < paths.length; i++) {
                fdbkView = new FdbkView(getActivity().getApplicationContext());
                if (i == page) fdbkView.setPosn(xPosn, yPosn);
                b = setImage(fdbkView, paths[i]);
                LayoutParams lp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.TOP; lp.topMargin = -70; lp.bottomMargin = -70;
                fdbkView.setLayoutParams(lp);
                llPreview.addView(fdbkView);
            }
            
            final ScrollView svPreview = new ScrollView(activity);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            svPreview.setLayoutParams(lp);
            svPreview.addView(llPreview);
            rootView.addView(svPreview);
            
            final Bitmap bmap = b;
            if (yPosn != FdbkView.NO_FEEDBACK) {
                svPreview.post(new Runnable() {
                    @Override
                    public void run() {
                        int scrollTo = bmap.getHeight()*(page*100 + yPosn)/100 - dmetrics.heightPixels/2;
                        svPreview.smoothScrollTo(0, scrollTo);
                    }
                });
            }
            
        } else {
            WebView solnView = new WebView(activity);
            solnView.setBackgroundColor(Color.TRANSPARENT);
            solnView.getSettings().setBuiltInZoomControls(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                solnView.getSettings().setDisplayZoomControls(false);
            solnView.getSettings().setDefaultTextEncodingName("utf-8");
            solnView.getSettings().setUseWideViewPort(true);
            String html = String.format(HTML, Uri.fromFile(new File(paths[0])).toString());
            solnView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
            rootView.addView(solnView);
        }
        
        return rootView;
    }
         
    private Bitmap setImage(FdbkView iv, String path) {
        Bitmap bmap = (new File(path)).exists() ?
            BitmapFactory.decodeFile(path):
            getBitmapFromAssets("albert_einstein.jpg");
            
        float bmapAspectRatio = (float)bmap.getWidth()/bmap.getHeight();
        int w = dmetrics.widthPixels < MIN_WIDTH ? MIN_WIDTH : dmetrics.widthPixels;
        bmap = Bitmap.createScaledBitmap(bmap, w, (int)(w/bmapAspectRatio), false);
        iv.setImageBitmap(bmap);
        return bmap;
    }
    
    private Bitmap getBitmapFromAssets(String name) {
        AssetManager assetManager = this.getActivity().getAssets();
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(assetManager.open(name));
        } catch (Exception e) {
            return null;
        }
        return bitmap;
    }
    
    private DisplayMetrics dmetrics;
    private final int MIN_WIDTH = 600;
    
    private final String HTML = "<html><head/><body><img src='%s'/></body></html>";
    
}

class FlowAdapter extends FragmentStatePagerAdapter implements IConstants {

    public FlowAdapter(Question[] questions, FragmentManager fragmentManager) {
        super(fragmentManager);
        this.questions = questions;
        xPosn = yPosn = FdbkView.NO_FEEDBACK;
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

class FdbkView extends ImageView {

    public FdbkView(Context context) {
        super(context);
        init(context);
    }
    
    public FdbkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);        
    }
    
    public void setPosn(int xPosn, int yPosn) {
        this.xPosn = xPosn;
        this.yPosn = yPosn;
    }
    
    @Override
    public void setImageBitmap(Bitmap bmp) {
        super.setImageBitmap(bmp);
        imgWidth = bmp.getWidth();
        imgHeight = bmp.getHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (yPosn != NO_FEEDBACK) {
            int unit = imgHeight/20;
            paint.setStrokeWidth(unit);
            bpaint.setStrokeWidth(unit/2);
            float x = xPosn*imgWidth/100, y = yPosn*imgHeight/100;
            canvas.drawLine(0, y, imgWidth, y, paint);
            canvas.drawLine(0, y-unit/4, 0, y+unit/4, bpaint);
            canvas.drawLine(imgWidth, y-unit/4, imgWidth, y+unit/4, bpaint);
        }
    }
    
    private void init(Context context) {
        paint = new Paint();
        paint.setColor(0x33676767);
        paint.setStyle(Style.STROKE);
        paint.setStrokeCap(Cap.SQUARE);
        
        bpaint = new Paint();
        bpaint.setColor(0xFFF88017);
        bpaint.setStyle(Style.STROKE);
        bpaint.setStrokeCap(Cap.SQUARE);
        
        xPosn = yPosn = NO_FEEDBACK;
    }
    
    private Paint paint, bpaint;
    private int xPosn, yPosn;
    private int imgWidth, imgHeight;
    
    public static final int NO_FEEDBACK = -1;
}

