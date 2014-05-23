package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class Manifest extends BaseExpandableListAdapter implements IConstants {

    public Manifest(Context context, String json, OnClickListener ocl) throws Exception {
        this.listener = ocl;
        this.inflater = LayoutInflater.from(context);
        parse(context.getFilesDir(), json);
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
            
            LayoutParams lp = (LayoutParams)tv.getLayoutParams();
            lp.height = parent.getWidth()/4;
            tv.setLayoutParams(lp);            
            if (row[i] != null) {
                tv.setText(row[i].getName());
                tv.setTag(row[i].getGRId());
                short state = row[i].getState();
                switch(state) {
                case WAITING:
                    tv.setOnClickListener(listener);
                    tv.setBackgroundResource(R.drawable.selected);
                    break;
                case DOWNLOADED:
                    tv.setOnClickListener(listener);
                    tv.setBackgroundResource(R.drawable.unselected);
                    break;
                case CAPTURED:
                    tv.setOnClickListener(listener);
                    tv.setBackgroundResource(R.drawable.saved);
                    break;
                case SENT:
                    tv.setOnClickListener(listener);
                    tv.setBackgroundResource(R.drawable.sent);
                case RECEIVED:
                    tv.setOnClickListener(listener);
                    tv.setBackgroundResource(R.drawable.selected);
                    break;
                case GRADED:
                    tv.setOnClickListener(listener);
                    tv.setBackgroundResource(R.drawable.unselected);
                    break;
                }
            } else {
                tv.setText("");
                tv.setClickable(false);
                tv.setBackgroundResource(R.drawable.blank);
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
        TextView tv = (TextView)convertView.findViewById(R.id.tvQuiz);
        tv.setTag(groupPosition);
        tv.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)tv.getLayoutParams();
        lp.height = parent.getWidth()/4;
        tv.setLayoutParams(lp);
        tv.setText(quiz.toString());
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
    
    public void update(String id, short state) {
        Question q = this.findQuestionById(id);
        if (q.getState() != state) {
            q.setState(state);
            String stateLoc = stateLocation.getProperty(id);
            stateLocation.setProperty(id, (String.valueOf(state) + stateLoc.substring(1)));
            this.notifyDataSetChanged();            
        }
    }       
    
    public void checkUncheck(String tag) {
        String stateLoc = stateLocation.getProperty(tag);
        char status = stateLoc.charAt(0);
        switch (status) {
        case UNMARKED:
            stateLocation.setProperty(tag, (String.valueOf(MARKED) + stateLoc.substring(1)));
            break;
        case MARKED:
            stateLocation.setProperty(tag, (String.valueOf(UNMARKED) + stateLoc.substring(1)));
            break;
        case SAVED:// for re-takes
            stateLocation.setProperty(tag, (String.valueOf(SAVED) + stateLoc.substring(1)));
        }
        this.notifyDataSetChanged();
    }
    
    public Question[] getSaved() {
        return this.questionsWithStatus('S');
    }    
    
    public Question[] getSelected() {
        return this.questionsWithStatus('M');
    }
    
    public void clearSelected() {
        this.changeStatus((short)'M', (short)'U');
    }    
    
    public void saveSelected() {
        this.changeStatus((short)'M', (short)'S');
    }
    
    public void sendSaved() {
        this.changeStatus((short)'S', (short)'S');
    }
    
    public void commit() throws Exception {
        stateLocation.store(new FileOutputStream(manifest), null);
    }
    
    private Question findQuestionById(String id) {
        String locString = stateLocation.getProperty(id);
        String[] tokens = locString.split(",");
        int[] idx = new int[3];
        idx[0] = Integer.parseInt(tokens[1]);
        idx[1] = Integer.parseInt(tokens[2]);
        idx[2] = Integer.parseInt(tokens[3]);
        return quizzes.get(idx[0]).get(idx[1])[idx[2]];
    }

    private Question[] questionsWithStatus(char status) {
        ArrayList<Question> selected = new ArrayList<Question>();
        String id = null, stateLoc = null;
        Enumeration<Object> ids = stateLocation.keys();
        while (ids.hasMoreElements()) {
            id = (String)ids.nextElement();
            stateLoc = stateLocation.getProperty(id);
            if (stateLoc.charAt(0) == status) {
                selected.add(findQuestionById(stateLoc));
            }
        }
        return selected.toArray(new Question[selected.size()]);
    }
    
    private void changeStatus(short from, short to) {
        String key = null, stateLocString = null;
        Enumeration<Object> keys = stateLocation.keys();
        while (keys.hasMoreElements()) {
            key = (String)keys.nextElement();
            stateLocString = stateLocation.getProperty(key);
            if (stateLocString.charAt(0) == from) {
                Question q = findQuestionById(key);
                stateLocation.put(key, stateLocString.replace((char)from, (char)to));
                q.setState(to);
            }
        }
        this.notifyDataSetChanged();
    }
    
    private void parse(File appDir, String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
        
        File manifests = new File(appDir, MANIFEST_DIR_NAME); 
        manifests.mkdir();
        
        Properties lastStateLocation = new Properties();
        manifest = new File(manifests, email); manifest.createNewFile();
        lastStateLocation.load(new FileInputStream(manifest));
        
        quizzes = new ArrayList<Quiz>();
        stateLocation = new Properties();
        
        Quiz quiz = null; Question[] row = null;
        String stateLoc = null, lastStateLoc = null;
        long quizId = 0; int padding = 0, position = 0;
        for (int i = 0; i < items.size(); i++) {

            JSONObject item = (JSONObject) items.get(i);
            if (quizId == 0 || quizId != (Long)item.get(QUIZ_ID_KEY)) {
                if (quiz != null) quizzes.add(quiz);
                quizId = (Long)item.get(QUIZ_ID_KEY);
                quiz = new Quiz(((String)item.get(QUIZ_NAME_KEY)).replace("-", " "),
                        (String)item.get(QUIZ_PATH_KEY), quizId);
                padding = i%ITEMS_PER_ROW;
            }
            Question question = new Question(((String)item.get(NAME_KEY)).replace("-", ""),
                    String.valueOf((Long)item.get(GR_ID_KEY)), (String)item.get(GR_PATH_KEY));
            String scan = (String)item.get("scan");
            double marks = (Double)item.get("marks");
            if (scan == null) {
                lastStateLoc = lastStateLocation.getProperty(question.getGRId());
                question.setState(lastStateLoc == null ? WAITING : 
                    Short.parseShort(lastStateLoc.substring(0, 1)));
            } else {
                question.setScanLocn(scan);
                question.setState(marks < 0 ? RECEIVED : GRADED);
            }
            
            position = (i-padding)%ITEMS_PER_ROW; 
            if (position == 0) {
                row = new Question[ITEMS_PER_ROW];
                quiz.add(row);
            }                        
            row[position] = question;
            
            stateLoc = String.format("%s,%s,%s,%s",
                    question.getState(), quizzes.size(), quiz.size()-1, position);
            stateLocation.put(question.getGRId(), stateLoc);
            
        }
        if (quiz != null) quizzes.add(quiz);
    }
        
    private String name, email, token;
    private File manifest;
    
    private Properties stateLocation;
    private ArrayList<Quiz> quizzes;
    
    private LayoutInflater inflater;        
    private OnClickListener listener;
    
    private final int ITEMS_PER_ROW = 4;
    
}

class Quiz extends ArrayList<Question[]> {
    
    public Quiz(String name, String path, long id) {
        this.name = name;
        this.id = id;
        this.path = path;
    }
    
    public String getName() {
        return name;
    }
    
    public long getId() {
        return id;
    }
    
    public String getPath() {
        return path;
    }
    
    public Question[] getQuestions() {
        ArrayList<Question> questions = new ArrayList<Question>();
        for (Question[] row : this) {
            for (Question question : row) {
                if (question != null)
                    questions.add(question);
            }            
        }
        return questions.toArray(new Question[questions.size()]);
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name, path;
    private long id;
    
    private static final long serialVersionUID = 1L;
}

class Question {
    
    public Question(String name, String GRId, String imgLocn) {
        this.name = name;
        this.GRId = GRId;
        this.imgLocn = imgLocn;
    }
    
    public String getGRId() {
        return GRId;
    }
    
    public String getImgLocn() {
        return imgLocn;
    }
    
    public String getScanLocn() {
        return scanLocn;
    }
    
    public String getName() {
        return name;
    }
    
    public short getState() {
        return state;
    }
    
    public String getNameStateId() {
        return String.format("%s-%s-%s", name, state, GRId);
    }
    
    public void setImgLocn(String imgLocn) {
        this.imgLocn = imgLocn;
    }
    
    public void setScanLocn(String scanLocn) {
        this.scanLocn = scanLocn;
    }
    
    public void setState(short state) {
        this.state = state;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name, GRId, imgLocn, scanLocn;
    private short state;
}
