package com.gradians.collect;

import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

class Question implements Parcelable, JSONAware, IConstants {
    
    public Question(String name, String id, long qsnId, String spIds, String imgLocn, short span) {
        this.name = name;
        this.id = id;
        this.qsnId = qsnId;
        this.imgLocn = imgLocn;
        this.imgSpan = span;
        
        String[] tokens = spIds.split(",");
        int subparts = tokens.length;
        this.spId = new int[subparts];
        for (int i = 0; i < subparts; i++)
            spId[i] = Integer.valueOf(tokens[i]);
        this.grId = new int[subparts];
        this.pgMap = new int[subparts];
        this.scans = new String[subparts];
    }
    
    public String getId() {
        return id;
    }
    
    public long getQsnId() {
        return qsnId;
    }
    
    public int[] getSubpartId() {
        return this.spId;
    }
    
    public String getSubpartId(String delim) {
        String[] tmp = new String[grId.length];
        for (int i = 0; i < tmp.length; i++)
            tmp[i] = String.valueOf(spId[i]);
        return TextUtils.join(delim, tmp);
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
    
    public char getVersion() {
        int posn = this.imgLocn.lastIndexOf('/');
        return imgLocn.charAt(posn+1);
    }

    public void setGRId(String grIds) {
        if (grIds != null) {
            String[] tokens = grIds.split(",");
            for (int i = 0; i < tokens.length; i++)
                grId[i] = Integer.parseInt(tokens[i]);
        }
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setGRId(int[] grId) {
        this.grId = grId;
    }
    
    public void setImgLocn(String imgLocn) {
        this.imgLocn = imgLocn;
    }
    
    public void setScanLocn(String scanLocn) {
        String[] tokens = scanLocn.split(",", -1);
        int page = 1;
        for (int i = 0; i < scans.length; i++) {
            scans[i] = tokens[i];
            if (scans[i].equals("")) continue;
            if (i != 0 && !tokens[i].equals(scans[i-1])) {
                page++;
            }
            pgMap[i] = page;
        }
    }
    
    public void setState(short state) {
        this.state = state;
    }
    
    public void setPgMap(String map) {
        String[] tokens = map.split("-");
        for (int i = 0; i < tokens.length; i++)
            pgMap[i] = Integer.parseInt(tokens[i]);
        if (!map.contains("0"))
            setState(IConstants.SENT);// will be checked later
        else
            setState(IConstants.DOWNLOADED);
    }
    
    public void setPgMap(int[] map) {
        pgMap = map;
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
    
    public boolean isPuzzle() {
        return puzzle;
    }

    public void setPuzzle(boolean puzzle) {
        this.puzzle = puzzle;
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
        dest.writeIntArray(spId);
        dest.writeInt(state);
        dest.writeIntArray(pgMap);
        dest.writeFloat(marks);
        dest.writeInt(outof);
        dest.writeIntArray(grId);
        dest.writeInt(imgSpan);
        dest.writeString(imgLocn);
        dest.writeStringArray(scans);
        dest.writeInt(puzzle ? 1 : 0);
        dest.writeLong(hintMrkr);
        dest.writeLong(fdbkMrkr);
    }
    
    private Question(Parcel in) {
        name = in.readString();
        id = in.readString();
        qsnId = in.readLong();
        spId = in.createIntArray();
        state = (short)in.readInt();
        pgMap = in.createIntArray();
        marks = in.readFloat();
        outof = (short)in.readInt();
        grId = in.createIntArray();
        imgSpan = (short)in.readInt();
        imgLocn = in.readString();
        scans = in.createStringArray();
        puzzle = in.readInt() == 1;
        hintMrkr = in.readLong();
        fdbkMrkr = in.readLong();
    }
    
    @Override
    public String toString() {
        int sent = getState() == SENT ? 1 : 0;
        return getPgMap("-") + "," + sent;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public String toJSONString() {
        JSONObject obj = new JSONObject();
        obj.put(NAME_KEY, name);
        obj.put(ID_KEY, id);
        obj.put(QUESN_ID_KEY, qsnId);
        obj.put(SBPRTS_ID_KEY, getSubpartId(","));
        obj.put(IMG_PATH_KEY, imgLocn);
        obj.put(IMG_SPAN_KEY, imgSpan);
        obj.put(GR_ID_KEY, grId[0] == 0 ? null : getGRId(","));
        obj.put(SCANS_KEY, grId[0] == 0 ? null : TextUtils.join(",", scans));
        obj.put(OUT_OF_KEY, outof);
        obj.put(MARKS_KEY, grId[0] == 0 ? -1f : marks);
        obj.put(EXAMINER_KEY, examiner == 0 ? null : examiner);
        obj.put(FDBK_MRKR_KEY, fdbkMrkr == 0 ? null : fdbkMrkr);
        obj.put(HINT_MRKR_KEY, hintMrkr == 0 ? null : hintMrkr);
        return obj.toString();
    }

    private String name, id, imgLocn;
    private long qsnId, hintMrkr, fdbkMrkr;
    private int[] spId;
    private int[] grId;
    private String[] scans;
    private int[] pgMap;
    private short state = LOCKED, imgSpan;
    private float marks;
    private short outof;
    private int examiner;
    private boolean puzzle;
    
    public static final String SEP = ",";


 }