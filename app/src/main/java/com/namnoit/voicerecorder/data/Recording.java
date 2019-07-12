package com.namnoit.voicerecorder.data;

public class Recording {
    private int ID, duration;
    private String name, date;
    private long size;


    public Recording(int ID, String name, long size, int duration, String date){
        this.ID = ID;
        this.duration = duration;
        this.name = name;
        this.date = date;
        this.size = size;
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

    public void setName(String name){
        this.name = name;
    }
}
