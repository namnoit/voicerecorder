package com.namnoit.voicerecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.namnoit.voicerecorder.data.RecordingsDbHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RecorderService extends Service {
    private MediaRecorder recorder;
    private String tempFile = null;
    private SharedPreferences pref;
    private static final String CHANNEL_ID = "Voice_Recorder";
    public static final String BROADCAST_RECORDING_INSERTED = "RECORDING.INSERTED";
    private String date;
    private String dir;
    private Date dateNow;
    public RecorderService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create foreground Notification
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Recorder",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(serviceChannel);
        }
        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(getText(R.string.notification_title_recording))
                        .setContentText(getText(R.string.notification_title_recording))
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(pendingIntent)
                        .build();

        // Get file name
        dir = getApplicationContext().getFilesDir().getAbsolutePath();
        SimpleDateFormat nameFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        SimpleDateFormat normalFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        dateNow = new Date();
        date = normalFormat.format(dateNow);
        tempFile = "Recording_" + nameFormat.format(dateNow);
        // Configure Media Recorder
        recorder = new MediaRecorder();
        recorder.setAudioChannels(2);
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        pref = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        int audioFormat = pref.getInt(MainActivity.KEY_QUALITY,MainActivity.QUALITY_GOOD);

        switch (audioFormat){
            case MainActivity.QUALITY_GOOD:
                recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setAudioSamplingRate(48000);
                recorder.setAudioEncodingBitRate(320000);
                tempFile += ".aac";
                break;
            case MainActivity.QUALITY_SMALL:
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.setAudioSamplingRate(16000);
                recorder.setAudioEncodingBitRate(128000);
                tempFile += ".3gp";
                break;
            default:
                recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                recorder.setAudioSamplingRate(16000);
                recorder.setAudioEncodingBitRate(128000);
                tempFile += ".aac";
                break;
        }
        recorder.setOutputFile(dir + "/" + tempFile);
        try {
            startForeground(1, notification);
            recorder.prepare();
            recorder.start();
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean(MainActivity.KEY_SAVED,false);
            editor.putString(MainActivity.KEY_FILE_NAME,tempFile);
            editor.putString(MainActivity.KEY_DATE,date);
            editor.apply();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        recorder.stop();
        recorder.reset();
        recorder.release();
        recorder = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(dir + "/" + tempFile);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            byte[] buffer =new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
                baos.flush();
            }
            byte[] fileByteArray = baos.toByteArray();

            MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(dir + "/" + tempFile);
            String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

            RecordingsDbHelper dbHelper = new RecordingsDbHelper(getApplicationContext());
            dbHelper.insert(tempFile,fileByteArray,Integer.parseInt(duration),date);
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean(MainActivity.KEY_SAVED,true);
            editor.apply();
            // Delete temporary file
            File delFile = new File(dir + "/" + tempFile);
            delFile.delete();
            if(delFile.exists()){
                delFile.getCanonicalFile().delete();
                if(delFile.exists()){
                    getApplicationContext().deleteFile(delFile.getName());
                }
            }

            Intent broadcast = new Intent(BROADCAST_RECORDING_INSERTED);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcast);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        super.onDestroy();
    }
}
