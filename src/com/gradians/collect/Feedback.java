package com.gradians.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.util.Log;

class Feedback implements IConstants {    
    
    public static Feedback load(File feedbackDir, Question question) {
        File latex = new File(feedbackDir, question.getId());
        if (!latex.exists()) return null;
        
        int[] partOnPg = question.getPgMap();
        int highestPg = partOnPg[0];
        for (int i = 0; i < partOnPg.length; i++) {
            if (partOnPg[i] < highestPg) {
                highestPg = partOnPg[i];
            }
        }        
        
        BufferedReader br = null;
        Feedback feedback = null;
        try {
            br = new BufferedReader(new FileReader(latex));
            JSONParser jsonParser = new JSONParser();
            JSONObject respObject = (JSONObject)jsonParser.parse(br.readLine());
            JSONArray comments = (JSONArray)respObject.get(COMMENTS_KEY);
            br.close();
            String[] text = new String[comments.size()];
            int[] page = new int[text.length];
            int[] x = new int[text.length]; 
            int[] y = new int[text.length];
            JSONObject firstComment = (JSONObject)comments.get(0);
            int id = ((Long)firstComment.get(ID_KEY)).intValue();            
            int partIdx = 0;
            for (int i = 0; i < comments.size(); i++) {
                JSONObject comment = (JSONObject)comments.get(i);
                text[i] = (String)comment.get(COMMENT_KEY);
                x[i] = ((Long)comment.get(X_POSN_KEY)).intValue();
                y[i] = ((Long)comment.get(Y_POSN_KEY)).intValue();
                if (id != ((Long)comment.get(ID_KEY)).intValue()) {
                    id = ((Long)comment.get(ID_KEY)).intValue();
                    partIdx++;
                }                
                page[i] = partOnPg[partIdx]-highestPg;
            }
            feedback = new Feedback(text, page, x, y);
        } catch (Exception e) { 
            Log.e(TAG, "Error loading feedback " + e.getMessage());
        }
        return feedback;
    }
    
    public Feedback(String[] text, int[] page, int[] x, int[] y) {
        this.text = text; this.page = page; this.x = x; this.y = y;
    }
    public int[] page, x, y;
    public String[] text;
}