package com.gradians.collect;

import java.util.ArrayList;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class Manifest extends BaseExpandableListAdapter implements IConstants {
    
    public Manifest(Context context, String json) throws Exception {
        parse(json);
        inflater = LayoutInflater.from(context);
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

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return quizzes.get(groupPosition).get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
            boolean isLastChild, View convertView, ViewGroup parent) {
//        if(convertView == null) {
//            convertView = inflater.inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
//        }
//        
//        Question question = (Question)getChild(groupPosition,childPosition);
//        ((TextView)convertView).setText(question.toString());
        
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.layout_question, parent, false);
        }
        
        Question question = (Question)getChild(groupPosition,childPosition);
        ((TextView)convertView.findViewById(R.id.tvQuestion)).
            setText(question.toString());
        
        if (question.isSent()) {
            ((ImageView)convertView.findViewById(R.id.ivStationary)).
            setImageResource(android.R.drawable.ic_menu_upload);
        } else {
            ((ImageView)convertView.findViewById(R.id.ivStationary)).
            setImageResource(android.R.drawable.ic_menu_camera);
        }
        return convertView;    
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return quizzes.get(groupPosition).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return quizzes.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return quizzes.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return quizzes.get(groupPosition).getId();
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
            View convertView, ViewGroup parent) {
//        if (convertView == null) {
//            convertView = inflater.inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
//        }
//
//        Quiz quiz = (Quiz)getGroup(groupPosition);
//        ((TextView)convertView).setText(quiz.toString());
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.layout_quiz, parent, false);
        }

        Quiz quiz = (Quiz)getGroup(groupPosition);
        ((TextView)convertView.findViewById(R.id.tvQuiz)).
            setText(quiz.toString());
        
        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {        
        return !quizzes.get(groupPosition).get(childPosition).isSent();
    }
    
    public void adjust(int quizPosn, int questionPosn, boolean worksheet) {
        Quiz quiz = quizzes.get(quizPosn);
        Question question = quiz.get(questionPosn);
        question.isSent(true);
        
        if (worksheet) {
            String qrcode = question.getQRCode();
            //Also mark other questions on the same page (if any)
            for (Question q : quiz) {
                if (q.getQRCode().equals(qrcode)) {
                    q.isSent(true);
                }
            }
        }
    }
    
    private void parse(String json) throws Exception {        
        this.quizzes = new ArrayList<Quiz>();
        
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
                if (quiz != null) quizzes.add(quiz);
                quizId = (Long)((JSONObject)item).get("quizId");
                quiz = new Quiz((String)((JSONObject)item).get("quiz"), quizId);
            }
            quiz.add(new Question((String)((JSONObject)item).get("name"),
                    (String)((JSONObject)item).get("scan"), 
                    String.valueOf(((JSONObject)item).get("id"))));
        }
        quizzes.add(quiz);
    }
    
    private ArrayList<Quiz> quizzes;
    private String name, email, token;
    
    private LayoutInflater inflater;
    
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
    
    public void userPlainPaper(boolean plainPaper) {
        this.plainPaper = plainPaper;
    }
    
    public boolean usedPlainPaper() {
        return plainPaper;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private boolean plainPaper;
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
    
    public void isSent(boolean sent) {
        this.sent = sent;
    }
    
    public boolean isSent() {
        return sent;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private boolean sent;
    private String name, QRCode, GRId;
    
}
