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
            question.setSentState(q.getSentState());
            question.setState(q.getState());            
            stateMap.setProperty(q.getId(), q.toString());
        }
        
        for (Quij quiz : quizzes)
            quiz.determineState();        
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
        stateMap.store(new FileOutputStream(stateFile), null);
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
        stateFile = new File(manifests, email.replace('@', '.'));
        stateFile.createNewFile();
        
        Properties lastState = new Properties();
        lastState.load(new FileInputStream(stateFile));
        
        stateMap = new Properties();
        for (int i = 0; i < items.size(); i++) {
            JSONObject quizItem = (JSONObject) items.get(i);
            quizzes[i] = new Quij((
                (String)quizItem.get(QUIZ_NAME_KEY)).replace(",", " "),
                (String)quizItem.get(QUIZ_PATH_KEY), 
                (Long)quizItem.get(QUIZ_ID_KEY),
                ((Long)quizItem.get(QUIZ_PRICE_KEY)).intValue(),
                (String)quizItem.get(QUIZ_LAYOUT_KEY));
            
            JSONArray questions = (JSONArray)quizItem.get(QUESTIONS_KEY);
            for (int j = 0; j < questions.size(); j++) {
                JSONObject item = (JSONObject)questions.get(j);
                Question question = new Question((
                    (String)item.get(NAME_KEY)).replace("-", ""),
                    (String)item.get(ID_KEY),
                    (Long)item.get(QUESN_ID_KEY),
                    (String)item.get(SBPRTS_ID_KEY),
                    (String)item.get(IMG_PATH_KEY),
                    ((Long)item.get(IMG_SPAN_KEY)).shortValue());

                if (item.get(GR_ID_KEY) != null) {
                    question.setGRId((String)item.get(GR_ID_KEY));
                    question.setScanLocn((String)item.get(SCANS_KEY));
                    question.setOutOf(((Long)item.get(OUT_OF_KEY)).shortValue());
                    question.setExaminer(item.get(EXAMINER_KEY) == null ? 
                        0 : ((Long)item.get(EXAMINER_KEY)).intValue());
                    question.setFdbkMarker(item.get(FDBK_MRKR_KEY) == null ? 
                        0 : (Long)item.get(FDBK_MRKR_KEY));
                    question.setHintMarker(item.get(HINT_MRKR_KEY) == null ? 
                        0 : (Long)item.get(HINT_MRKR_KEY));
                    
                    boolean allPartsReceived = true;
                    for (String s : question.getScanLocn()) {
                        if (s.equals("")) {
                            allPartsReceived = false;
                            break;
                        }
                    }
                    
                    if (!allPartsReceived) {
                        // check local state
                        String qsnState = lastState.getProperty(question.getId());
                        if (qsnState != null) {
                            String[] tokens = qsnState.split(",");
                            
                            if (!tokens[0].contains("0")) question.setState(CAPTURED);
                            if (!tokens[1].contains("0")) question.setState(SENT);                            
                            question.setPgMap(tokens[0]);
                            question.setSentState(tokens[1]);
                        }                        
                    } else {
                        float marks = item.get(MARKS_KEY) == null ? -1f : 
                            ((Double)item.get(MARKS_KEY)).floatValue();
                        question.setState(marks < 0 ? RECEIVED : GRADED);
                        question.setMarks(marks);
                    }
                }
                quizzes[i].add(question);
                questionByIdMap.put(question.getId(), question);
            }
            quizzes[i].determineState();
            quizzes[i].updatePgMaps();
        }
    }
        
    private String name, email, token;
    private File stateFile;
    private Properties stateMap;
    
    private HashMap<String, Question> questionByIdMap;
    private Quij[] quizzes;

}

class Quij extends ArrayList<Question> implements Parcelable, IConstants {
    
    public Quij(String name, String path, long id, int price, String layout) {
        this.name = name;
        this.id = id;
        this.path = path;
        this.price = price;
        this.layout = layout;
    }
    
    public void updatePgMaps() {
        if (this.state == NOT_YET_BILLED) return;
        
        Question q = null;
        int[] map = null;
        int page = 1;
        HashMap<String, Integer> scanPgMap = new HashMap<String, Integer>();
        for (int i = 0; i < this.size(); i++) {
            q = this.get(i);
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
    }
    
    private Quij(Parcel in) {
        name = in.readString();
        id = in.readLong();
        state = (short)in.readInt();
        price = in.readInt();
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    private int price;
    private String name, path, layout;
    private long id;
    private short state;
    
    private static final long serialVersionUID = 1L;
}

class Question implements Parcelable {
    
    public Question(String name, String id, long qsnId, String spId, String imgLocn, short span) {
        this.name = name;
        this.id = id;
        this.qsnId = qsnId;
        this.imgLocn = imgLocn;
        this.imgSpan = span;
        
        this.spId = spId.split(",");
        int subparts = this.spId.length;
        this.grId = new int[subparts];
        this.pgMap = new int[subparts];
        this.scans = new String[subparts];
        this.sentState = new boolean[subparts];
    }
    
    public String getId() {
        return id;
    }
    
    public long getQsnId() {
        return qsnId;
    }
    
    public String[] getSubpartId() {
        return this.spId;
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
    
    public long getFdbkMarker() {
        return fdbkMrkr;
    }
    
    public long getHintMarker() {
        return hintMrkr;
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
    
    public void setFdbkMarker(long marker) {
        this.fdbkMrkr = marker;        
    }
    
    public void setHintMarker(long marker) {
        this.hintMrkr = marker;
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
        dest.writeLong(qsnId);
        dest.writeStringArray(spId);
        dest.writeInt(state);
        dest.writeIntArray(pgMap);
        dest.writeBooleanArray(sentState);
        dest.writeFloat(marks);
        dest.writeInt(outof);
        dest.writeIntArray(grId);
        dest.writeInt(imgSpan);
        dest.writeString(imgLocn);
        dest.writeStringArray(scans);
        dest.writeLong(hintMrkr);
        dest.writeLong(fdbkMrkr);
    }
    
    private Question(Parcel in) {
        name = in.readString();
        id = in.readString();
        qsnId = in.readLong();
        spId = in.createStringArray();
        state = (short)in.readInt();
        pgMap = in.createIntArray();
        sentState = in.createBooleanArray();
        marks = in.readFloat();
        outof = (short)in.readInt();
        grId = in.createIntArray();
        imgSpan = (short)in.readInt();
        imgLocn = in.readString();
        scans = in.createStringArray();
        hintMrkr = in.readLong();
        fdbkMrkr = in.readLong();
    }
    
    @Override
    public String toString() {
        return getPgMap("-") + ","
            + getSentState("-");
    }
    
    private String name, id, imgLocn;
    private long qsnId, hintMrkr, fdbkMrkr;
    private String[] spId;
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