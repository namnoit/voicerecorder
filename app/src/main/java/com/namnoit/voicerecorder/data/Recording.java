package com.namnoit.voicerecorder.data;

public class Recording {
    public static final int LOCATION_ON_PHONE = 0;
    public static final int LOCATION_ON_DRIVE = 1;
    public static final int LOCATION_PHONE_DRIVE = 2;

    private int ID, duration;
    private String name, date, hashValue;
    private long size;
    private int location;

    public Recording(int ID, String name, long size, int duration, String date, String hashValue){
        this.ID = ID;
        this.duration = duration;
        this.name = name;
        this.date = date;
        this.size = size;
        this.hashValue = hashValue;
        location = LOCATION_ON_PHONE;
    }

    public int getID() {
        return ID;
    }

    public int getDuration() {
        return duration;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public String getDate() {
        return date;
    }

    public String getHashValue() {
        return hashValue;
    }

    public void setName(String name){
        this.name = name;
    }

    public void setLocation(int loc){
        location = loc;
    }

    public int getLocation() {
        return location;
    }
}
