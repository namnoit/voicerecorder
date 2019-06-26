package com.namnoit.voicerecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RecorderService extends Service {
    private MediaRecorder recorder;
    private String outputFile = null;
    private static final String CHANNEL_ID = "Voice_Recorder";

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
        SimpleDateFormat s = new SimpleDateFormat("ddMMyyyy_hhmmss");
        String date = s.format(new Date());
        outputFile = Environment.getExternalStorageDirectory().
                getAbsolutePath() +  "/" + date;

        // Configure Media Recorder
        recorder = new MediaRecorder();
        recorder.setAudioChannels(2);
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        SharedPreferences pref = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        int audioFormat = pref.getInt(MainActivity.KEY_QUALITY,MainActivity.QUALITY_GOOD);
        switch (audioFormat){
            case MainActivity.QUALITY_GOOD:
                recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
                recorder.setAudioSamplingRate(48000);
                recorder.setAudioEncodingBitRate(320000);
                recorder.setOutputFile(outputFile + ".m4a");
                break;
            case MainActivity.QUALITY_SMALL:
                recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.setAudioSamplingRate(16000);
                recorder.setAudioEncodingBitRate(128000);
                recorder.setOutputFile(outputFile + ".3gp");
                break;
            default:
                recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                recorder.setAudioSamplingRate(16000);
                recorder.setAudioEncodingBitRate(128000);
                recorder.setOutputFile(outputFile + ".aac");
                break;
        }

        try {
            startForeground(1, notification);
            recorder.prepare();
            recorder.start();

        } catch (IllegalStateException e) {
            // start:it is called before prepare()
            // prepare: it is called after start() or before setOutputFormat()
            e.printStackTrace();
        } catch (IOException e) {
            // prepare() fails
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
        super.onDestroy();
    }
}
