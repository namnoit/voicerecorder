package com.namnoit.voicerecorder;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferenceManager {
    private static final String PREF_NAME = "config";
    private static SharedPreferenceManager sSharedPrefs;
    private SharedPreferences mPref;

    public static class Key{
        public static final String QUALITY_KEY = "quality";
        public static final String STATUS_KEY = "status";
        public static final String RECORD_STATUS_KEY = "RECORD_STATUS";
        public static final String CURRENT_POSITION_KEY = "current_position";
        public static final String FILE_NAME_KEY = "file_name";
        public static final String DURATION_KEY = "duration";
        public static final String PAUSE_POSITION = "PAUSE_POSITION";
        static final String CURRENT_POSITION_ADAPTER_KEY = "selected_position";
    }


    private SharedPreferenceManager(Context c){
        mPref = c.getSharedPreferences(PREF_NAME,Context.MODE_PRIVATE);
    }

    public static SharedPreferenceManager getInstance(Context context) {
        if (sSharedPrefs == null) {
            sSharedPrefs = new SharedPreferenceManager(context.getApplicationContext());
        }
        return sSharedPrefs;
    }

    public static SharedPreferenceManager getInstance(){
        if(sSharedPrefs != null){
            return sSharedPrefs;
        }
        throw new IllegalArgumentException("Need context to create SharedPreferenceManager");
    }

    public void put(String key, String val) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putString(key, val);
        editor.apply();
    }

    public void put(String key, int val) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putInt(key, val);
        editor.apply();
    }

    public void put(String key, boolean val) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putBoolean(key, val);
        editor.apply();
    }

    public Boolean getBoolean(String key) {
        return mPref.getBoolean(key, true);
    }

    public String getString(String key) {
        return mPref.getString(key, "");
    }

    public int getInt(String key) {
        return mPref.getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        return mPref.getInt(key, defaultValue);
    }

}
