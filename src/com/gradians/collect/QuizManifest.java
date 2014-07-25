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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

public class QuizManifest implements IConstants {
    
    public QuizManifest(File manifests, String json) throws Exception {
        parse(manifests, json);
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
    
    public Quij[] get() {
        return quizzes;
    }
    
    public void update(Question[] questions) {
        Question question;
        for (Question q : questions) {
            question = questionByIdMap.get(q.getId());
            question.setPgMap(q.getPgMap());
            question.setState(q.getState());
            
            pgMap.setProperty(q.getId(), q.getPgMap("-"));
            sendState.setProperty(q.getId(), q.getSentState("-"));            
        }
        
        for (Quij quiz : quizzes)
            quiz.determineState();
        
        try {
            commit();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }
        
    public Quij[] getInboxItems() {
        ArrayList<Quij> items = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            if (quiz.getState() < NOT_YET_GRADED)
                items.add(quiz);
        }
        return items.toArray(new Quij[items.size()]);
    }
    
    public Quij[] getOutboxItems() {
        ArrayList<Quij> items = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            if (quiz.getState() == NOT_YET_GRADED)
                items.add(quiz);
        }
        return items.toArray(new Quij[items.size()]);
    }
    
    public Quij[] getGradedItems() {
        ArrayList<Quij> items = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            if (quiz.getState() > NOT_YET_GRADED)
                items.add(quiz);
        }
        return items.toArray(new Quij[items.size()]);
    }
    
    public void commit() throws Exception {
        pgMap.store(new FileOutputStream(pgMapFile), null);
        sendState.store(new FileOutputStream(sentStateFile), null);
    }
    
    private void parse(File manifests, String json) throws Exception {
        JSONParser jsonParser = new JSONParser();
        JSONObject respObject = (JSONObject)jsonParser.parse(json);
        
        token = (String)respObject.get(TOKEN_KEY);
        name = (String)respObject.get(NAME_KEY);
        email = (String)respObject.get(EMAIL_KEY);
        JSONArray items = (JSONArray)respObject.get(ITEMS_KEY);
        quizzes = new Quij[items.size()];
        
        questionByIdMap = new HashMap<String, Question>();
        
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
                    question.setExaminer(item.get(EXAMINER_KEY) == null ? 
                        0 : ((Long)item.get(EXAMINER_KEY)).intValue());
                    question.setGRId((String)item.get(GR_ID_KEY));
                    question.setScanLocn((String)item.get(SCANS_KEY));
                    question.setOutOf(((Long)item.get(OUT_OF_KEY)).shortValue());
                    
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
                        question.setState(marks < 0 ? RECEIVED : GRADED);
                        question.setMarks(marks);
                    }                    
                }                
                quizzes[i].add(question);
                questionByIdMap.put(question.getId(), question);
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
    
    private HashMap<String, Question> questionByIdMap;
    private Quij[] quizzes;

}

class Quij extends ArrayList<Question> implements Parcelable, IConstants {
    
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
            // in case questionByIdMap are already captured/sent
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
    
    public void determineState() {
        if (this.get(0).getGRId()[0] == 0) {
            state = NOT_YET_BILLED;
            return;
        }
        
        short leastFarAlong = this.get(this.size()-1).getState();
        for (Question q : this) {
            leastFarAlong = q.getState() < leastFarAlong ?
                q.getState() : leastFarAlong;
        }
        
        switch (leastFarAlong) {
        case WAITING:
        case DOWNLOADED:
            state = NOT_YET_COMPLETED;
            break;
        case CAPTURED:
            state = NOT_YET_SENT;
            break;
        case SENT:
        case RECEIVED:
            state = NOT_YET_GRADED;
            break;
        case GRADED:
            state = GRADED;
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
    public int describeContents() {
        return 0;
    }
    
    public static final Parcelable.Creator<Quij> CREATOR
        = new Parcelable.Creator<Quij>() {

            @Override
            public Quij createFromParcel(Parcel source) {
                return new Quij(source);
            }

            @Override
            public Quij[] newArray(int size) {
                return new Quij[size];
            }
    };
    

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeLong(id);
        dest.writeInt(state);
        dest.writeInt(price);
        dest.writeLong(fdbkMrkr);
    }
    
    private Quij(Parcel in) {
        name = in.readString();
        id = in.readLong();
        state = (short)in.readInt();
        price = in.readInt();
        fdbkMrkr = in.readLong();
    }
    
    @Override
    public String toString() {
        return name;
    }
    
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
    
    public int getExaminer() {
        return examiner;
    }

    public void setGRId(String grIds) {
        if (grIds != null) {
            String[] tokens = grIds.split(",");
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

    public void setExaminer(int examiner) {
        this.examiner = examiner;
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
        dest.writeString(imgLocn);
        dest.writeStringArray(scans);
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
        imgLocn = in.readString();
        scans = in.createStringArray();
    }
    
    private String name, id, imgLocn;
    private int[] grId;
    private String[] scans;
    private int[] pgMap;
    private boolean[] sentState;
    private short state = IConstants.WAITING, imgSpan;
    private float marks;
    private short outof;
    private int examiner;
    
    public static final String SEP = ",";

}