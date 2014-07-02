package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Properties;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class QuizManifest extends BaseAdapter implements IConstants {
    
    public QuizManifest(Context context, String json) throws Exception {
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

    public void update(int quizPosn, int itemPosn, short newState) {
        Question q = quizzes[quizPosn].get(itemPosn);
        String id = q.getId();
        if (q.getState() != newState) {
            q.setState(newState);
            state.setProperty(id, String.valueOf(newState));
            quizzes[quizPosn].determineState();
            this.notifyDataSetChanged();            
        }
    }
    
    @Override
    public int getCount() {
        return quizzes.length;
    }

    @Override
    public Object getItem(int position) {
        return quizzes[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.layout_quiz, parent, false);
        }
        
        Quij quiz = (Quij)getItem(position);
        
        TextView tv = (TextView)convertView.findViewById(R.id.tvQuiz);
        tv.setTag(position);
        tv.setText(quiz.toString());

        TextView tvTotal = (TextView)convertView.findViewById(R.id.tvTotal);
        tvTotal.setText(String.valueOf(quiz.size()));
        
        if (quiz.getState() == NOT_YET_BILLED) {
            tvTotal.setBackgroundResource(R.drawable.gray_background);
        } else if (quiz.getState() == NOT_YET_STARTED) {
            tvTotal.setBackgroundResource(R.drawable.light_background);
        } else if (quiz.getState() == GRADED) {
            tvTotal.setBackgroundResource(R.drawable.green_background);
        } else {            
            tvTotal.setBackgroundResource(R.drawable.blue_button);
        }
        
        return convertView;
    }

    public void commit() throws Exception {
        state.store(new FileOutputStream(manifest), null);
    }
    
    private void parse(File appDir, String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject = (JSONObject)jsonParser.parse(json);
        
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
        quizzes = new Quij[items.size()];
        
        File manifests = new File(appDir, "manifest"); 
        manifests.mkdir();
        
        Properties lastState = new Properties();
        manifest = new File(manifests, email); manifest.createNewFile();
        lastState.load(new FileInputStream(manifest));
        state = new Properties();

        for (int i = 0; i < items.size(); i++) {
            JSONObject quizItem = (JSONObject) items.get(i);
            quizzes[i] = new Quij(((String)quizItem.get(QUIZ_NAME_KEY)).replace(",", " "),
                (String)quizItem.get(QUIZ_PATH_KEY), (Long)quizItem.get(QUIZ_ID_KEY),
                ((Long)quizItem.get(QUIZ_PRICE_KEY)).intValue());
            JSONArray questions = (JSONArray)quizItem.get("questions");
            
            for (int j = 0; j < questions.size(); j++) {                
                JSONObject item = (JSONObject)questions.get(j);
                Question question = new Question((
                    (String)item.get(NAME_KEY)).replace("-", ""),
                    (String)item.get(ID_KEY),
                    (String)item.get(GR_ID_KEY),
                    (String)item.get(GR_PATH_KEY));

                String scan = (String)item.get(SCAN_KEY);
                double marks = (Double)item.get(MARKS_KEY);
                if (scan == null || scan.equals("")) {
                    question.setState(lastState.getProperty(question.getId()) == null
                            ? WAITING : Short.parseShort(lastState.getProperty(question.getId())));
                } else {
                    question.setScanLocn(scan);
                    question.setState(marks < 0 ? RECEIVED : GRADED);
                }
                quizzes[i].add(question);
            }
            quizzes[i].determineState();
        }
    }
        
    private String name, email, token;
    private File manifest;
    
    private Properties state;
    private Quij[] quizzes;
    
    private LayoutInflater inflater;

}

class Quij extends ArrayList<Question> implements IConstants {
    
    public Quij(String name, String path, long id, int price) {
        this.name = name;
        this.id = id;
        this.path = path;
        this.price = price;
    }
    
    public void determineState() {
        if (this.get(0).getGRId().equals("")) {
            state = NOT_YET_BILLED;
            return;
        }
        
        short mostFarAlong = this.get(0).getState(), 
              leastFarAlong = this.get(this.size()-1).getState();
        for (Question q : this) {            
            mostFarAlong = q.getState() > mostFarAlong ? 
                q.getState() : mostFarAlong;
            leastFarAlong = q.getState() < leastFarAlong ?
                q.getState() : leastFarAlong;
        }
        
        switch (mostFarAlong) {
        case WAITING:
        case DOWNLOADED:
            state = NOT_YET_STARTED;
            break;
        case CAPTURED:
        case SENT:
        case RECEIVED:
            state = leastFarAlong == DOWNLOADED ? 
                    NOT_YET_COMPLETED : NOT_YET_GRADED;
            break;
        case GRADED:
            state = leastFarAlong == GRADED ? 
                    GRADED : NOT_YET_GRADED;
        }
    }
    
    public short getState() {
        return state;
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
    
    public int getPrice() {
        return price;
    }
    
    public Question[] getQuestions() {
        return this.toArray(new Question[this.size()]);
    }
    
    public Question[] getQuestionsByState(int state) {
        ArrayList<Question> filtered = new ArrayList<Question>();
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).getState() == state) {
                filtered.add(this.get(i));
            }
        }
        return filtered.toArray(new Question[filtered.size()]);
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private int price;
    private String name, path;
    private long id;
    private short state;
    
    private static final long serialVersionUID = 1L;
}

class Question {
    
    public Question(String name, String id, String GRId, String imgLocn) {
        this.name = name;
        this.id = id;
        this.GRId = GRId;
        this.imgLocn = imgLocn;
    }
    
    public String getId() {
        return id;
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
    
    public void setGRId(String GRId) {
        this.GRId = GRId;
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
    
    public String getNameStateId() {
        return name + SEP + state + SEP + id + SEP + 
            (GRId.equals("") ? "0" : GRId);
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name, id, GRId, imgLocn, scanLocn;
    private short state;
    
    public static final String SEP = ",";
    
}