package com.namnoit.voicerecorder;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A utility for performing read/write operations on Drive files via the REST API and opening a
 * file picker UI via Storage Access Framework.
 */
public class DriveServiceHelper {
    public static final String TYPE_FOLDER = "application/vnd.google-apps.folder";
    public static final String TYPE_AUDIO = "audio/mpeg";
    private Context context;
    private ExecutorService executor;
    private Drive mDriveService;
    private SharedPreferences pref;
    public DriveServiceHelper(Context c, Drive drive){
        context = c;
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mDriveService = drive;
        pref = context.getSharedPreferences(MainActivity.PREF_NAME,Context.MODE_PRIVATE);
    }

    private Runnable createFolderRunnable = new Runnable() {
        @Override
        public void run() {
            if (pref.getString(MainActivity.KEY_FOLDER_ID,"").equals("")) {
                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(context.getResources().getString(R.string.app_name));
                fileMetadata.setMimeType(TYPE_FOLDER);
                try {
                    com.google.api.services.drive.model.File folder = mDriveService.files().create(fileMetadata)
                            .setFields("id")
                            .execute();

                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString(MainActivity.KEY_FOLDER_ID, folder.getId());
                    editor.apply();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };


    public void upload () {
        executor.execute(createFolderRunnable);
        RecordingsDbHelper db = new RecordingsDbHelper(context);
        ArrayList<Recording> list = db.getAll();
        for (Recording recording: list){
            UploadThread uploadThread = new UploadThread(recording.getName());
            executor.execute(uploadThread);
        }

    }


    private class UploadThread implements Runnable{
        private String fileName;

        public UploadThread(String fileName){
            this.fileName = fileName;
        }

        @Override
        public void run() {
            int count = 0;
            String folderId;
            while ((folderId = pref.getString(MainActivity.KEY_FOLDER_ID,"")).equals("")) {
//                if (count > 10) return;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                count++;
            }
            File file = new File(MainActivity.APP_DIR + File.separator + fileName);
            if (file.exists()){
                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(folderId));
                Log.d("folderid",folderId);
                FileContent mediaContent = new FileContent(TYPE_AUDIO, file);
                try {
                    com.google.api.services.drive.model.File driveFile = mDriveService.files().create(fileMetadata, mediaContent)
                            .setFields("id, parents")
                            .execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}

