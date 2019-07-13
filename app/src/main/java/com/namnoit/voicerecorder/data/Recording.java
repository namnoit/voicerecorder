package com.namnoit.voicerecorder.data;

public class Recording {
    private int ID, duration;
    private String name, date, md5;
    private long size;


    public Recording(int ID, String name, long size, int duration, String date, String md5){
        this.ID = ID;
        this.duration = duration;
        this.name = name;
        this.date = date;
        this.size = size;
        this.md5 = md5;
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

    public String getMd5() {
        return md5;
    }

    public void setName(String name){
        this.name = name;
    }
}
