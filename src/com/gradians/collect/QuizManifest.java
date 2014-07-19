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
import android.text.TextUtils;
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

    public void update(int quizPosn, Question[] questions) {
        Quij quiz = quizzes[quizPosn];
        Question q = null;
        for (int i = 0; i < questions.length; i++) {
            q = quiz.get(i);
            q.setPgMap(questions[i].getPgMap());
            q.setState(questions[i].getState());            
            pgMap.setProperty(q.getId(), q.getPgMap("-"));
            sendState.setProperty(q.getId(), q.getSentState("-"));
        }
        quiz.determineState();
        this.notifyDataSetChanged();
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
        pgMap.store(new FileOutputStream(pgMapFile), null);
        sendState.store(new FileOutputStream(sentStateFile), null);
    }
    
    private void parse(File appDir, String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject = (JSONObject)jsonParser.parse(json);
        
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
        quizzes = new Quij[items.size()];
        
        File manifests = new File(appDir, "manifests"); 
        manifests.mkdir();
        
        pgMapFile = new File(manifests, email.replace('@', '.') + ".pg"); 
        pgMapFile.createNewFile(); // creates only if needed
        sentStateFile = new File(manifests, email.replace('@', '.') + ".ss"); 
        sentStateFile.createNewFile(); // creates only if needed
        
        Properties pgMapPrev = new Properties();
        pgMapPrev.load(new FileInputStream(pgMapFile));
        Properties sentStatePrev = new Properties();
        sentStatePrev.load(new FileInputStream(sentStateFile));
        
        pgMap = new Properties();
        sendState = new Properties();
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
                    ((Long)item.get(SUBPARTS_COUNT_KEY)).shortValue(),
                    (String)item.get(IMG_PATH_KEY),
                    ((Long)item.get(IMG_SPAN_KEY)).shortValue());
                
                if (item.get(GR_ID_KEY) != null) {
                    question.setGRId((String)item.get(GR_ID_KEY));
                    question.setScanLocn((String)item.get(SCANS_KEY));

                    boolean allPartsReceived = true;
                    for (String s : question.getScanLocn()) {
                        if (s.equals("")) {
                            allPartsReceived = false;
                            break;
                        }
                    }
                    
                    if (!allPartsReceived) {
                        // check local state
                        String pm = pgMapPrev.getProperty(question.getId());
                        String ss = sentStatePrev.getProperty(question.getId());                        
                        if (pm != null) {
                            question.setPgMap(pm);
                            question.setSentState(ss);
                            if (!pm.contains("0")) question.setState(CAPTURED);
                            if (!ss.contains("0")) question.setState(SENT);
                        }
                    } else {
                        float marks = item.get(MARKS_KEY) == null ? -1f : ((Double)item.get(MARKS_KEY)).floatValue();
                        short outof = item.get(OUT_OF_KEY) == null ? 0 : ((Long)item.get(OUT_OF_KEY)).shortValue();
                        question.setState(marks < 0 ? RECEIVED : GRADED);
                        question.setMarks(marks);
                        question.setOutOf(outof);
                    }                    
                }                
                quizzes[i].add(question);
            }
            quizzes[i].determineState();
            if (quizzes[i].getState() > NOT_YET_BILLED) {
                quizzes[i].updatePgMaps();
            }
        }
    }
        
    private String name, email, token;
    private File pgMapFile, sentStateFile;
    
    private Properties pgMap, sendState;
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
    }
    
    public void updatePgMaps() {
        Question q = null;
        int[] map = null;
        int page = 1;
        HashMap<String, Integer> scanPgMap = new HashMap<String, Integer>();
        for (int i = 0; i < this.size(); i++) {
            q = this.get(i);
            // in case questions are already captured/sent
            map = q.getPgMap();
            for (int k = 0; k < map.length; k++) {
                if (page == map[k]) {
                    page++;
                }
            }
            
            String[] scans = q.getScanLocn();
            for (int j = 0; j < scans.length; j++) {
                if (!scans[j].equals("")) {
                    if (!scanPgMap.containsKey(scans[j])) {
                        scanPgMap.put(scans[j], page);
                        page++;
                    }
                    map[j] = scanPgMap.get(scans[j]);
                }
            }
            q.setPgMap(map);
        }
    }
    
