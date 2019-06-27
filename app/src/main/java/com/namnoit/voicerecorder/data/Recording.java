package com.namnoit.voicerecorder.data;

public class Recording {
    private int ID, duration;
    private String name, date;

    public Recording(int ID, String name, int duration, String date){
        this.ID = ID;
        this.duration = duration;
        this.name = name;
        this.date = date;
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

    public String getDate() {
        return date;
    }
}
