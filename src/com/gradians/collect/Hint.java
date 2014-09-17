package com.gradians.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.util.Log;


class Hint implements IConstants {
    
    public static Hint load(File hintsDir, Question question) {
        File latex = new File(hintsDir, question.getId());
        if (!latex.exists()) return null;
        
        int[] subpartId = question.getSubpartId();
        BufferedReader br = null;
        Hint hint = null;
        try {
            br = new BufferedReader(new FileReader(latex));
            JSONParser jsonParser = new JSONParser();
            JSONObject respObject = (JSONObject)jsonParser.parse(br.readLine());
            br.close();
            if (!respObject.isEmpty()) {
                hint = new Hint(subpartId.length);
                JSONArray items;
                for (int i = 0; i < subpartId.length; i++) {
                    items = (JSONArray)respObject.get(subpartId[i]);
                    String[] text = new String[items.size()];
                    for (int j = 0; j < text.length; j++) {
                        text[j] = (String)items.get(j);
                    }
                    hint.setText(i, text);
                }
            }                
        } catch (Exception e) { 
            Log.e(TAG, "Error loading hint " + e.getMessage());
        }
        return hint;
    }
    
    public Hint(int subparts) {
        this.subparts = subparts;
        text = new String[subparts][];
    }
    
    public void setText(int position, String[] text) {
        this.text[position] = text;
    }
    
    public String[] getText(int position) {
        return this.text[position];
    }
    
    public int subparts;
    public String[][] text;
}