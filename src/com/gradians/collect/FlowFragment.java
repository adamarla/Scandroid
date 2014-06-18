package com.gradians.collect;

import java.io.File;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;

public class FlowFragment extends Fragment implements IConstants {
    
    public static final FlowFragment newInstance(Question question,
            boolean flipped, boolean zoomed,int x, int y) {
        Bundle bundle = new Bundle(7);
        if (x != FdbkView.NO_FEEDBACK) bundle.putInt("xPosn", x);
        if (y != FdbkView.NO_FEEDBACK) bundle.putInt("yPosn", y);
        if (zoomed) bundle.putBoolean("zoomed", zoomed);
        if (flipped) bundle.putBoolean("flipped", flipped);
        bundle.putString(SCAN_KEY, question.getScanLocn());
        bundle.putString(GR_PATH_KEY, question.getImgLocn());
        bundle.putString(ID_KEY, question.getId());
        FlowFragment pf = new FlowFragment();
        pf.setArguments(bundle);
        return pf;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        dmetrics = getActivity().getApplicationContext().getResources().getDisplayMetrics();
        
        Bundle bundle = this.getArguments();
        int xPosn = bundle.getInt("xPosn", FdbkView.NO_FEEDBACK);
        final int yPosn = bundle.getInt("yPosn", FdbkView.NO_FEEDBACK);
        boolean flipped = bundle.getBoolean("flipped", false);
        String scan = bundle.getString(SCAN_KEY);
        String path = bundle.getString(GR_PATH_KEY);

        ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.fragment_flow, container, false);
        FdbkView ivPreview = (FdbkView)rootView.findViewById(R.id.ivPreview);
        ivPreview.setPosn(xPosn, yPosn);
        String imgPath = flipped ? path : scan;
        final Bitmap bmap = setImage(ivPreview, imgPath);
        
        if (yPosn != FdbkView.NO_FEEDBACK) {
            final ScrollView svPreview = (ScrollView)rootView.findViewById(R.id.svPreview);
            svPreview.post(new Runnable() {
                @Override
                public void run() {
                    int scrollTo = bmap.getHeight()*yPosn/100 - dmetrics.heightPixels/2;
                    svPreview.smoothScrollTo(0, scrollTo);
                }
            });            
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
}

class FlowAdapter extends FragmentStatePagerAdapter implements IConstants {

    public FlowAdapter(Question[] questions, FragmentManager fragmentManager) {
        super(fragmentManager);
        this.questions = questions;
        yPosn = FdbkView.NO_FEEDBACK;
    }    
    
    public void shift(int x, int y, int position) {
        this.xPosn = x;
        this.yPosn = y;
        update(position);
    }
    
    public void flip(int position) {
        flipped = !flipped;
        update(position);
    }
    
    public void zoom(int position) {
        zoomed = !zoomed;
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
    
    public boolean getZoomed() {
        return zoomed;
    }
    
    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }
    
    public void setZoomed(boolean zoomed) {
        this.zoomed = zoomed;
    }
    
    @Override
    public int getItemPosition(Object object) {
        FlowFragment fragment = (FlowFragment) object;
        String id = fragment.getArguments().getString(ID_KEY);
        return id.equals(lastChangedId) ? 
                POSITION_NONE : POSITION_UNCHANGED;
    }

    @Override
    public int getCount() {
        return questions.length;
    }

    @Override
    public Fragment getItem(int position) {
        return FlowFragment.newInstance(questions[position], 
                flipped,
                zoomed,
                xPosn,
                yPosn);
    }
    
    private int xPosn, yPosn;
    private boolean zoomed;
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
            paint.setStrokeWidth(imgHeight/20);
            bpaint.setStrokeWidth(imgHeight/20);
            float x = xPosn*imgWidth/100, y = yPosn*imgHeight/100;
            canvas.drawLine(0, y, imgWidth, y, paint);
            canvas.drawLine(0, y-1, 0, y+1, bpaint);
            canvas.drawLine(imgWidth, y-1, imgWidth, y+1, bpaint);
        }
    }
    
    private void init(Context context) {
        paint = new Paint();
        paint.setColor(0x33676767);
        paint.setStyle(Style.STROKE);
        paint.setStrokeCap(Cap.SQUARE);
        
        bpaint = new Paint();
        bpaint.setColor(0xFFFF05AE);
        bpaint.setStyle(Style.STROKE);
        bpaint.setStrokeCap(Cap.SQUARE);
        
        xPosn = yPosn = NO_FEEDBACK;
    }
    
    private Paint paint, bpaint;
    private int xPosn, yPosn;
    private int imgWidth, imgHeight;
    
    public static final int NO_FEEDBACK = -1;
}
