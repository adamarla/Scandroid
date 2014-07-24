package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class DownloadMonitor extends BroadcastReceiver implements OnDismissListener {
    
    public DownloadMonitor(Activity activity) {
        this.activity = activity;
        this.downloads = new ArrayList<Download>();
    }
    
    public void add(String title, Uri srcUri, Uri destUri) {
        for (Download d : downloads) {
            if (d.srcUri.equals(srcUri)) return;
        }
        downloads.add(new Download(title, srcUri, destUri));
    }
    
    public boolean start(String title, String message, ITaskResult handler) {
        if (downloads.size() == 0) return false;
        
        resultHandler = handler;
        activity.registerReceiver(this, downloadCompleteIntentFilter);
        dm = (DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
        peedee = new ProgressDialog(activity);
        peedee.setMessage(message);
        peedee.setIndeterminate(false);
        peedee.setMax(downloads.size());
        peedee.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        peedee.setOnDismissListener(this);
        peedee.show();
        
        DownloadManager.Request request = null;
        for (Download download : downloads) {
            request = new DownloadManager.Request(download.srcUri);
            request.setTitle(download.title);
            request.setVisibleInDownloadsUi(false);
            request.setDestinationUri(download.destUri);
            requestIds.add(dm.enqueue(request));
        }
        return true;
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
            cursor.close();
            return;
        }            
        
        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
        int filenameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);        
        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
            onDownloadComplete(id, cursor.getString(filenameIndex));
        } else if (DownloadManager.STATUS_FAILED == cursor.getInt(statusIndex)) {
            Log.d("gradians", cursor.getString(filenameIndex) + " " + cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)));
        }
        cursor.close();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        activity.unregisterReceiver(this);
        if (resultHandler != null)
            resultHandler.onTaskResult(ITaskResult.DOWNLOAD_MONITOR_TASK_RESULT_CODE, 
                Activity.RESULT_FIRST_USER, null);       
    }
    
    private void onDownloadComplete(long requestId, String uri) {
        File file = new File(Uri.parse(uri).getPath());
        String name = file.getName();
        if (name.contains("-")) {
            file.renameTo(new File(file.getParentFile(), name.replaceFirst("-.*\\.", ".")));
        }
        requestIds.remove(requestId);
        if (peedee != null) {
            if (requestIds.size() == 0) {
                peedee.dismiss();
            } else {
                peedee.setProgress(peedee.getMax() - requestIds.size());
            }
        }
        if (requestIds.size() == 0 && resultHandler != null) 
            resultHandler.onTaskResult(ITaskResult.DOWNLOAD_MONITOR_TASK_RESULT_CODE, 
                Activity.RESULT_OK, null);        
    }

    private Activity activity;
    private ArrayList<Download> downloads;
    private ProgressDialog peedee;
    private ITaskResult resultHandler;
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
