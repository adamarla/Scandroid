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
            tvTotal.setBackgroundResource(R.drawable.blue_background);
        } else if (quiz.getState() == NOT_YET_STARTED) {
            tvTotal.setBackgroundResource(R.drawable.gray_background);
        } else if (quiz.getState() == NOT_YET_GRADED) {    
            tvTotal.setBackgroundResource(R.drawable.lt_green_background);
        } else if (quiz.getState() == GRADED) {
            tvTotal.setBackgroundResource(R.drawable.green_background);
            tvTotal.setText(String.format("%2.1f/%2d", quiz.getScore(), quiz.getMax()));
        }
        
        return convertView;
    }

    public void commit() throws Exception {
        state.store(new FileOutputStream(manifestFile), null);
    }
    
    private void parse(File appDir, String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject = (JSONObject)jsonParser.parse(json);
        
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
        quizzes = new Quij[items.size()];
        
        File manifests = new File(appDir, "manifestFile"); 
        manifests.mkdir();
        
        manifestFile = new File(manifests, email.replace('@', '.')); 
        manifestFile.createNewFile(); // creates only if needed
        
        Properties lastState = new Properties();
        lastState.load(new FileInputStream(manifestFile));
        state = new Properties();
        
        for (int i = 0; i < items.size(); i++) {
            JSONObject quizItem = (JSONObject) items.get(i);
            quizzes[i] = new Quij((
                (String)quizItem.get(QUIZ_NAME_KEY)).replace(",", " "),
                (String)quizItem.get(QUIZ_PATH_KEY), 
                (Long)quizItem.get(QUIZ_ID_KEY),
                ((Long)quizItem.get(QUIZ_PRICE_KEY)).intValue(),
                (Long)quizItem.get(QUIZ_FDBK_KEY));
            JSONArray questions = (JSONArray)quizItem.get(QUESTIONS_KEY);
            
            for (int j = 0; j < questions.size(); j++) {                
                JSONObject item = (JSONObject)questions.get(j);
                Question question = new Question((
                    (String)item.get(NAME_KEY)).replace("-", ""),
                    (String)item.get(ID_KEY),
                    (String)item.get(GR_ID_KEY),
                    (String)item.get(GR_PATH_KEY));

                String scan = (String)item.get(SCAN_KEY);
                float marks = item.get(MARKS_KEY) == null ? -1 : ((Double)item.get(MARKS_KEY)).floatValue();
                short outof = item.get(OUT_OF_KEY) == null ? 0 : ((Long)item.get(OUT_OF_KEY)).shortValue();
                String ls = lastState.getProperty(question.getId());
                if (scan == null || scan.equals("")) {
                    question.setState(ls == null ? WAITING : (short)((short)ls.charAt(0)-(short)'0'));
                } else {
                    question.setScanLocn(scan);
                    question.setState(marks < 0 ? RECEIVED : GRADED);
                    question.setMarks(marks);
                    question.setOutOf(outof);
                }
                quizzes[i].add(question);
            }
            quizzes[i].determineState();
        }
    }
        
    private String name, email, token;
    private File manifestFile;
    
    private Properties state;
    private Quij[] quizzes;
    
    private LayoutInflater inflater;

}

class Quij extends ArrayList<Question> implements IConstants {
    
    public Quij(String name, String path, long id, int price, long fdbkMrkr) {
        this.name = name;
        this.id = id;
        this.path = path;
        this.price = price;
        this.fdbkMrkr = fdbkMrkr;
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
    
    public double getScore() {
        double total = 0;
        for (Question q : this)
            total += q.getMarks();
        return total;
    }
    
    public short getMax() {
        short max = 0;
        for (Question q : this)
            max += q.getOutOf();
        return max;
    }
    
    public long getFdbkMrkr() {
        return fdbkMrkr;
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
    private long id, fdbkMrkr;
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
    
    public float getMarks() {
        return marks;
    }
    
    public short getOutOf() {
        return outof;
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
    
    public void setMarks(float marks) {
        this.marks = marks;
    }
    
    public void setOutOf(short outof) {
        this.outof = outof;        
    }
    
    public String getNameStateScoreId() {
        return name + SEP + state + SEP + marks + SEP + outof + SEP
            + id + SEP + (GRId.equals("") ? "0" : GRId);
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name, id, GRId, imgLocn, scanLocn;
    private short state;
    private float marks;
    private short outof;
    
    public static final String SEP = ",";
    
}