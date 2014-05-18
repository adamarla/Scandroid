package com.gradians.collect;

import java.io.File;
import java.util.HashSet;

import android.app.DownloadManager;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
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
import android.widget.TextView;
import android.widget.Toast;

public class ViewWorksheetActivity extends ListActivity implements OnItemClickListener, IConstants {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_quiz);
        
        DownloadManager dm = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        getApplicationContext().registerReceiver(downloadCompleteReceiver, 
                downloadCompleteIntentFilter);
        
        requestIds = new HashSet<String>();
        String[] name_path_ids = this.getIntent().getStringArrayExtra(TAG_ID);
        String[] name_state_ids = new String[name_path_ids.length];
        String[] tokens; String name, path, id, url; int i = 0;
        DownloadManager.Request request;
        for (String name_path_id : name_path_ids) {
            
            tokens = name_path_id.split("-");
            name = tokens[0]; path = tokens[1]; id = tokens[2];
            if (isDownloaded(id)) {
                name_state_ids[i++] = name + "-" + DOWNLOADED + "-" + id; 
                continue;
            }
            name_state_ids[i++] = name + "-" + NOT_DOWNLOADED + "-" + id; 
            
            url = String.format(URL, BANK_HOST_PORT, path);
            request = new DownloadManager.Request(Uri.parse(url));
            request.setVisibleInDownloadsUi(false);
            request.setTitle(name);
            request.setDestinationInExternalFilesDir(getApplicationContext(), null, id);
            requestIds.add(String.valueOf(dm.enqueue(request)));
        }

        this.adapter = new ViewQuizAdapter(this, R.layout.layout_worksheet, name_state_ids);
        this.setListAdapter(adapter);
        this.getListView().setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        String ws_id = ((TextView)view.findViewById(R.id.tvWsName)).getTag().toString();
        if (!isDownloaded(ws_id)) {
            Toast.makeText(getApplicationContext(),
                "Hang on, still downloading...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File file = new File(this.getExternalFilesDir(null), ws_id);
        Intent target = new Intent(Intent.ACTION_VIEW);
        target.setDataAndType(Uri.fromFile(file),"application/pdf");
        target.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        Intent intent = Intent.createChooser(target, "Open File");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), 
                "Please install a free PDF reader app", Toast.LENGTH_SHORT).show();
        }
    }
    
    private boolean isDownloaded(String id) {
        return (new File(this.getExternalFilesDir(null), id)).exists();
    }
    
    private BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
            if (!requestIds.contains(String.valueOf(id))) {
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
            int uri_index = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                onDownloadedComplete(
                        Uri.parse(cursor.getString(uri_index)).getLastPathSegment());
            }
        }
    };
    
    private void onDownloadedComplete(String ws_id) {
        adapter.updateState(ws_id);
    }
    
    private ViewQuizAdapter adapter;
    private HashSet<String> requestIds;

    private final IntentFilter downloadCompleteIntentFilter = 
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);       
    private final String URL = "http://%s/mint/%s/document.pdf";
    
    protected static final char DOWNLOADED = 'D', NOT_DOWNLOADED = 'N';
}

class ViewQuizAdapter extends ArrayAdapter<String> {

    public ViewQuizAdapter(Context context, int resource, String[] name_state_ids) {
        super(context, resource, name_state_ids);
        this.name_state_ids = name_state_ids;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater)this.getContext().
                getSystemService(Context.LAYOUT_INFLATER_SERVICE);
 
        String[] tokens = name_state_ids[position].split("-");
        String name = tokens[0], id = tokens[2];
        char state = tokens[1].charAt(0);
        View rowView = inflater.inflate(R.layout.layout_worksheet, parent, false);
        TextView textView = (TextView) rowView.findViewById(R.id.tvWsName);
        textView.setText(name);
        textView.setTag(id);
        
        int drawable = state == ViewWorksheetActivity.DOWNLOADED ? 
                android.R.drawable.ic_menu_view : android.R.drawable.ic_menu_close_clear_cancel; 
        textView.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0);
        return rowView;
    }
    
    protected void updateState(String ws_id) {
        for (int i = 0; i < name_state_ids.length; i++) {
            if (name_state_ids[i].endsWith(ws_id)) {
                String[] tokens = name_state_ids[i].split("-");
                String name = tokens[0], id = tokens[2];
                name_state_ids[i] = name + "-" + ViewWorksheetActivity.DOWNLOADED + "-" + id;
            }
        }
        this.notifyDataSetChanged();
    }
    
    private String[] name_state_ids;
    
}