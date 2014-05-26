package com.gradians.collect;

import java.util.ArrayList;
import java.util.HashSet;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class DownloadMonitor extends BroadcastReceiver {
    
    public DownloadMonitor(Activity activity) {
        this.activity = activity;
        this.srcDest = new ArrayList<Uri[]>();
    }
    
    public void add(Uri srcUri, Uri destUri) {
        srcDest.add(new Uri[] { srcUri, destUri });
    }
    
    public void start(String title, String message) {
        Log.d("gradians", "DownloadMonitor.start() --> " + srcDest.size());
        if (srcDest.size() == 0) return;
        
        activity.registerReceiver(this, downloadCompleteIntentFilter);
        dm = (DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
        peedee = new ProgressDialog(activity);
        peedee.setTitle(title);
        peedee.setMessage(message);
        peedee.setIndeterminate(false);
        peedee.setMax(srcDest.size());
        peedee.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        peedee.show();
        
        DownloadManager.Request request = null;
        for (Uri[] sd : srcDest) {
            request = new DownloadManager.Request(sd[0]);
            request.setVisibleInDownloadsUi(false);
            request.setDestinationUri(sd[1]);
            requestIds.add(dm.enqueue(request));
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
        if (!requestIds.contains(id)) {
            return;
        }
        
        DownloadManager dm = (DownloadManager)
                context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        Cursor cursor = dm.query(query);

        // it shouldn't be empty, but just in case
        if (!cursor.moveToFirst()) {
            return;
        }            
        
        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
            onDownloadComplete(id);
        }
    }

    private void onDownloadComplete(long requestId) {
        requestIds.remove(requestId);
        if (peedee != null) {
            if (requestIds.size() == 0) {
                peedee.dismiss();
            } else {
                peedee.setProgress(peedee.getMax() - requestIds.size());
            }
        }
        if (requestIds.size() == 0) activity.unregisterReceiver(this);
    }

    private Activity activity;
    private ArrayList<Uri[]> srcDest;
    private ProgressDialog peedee;
    private HashSet<Long> requestIds = new HashSet<Long>();
    private DownloadManager dm;
    private final IntentFilter downloadCompleteIntentFilter = 
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);       
        
}