//    public void updateMapFromLayout() {        
//        // derive layout from page breaks (for printed work sheets)
//        if (layout != null) {
//            String[] tokens = layout.length() == 0 ? 
//                    new String[0] : layout.split(",");
//            pgBrks = new int[tokens.length];
//            for (int i = 0; i < pgBrks.length; i++)
//                pgBrks[i] = Integer.valueOf(tokens[i]);
//        }
//        
//        Question q = null;
//        String[] map = null;
//        int page = 1;
//        int partCount = 0;
//        for (int i = 0; i < this.size(); i++) {
//            q = this.get(i);
//            String maps = "";
//            int parts = q.getGRId().split("-").length;
//            boolean multipart = parts > 1;
//            for (int j = 0; j < parts; j++) {
//                page = getPage(partCount);
//                maps += page;
//                if (multipart && j != parts-1) maps += "-";
//                partCount++;
//            }
//            q.setMap(maps);
//        }
//        
//    }
//    
//    private int getPage(int partIndex) {
//        int page = pgBrks.length+1;
//        for (int i = 0; i < pgBrks.length; i++) {
//            if (partIndex <= pgBrks[i]) {
//                page = i+1;
//                break;
//            }
//        }
//        return page;
//    }       
    
    public void determineState() {
        if (this.get(0).getGRId()[0] == 0) {
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
    
//    private int[] pgBrks;
    private int price;
    private String name, path, layout;
    private long id, fdbkMrkr;
    private short state;
    
    private static final long serialVersionUID = 1L;
}

class Question implements Parcelable {
    
    public Question(String name, String id, short subparts, String imgLocn, short span) {
        this.name = name;
        this.id = id;
        this.imgLocn = imgLocn;
        this.imgSpan = span;
        
        this.grId = new int[subparts];
        this.pgMap = new int[subparts];
        this.scans = new String[subparts];
        this.sentState = new boolean[subparts];
    }
    
    public String getId() {
        return id;
    }
    
    public int[] getGRId() {
        return this.grId;
    }
    
    public String getGRId(String delim) {
        String[] tmp = new String[grId.length];
        for (int i = 0; i < tmp.length; i++)
            tmp[i] = String.valueOf(grId[i]);
        return TextUtils.join(delim, tmp);
    }
    
    public String getImgLocn() {
        return imgLocn;
    }
    
    public String[] getScanLocn() {
        return scans;
    }
    
    public String getName() {
        return name;
    }
    
    public short getState() {
        return state;
    }
    
    public int[] getPgMap() {
        return pgMap;
    }

    public String getPgMap(String delim) {
        Integer[] tmp = new Integer[pgMap.length];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = pgMap[i];
        }
        return TextUtils.join(delim, tmp);
    }
    
    public boolean[] getSentState() {
        return sentState;
    }
    
    public String getSentState(String delim) {
        Integer[] tmp = new Integer[sentState.length];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = sentState[i] ? 1 : 0;
        }
        return TextUtils.join(delim, tmp);
    }
    
    public float getMarks() {
        return marks;
    }
    
    public short getOutOf() {
        return outof;
    }
    
    public short getImgSpan() {
        return imgSpan;
    }
    
    public void setGRId(String grIds) {
        if (grIds != null) {
            String[] tokens = grIds.split("-");
            for (int i = 0; i < tokens.length; i++)
                grId[i] = Integer.parseInt(tokens[i]);
        }
    }
    
    public void setGRId(int[] grId) {
        this.grId = grId;
    }
    
    public void setImgLocn(String imgLocn) {
        this.imgLocn = imgLocn;
    }
    
    public void setScanLocn(String scanLocn) {
        if (scanLocn != null) {
            String[] tokens = scanLocn.split(",", -1);
            for (int i = 0; i < scans.length; i++)
                scans[i] = tokens[i];
        }
    }
    
    public void setState(short state) {
        this.state = state;
    }
    
    public void setPgMap(String map) {
        String[] tokens = map.split("-");
        for (int i = 0; i < tokens.length; i++)
            pgMap[i] = Integer.parseInt(tokens[i]);
    }
    
    public void setSentState(String state) {
        String[] tokens = state.split("-");
        for (int i = 0; i < tokens.length; i++)
            sentState[i] = tokens[i].equals("1");
    }
    
    public void setPgMap(int[] map) {
        pgMap = map;
    }
    
    public void setSentState(boolean[] state) {
        sentState = state;
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
    public int describeContents() {
        return 0;
    }
    
    public static final Parcelable.Creator<Question> CREATOR
        = new Parcelable.Creator<Question>() {

            @Override
            public Question createFromParcel(Parcel source) {
                return new Question(source);
            }

            @Override
            public Question[] newArray(int size) {
                return new Question[size];
            }
    };
    

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(id);
        dest.writeInt(state);
        dest.writeIntArray(pgMap);
        dest.writeBooleanArray(sentState);
        dest.writeFloat(marks);
        dest.writeInt(outof);
        dest.writeIntArray(grId);
        dest.writeInt(imgSpan);
    }
    
    private Question(Parcel in) {
        name = in.readString();
        id = in.readString();
        state = (short)in.readInt();
        pgMap = in.createIntArray();
        sentState = in.createBooleanArray();
        marks = in.readFloat();
        outof = (short)in.readInt();
        grId = in.createIntArray();
        imgSpan = (short)in.readInt();
    }
    
    private String name, id, imgLocn;
    private int[] grId;
    private String[] scans;
    private int[] pgMap;
    private boolean[] sentState;
    private short state = IConstants.WAITING, imgSpan;
    private float marks;
    private short outof;
    
    public static final String SEP = ",";

}