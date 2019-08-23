package com.namnoit.voicerecorder.drive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.namnoit.voicerecorder.MainActivity;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.RecordingsAdapter;
import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import com.namnoit.voicerecorder.ui.main.RecordingsFragment;

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
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class DriveServiceHelper {
    private static final String TYPE_FOLDER = "application/vnd.google-apps.folder";
    private static final String TYPE_AUDIO = "audio/mpeg";
    private static final String TYPE_JSON = "application/json";
    private static final String CONFIG_FILE = "config.json";
    private static final String FOLDER_ID = "folder_id";
    private static final String DEFAULT_CHANNEL_ID = "ez_voice_recorder_default";
    private static final String IMPORTANCE_CHANNEL_ID = "ez_voice_recorder_importance";
    private static int NOTIFICATION_ID;
    private Context mContext;
    private ExecutorService mExecutor;
    private Drive mDriveService;
    private ArrayList<Recording> mLocalList;
    private RecordingsAdapter mAdapter;
    private RecordingsDbHelper mDb;
    private String appDir;
    public DriveServiceHelper(Context c, Drive drive, ArrayList<Recording> list,RecordingsAdapter adapter){
        mContext = c;
        mDriveService = drive;
        mLocalList = list;
        this.mAdapter = adapter;
        mDb = new RecordingsDbHelper(mContext);
        NOTIFICATION_ID = 100;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = mContext.getSystemService(NotificationManager.class);
            NotificationChannel serviceChannel = new NotificationChannel(
                    DEFAULT_CHANNEL_ID,
                    mContext.getResources().getString(R.string.alert_channel),
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationChannel pushNotificationChannel = new NotificationChannel(
                    IMPORTANCE_CHANNEL_ID,
                    mContext.getResources().getString(R.string.importance_alert_channel),
                    NotificationManager.IMPORTANCE_HIGH
            );
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(pushNotificationChannel);
            }
        }
        appDir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q?
                Objects.requireNonNull(mContext.getExternalFilesDir(null)).getAbsolutePath() +
                        File.separator +
                        MainActivity.APP_FOLDER :
                MainActivity.APP_DIR;
    }

    private Runnable backUpRunnable = new Runnable(){
        @Override
        public void run() {
            Notification pushNotification =
                    new NotificationCompat.Builder(mContext, IMPORTANCE_CHANNEL_ID)
                            .setContentTitle(mContext.getResources().getString(R.string.app_name))
                            .setContentText(mContext.getText(R.string.backup_in_progress))
                            .setSmallIcon(R.drawable.ic_mic_foreground)
                            .setColor(mContext.getResources().getColor(R.color.colorPrimary))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setDefaults(Notification.DEFAULT_ALL)
                            .build();
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
            notificationManager.notify(NOTIFICATION_ID+1,pushNotification);
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
                Intent errorIntent = e.getIntent();
                errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(errorIntent);
                Handler handler = new Handler(mContext.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext.getApplicationContext(),
                                mContext.getResources().getString(R.string.toast_permissions_check_failed),
                                Toast.LENGTH_LONG).show();
                    }
                });
                return;
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
                fileMetadata.setName(mContext.getResources().getString(R.string.app_name));
                fileMetadata.setMimeType(TYPE_FOLDER);
                try {
                    com.google.api.services.drive.model.File folder = mDriveService.files().create(fileMetadata)
                            .setFields("id")
                            .execute();

                    JSONObject json = new JSONObject();
                    json.put(FOLDER_ID, folderId = folder.getId());
                    FileWriter fileWriter = new FileWriter(mContext.getCacheDir() + File.separator + CONFIG_FILE);
                    fileWriter.write(json.toString());
                    fileWriter.close();

                    com.google.api.services.drive.model.File fileMetadataJson = new com.google.api.services.drive.model.File();
                    fileMetadataJson.setName(CONFIG_FILE);
                    fileMetadataJson.setParents(Collections.singletonList("appDataFolder"));
                    File jsonFile = new File(mContext.getCacheDir() + File.separator + CONFIG_FILE);
                    FileContent jsonContent = new FileContent(TYPE_JSON, jsonFile);
                    mDriveService.files().create(fileMetadataJson, jsonContent)
                            .setFields("id")
                            .execute();
                } catch (UserRecoverableAuthIOException e) {
                    Intent errorIntent = e.getIntent();
                    errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(errorIntent);
                    Handler handler = new Handler(mContext.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext.getApplicationContext(),
                                    mContext.getResources().getString(R.string.toast_permissions_check_failed),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
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
            RecordingsDbHelper db = new RecordingsDbHelper(mContext);
            ArrayList<Recording> localList = db.getAll();
            // mLocalList: Files in local
            // driveList: Files in Google Drive
            boolean shouldCancelNotification = true;
            for (Recording recording : localList) {
                boolean exist = false;
                for (com.google.api.services.drive.model.File driveFile : driveList) {
                    if (driveFile.getName().equals(recording.getName())) {
                        exist = true;
                        break;
                    }
                }
                if (!exist) {
                    shouldCancelNotification = false;
                    UploadThread uploadThread = new UploadThread(recording.getName(), folderId, recording.getHashValue());
                    mExecutor.execute(uploadThread);
                }
            }
            mExecutor.shutdown();
            if (shouldCancelNotification)
                notificationManager.cancel(NOTIFICATION_ID+1);
        }
    };


    public void backUp() {
        mExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        mExecutor.execute(backUpRunnable);
    }


    public void sync(){
        mExecutor = Executors.newSingleThreadExecutor();
        mExecutor.execute(syncRunnable);
        mExecutor.shutdown();
    }

    public void downloadFile(String fileId, String fileName){
        DownLoadThread thread = new DownLoadThread(fileId,fileName);
        mExecutor = Executors.newSingleThreadExecutor();
        mExecutor.execute(thread);
        mExecutor.shutdown();
    }


    private class UploadThread implements Runnable{
        private String fileName, folderId, hashValue;

        UploadThread(String fileName, String folderId, String hashValue){
            this.fileName = fileName;
            this.folderId = folderId;
            this.hashValue = hashValue;
        }

        @Override
        public void run() {
            File file = new File(appDir,fileName);
            if (file.exists()){
                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(folderId));
                FileContent mediaContent = new FileContent(TYPE_AUDIO, file);
                try {
                    mDriveService.files().create(fileMetadata, mediaContent)
                            .setFields("id, parents")
                            .execute();
                    Intent notificationIntent = new Intent(mContext, MainActivity.class);
                    notificationIntent.setAction(Intent.ACTION_MAIN);
                    notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PendingIntent pendingIntent =
                            PendingIntent.getActivity(mContext, 0, notificationIntent, 0);
                    Notification notification =
                            new NotificationCompat.Builder(mContext, DEFAULT_CHANNEL_ID)
                                    .setContentTitle(fileName)
                                    .setContentText(mContext.getText(R.string.notification_text_uploaded))
                                    .setSmallIcon(R.drawable.ic_mic_foreground)
                                    .setContentIntent(pendingIntent)
                                    .setAutoCancel(true)
                                    .setColor(mContext.getResources().getColor(R.color.colorPrimary))
                                    .build();
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
                    notificationManager.notify(++NOTIFICATION_ID,notification);
                    Intent broadcast = new Intent(RecordingsFragment.BROADCAST_FILE_UPLOADED);
                    broadcast.putExtra(RecordingsFragment.KEY_HASH_VALUE,hashValue);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class DownLoadThread implements Runnable {
        private String fileId, fileName;

        DownLoadThread(String fileId, String fileName) {
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
                File file = new File(appDir, fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(byteArray);
                fileOutputStream.close();

                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                metadataRetriever.setDataSource(appDir + File.separator + fileName);
                String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String dateNoFormat = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
                MessageDigest digest;
                digest = MessageDigest.getInstance(RecordingsAdapter.HASH_ALGORITHM);
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
                Intent notificationIntent = new Intent(mContext, MainActivity.class);
                notificationIntent.setAction(Intent.ACTION_MAIN);
                notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pendingIntent =
                        PendingIntent.getActivity(mContext, 0, notificationIntent, 0);
                Notification notification =
                        new NotificationCompat.Builder(mContext, DEFAULT_CHANNEL_ID)
                                .setContentTitle(fileName)
                                .setContentText(mContext.getText(R.string.notification_text_downloaded))
                                .setSmallIcon(R.drawable.ic_mic_foreground)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true)
                                .setColor(mContext.getResources().getColor(R.color.colorPrimary))
                                .build();
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
                notificationManager.notify(++NOTIFICATION_ID,notification);
                Intent broadcast = new Intent(RecordingsFragment.BROADCAST_FILE_DOWNLOADED);
                broadcast.putExtra(RecordingsFragment.KEY_HASH_VALUE,hashValue);
                broadcast.putExtra(RecordingsFragment.KEY_FILE_NAME,fileName);
                mDb.insert(fileName, file.length(), Integer.parseInt(duration), formattedDate, hashValue);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    private Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            Notification notification =
                    new NotificationCompat.Builder(mContext, IMPORTANCE_CHANNEL_ID)
                            .setContentTitle(mContext.getResources().getString(R.string.app_name))
                            .setContentText(mContext.getText(R.string.sync_in_progress))
                            .setSmallIcon(R.drawable.ic_mic_foreground)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setDefaults(Notification.DEFAULT_ALL)
                            .setColor(mContext.getResources().getColor(R.color.colorPrimary))
                            .build();
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
            notificationManager.notify(NOTIFICATION_ID,notification);
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
            List<com.google.api.services.drive.model.File> driveList = new ArrayList<>();
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

                for (com.google.api.services.drive.model.File driveFile : driveList) {
                    boolean exist = false;
                    for (int i = 0; i < mLocalList.size(); i++) {
                        final int pos = i;
                        if (mLocalList.get(i).getName().equals(driveFile.getName())) {
                            exist = true;
                            if (mLocalList.get(i).getLocation() == Recording.LOCATION_ON_PHONE) {
                                mLocalList.get(i).setLocation(Recording.LOCATION_PHONE_DRIVE);
                                Handler handler = new Handler(mContext.getMainLooper());
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mAdapter.notifyItemChanged(pos);
                                    }
                                });
                                break;
                            }
                        }
                    }
                    // File is on remote
                    if (!exist){
                        Recording r = new Recording(-1,driveFile.getName(),driveFile.getSize(),0,"",driveFile.getId());
                        r.setLocation(Recording.LOCATION_ON_DRIVE);
                        mLocalList.add(mLocalList.size(),r);
                        Handler handler = new Handler(mContext.getMainLooper());
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mAdapter.notifyItemInserted(mLocalList.size());
                            }
                        },0);
                    }
                }
            }
            notificationManager.cancel(NOTIFICATION_ID);
        }
    };
}
