package com.namnoit.voicerecorder;

import android.content.Context;
import android.os.Handler;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.namnoit.voicerecorder.data.Recording;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DriveChecker {
    private static final String CONFIG_FILE = "config.json";
    private static final String FOLDER_ID = "folder_id";
    private Context context;
    private ExecutorService executor;
    private Drive mDriveService;
    private ArrayList<Recording> localList;
    private RecordingsAdapter adapter;

    public DriveChecker(Context c, Drive drive, ArrayList<Recording> list, RecordingsAdapter adapter){
        context = c;
        executor = Executors.newSingleThreadExecutor();
        mDriveService = drive;
        this.localList = list;
        this.adapter = adapter;
    }

    public void check(){
        executor.execute(task);
        executor.shutdown();
    }

    private Runnable task = new Runnable() {
        @Override
        public void run() {
            String folderId = "";
            FileList filesAppData;
            try {
                filesAppData = mDriveService.files().list()
                        .setSpaces("appDataFolder")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageSize(10)
                        .execute();
                for (com.google.api.services.drive.model.File file : filesAppData.getFiles()) {
                    if (file.getName().equals(CONFIG_FILE)) {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        mDriveService.files().get(file.getId())
                                .executeMediaAndDownloadTo(outputStream);
                        byte[] byteArray = outputStream.toByteArray();
                        JSONObject json = new JSONObject(new String(byteArray));
                        folderId = json.getString(FOLDER_ID);
                        break;
                    }
                }
            } catch (UserRecoverableAuthIOException e) {
                return;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // List of files in Google Drive folder
            List<File> driveList = new ArrayList<>();
            // Folder has not been created yet, create new folder
            if (!folderId.equals("")) {
                try {
                    Drive.Files.List request = mDriveService.files().list()
                            .setFields("nextPageToken, files")
                            .setQ("trashed = false and '" + folderId + "' in parents")
                            .setPageSize(1000);
                    do {
                        FileList files = request.execute();
                        driveList.addAll(files.getFiles());
                        request.setPageToken(files.getNextPageToken());
                    }
                    while (request.getPageToken() != null &&
                            request.getPageToken().length() > 0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                for (int i = 0;i < localList.size();i++){
                    for (com.google.api.services.drive.model.File driveFile: driveList){
                        Recording recording = localList.get(i);
                        if (recording.getName().equals(driveFile.getName())){
                            final int pos = i;
                            recording.setOnGoogleDrive(true);
                            Handler handler = new Handler(context.getMainLooper());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    adapter.notifyItemChanged(pos);
                                }
                            });
                            break;
                        }
                    }
                }
            }

        }
    };

}
