package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import android.util.Log;

public class Markers implements IConstants {
    
    public Markers(File studentDir) {
        markers = new Properties();
        File filesDir = new File(studentDir, FILES_DIR_NAME);
        markerFile = new File(filesDir, MARKER_FILE);
        try {
            markerFile.createNewFile();
            markers.load(new FileInputStream(markerFile));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }        
    }
    
    public String get(String id) {
        return markers.getProperty(id);
    }
    
    public void set(String id, String value) {
        markers.setProperty(id, value);
    }
    
    public long getFdbkId(String id) {
        String marker = (String)markers.getProperty(id);
        return marker == null ? 0 : Long.parseLong(marker.split(",")[1]);
    }
    
    public boolean isSeen(String id) {
        String marker = (String)markers.getProperty(id);
        return marker == null ? false : marker.split(",")[0].equals("1");    
    }

    public void setFdbkId(String id, long fdbk) {
        String marker = (String)markers.getProperty(id);
        String[] tokens;
        if (marker == null) {
            tokens = new String[2];
            tokens[0] = "0";
        } else {
            tokens = marker.split(",");
        }
        tokens[1] = String.valueOf(fdbk);
        markers.put(id, tokens[0] + "," + tokens[1]);
        commit();
    }
    
    public void setIsSeen(String id) {
        String marker = (String)markers.get(id);
        String[] tokens;
        if (marker == null) {
            tokens = new String[2];
            tokens[1] = "0";
        } else {
            tokens = marker.split(",");
        }
        tokens[0] = "1";
        markers.put(id, tokens[0] + "," + tokens[1]);
        commit();
    }
    
    public void commit() {
        try {
            markers.store(new FileOutputStream(markerFile), null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }        
    }
    
    Properties markers;
    File markerFile;
}
