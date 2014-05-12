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
            
            LayoutParams lp = (LayoutParams)tv.getLayoutParams();
            lp.height = parent.getWidth()/4;
            tv.setLayoutParams(lp);            
            if (row[i] != null) {
                tv.setText(row[i].getName());
                tv.setTag(row[i].getGRId());
                char status = stateLocation.get(row[i].getGRId()).toString().charAt(0);
                switch(status) {
                case MARKED:
                    tv.setOnClickListener(listener);
                    tv.setBackgroundResource(R.drawable.selected);
                    break;
                case UNMARKED:
                    tv.setOnClickListener(listener);
                    tv.setBackgroundResource(R.drawable.unselected);
                    break;
                case SAVED:
                    tv.setClickable(false);
                    tv.setBackgroundResource(R.drawable.saved);
                    break;
                case SENT:
                    tv.setClickable(false);
                    tv.setBackgroundResource(R.drawable.sent);
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
    
    public void checkUncheck(String tag) {        
        String stateLocString = stateLocation.getProperty(tag);
        char status = stateLocString.charAt(0);
        switch (status) {
        case UNMARKED:
            stateLocation.setProperty(tag, stateLocString.replace(UNMARKED, MARKED));
            break;
        case MARKED:
            stateLocation.setProperty(tag, stateLocString.replace(MARKED, UNMARKED));
            break;
        case SAVED:// for re-takes
            stateLocation.setProperty(tag, stateLocString.replace(SAVED, UNMARKED));
        }
        this.notifyDataSetChanged();
    }
    
    public Question[] getSaved() {
        return this.itemsWithStatus(SAVED);
    }    
    
    public Question[] getSelected() {
        return this.itemsWithStatus(MARKED);
    }
    
    public void clearSelected() {
        this.changeStatus(MARKED, UNMARKED);
    }    
    
    public void saveSelected() {
        this.changeStatus(MARKED, SAVED);
    }
    
    public void sendSaved() {
        this.changeStatus(SAVED, SENT);
    }
    
    public void commit() throws Exception {
        stateLocation.store(new FileOutputStream(new File(manifestDir, this.getEmail())), null);
    }
    
    private Question getQuestionAt(String locString) {
        String[] tokens = locString.split(",");
        int[] idx = new int[3];
        idx[0] = Integer.parseInt(tokens[1]);
        idx[1] = Integer.parseInt(tokens[2]);
        idx[2] = Integer.parseInt(tokens[3]);
        return quizzes.get(idx[0]).get(idx[1])[idx[2]];
    }

    private Question[] itemsWithStatus(char status) {
        ArrayList<Question> selected = new ArrayList<Question>();
        String key = null, stateLocString = null;
        Enumeration<Object> keys = stateLocation.keys();
        while (keys.hasMoreElements()) {
            key = (String)keys.nextElement();
            stateLocString = stateLocation.getProperty(key);
            if (stateLocString.charAt(0) == status) {
                selected.add(this.getQuestionAt(stateLocString));
            }
        }
        return selected.toArray(new Question[selected.size()]);
    }
    
    private void changeStatus(char from, char to) {
        String key = null, stateLocString = null;
        Enumeration<Object> keys = stateLocation.keys();
        while (keys.hasMoreElements()) {
            key = (String)keys.nextElement();
            stateLocString = stateLocation.getProperty(key);
            if (stateLocString.charAt(0) == from) {
                stateLocation.put(key, stateLocString.replace(from, to));
            }
        }
        this.notifyDataSetChanged();
    }
    
    private void parse(String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);        
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);        
        
        Properties lastStateLocation = new Properties();
        File file = new File(manifestDir, email); file.createNewFile(); //creates if needed
        lastStateLocation.load(new FileInputStream(file));
    
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
                    String.valueOf(item.get(GR_ID_KEY)));
            position = (i-padding)%ITEMS_PER_ROW; 
            if (position == 0) {
                row = new Question[ITEMS_PER_ROW];
                quiz.add(row);
            }            
            row[position] = question;
            
            lastStateLoc = lastStateLocation.getProperty(question.getGRId());
            stateLoc = String.format("%s,%s,%s,%s", 
                    lastStateLoc == null ? UNMARKED : lastStateLoc.charAt(0), 
                    quizzes.size(), quiz.size()-1, position);            
            stateLocation.put(question.getGRId(), stateLoc);
        }
        if (quiz != null) quizzes.add(quiz);
    }

    private String name, email, token;
    
    private Properties stateLocation;
    private ArrayList<Quiz> quizzes;
    
    private LayoutInflater inflater;        
    private OnClickListener listener;
    
    private File manifestDir;
    
    private final int ITEMS_PER_ROW = 4;
    private final char
        UNMARKED = 'U', MARKED = 'M', SAVED = 'D', SENT = 'S';
    private final String MANIFEST_DIR_NAME = "manifests";
    
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
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name, path;
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
