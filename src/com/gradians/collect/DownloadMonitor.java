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

public class DownloadMonitor extends BroadcastReceiver {
    
    public DownloadMonitor(Activity activity) {
        this.activity = activity;
        this.downloads = new ArrayList<Download>();
    }
    
    public void add(String title, Uri srcUri, Uri destUri) {
        downloads.add(new Download(title, srcUri, destUri));
    }
    
    public void start(String title, String message) {
        if (downloads.size() == 0) return;
        
        activity.registerReceiver(this, downloadCompleteIntentFilter);
        dm = (DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
        peedee = new ProgressDialog(activity);
        peedee.setMessage(message);
        peedee.setIndeterminate(false);
        peedee.setMax(downloads.size());
        peedee.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        peedee.show();
        
        DownloadManager.Request request = null;
        for (Download download : downloads) {
            request = new DownloadManager.Request(download.srcUri);
            request.setTitle(download.title);
            request.setVisibleInDownloadsUi(false);
            request.setDestinationUri(download.destUri);
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
    private ArrayList<Download> downloads;
    private ProgressDialog peedee;
    private HashSet<Long> requestIds = new HashSet<Long>();
    private DownloadManager dm;
    private final IntentFilter downloadCompleteIntentFilter = 
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);       
        
}

class Download {
    
    public Download(String title, Uri src, Uri dest) {
        this.title = title;
        this.srcUri = src;
        this.destUri = dest;
    }
    
    public String title;
    public Uri srcUri, destUri;
}
