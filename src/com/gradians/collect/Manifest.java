package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class Manifest extends BaseExpandableListAdapter implements IConstants {
    public Manifest(Context context, String json, OnClickListener l) throws Exception {
        this.listener = l;
        this.inflater = LayoutInflater.from(context);
        this.appDir = context.getDir(APP_DIR_NAME, Context.MODE_PRIVATE);
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
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.layout_question, parent, false);
        }
        
        Question[] row = (Question[])getChild(groupPosition, childPosition);        
        for (int i = 0; i < row.length; i++) {
            if (row[i] != null) {
                TextView tv = null;
                switch(i) {
                case 0:
                    tv = ((TextView)convertView.findViewById(R.id.tvQuestion1));
                    break;
                case 1:
                    tv = ((TextView)convertView.findViewById(R.id.tvQuestion2));
                    break;
                case 2:
                    tv = ((TextView)convertView.findViewById(R.id.tvQuestion3));
                    break;
                case 3:
                    tv = ((TextView)convertView.findViewById(R.id.tvQuestion4));
                    break;
                }
                tv.setText(row[i].getName());
                tv.setOnClickListener(listener);
                String status = getSentStatus(row[i]);
                if (status.equals(MARKED)) {
                    tv.setBackgroundResource(R.drawable.unselected);
                } else if (status.equals(UNMARKED)) {
                    tv.setBackgroundResource(R.drawable.selected);
                } else {
                    tv.setBackgroundResource(R.drawable.sent);
                }
            }
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
//        Question q = quizzes.get(groupPosition).get(childPosition);
//        return !(frozen || getSentStatus(q).equals(SENT));
        return false;
    }
    
    public void checkUncheck(String name) {
        Question q = this.questionsByName.get(name);
        String status = this.getSentStatus(q);
        if (status.equals(UNMARKED)) {
            this.setSentStatus(q, MARKED);
        } else if (status.equals(MARKED)) {
            this.setSentStatus(q, UNMARKED);
        }
    }
    
    public void checkUncheck(int quizPosn, int questionPosn) {
//        Quiz quiz = quizzes.get(quizPosn);
//        Question question = quiz.get(questionPosn);
//        setSentStatus(question, getSentStatus(question).equals(MARKED)?
//                UNMARKED : MARKED);        
    }

    public String[] getSelected() {
        ArrayList<String> selected = new ArrayList<String>();
        String key = null;
        Enumeration<Object> keys = state.keys();
        while (keys.hasMoreElements()) {
            key = (String)keys.nextElement();
            if (state.get(key).equals(MARKED)) {
                selected.add(key);
            }
        }
        return selected.toArray(new String[selected.size()]);
    }
    
    public void markAsSent() {
        String key = null;
        Enumeration<Object> keys = state.keys();
        while (keys.hasMoreElements()) {
            key = (String)keys.nextElement();
            if (state.get(key).equals(MARKED)) {
                state.put(key, SENT);
            }
        }
    }    
    
    public void commit() throws Exception {
        state.store(new FileOutputStream(new File(appDir, this.getEmail())), null);
    }
    
    private void parse(String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);        
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);        
        
        Properties lastState = new Properties();
        File file = new File(appDir, email); file.createNewFile(); //creates if needed
        lastState.load(new FileInputStream(file));

        questionsByName = new HashMap<String, Question>();
        quizzes = new ArrayList<Quiz>();
        state = new Properties();
        long quizId = 0; Quiz quiz = null; Question[] row = null;
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = (JSONObject) items.get(i);
            if (quizId == 0 || quizId != (Long)((JSONObject)item).get(QUIZ_ID_KEY)) {
                if (quiz != null) quizzes.add(quiz);
                quizId = (Long)((JSONObject)item).get(QUIZ_ID_KEY);
                quiz = new Quiz((String)((JSONObject)item).get(QUIZ_NAME_KEY), quizId);
            }
            Question question = new Question((String)((JSONObject)item).get(NAME_KEY),
                    String.valueOf(((JSONObject)item).get(GR_ID_KEY)));
            if (i%ITEMS_PER_ROW == 0) {
                row = new Question[ITEMS_PER_ROW];
                quiz.add(row);
            }
            questionsByName.put(question.getName(), question);
            row[i%ITEMS_PER_ROW] = question;
            setSentStatus(question, lastState.getProperty(question.getGRId(), UNMARKED));
        }
        if (quiz != null) quizzes.add(quiz);
    }
    
    private void setSentStatus(Question question, String value) {
        state.put(question.getGRId(), String.valueOf(value));
    }
    
    private String getSentStatus(Question question) {
        return state.getProperty(question.getGRId());
    }
    
    private String name, email, token;
    private Properties state;
    private File appDir;
    private ArrayList<Quiz> quizzes;
    private HashMap<String, Question> questionsByName;
    
    private LayoutInflater inflater;        
    private OnClickListener listener;
    
    private final int ITEMS_PER_ROW = 4;
    private static final String 
        TOKEN_KEY = "token", NAME_KEY = "name", EMAIL_KEY = "email";
    private static final String
        UNMARKED = "U", MARKED = "M", SENT = "S";

    
}

class Quiz extends ArrayList<Question[]> {
    
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
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name;
    private long id;
    
    private static final long serialVersionUID = 1L;
}

class Question {
    
    public Question(String name, String GRId) {
        this.name = name;
        this.GRId = GRId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getGRId() {
        return GRId;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name, GRId;
    
}
