package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import android.content.Context;
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
        this.context = context;
        this.inflater = LayoutInflater.from(context);
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
        
        Question question = (Question)getChild(groupPosition, childPosition);
        ((TextView)convertView.findViewById(R.id.tvQuestion)).
            setText(question.toString());
        
        String status = getSentStatus(question);        
        ImageView iv = ((ImageView)convertView.findViewById(R.id.ivState));
        if (status.equals(MARKED)) {
            iv.setImageResource(android.R.drawable.checkbox_on_background);
        } else if (status.equals(UNMARKED)) {
            iv.setImageResource(android.R.drawable.checkbox_off_background);
        } else {
            iv.setImageResource(android.R.drawable.ic_secure);
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
        Question q = quizzes.get(groupPosition).get(childPosition);
        return !getSentStatus(q).equals(SENT);
    }
    
    public void checkUncheck(int quizPosn, int questionPosn) {
        Quiz quiz = quizzes.get(quizPosn);
        Question question = quiz.get(questionPosn);
        setSentStatus(question, getSentStatus(question).equals(MARKED)?
                UNMARKED : MARKED);        
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
        File dir = new File(context.getExternalFilesDir(null), APP_DIR_NAME);
        state.store(new FileOutputStream(new File(dir, STATE)), null);
    }
    
    private void parse(String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);        
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);        
        
        File dir = new File(context.getExternalFilesDir(null), APP_DIR_NAME);
        Properties lastState = new Properties();
        File file = new File(dir, STATE); file.createNewFile(); //creates if needed
        lastState.load(new FileInputStream(file));
        
        quizzes = new ArrayList<Quiz>();
        state = new Properties();
        long quizId = 0; Quiz quiz = null;
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = (JSONObject) items.get(i);
            if (quizId == 0 || quizId != (Long)((JSONObject)item).get(QUIZ_ID_KEY)) {
                if (quiz != null) quizzes.add(quiz);
                quizId = (Long)((JSONObject)item).get(QUIZ_ID_KEY);
                quiz = new Quiz((String)((JSONObject)item).get(QUIZ_NAME_KEY), quizId);
            }
            Question question = new Question((String)((JSONObject)item).get(NAME_KEY),
                    (String)((JSONObject)item).get(SCAN_KEY),
                    String.valueOf(((JSONObject)item).get(GR_ID_KEY)));
            quiz.add(question);
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
    private ArrayList<Quiz> quizzes;
    
    private LayoutInflater inflater;
    private Context context;
        
    private static final String 
        TOKEN_KEY = "token", NAME_KEY = "name", EMAIL_KEY = "email",
        UNMARKED = "U", MARKED = "M", SENT = "S";

    
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
    
    private static final long serialVersionUID = 1L;
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
