package com.gradians.collect;

import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.webkit.WebView;

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