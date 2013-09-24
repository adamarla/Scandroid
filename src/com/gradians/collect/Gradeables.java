package com.gradians.collect;

import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.util.Log;

public class Gradeables extends ArrayList<Quiz> implements IConstants {
    
    public Gradeables(String json) throws Exception {
        parse(json);
    }
    
    public String getName() {
        return name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getAuthToken() {
        return token;
    }
    
    public String[] getQuizNames() {
        String[] names = new String[this.size()];
        for (int i = 0; i < this.size(); i++) {
            names[i] = this.get(i).getName();
        }        
        return names;
    }
    
    public String[] getQuestionNames(int quizPosn) {
        Quiz quiz = this.get(quizPosn);
        String[] names = quiz != null? new String[quiz.size()]:
            new String[0];
        for (int i = 0; i < quiz.size(); i++) {
            names[i] = quiz.get(i).getName();
        }
        return names;
   }
    
    public String getQRCode(int quizPosn, int questionPosn) {
        return this.get(quizPosn).get(questionPosn).getQRCode();
    }
    
    public String getGRId(int quizPosn, int questionPosn) {
        return this.get(quizPosn).get(questionPosn).getGRId();
    }    
    
    public void adjust(int quizPosn, int questionPosn, boolean worksheet) {
        Quiz quiz = this.get(quizPosn);
        String qrcode = quiz.remove(questionPosn).getQRCode();
        if (worksheet) {
            //Also remove other questions on the same page (if any)
            boolean[] toPop = new boolean[quiz.size()];
            for (int i = 0; i < quiz.size(); i++) {
                toPop[i] = quiz.get(i).getQRCode().equals(qrcode);
            }
            for (int i = 0; i < toPop.length; i++) {
                if (toPop[i]) quiz.remove(i);
            }
        }
        if (quiz.size() == 0) this.remove(quizPosn);
    }
    
    private void parse(String json) throws Exception {
        
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);
        Log.v(TAG, name + " " + email + " " + token);
        
        JSONArray items = (JSONArray)respObject.get("gradeables");
        long quizId = 0;
        Quiz quiz = null;
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = (JSONObject) items.get(i);
            if (quizId == 0 || quizId != (Long)((JSONObject)item).get("quizId")) {
                if (quiz != null) this.add(quiz);
                quizId = (Long)((JSONObject)item).get("quizId");
                quiz = new Quiz((String)((JSONObject)item).get("quiz"), quizId);
            }
            quiz.add(new Question((String)((JSONObject)item).get("name"),
                    (String)((JSONObject)item).get("scan"), 
                    String.valueOf(((JSONObject)item).get("id"))));
        }
        this.add(quiz);
    }
    
    private String name, email, token;
    
    private static final String 
        TOKEN_KEY = "token", NAME_KEY = "name", EMAIL_KEY = "email";

}

class Quiz extends ArrayList<Question> {
    
    public Quiz(String name, long id) {
        this.name = name;
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public long getId() {
        return id;
    }
    
    private String name;
    private long id;
}

class Question {
    
    public Question(String name, String QRCode, String GRId) {
        this.name = name;
        this.QRCode = QRCode;
        this.GRId = GRId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getQRCode() {
        return QRCode;
    }
    
    public String getGRId() {
        return GRId;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name, QRCode, GRId;
}
