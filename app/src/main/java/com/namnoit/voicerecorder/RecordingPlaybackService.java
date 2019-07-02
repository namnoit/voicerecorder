package com.namnoit.voicerecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

public class RecordingPlaybackService extends Service {
    private MediaPlayer mediaPlayer;
    private int currentPosition = 0; // For resume
    public static final String cacheFile = "/recording.mp3";
    private String cacheFilePath;
    private String fileName;
    private static final String CHANNEL_ID = "Voice_Recorder_Playback";
    public static final String KEY_FILE_NAME = "filename";
    public static final String ACTION_PLAY = "PLAY";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_STOP_SERVICE = "STOP";


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        cacheFilePath = getCacheDir().getAbsolutePath() + cacheFile;

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stopSelf();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String fn = intent.getStringExtra(KEY_FILE_NAME);
        if (fn != null && !fn.equals("")) fileName = fn;
        if (Objects.equals(intent.getAction(), ACTION_PLAY)) {
            createNotification(fileName,getResources().getString(R.string.notification_text_playing));
            mediaPlayer.reset();
            try {
                mediaPlayer.setDataSource(cacheFilePath);
                mediaPlayer.prepareAsync();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(),
                        getResources().getText(R.string.error_open_file),
                        Toast.LENGTH_SHORT).show();
                stopSelf();
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_PAUSE)) {
            createNotification(fileName,getResources().getString(R.string.notification_text_paused));
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                currentPosition = mediaPlayer.getCurrentPosition();
                mediaPlayer.pause();
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_RESUME)) {
            Log.d("media","resume");
            createNotification(fileName,getResources().getString(R.string.notification_text_playing));
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.seekTo(currentPosition);
                mediaPlayer.start();
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_STOP_SERVICE)) stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Delete temporary file
        try {
            File delFile = new File(cacheFilePath);
            delFile.delete();
            if (delFile.exists()) {
                delFile.getCanonicalFile().delete();
                if (delFile.exists()) {
                    getApplicationContext().deleteFile(delFile.getName());
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
//        mediaPlayer.stop();
        mediaPlayer.release();
        mediaPlayer = null;
    }

    private void createNotification(String fileName, String status){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Recorder Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(serviceChannel);
        }



        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);
        // To stop
        Intent stopIntent = new Intent(this, RecordingPlaybackService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,PendingIntent.FLAG_CANCEL_CURRENT);
        // To pause
        Intent pauseIntent = new Intent(this, RecordingPlaybackService.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent,PendingIntent.FLAG_CANCEL_CURRENT);

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(fileName)
                        .setContentText(status)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_pause_white,"Pause",pausePendingIntent)
                        .addAction(R.drawable.ic_close,"Stop",stopPendingIntent)

                        .build();
        startForeground(2, notification);
    }
}
