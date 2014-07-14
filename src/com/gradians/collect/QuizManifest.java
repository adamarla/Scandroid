package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
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
            map.setProperty(id, String.valueOf(newState));
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
        map.store(new FileOutputStream(manifestFile), null);
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
        
        Properties lastMap = new Properties();
        lastMap.load(new FileInputStream(manifestFile));
        map = new Properties();
        
        for (int i = 0; i < items.size(); i++) {
            JSONObject quizItem = (JSONObject) items.get(i);
            quizzes[i] = new Quij((
                (String)quizItem.get(QUIZ_NAME_KEY)).replace(",", " "),
                (String)quizItem.get(QUIZ_PATH_KEY), 
                (Long)quizItem.get(QUIZ_ID_KEY),
                ((Long)quizItem.get(QUIZ_PRICE_KEY)).intValue(),
                (String)quizItem.get(QUIZ_LAYOUT_KEY),
                (Long)quizItem.get(QUIZ_FDBK_KEY));
            
            JSONArray questions = (JSONArray)quizItem.get(QUESTIONS_KEY);
            for (int j = 0; j < questions.size(); j++) {
                JSONObject item = (JSONObject)questions.get(j);
                Question question = new Question((
                    (String)item.get(NAME_KEY)).replace("-", ""),
                    (String)item.get(ID_KEY),
                    (String)item.get(GR_ID_KEY),
                    (String)item.get(IMG_PATH_KEY),
                    ((Long)item.get(IMG_SPAN_KEY)).shortValue());
                
                String scan = (String)item.get(SCANS_KEY);
                String[] tokens = scan.split(",", -1);
                boolean allPartsReceived = true;
                for (String s : tokens) {
                    if (s.equals("")) {
                        allPartsReceived = false;
                        break;
                    }
                }
                question.setScanLocn(scan);                
                if (!allPartsReceived) {
                    // check if any scan was captured
                    String lm = lastMap.getProperty(question.getId());
                    if (lm == null) {
                        question.setState(WAITING);
                    } else {
                        question.setState(CAPTURED);
                        question.setMap(lm);
                    }
                } else {
                    float marks = item.get(MARKS_KEY) == null ? -1f : ((Double)item.get(MARKS_KEY)).floatValue();
                    short outof = item.get(OUT_OF_KEY) == null ? 0 : ((Long)item.get(OUT_OF_KEY)).shortValue();
                    question.setState(marks < 0 ? RECEIVED : GRADED);
                    question.setMarks(marks);
                    question.setOutOf(outof);
                }
                quizzes[i].add(question);
            }
            quizzes[i].determineState();
            if (quizzes[i].getState() > NOT_YET_BILLED) 
                quizzes[i].updateMap();
        }
    }
        
    private String name, email, token;
    private File manifestFile;
    
    private Properties map;
    private Quij[] quizzes;
    
    private LayoutInflater inflater;

}

class Quij extends ArrayList<Question> implements IConstants {
    
    public Quij(String name, String path, long id, int price, String layout, long fdbkMrkr) {
        this.name = name;
        this.id = id;
        this.path = path;
        this.price = price;
        this.layout = layout;
        this.fdbkMrkr = fdbkMrkr;
        if (layout != null) {
            String[] tokens = layout.length() == 0 ? 
                    new String[0] : layout.split(",");
            pgBrks = new int[tokens.length];
            for (int i = 0; i < pgBrks.length; i++)
                pgBrks[i] = Integer.valueOf(tokens[i]);
        }
    }
    
    private int getPage(int partIndex) {
        int page = pgBrks.length+1;
        for (int i = 0; i < pgBrks.length; i++) {
            if (partIndex <= pgBrks[i]) {
                page = i+1;
                break;
            }
        }
        return page;
    }
    
    public void updateMap() {
        Question q = null;
        String map = null;
        int page = 1;
        if (layout != null) {
            int partCount = 0;
            for (int i = 0; i < this.size(); i++) {
                q = this.get(i);
                map = "";
                int parts = q.getGRId().split("-").length;
                boolean multipart = parts > 1;
                for (int j = 0; j < parts; j++) {
                    page = getPage(partCount);
                    map += page;
                    if (multipart && j != parts-1) map += "-";
                    partCount++;
                }
                q.setMap(map);
            }
        } else {
            HashMap<String, Integer> scanPgMap = new HashMap<String, Integer>();            
            for (int i = 0; i < this.size(); i++) {
                q = this.get(i);
                map = "";                
                // in case questions are already captured
                if (q.getMap() != null) {
                    String[] oldmap = q.getMap().split("-");
                    for (String s : oldmap) {
                        if (page == Integer.parseInt(s)) {
                            page++;
                        }
                    }
                }
                String[] scans = q.getScanLocn().split(",", -1);
                for (int j = 0; j < scans.length; j++) {
                    if (scans[j].equals("")) {
                        map += "0";
                    } else {
                        if (!scanPgMap.containsKey(scans[j])) {
                            scanPgMap.put(scans[j], page);
                            page++;
                        }
                        map += scanPgMap.get(scans[j]);
                    }
                    if (j != scans.length-1) map += "-";                    
                }
                q.setMap(map);
            }            
        }
    }
    
    public void determineState() {
        if (this.get(0).getGRId() == null) {
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
    
    public String getLayout() {
        return layout;
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
    
    private int[] pgBrks;
    private int price;
    private String name, path, layout;
    private long id, fdbkMrkr;
    private short state;
    
    private static final long serialVersionUID = 1L;
}

class Question implements Parcelable {
    
    public Question(String name, String id, String GRId, String imgLocn, short span) {
        this.name = name;
        this.id = id;
        this.GRId = GRId;
        this.imgLocn = imgLocn;
        this.imgSpan = span;
        if (GRId != null) {
            map = this.GRId.replaceAll("[0-9]+", "0");
            map = map.replaceAll("-", ",");
        }
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
    
    public String getMap() {
        return map;
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
    
    public short getImgSpan() {
        return imgSpan;
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
    
    public void setMap(String map) {
        this.map = map;
    }
    
    public void setMarks(float marks) {
        this.marks = marks;
    }
    
    public void setOutOf(short outof) {
        this.outof = outof;        
    }
    
    public void setImgSpan(short imgSpan) {
        this.imgSpan = imgSpan;
    }

    @Override
    public String toString() {
        return  name + SEP + 
                state + SEP + 
                marks + SEP + 
                outof + SEP + 
                id + SEP + 
                (GRId.equals("") ? "0" : GRId);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Parcelable.Creator<Question> CREATOR
        = new Parcelable.Creator<Question>() {

            @Override
            public Question createFromParcel(Parcel source) {
                // TODO Auto-generated method stub
                return new Question(source);
            }

            @Override
            public Question[] newArray(int size) {
                // TODO Auto-generated method stub
                return new Question[size];
            }
    };
    

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(id);
        dest.writeInt(state);
        dest.writeString(map);
        dest.writeFloat(marks);
        dest.writeInt(outof);
        dest.writeString(GRId.equals("") ? "0" : GRId);
        dest.writeInt(imgSpan);
        dest.writeString(scanLocn);
    }
    
    private Question(Parcel in) {
        name = in.readString();
        id = in.readString();
        state = (short)in.readInt();
        map = in.readString();
        marks = in.readFloat();
        outof = (short)in.readInt();
        GRId = in.readString();
        imgSpan = (short)in.readInt();
        scanLocn = in.readString();
    }
    
    private String name, id, GRId, imgLocn, scanLocn, map;
    private short state, imgSpan;
    private float marks;
    private short outof;
    
    public static final String SEP = ",";

}