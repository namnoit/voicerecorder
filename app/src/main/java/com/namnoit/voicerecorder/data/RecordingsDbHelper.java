package com.namnoit.voicerecorder.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RecordingsDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "studentsinfo.db";
    public static int DATABASE_VERSION = 1;
    private static final String TEXT_TYPE = " TEXT";
    private static final String INT_TYPE = " INTEGER";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_ENTRIES = "CREATE TABLE " +
            RecordingsContract.RecordingsEntry.TABLE_NAME + " (" +
            RecordingsContract.RecordingsEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT DEFAULT 0," +
            RecordingsContract.RecordingsEntry.COLUMN_NAME + TEXT_TYPE + COMMA_SEP +
            RecordingsContract.RecordingsEntry.COLUMN_DATE + TEXT_TYPE + COMMA_SEP +
            RecordingsContract.RecordingsEntry.COLUMN_DURATION + INT_TYPE + ")";
    private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " +
            RecordingsContract.RecordingsEntry.TABLE_NAME;

    public RecordingsDbHelper(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        DATABASE_VERSION++;
        onCreate(db);
    }

    public List<Recording> getAll(){
        List<Recording> recordings = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " +
                RecordingsContract.RecordingsEntry.TABLE_NAME, null);
        if (cursor.moveToFirst()) {
            do {
                Recording recording = new Recording(cursor.getInt(0),
                        cursor.getString(0),
                        cursor.getInt(1),
                        cursor.getString(1));
                recordings.add(recording);
            } while (cursor.moveToNext());
        }
        db.close();
        return recordings;
    }
}
