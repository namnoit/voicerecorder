package com.namnoit.voicerecorder.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import com.namnoit.voicerecorder.SharedPreferenceManager;
import com.namnoit.voicerecorder.ui.main.RecordingsFragment;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class RecordingPlaybackService extends Service {
    private MediaPlayer mMediaPlayer;
    private int mCurrentPosition = 0; // For resume
    private Handler mHandler = new Handler();
    private int duration = 0;
    private String mFileName;
    private SharedPreferenceManager mPref;
    private Intent mUpdateTimeBroadcast = new Intent(RecordingsFragment.BROADCAST_UPDATE_SEEK_BAR);
    private Intent mFinishPlayingBroadcast = new Intent(RecordingsFragment.BROADCAST_FINISH_PLAYING);
    private Intent mStartPlayingBroadcast = new Intent(RecordingsFragment.BROADCAST_START_PLAYING);
    private Intent mPausedBroadcast = new Intent(RecordingsFragment.BROADCAST_PAUSED);
    private AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener mAFChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause();
                    else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) resume();
                }
            };

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
        mPref = SharedPreferenceManager.getInstance();
        mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                duration = mMediaPlayer.getDuration();
                mStartPlayingBroadcast.putExtra(RecordingsFragment.KEY_FILE_NAME, mFileName);
                mStartPlayingBroadcast.putExtra(RecordingsFragment.KEY_DURATION,duration);
                mp.start();
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(mStartPlayingBroadcast);
                mHandler.postDelayed(updateSeekBarTask,0);

                mPref.put(SharedPreferenceManager.Key.STATUS_KEY,RecordingsFragment.STATUS_PLAYING);
                mPref.put(SharedPreferenceManager.Key.FILE_NAME_KEY, mFileName);
                mPref.put(SharedPreferenceManager.Key.DURATION_KEY,duration);
            }
        });
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stopSelf();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String fn = intent.getStringExtra(RecordingsFragment.KEY_FILE_NAME);
        if (fn != null) mFileName = fn;
        if (Objects.equals(intent.getAction(), ACTION_PLAY)) {
            mHandler.removeCallbacks(updateSeekBarTask);
            int result = mAudioManager.requestAudioFocus(mAFChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Start playback
                createNotification(mFileName, RecordingsFragment.STATUS_PLAYING);
                mMediaPlayer.reset();
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                try {
                    mMediaPlayer.setDataSource((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                            Objects.requireNonNull(getApplicationContext().getExternalFilesDir(null)).getAbsolutePath() +
                                    File.separator +
                                    MainActivity.APP_FOLDER :
                            MainActivity.APP_DIR) +
                            File.separator +
                            mFileName);
                    mMediaPlayer.prepareAsync();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(),
                            getResources().getText(R.string.error_open_file),
                            Toast.LENGTH_SHORT).show();
                    stopSelf();
                }
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_PAUSE)) {
            mAudioManager.abandonAudioFocus(mAFChangeListener);
            pause();
        }
        if (Objects.equals(intent.getAction(), ACTION_RESUME)) {
            int result = mAudioManager.requestAudioFocus(mAFChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                resume();
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_SEEK)) {
            int seekTo = intent.getIntExtra(RecordingsFragment.KEY_SEEK_TO_POSITION,0);
            if (mMediaPlayer != null) {
                mCurrentPosition = Math.round((float)seekTo);
                mMediaPlayer.seekTo(mCurrentPosition);
            }
        }
        if (Objects.equals(intent.getAction(), ACTION_STOP_SERVICE)) stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAudioManager.abandonAudioFocus(mAFChangeListener);
        mHandler.removeCallbacks(updateSeekBarTask);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(mFinishPlayingBroadcast);
        mPref.put(SharedPreferenceManager.Key.STATUS_KEY,RecordingsFragment.STATUS_STOPPED);
        mMediaPlayer.release();
        mMediaPlayer = null;
    }

    private void pause(){
        createNotification(mFileName, RecordingsFragment.STATUS_PAUSED);
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mHandler.removeCallbacks(updateSeekBarTask);
            mCurrentPosition = mMediaPlayer.getCurrentPosition();
            mMediaPlayer.pause();
            mPref.put(SharedPreferenceManager.Key.STATUS_KEY,RecordingsFragment.STATUS_PAUSED);
            mPref.put(SharedPreferenceManager.Key.CURRENT_POSITION_KEY, mCurrentPosition);
            mPausedBroadcast.putExtra(SharedPreferenceManager.Key.CURRENT_POSITION_KEY, mCurrentPosition);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(mPausedBroadcast);
        }
    }

    private void resume() {
        createNotification(mFileName, RecordingsFragment.STATUS_PLAYING);
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            mHandler.post(updateSeekBarTask);
            mMediaPlayer.seekTo(mCurrentPosition);
            mMediaPlayer.start();
            mPref.put(SharedPreferenceManager.Key.STATUS_KEY, RecordingsFragment.STATUS_PLAYING);
        }
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
        PendingIntent stopPendingIntent =
                PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat
                .Builder(this, RecorderService.SERVICE_CHANNEL_ID)
                .setContentTitle(fileName)
                .setSmallIcon(R.drawable.ic_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true);
        if (status == RecordingsFragment.STATUS_PLAYING) {
            // Pause button
            Intent pauseIntent = new Intent(this, RecordingPlaybackService.class);
            pauseIntent.setAction(ACTION_PAUSE);
            PendingIntent pausePendingIntent =
                    PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            builder.setContentText(getResources().getString(R.string.notification_text_playing))
                    .addAction(R.drawable.ic_pause_white, getResources().getString(R.string.pause), pausePendingIntent);
        } else {
            // Resume button
            Intent resumeIntent = new Intent(this, RecordingPlaybackService.class);
            resumeIntent.setAction(ACTION_RESUME);
            PendingIntent resumePendingIntent =
                    PendingIntent.getService(this, 0, resumeIntent, PendingIntent.FLAG_CANCEL_CURRENT);
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
            mUpdateTimeBroadcast.putExtra(RecordingsFragment.KEY_CURRENT_POSITION, mMediaPlayer.getCurrentPosition());
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(mUpdateTimeBroadcast);
            mHandler.postDelayed(this, 995); // 1 seconds
        }
    };

}
