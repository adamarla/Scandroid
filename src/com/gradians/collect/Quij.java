package com.gradians.collect;

import java.util.ArrayList;
import java.util.HashMap;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Using it as a collection of questions based on any creiterea. 
 * Need not be a formal Quiz given by an instructor to students.
 * 
 * @author adamarla
 */
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
        dest.writeTypedList(this);
    }
    
    private Quij(Parcel in) {
        name = in.readString();
        id = in.readLong();
        state = (short)in.readInt();
        price = in.readInt();
        this.addAll(in.createTypedArrayList(Question.CREATOR));
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