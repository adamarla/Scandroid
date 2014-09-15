package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.util.Log;

public class StabManifest extends BaseManifest {
    
    public StabManifest(File dir, JSONArray items, Topic[] topics) throws Exception {
        super(dir, items, topics);
    }
    
    public Quij[] getStabs() {
        ArrayList<Quij> toReturn = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            if (quiz != null) {
                quiz.determineState();
                toReturn.add(quiz);
            }
        }
        return toReturn.size() > 0 ? 
            toReturn.toArray(new Quij[toReturn.size()]):
            new Quij[0];
    }
    
    @Override
    public void parse(JSONArray items) throws Exception {        
        if (items == null) return;
        
        Date today = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        int monthsSinceEpoch = getMonthsSinceEpoch(sdf.format(today));
        quizzes = new Quij[monthsSinceEpoch];
        
        JSONObject item;
        for (int i = items.size()-1; i > -1; i--) {
            item = (JSONObject)items.get(i);
            Question question = new Question(
                getQuestionName((String)item.get(NAME_KEY)),
                (String)item.get(ID_KEY),
                (Long)item.get(QUESN_ID_KEY),
                (String)item.get(SBPRTS_ID_KEY),
                (String)item.get(IMG_PATH_KEY),
                ((Long)item.get(IMG_SPAN_KEY)).shortValue());
            question.setPuzzle((Boolean)item.get(PZL_KEY));
            question.setScanLocn((String)item.get(SCANS_KEY));
            question.setOutOf(((Long)item.get(OUT_OF_KEY)).shortValue());
            question.setExaminer(((Long)item.get(EXAMINER_KEY)).intValue());
            question.setMarks(((Double)item.get(MARKS_KEY)).floatValue());            
            question.setFdbkMarker((Long)item.get(FDBK_MRKR_KEY));
            
            question.setState(question.getMarks() < 0 ? RECEIVED : GRADED);
            state.remove(question.getId());
            
            monthsSinceEpoch = getMonthsSinceEpoch((String)item.get(NAME_KEY));
            if (quizzes[monthsSinceEpoch] == null)
                quizzes[monthsSinceEpoch] = new Quij(
                    getQuizName((String)item.get(NAME_KEY)), 
                    null, monthsSinceEpoch, 0, "QSN", null);
            question.setName(String.format("Q.%d", (quizzes[monthsSinceEpoch].size()+1)));
            quizzes[monthsSinceEpoch].add(question);
            questionByIdMap.put(question.getId(), question);
        }
    }
    
    private String getQuestionName(String yyyy_mm_dd) {
        SimpleDateFormat informatter = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        SimpleDateFormat outformatter = new SimpleDateFormat("EEE, d MMM", Locale.UK);
        Date d = null; String s;
        try {
            s = informatter.format(informatter.parse(yyyy_mm_dd));
            d = (Date)informatter.parseObject(s);
        } catch (Exception e) { }
        return outformatter.format(d);
    }
    
    private String getQuizName(String yyyy_mm_dd) {
        SimpleDateFormat informatter = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        SimpleDateFormat outformatter = new SimpleDateFormat("MMM ''yy", Locale.UK);
        Date d = null; String s;
        try {
            s = informatter.format(informatter.parse(yyyy_mm_dd));
            d = (Date)informatter.parseObject(s);
        } catch (Exception e) { }
        return outformatter.format(d);
    }
    
    private int getMonthsSinceEpoch(String yyyy_mm_dd) {
        // Epoch - 11/2012, when it all began!
        int year = Integer.parseInt(yyyy_mm_dd.substring(2, 4));
        int month = Integer.parseInt(yyyy_mm_dd.substring(5, 7));
        return month + 12*(year - 12) - 11;
    }
    
    private Quij[] quizzes;

}