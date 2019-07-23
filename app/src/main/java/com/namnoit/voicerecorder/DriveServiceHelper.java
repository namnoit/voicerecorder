package com.namnoit.voicerecorder;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import com.namnoit.voicerecorder.service.RecorderService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A utility for performing read/write operations on Drive files via the REST API and opening a
 * file picker UI via Storage Access Framework.
 */
class DriveServiceHelper {
    private static final String TYPE_FOLDER = "application/vnd.google-apps.folder";
    private static final String TYPE_AUDIO = "audio/mpeg";
    private static final String TYPE_JSON = "application/json";
    private static final String CONFIG_FILE = "config.json";
    private static final String FOLDER_ID = "folder_id";
    private Context context;
    private ExecutorService executor;
    private Drive mDriveService;
    private SharedPreferences pref;
    private RecordingsDbHelper db;
    DriveServiceHelper(Context c, Drive drive){
        context = c;
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mDriveService = drive;
        pref = context.getSharedPreferences(MainActivity.PREF_NAME,Context.MODE_PRIVATE);
        db = new RecordingsDbHelper(context);
    }

    private class CreateFolderRunnable implements Runnable {
        private boolean backup;
        private CreateFolderRunnable(boolean backup){
            this.backup = backup;
        }
        @Override
        public void run() {
            String folderId = "";
            //
            FileList filesAppData = new FileList();
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
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // List of files in Google Drive folder
            List<com.google.api.services.drive.model.File> driveList = new ArrayList<>();
            // Folder has not been created yet, create new folder
            if (folderId.equals("")) {
                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(context.getResources().getString(R.string.app_name));
                fileMetadata.setMimeType(TYPE_FOLDER);
                try {
                    com.google.api.services.drive.model.File folder = mDriveService.files().create(fileMetadata)
                            .setFields("id")
                            .execute();

                    JSONObject json = new JSONObject();
                    json.put(FOLDER_ID,folder.getId());
                    FileWriter fileWriter = new FileWriter(MainActivity.APP_DIR + File.separator + CONFIG_FILE);
                    fileWriter.write(json.toString());
                    fileWriter.close();

                    com.google.api.services.drive.model.File fileMetadataJson = new com.google.api.services.drive.model.File();
                    fileMetadataJson.setName(CONFIG_FILE);
                    fileMetadataJson.setParents(Collections.singletonList("appDataFolder"));
                    File jsonFile = new File(MainActivity.APP_DIR + File.separator + CONFIG_FILE);
                    FileContent jsonContent = new FileContent(TYPE_JSON, jsonFile);
                    try {
                        com.google.api.services.drive.model.File f = mDriveService.files().create(fileMetadataJson, jsonContent)
                                .setFields("id")
                                .execute();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                catch (UserRecoverableAuthIOException e) {
                    Intent errorIntent = e.getIntent();
                    errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(errorIntent);
                    Handler handler = new Handler(context.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context.getApplicationContext(),
                                    context.getResources().getString(R.string.toast_permissions_check_failed),
                                    Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            // Folder exists, retrieve all child files
            else {
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
            }
            RecordingsDbHelper db = new RecordingsDbHelper(context);
            ArrayList<Recording> localList = db.getAll();
            // localList: Files in local
            // driveList: Files in Google Drive
            if (backup) {
                for (Recording recording : localList) {
                    boolean exist = false;
                    for (com.google.api.services.drive.model.File driveFile : driveList) {
                        if (driveFile.getName().equals(recording.getName())) {
                            exist = true;
                            break;
                        }
                    }
                    if (!exist) {
                        UploadThread uploadThread = new UploadThread(recording.getName(), folderId);
                        executor.execute(uploadThread);
                    }
                }
            }
            else {
                for (com.google.api.services.drive.model.File driveFile : driveList) {
                    boolean exist = false;
                    for (Recording recording : localList) {
                        if (recording.getName().equals(driveFile.getName())){
                            exist = true;
                            break;
                        }
                    }
                    if (!exist){
                        DownLoadThread downLoadThread = new DownLoadThread(driveFile.getId(),driveFile.getName());
                        executor.execute(downLoadThread);
                    }
                }
            }
        }
    }


    void backUp() {
        CreateFolderRunnable runnable = new CreateFolderRunnable(true);
        executor.execute(runnable);
    }

    void restore(){
        CreateFolderRunnable runnable = new CreateFolderRunnable(false);
        executor.execute(runnable);
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
                    mDriveService.files().create(fileMetadata, mediaContent)
                            .setFields("id, parents")
                            .execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private class DownLoadThread implements Runnable{
        private String fileId, fileName;

        DownLoadThread(String fileId, String fileName){
            this.fileId = fileId;
            this.fileName = fileName;
        }

        @Override
        public void run() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                mDriveService.files().get(fileId)
                        .executeMediaAndDownloadTo(outputStream);
                byte[] byteArray = outputStream.toByteArray();
                File file = new File(MainActivity.APP_DIR + File.separator + fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(byteArray);
                fileOutputStream.close();

                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                metadataRetriever.setDataSource(MainActivity.APP_DIR + File.separator + fileName);
                String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String dateNoFormat = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
                MessageDigest digest;
                try {
                    digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[8192];
                    int read;
                    InputStream is = new FileInputStream(file);
                    while ((read = is.read(buffer)) > 0) {
                        digest.update(buffer, 0, read);
                    }
                    byte[] hashSum = digest.digest();
                    BigInteger bigInt = new BigInteger(1, hashSum);
                    String hashValue = bigInt.toString(16);
                    // Fill to 32 chars
                    hashValue = String.format("%32s", hashValue).replace(' ', '0');
                    String formattedDate;
                    if (dateNoFormat != null) {
                        SimpleDateFormat readDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.getDefault());
                        readDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                        Date inputDate = readDateFormat.parse(dateNoFormat);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
                        formattedDate = dateFormat.format(inputDate);
                    } else {
                        formattedDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
                    }

                    db.insert(fileName, file.length(), Integer.parseInt(duration), formattedDate, hashValue);
                    Intent broadcast = new Intent(RecorderService.BROADCAST_FINISH_RECORDING);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(broadcast);
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
