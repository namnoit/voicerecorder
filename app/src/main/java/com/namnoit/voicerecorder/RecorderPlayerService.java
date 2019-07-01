package com.namnoit.voicerecorder;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Objects;

public class RecorderPlayerService extends Service {
    private MediaPlayer mediaPlayer;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("play","create");
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(getCacheDir().getAbsolutePath() + "/recording.mp3");
            mediaPlayer.reset();
        } catch (IOException e) {
            Log.d("play","err");
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),getResources().getText(R.string.error_open_file),Toast.LENGTH_SHORT).show();
            onDestroy();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (Objects.equals(intent.getAction(), "PLAY")) {

             if (mediaPlayer!=null) mediaPlayer.start();
        }
        if (Objects.equals(intent.getAction(), "PAUSE")) {
            if (mediaPlayer!=null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaPlayer.stop();
        mediaPlayer.release();
    }
}
