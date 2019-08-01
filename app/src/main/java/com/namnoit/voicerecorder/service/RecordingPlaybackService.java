package com.namnoit.voicerecorder.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.namnoit.voicerecorder.MainActivity;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.ui.main.RecordingsFragment;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class RecordingPlaybackService extends Service {
    private MediaPlayer mediaPlayer;
    private int currentPosition = 0; // For resume
    private Handler handler = new Handler();
    private int duration = 0;
    private String fileName;
    private Intent broadcastUpdateTime = new Intent(RecordingsFragment.BROADCAST_UPDATE_SEEK_BAR);
    private Intent broadcastFinishPlaying = new Intent(RecordingsFragment.BROADCAST_FINISH_PLAYING);
    private Intent broadcastStartPlaying = new Intent(RecordingsFragment.BROADCAST_START_PLAYING);
    private Intent broadcastPaused = new Intent(RecordingsFragment.BROADCAST_PAUSED);
    public static final String ACTION_PLAY = "PLAY";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_STOP_SERVICE = "STOP";
    public static final String ACTION_SEEK = "SEEK";


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                duration = mediaPlayer.getDuration();
                broadcastStartPlaying.putExtra(RecordingsFragment.KEY_FILE_NAME,fileName);
                broadcastStartPlaying.putExtra(RecordingsFragment.KEY_DURATION,duration);
                mp.start();
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcastStartPlaying);
                handler.postDelayed(updateSeekBarTask,0);
                SharedPreferences pref = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt(MainActivity.KEY_STATUS,RecordingsFragment.STATUS_PLAYING);
                editor.putString(RecordingsFragment.KEY_FILE_NAME,fileName);
                editor.putInt(RecordingsFragment.KEY_DURATION,duration);
                editor.apply();
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
        String fn = intent.getStringExtra(RecordingsFragment.KEY_FILE_NAME);
        if (fn != null) fileName = fn;
        if (Objects.equals(intent.getAction(), ACTION_PLAY)) {
            handler.removeCallbacks(updateSeekBarTask);
            createNotification(fileName, RecordingsFragment.STATUS_PLAYING);
            mediaPlayer.reset();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                mediaPlayer.setDataSource(MainActivity.APP_DIR + File.separator + fileName);
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
            createNotification(fileName, RecordingsFragment.STATUS_PAUSED);

            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                handler.removeCallbacks(updateSeekBarTask);
                currentPosition = mediaPlayer.getCurrentPosition();
                mediaPlayer.pause();
                SharedPreferences pref = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt(MainActivity.KEY_STATUS,RecordingsFragment.STATUS_PAUSED);
                editor.putInt(RecordingsFragment.KEY_CURRENT_POSITION,currentPosition);
                editor.apply();
                broadcastPaused.putExtra(RecordingsFragment.KEY_CURRENT_POSITION,currentPosition);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcastPaused);
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_RESUME)) {
            createNotification(fileName, RecordingsFragment.STATUS_PLAYING);
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                handler.post(updateSeekBarTask);
                mediaPlayer.seekTo(currentPosition);
                mediaPlayer.start();
                SharedPreferences pref = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt(MainActivity.KEY_STATUS,RecordingsFragment.STATUS_PLAYING);
                editor.apply();
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_SEEK)) {
            int seekTo = intent.getIntExtra(RecordingsFragment.KEY_SEEK_TO_POSITION,0);
            if (mediaPlayer != null) {
                currentPosition = Math.round((float)seekTo/100*duration);
                mediaPlayer.seekTo(currentPosition);
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_STOP_SERVICE)) stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBarTask);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcastFinishPlaying);
        SharedPreferences pref = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(MainActivity.KEY_STATUS,RecordingsFragment.STATUS_STOPPED);
        editor.apply();
        mediaPlayer.release();
        mediaPlayer = null;
    }


    private void createNotification(String fileName, int status) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel serviceChannel = new NotificationChannel(
                    RecorderService.SERVICE_CHANNEL_ID,
                    getResources().getString(R.string.service_channel),
                    NotificationManager.IMPORTANCE_LOW
            );
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);
        // Stop button
        Intent stopIntent = new Intent(this, RecordingPlaybackService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, RecorderService.SERVICE_CHANNEL_ID)
                .setContentTitle(fileName)
                .setSmallIcon(R.drawable.ic_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true);
        if (status == RecordingsFragment.STATUS_PLAYING) {
            // Pause button
            Intent pauseIntent = new Intent(this, RecordingPlaybackService.class);
            pauseIntent.setAction(ACTION_PAUSE);
            PendingIntent pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            builder.setContentText(getResources().getString(R.string.notification_text_playing))
                    .addAction(R.drawable.ic_pause_white, getResources().getString(R.string.pause), pausePendingIntent);
        } else {
            // Resume button
            Intent resumeIntent = new Intent(this, RecordingPlaybackService.class);
            resumeIntent.setAction(ACTION_RESUME);
            PendingIntent resumePendingIntent = PendingIntent.getService(this, 0, resumeIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            builder.setContentText(getResources().getString(R.string.notification_text_paused))
                    .addAction(R.drawable.ic_play, getResources().getString(R.string.resume), resumePendingIntent);
        }

        Notification notification = builder
                .addAction(R.drawable.ic_close, getResources().getString(R.string.stop), stopPendingIntent)
                .build();
        startForeground(2, notification);
    }

    private Runnable updateSeekBarTask = new Runnable() {
        public void run() {
            broadcastUpdateTime.putExtra(RecordingsFragment.KEY_CURRENT_POSITION, mediaPlayer.getCurrentPosition());
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcastUpdateTime);
            handler.postDelayed(this, 1000); // 1 seconds
        }
    };

}
