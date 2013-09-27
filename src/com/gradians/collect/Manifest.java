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
        
        Question question = (Question)getChild(groupPosition,childPosition);
        ((TextView)convertView.findViewById(R.id.tvQuestion)).
            setText(question.toString());
        
        if (getSentStatus(question)) {
            ((ImageView)convertView.findViewById(R.id.ivStationary)).
            setImageResource(android.R.drawable.ic_lock_lock);
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
        return !getSentStatus(quizzes.get(groupPosition).get(childPosition));
    }
    
    public void adjust(int quizPosn, int questionPosn, boolean worksheet) {
        Quiz quiz = quizzes.get(quizPosn);        
        Question question = quiz.get(questionPosn);
        markAsSent(question);
        
        if (worksheet) {
            String qrcode = question.getQRCode();
            //Also mark other questions on the same page (if any)
            for (Question q : quiz) {
                if (q.getQRCode().equals(qrcode)) {
                    markAsSent(q);
                }
            }
        }
    }
    
    public void commit() throws Exception {
        File dir = new File(context.getExternalFilesDir(null), APP_DIR_NAME);
        File file = new File(dir, SENT_STATUS);
        sentStatus.store(new FileOutputStream(file), null);
    }
    
    private void parse(String json) throws Exception {        
        this.quizzes = new ArrayList<Quiz>();
        
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);        
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
        
        this.sentStatus = new Properties();        
        long quizId = 0;
        Quiz quiz = null;
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
            sentStatus.put(question.getGRId(), "false");
        }
        if (quiz != null) quizzes.add(quiz);

        File dir = new File(context.getExternalFilesDir(null), APP_DIR_NAME);
        File file = new File(dir, SENT_STATUS); file.createNewFile();
        Properties lastState = new Properties();
        lastState.load(new FileInputStream(file));

        String grId = null;
        Enumeration keys = lastState.keys();
        while (keys.hasMoreElements()) {
            grId = (String)keys.nextElement();
            if (sentStatus.containsKey(grId)) {
                sentStatus.put(grId, lastState.get(grId));
            }
        }
    }
    
    private boolean getSentStatus(Question question) {
        return Boolean.parseBoolean(sentStatus.getProperty(question.getGRId()));
    }
    
    private void markAsSent(Question question) {
        sentStatus.put(question.getGRId(), "true");
    }
    
    private String name, email, token;
    private Properties sentStatus;    
    private ArrayList<Quiz> quizzes;
    
    private LayoutInflater inflater;
    private Context context;
    
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
