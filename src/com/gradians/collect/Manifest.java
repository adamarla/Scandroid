package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import android.content.Context;
import android.graphics.Color;
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
        this.manifestDir = context.getDir(MANIFEST_DIR_NAME, Context.MODE_PRIVATE);
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
            
            if (row[i] != null) {
                tv.setText(row[i].getName());
                tv.setTag(groupPosition + "," + childPosition + "," + i);
                tv.setOnClickListener(listener);
                char status = getSentStatus(row[i]);
                switch(status) {
                case MARKED:
                    tv.setBackgroundResource(R.drawable.unselected);
                    break;
                case UNMARKED:
                    tv.setBackgroundResource(R.drawable.selected);
                    break;
                case SAVED:
                    tv.setBackgroundResource(R.drawable.saved);
                    break;
                case SENT:
                    tv.setBackgroundResource(R.drawable.sent);                    
                }
//                if (status == MARKED) {
//                    tv.setBackgroundResource(R.drawable.unselected);
//                } else if (status == UNMARKED) {
//                    tv.setBackgroundResource(R.drawable.selected);
//                } else {
//                    tv.setBackgroundResource(R.drawable.sent);
//                }
            } else {
                tv.setText("");
                tv.setBackgroundColor(Color.BLACK);
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
        return false;
    }
    
    public void checkUncheck(String tag) {
        String[] positions = tag.split(",");
        int quiz = Integer.parseInt(positions[0]);
        int row = Integer.parseInt(positions[1]);
        int question = Integer.parseInt(positions[2]);
        
        Question q = quizzes.get(quiz).get(row)[question];
        char status = this.getSentStatus(q);
        switch (status) {
        case UNMARKED:
            this.setSentStatus(q, MARKED);
            break;
        case MARKED:
            this.setSentStatus(q, UNMARKED);
        }
//        if (status == UNMARKED) {
//            this.setSentStatus(q, MARKED);
//        } else if (status == MARKED) {
//            this.setSentStatus(q, UNMARKED);
//        }
    }
    
    public String[] getSelected() {
        ArrayList<String> selected = new ArrayList<String>();
        for (Quiz quiz : quizzes) {
            for (Question[] row : quiz) {
                for (Question question : row) {
                    if (question == null) continue;
                    if (getSentStatus(question) == MARKED) {
                        selected.add(String.format("%s-%s", 
                                question.getName().replace("-", ""), 
                                question.getGRId()));
                    }
                }
            }
        }
//        String key = null;
//        Enumeration<Object> keys = state.keys();
//        while (keys.hasMoreElements()) {
//            key = (String)keys.nextElement();
//            if (state.get(key).equals(MARKED)) {
//                selected.add(key);
//            }
//        }
        return selected.toArray(new String[selected.size()]);
    }
    
    public void updateSaved() {
        this.changeStatus(MARKED, SAVED);
    }
    
    public void updateSent() {
        this.changeStatus(SAVED, SENT);
    }
    
    public void commit() throws Exception {
        state.store(new FileOutputStream(new File(manifestDir, this.getEmail())), null);
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
        File file = new File(manifestDir, email); file.createNewFile(); //creates if needed
        lastState.load(new FileInputStream(file));

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
            Question question = new Question(
                    ((String)((JSONObject)item).get(NAME_KEY)).replace("-", ""),
                    String.valueOf(((JSONObject)item).get(GR_ID_KEY)));
            if (i%ITEMS_PER_ROW == 0) {
                row = new Question[ITEMS_PER_ROW];
                quiz.add(row);
            }
            row[i%ITEMS_PER_ROW] = question;
            setSentStatus(question,
                    lastState.getProperty(question.getGRId(), String.valueOf(UNMARKED)).charAt(0));
        }
        if (quiz != null) quizzes.add(quiz);
    }
    
    private void changeStatus(char from, char to) {
        ArrayList<String> selected = new ArrayList<String>();
        for (Quiz quiz : quizzes) {
            for (Question[] row : quiz) {
                for (Question question : row) {
                    if (question == null) continue;
                    if (getSentStatus(question) == from) {
                        setSentStatus(question, to);
                        selected.add(String.format("%s-%s", 
                                question.getName(),
                                question.getGRId()));
                    }
                }
            }
        }        
    }

    private void setSentStatus(Question question, char value) {
        state.put(question.getGRId(), String.valueOf(value));
    }
    
    private char getSentStatus(Question question) {
        return state.getProperty(question.getGRId()).charAt(0);
    }
    
    private String name, email, token;
    private Properties state;
    private File manifestDir;
    private ArrayList<Quiz> quizzes;
    
    private LayoutInflater inflater;        
    private OnClickListener listener;
    
    private final int ITEMS_PER_ROW = 4;
    private static final String 
        TOKEN_KEY = "token", NAME_KEY = "name", EMAIL_KEY = "email";
    private static final char
        UNMARKED = 'U', MARKED = 'M', SAVED = 'D', SENT = 'S';

    
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
