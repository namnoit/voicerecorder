package com.namnoit.voicerecorder;


import android.content.Context;
import android.content.SharedPreferences;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A utility for performing read/write operations on Drive files via the REST API and opening a
 * file picker UI via Storage Access Framework.
 */
class DriveServiceHelper {
    private static final String TYPE_FOLDER = "application/vnd.google-apps.folder";
    private static final String TYPE_AUDIO = "audio/mpeg";
    private Context context;
    private ExecutorService executor;
    private Drive mDriveService;
    private SharedPreferences pref;
    DriveServiceHelper(Context c, Drive drive){
        context = c;
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mDriveService = drive;
        pref = context.getSharedPreferences(MainActivity.PREF_NAME,Context.MODE_PRIVATE);
    }

    private Runnable createFolderRunnable = new Runnable() {
        @Override
        public void run() {
            String folderId = pref.getString(MainActivity.KEY_FOLDER_ID,"");
            // List of files in Google Drive folder
            List<com.google.api.services.drive.model.File> fileList = new ArrayList<>();
            // Folder has not been created yet, create new folder
            if (folderId.equals("")) {
                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(context.getResources().getString(R.string.app_name));
                fileMetadata.setMimeType(TYPE_FOLDER);
                try {
                    com.google.api.services.drive.model.File folder = mDriveService.files().create(fileMetadata)
                            .setFields("id")
                            .execute();
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString(MainActivity.KEY_FOLDER_ID, folderId = folder.getId());
                    editor.apply();
                }
                catch (UserRecoverableAuthIOException e) {
                    context.startActivity(e.getIntent());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Folder exists, retrieve all child files
            else {
                try {
                    Drive.Files.List request = mDriveService.files().list().setFields("nextPageToken, files");
                    request.setQ("trashed = false and '" + folderId + "' in parents");
                    request.setPageSize(1000);
                    do {
                        FileList files = request.execute();
                        fileList.addAll(files.getFiles());
                        request.setPageToken(files.getNextPageToken());
                        request.setPageToken(files.getNextPageToken());
                    }
                    while (request.getPageToken() != null &&
                            request.getPageToken().length() > 0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            RecordingsDbHelper db = new RecordingsDbHelper(context);
            ArrayList<Recording> list = db.getAll();
            if (fileList.isEmpty()) {
                for (Recording recording : list) {
                    UploadThread uploadThread = new UploadThread(recording.getName(),folderId);
                    executor.execute(uploadThread);
                }
            }
            else
            for (Recording recording: list){
                boolean exist = false;
                for (com.google.api.services.drive.model.File driveFile: fileList) {
                    if (driveFile.getName().equals(recording.getName())) {
                        exist = true;
                        break;
                    }
                }
                if (!exist) {
                    UploadThread uploadThread = new UploadThread(recording.getName(),folderId);
                    executor.execute(uploadThread);
                }
            }
        }
    };



    public void upload() {
        executor.execute(createFolderRunnable);
    }


    private class UploadThread implements Runnable{
        private String fileName, folderId;

        UploadThread(String fileName, String folderId){
            this.fileName = fileName;
            this.folderId = folderId;
        }

        @Override
        public void run() {
            File file = new File(MainActivity.APP_DIR + File.separator + fileName);
            if (file.exists()){
                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(folderId));
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
