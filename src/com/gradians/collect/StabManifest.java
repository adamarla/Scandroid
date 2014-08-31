package com.gradians.collect;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.util.Log;

public class StabManifest implements IConstants {
    
    public StabManifest(JSONArray items) throws Exception {
        parse(items);
    }
    
    public Quij[] getStabs() {
        ArrayList<Quij> toReturn = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            if (quiz != null) {
                quiz.determineState();
                quiz.updatePgMaps();
                toReturn.add(quiz);
            }
        }
        return toReturn.toArray(new Quij[toReturn.size()]);
    }
    
    private void parse(JSONArray items) throws Exception {
        
        if (items == null) return;
        
        Date today = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        int monthsSinceEpoch = getMonthsSinceEpoch(sdf.format(today));
        quizzes = new Quij[monthsSinceEpoch];
        Log.d(TAG, "Months since epoch " + monthsSinceEpoch);
        
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
            question.setGRId((String)item.get(GR_ID_KEY));
            question.setScanLocn((String)item.get(SCANS_KEY));
            question.setOutOf(((Long)item.get(OUT_OF_KEY)).shortValue());
            question.setExaminer(((Long)item.get(EXAMINER_KEY)).intValue());
            question.setFdbkMarker((Long)item.get(FDBK_MRKR_KEY));
            question.setHintMarker(item.get(HINT_MRKR_KEY) == null ? 
                0:(Long)item.get(HINT_MRKR_KEY));
            question.setMarks(((Double)item.get(MARKS_KEY)).floatValue());
            question.setState(GRADED);
            
            monthsSinceEpoch = getMonthsSinceEpoch((String)item.get(NAME_KEY));
            if (quizzes[monthsSinceEpoch] == null)
                quizzes[monthsSinceEpoch] = new Quij(
                    getQuizName((String)item.get(NAME_KEY)), 
                    null, monthsSinceEpoch, 0, null);            
            quizzes[monthsSinceEpoch].add(question);
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