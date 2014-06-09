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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout.LayoutParams;
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

        float graded, sentRecvd, downloaded;
        graded = quiz.getQuestionsByState(GRADED).length;
        sentRecvd = quiz.getQuestionsByState(SENT).length + 
                quiz.getQuestionsByState(RECEIVED).length;
        downloaded = quiz.size() - (graded + sentRecvd);
        
        TextView tv = (TextView)convertView.findViewById(R.id.tvQuiz);
        tv.setTag(position);
        tv.setText(quiz.toString());

        TextView tvTotal = (TextView)convertView.findViewById(R.id.tvTotal);
        tvTotal.setText(String.format("Total %2d", quiz.size()));
        TextView tvAttempted = (TextView)convertView.findViewById(R.id.tvAttempted);
        tvAttempted.setText(String.format("Sent %2d", (int)(sentRecvd + graded)));
        TextView tvGraded = (TextView)convertView.findViewById(R.id.tvGraded);
        tvGraded.setText(String.format("Graded %2d", (int)graded));

        LayoutParams param = null;
        View vGraded = (View)convertView.findViewById(R.id.vGraded);
        param = new LayoutParams(0, LayoutParams.MATCH_PARENT, graded);
        vGraded.setLayoutParams(param);
        View vAttempted = (View)convertView.findViewById(R.id.vAttempted);
        param = new LayoutParams(0, LayoutParams.MATCH_PARENT, sentRecvd);
        vAttempted.setLayoutParams(param);
        View vDownloaded = (View)convertView.findViewById(R.id.vDownloaded);
        param = new LayoutParams(0, LayoutParams.MATCH_PARENT, downloaded);
        vDownloaded.setLayoutParams(param);

        return convertView;
    }

    public void commit() throws Exception {
        state.store(new FileOutputStream(manifest), null);
    }
    
    private void parse(File appDir, String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject;
        respObject = (JSONObject)jsonParser.parse(json);
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
        Log.d(TAG, manifest.getPath());
        state = new Properties();

        for (int i = 0; i < items.size(); i++) {
            JSONObject quizItem = (JSONObject) items.get(i);
            quizzes[i] = new Quij(((String)quizItem.get(QUIZ_NAME_KEY)).replace(",", " "),
                  (String)quizItem.get(QUIZ_PATH_KEY), (Long)quizItem.get(QUIZ_ID_KEY));
            JSONArray questions = (JSONArray)quizItem.get("questions");
            Log.d(TAG, quizzes[i].getId() + " " + quizzes[i].getName() + " has " + questions.size() + " questions");
            
            for (int j = 0; j < questions.size(); j++) {
                JSONObject item = (JSONObject)questions.get(j);
                Question question = new Question(((String)item.get(NAME_KEY)).replace("-", ""),
                                (String)item.get(ID_KEY),
                                (String)item.get(GR_ID_KEY),
                                (String)item.get(GR_PATH_KEY));
                Log.d(TAG, question.getName() + " " + question.getImgLocn());
                String scan = (String)item.get(SCAN_KEY);
                double marks = (Double)item.get(MARKS_KEY);
                if (scan == null) {
                    question.setState(lastState.getProperty(question.getId()) == null
                            ? WAITING : Short.parseShort(lastState.getProperty(question.getId())));
                } else {
                    question.setScanLocn(scan);
                    question.setState(marks < 0 ? RECEIVED : GRADED);
                }
                quizzes[i].add(question);
            }
        }
        
//        Quij quiz = null; long quizId = 0;
//        for (int i = 0; i < items.size(); i++) {
//
//            JSONObject item = (JSONObject) items.get(i);
//            if (quizId == 0 || quizId != (Long)item.get(QUIZ_ID_KEY)) {
//                if (quiz != null) quizzes.add(quiz);
//                quizId = (Long)item.get(QUIZ_ID_KEY);
//                quiz = new Quij(((String)item.get(QUIZ_NAME_KEY)).replace("-", " "),
//                        (String)item.get(QUIZ_PATH_KEY), quizId);
//            }
//            
//            Question question = new Question(((String)item.get(NAME_KEY)).replace("-", ""),
//                    String.valueOf((Long)item.get(GR_ID_KEY)), (String)item.get(GR_PATH_KEY));
//            String scan = (String)item.get(SCAN_KEY);
//            double marks = (Double)item.get(MARKS_KEY);            
//            if (scan == null) {
//                question.setState(lastState.getProperty(question.getGRId()) == null ? WAITING : 
//                    Short.parseShort(lastState.getProperty(question.getGRId())));
//            } else {
//                question.setScanLocn(scan);
//                question.setState(marks < 0 ? RECEIVED : GRADED);
//            }            
//            quiz.add(question);
//            state.put(question.getGRId(), String.valueOf(question.getState()));
//        }
//        if (quiz != null) quizzes.add(quiz);
    }
        
    private String name, email, token;
    private File manifest;
    
    private Properties state;
    private Quij[] quizzes;
    
    private LayoutInflater inflater;

}

class Quij extends ArrayList<Question> {
    
    public Quij(String name, String path, long id) {
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
    
    private String name, path;
    private long id;
    
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
        return String.format("%s,%s,%s,%s", name, state, id, GRId);
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private String name, id, GRId, imgLocn, scanLocn;
    private short state;    
}


