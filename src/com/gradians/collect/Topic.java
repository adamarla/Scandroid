package com.gradians.collect;

import android.os.Parcel;
import android.os.Parcelable;

public class Topic implements Parcelable {
    
    public long id, verticalId;
    public String name, verticalName;
    
    public Topic(long id, String name, long vId, String vName) {
        this.id = id;
        this.name = name;
        this.verticalId = vId;
        this.verticalName = vName;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Parcelable.Creator<Topic> CREATOR
        = new Parcelable.Creator<Topic>() {

        @Override
        public Topic createFromParcel(Parcel source) {
            return new Topic(source);
        }

        @Override
        public Topic[] newArray(int size) {
            return new Topic[size];
        }
    };

    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);        
        dest.writeString(name);
        dest.writeLong(verticalId);        
        dest.writeString(verticalName);
    }
    
    private Topic(Parcel in) {
        id = in.readLong();
        name = in.readString();
        verticalId = in.readLong();
        verticalName = in.readString();
    }
}
