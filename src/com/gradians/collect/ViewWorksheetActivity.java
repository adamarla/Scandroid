package com.gradians.collect;

import java.io.File;
import java.util.HashSet;

import android.app.DownloadManager;
import android.app.ListActivity;
//import android.content.BroadcastReceiver;
//import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ViewWorksheetActivity extends ListActivity implements OnItemClickListener, IConstants {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_quiz);
        
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
//        getApplicationContext().registerReceiver(downloadCompleteReceiver, 
//                downloadCompleteIntentFilter);
        
        requestIds = new HashSet<String>();
        Cursor cursor = this.getDownloadingItems(dm);
        
        String[] name_path_ids = this.getIntent().getStringArrayExtra(TAG);
        String[] tokens; String name, path, id, url;
        DownloadManager.Request request;
        for (String name_path_id : name_path_ids) {
            
            tokens = name_path_id.split("-");
            name = tokens[0]; path = tokens[1]; id = tokens[2];
            if (isDownloading(cursor, id)) continue;
            
            url = String.format(URL, BANK_HOST_PORT, path);
            request = new DownloadManager.Request(Uri.parse(url));
            request.setVisibleInDownloadsUi(false);
            request.setTitle(name);
            request.setDestinationInExternalFilesDir(getApplicationContext(), null, id);
            requestIds.add(String.valueOf(dm.enqueue(request)));
        }
                
        setListAdapter(new ViewQuizAdapter(this, R.layout.layout_worksheet, 
                name_path_ids));
        
        ((ListView)this.getListView()).setOnItemClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.view_quiz, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        String ws_id = ((TextView)view.findViewById(R.id.tvWsName)).getTag().toString();
        DownloadManager dm = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        
        String uri = getFilePath(dm, ws_id);
        if (uri == null) {
            Toast.makeText(getApplicationContext(), 
                "hang on, still downloading...", Toast.LENGTH_SHORT).show();;
            return;
        }
        
        Intent target = new Intent(Intent.ACTION_VIEW);
        target.setDataAndType(Uri.parse(uri),"application/pdf");
        target.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        Intent intent = Intent.createChooser(target, "Open File");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), 
                    "Please install a free PDF reader app", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getFilePath(DownloadManager dm, String id) {
        String uri = null;
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
        Cursor cursor = dm.query(query);
        int col = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
        for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            if (cursor.getString(col).endsWith(id)) {
                uri = cursor.getString(col);
                break;
            }
        }
        return uri;
    }

    private boolean isDownloading(Cursor cursor, String id) {
        boolean isDownloading = false;
        int status_col_idx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
        int uri_col_idx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
        String uri = ""; int status = 0;
        for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            uri = cursor.getString(uri_col_idx);
            status = cursor.getInt(status_col_idx);
            if (uri.endsWith(id)) {
                isDownloading = true;
                break;
            }
        }
        
        if (status == DownloadManager.STATUS_SUCCESSFUL)
            isDownloading = (new File(Uri.parse(uri).getPath())).exists();
        
        return isDownloading;
    }

    private Cursor getDownloadingItems(DownloadManager dm) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterByStatus(
            DownloadManager.STATUS_PAUSED|
            DownloadManager.STATUS_PENDING|
            DownloadManager.STATUS_RUNNING|
            DownloadManager.STATUS_SUCCESSFUL);
        return dm.query(query);
    }

//    private IntentFilter downloadCompleteIntentFilter = 
//            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
//    private BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            // TODO Auto-generated method stub
//            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
//            if (requestIds.contains(String.valueOf(id))) {
//                return;
//            }
//            
//            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
//            DownloadManager.Query query = new DownloadManager.Query();
//            query.setFilterById(id);
//            Cursor cursor = dm.query(query);
//
//            // it shouldn't be empty, but just in case
//            if (!cursor.moveToFirst()) {
//                Log.e(TAG, "Empty row");
//                return;
//            }
//            
//            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
//            if (DownloadManager.STATUS_SUCCESSFUL != cursor.getInt(statusIndex)) {
//                return;
//            } else {
//                int c = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
//                
//            }
//        }
//    };
    
    private final String URL = "http://%s/mint/%s/document.pdf";
    private HashSet<String> requestIds;

}

class ViewQuizAdapter extends ArrayAdapter<String> {

    public ViewQuizAdapter(Context context, int resource, String[] name_path_ids) {
        super(context, resource, name_path_ids);
        this.name_path_ids = name_path_ids;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater)this.getContext().
                getSystemService(Context.LAYOUT_INFLATER_SERVICE);
 
        String[] tokens = name_path_ids[position].split("-");
        String name = tokens[0],id = tokens[2];
        
        View rowView = inflater.inflate(R.layout.layout_worksheet, parent, false);
        TextView textView = (TextView) rowView.findViewById(R.id.tvWsName);
        textView.setText(name);
        textView.setTag(id);
        
        return rowView;
    }
    
    private String[] name_path_ids;
    
}