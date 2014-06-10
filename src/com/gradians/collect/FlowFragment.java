package com.gradians.collect;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
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
import android.widget.TextView;

public class FlowFragment extends Fragment implements IConstants {
    
    public static final FlowFragment newInstance(Question question, 
            boolean flipped, boolean zoomed,int x, int y) {
        Bundle bundle = new Bundle(8);
        if (x != FdbkView.NO_FEEDBACK) bundle.putInt("xPosn", x);
        if (y != FdbkView.NO_FEEDBACK) bundle.putInt("yPosn", y);
        if (zoomed) bundle.putBoolean("zoomed", zoomed);
        if (flipped) bundle.putBoolean("flipped", flipped);
        bundle.putString(SCAN_KEY, question.getScanLocn());
        bundle.putString(GR_PATH_KEY, question.getImgLocn());
        bundle.putString(NAME_KEY, question.getName());        
        bundle.putString(GR_ID_KEY, question.getId());
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
        int yPosn = bundle.getInt("yPosn", FdbkView.NO_FEEDBACK);
        boolean zoomed = bundle.getBoolean("zoomed", false);
        boolean flipped = bundle.getBoolean("flipped", false);
        String scan = bundle.getString(SCAN_KEY);
        String path = bundle.getString(GR_PATH_KEY);
        String name = bundle.getString(NAME_KEY);
        String id = bundle.getString(GR_ID_KEY);

        ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.fragment_flow, container, false);
        FdbkView ivPreview = (FdbkView)rootView.findViewById(R.id.ivPreview);
        float scale = zoomed ? 1.25f : 1.0f;
        ivPreview.setPosn(xPosn, yPosn);
        String imgPath = flipped ? scan : path;
        setImage(ivPreview, imgPath, scale);
        
        TextView tv = (TextView)rootView.findViewById(R.id.tvPreview);
        tv.setText(imgPath.contains("albert") ? "Just do it!" : name);
        tv.setTag(id);
        
        return rootView;
    }
    
    private Bitmap setImage(FdbkView iv, String path, float scale) {
        Bitmap bmap = path.contains("albert") ?
            getBitmapFromAssets("albert_einstein.jpg"):
            BitmapFactory.decodeFile(path);
            
        float bmapAspectRatio = (float)bmap.getWidth()/bmap.getHeight();
        int w = dmetrics.widthPixels < MIN_WIDTH ? MIN_WIDTH : dmetrics.widthPixels;        
        bmap = Bitmap.createScaledBitmap(bmap, 
                (int)(w*scale),
                (int)(w*scale/bmapAspectRatio), false);
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
    
    public int getYPixelPosn() {
        return (int)rect.top;
    }
    
    public int getXPixelPosn() {
        return (int)rect.left;
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
        canvas.scale((float)imgWidth/X_FACTOR, (float)imgHeight/Y_FACTOR);
        if (yPosn != NO_FEEDBACK) {
            rect.left = xPosn - OFFSET;
            rect.top = yPosn + OFFSET;
            rect.right = rect.left + DIA;
            rect.bottom = rect.top + DIA;
            float[] pts = {
                rect.left+DIA/2, rect.top+1, rect.left+DIA/2, rect.top-1,
                rect.left+DIA/2, rect.bottom+1, rect.left+DIA/2, rect.bottom-1,
                rect.left-1, rect.top+DIA/2, rect.left+1, rect.top+DIA/2,
                rect.right-1, rect.top+DIA/2, rect.right+1, rect.top+DIA/2
            };
            //canvas.drawRoundRect(rect, 0.5f, 0.5f, paint);
            canvas.drawOval(rect, paint);
            canvas.drawLines(pts, paint);
        }
    }
    
    private void init(Context context) {
        paint = new Paint();
        paint.setColor(0xff0000ff);
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(0.2f);
        
        rect = new RectF();
        xPosn = yPosn = NO_FEEDBACK;
    }
    
    private RectF rect;
    private Paint paint;
    private int xPosn, yPosn;
    private int imgWidth, imgHeight;
    
    private final int OFFSET = 8, DIA = 6;
    public static final int X_FACTOR = 90, Y_FACTOR = 120;
    public static final int NO_FEEDBACK = -1;
}
