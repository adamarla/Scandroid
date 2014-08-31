package com.gradians.collect;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

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
        return getPgMap("-") + "," + getSentState("-");
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