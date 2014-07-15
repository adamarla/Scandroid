package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
        dmetrics = getActivity().getApplicationContext().getResources().getDisplayMetrics();
        
        Bundle bundle = this.getArguments();
        Question question = bundle.getParcelable(TAG);
        final int xPosn = bundle.getInt(X_POSN_KEY);
        final int yPosn = bundle.getInt(Y_POSN_KEY);
        final int page = bundle.getInt("page");
        boolean flipped = bundle.getBoolean("flipped");
        Log.d("gradians", "Rendering on page " + page + " at " + xPosn + " " + yPosn);
        ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.fragment_flow, container, false);
        String[] paths;
        if (flipped) {
            if (question.getState() > SENT) {
                paths = getSolutions(question);
            } else {
                paths = getQuestions(question);
            }
        } else {
            if (question.getState() > DOWNLOADED) {
                paths = getAnswers(question);
            } else {
                paths = getQuestions(question);
            }
        }
        
        FdbkView fdbkView = null;
        LinearLayout llPreview = (LinearLayout)rootView.findViewById(R.id.llPreview);
        Bitmap b = null;
        for (int i = 0; i < paths.length; i++) {
            fdbkView = new FdbkView(getActivity().getApplicationContext());
            if (i == page) fdbkView.setPosn(xPosn, yPosn);
            b = setImage(fdbkView, paths[i]);
            llPreview.addView(fdbkView);
        }
        
        final Bitmap bmap = b;
        if (yPosn != FdbkView.NO_FEEDBACK) {
            final ScrollView svPreview = (ScrollView)rootView.findViewById(R.id.svPreview);
            svPreview.post(new Runnable() {
                @Override
                public void run() {
                    int scrollTo = bmap.getHeight()*page + bmap.getHeight()*yPosn/100 - dmetrics.heightPixels/2;
                    svPreview.smoothScrollTo(0, scrollTo);
                }
            });
        }
        return rootView;
    }
    
    private String[] getQuestions(Question question) {
        return new String[] { question.getImgLocn() + "/" + question.getId() };
    }
     
    private String[] getAnswers(Question question) {
        String map = question.getMap();
        String[] parts = map.split("-");
        HashSet<String> pages = new HashSet<String>();
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].equals("0")) {
                if (!pages.contains(parts[i])) {
                    pages.add(parts[i]);
                }
            }
        }
        String[] paths = new String[pages.size()];
        int i = 0;
        Iterator<String> iter = pages.iterator();
        while (iter.hasNext()) {            
            paths[i] = question.getScanLocn() + "/" + question.getId().split("\\.")[0] + "." + iter.next();
            i++;
        }
        return paths;
    }
     
    private String[] getSolutions(Question question) {
        String[] paths;
        String imgLocn = question.getImgLocn();
        paths = new String[question.getImgSpan()];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = imgLocn + "/" + question.getId() + "." + (i+1);
        }
        return paths;
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
