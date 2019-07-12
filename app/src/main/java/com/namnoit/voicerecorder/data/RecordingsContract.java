package com.namnoit.voicerecorder.data;

import android.provider.BaseColumns;

public class RecordingsContract {
    class RecordingsEntry implements BaseColumns {
        public static final String TABLE_NAME = "recordings";
        public static final String COLUMN_ID = BaseColumns._ID;
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_SIZE = "size";
        public static final String COLUMN_DURATION = "duration";
        public static final String COLUMN_DATE = "date";
    }
}
