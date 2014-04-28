package com.gradians.collect;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    public CameraPreview(Context context) {
        super(context);//constructor to be used by Editor only
    }
    
    @SuppressWarnings("deprecation")
    public CameraPreview(Context context, Camera camera) {
        super(context);
        this.camera = camera;
        SurfaceHolder holder = this.getHolder();
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder.addCallback(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        // Nothing to be done
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        Camera.Parameters params = camera.getParameters();
        params.setRotation(90);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        camera.stopPreview();
        try {
            camera.setParameters(params);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
        } catch (Exception e) { 
            Log.e("scanbot", e.getMessage());
        }
        camera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Nothing to be done
    }
    
    private Camera camera;

}
