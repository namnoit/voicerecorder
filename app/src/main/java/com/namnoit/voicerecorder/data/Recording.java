package com.namnoit.voicerecorder.data;

public class Recording {
    private int ID, duration;
    private String name, date, hashValue;
    private long size;


    public Recording(int ID, String name, long size, int duration, String date, String hashValue){
        this.ID = ID;
        this.duration = duration;
        this.name = name;
        this.date = date;
        this.size = size;
        this.hashValue = hashValue;
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
}
