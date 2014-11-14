package com.gradians.collect;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * ImageView that scales the image up properly for android versions below and above 17
 * 
 * Found it here
 * https://github.com/triposo/barone/blob/master/src/com/triposo/barone/ScalingImageView.java
 * 
 * Discussion on it here
 * http://stackoverflow.com/questions/7719617/imageview-adjustviewbounds-not-working?rq=1
 * 
 * @author adamarla
 *
 */
class StretchyImageView extends ImageView {

    public StretchyImageView(Context context) {
        super(context);
    }

    public StretchyImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StretchyImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Drawable mDrawable = getDrawable();
        if (mDrawable != null) {
            int mDrawableWidth = mDrawable.getIntrinsicWidth();
            int mDrawableHeight = mDrawable.getIntrinsicHeight();
            float actualAspect = (float) mDrawableWidth / (float) mDrawableHeight;
            
            // Assuming the width is ok, so we calculate the height.
            final int actualWidth = MeasureSpec.getSize(widthMeasureSpec);
            final int height = (int) (actualWidth / actualAspect);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}