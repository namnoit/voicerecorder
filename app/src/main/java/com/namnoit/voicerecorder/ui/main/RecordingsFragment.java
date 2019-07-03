package com.namnoit.voicerecorder.ui.main;


import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.namnoit.voicerecorder.MainActivity;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.RecordingsAdapter;
import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 */
public class RecordingsFragment extends Fragment {
    private RecyclerView recyclerView;
    private RecordingsAdapter recordingsAdapter;
    private ArrayList<Recording> list;
    private RecordingsDbHelper db;
    private SharedPreferences pref;
    private ImageButton closeRecordingButton, playRecordingButton;
    private SeekBar seekBar;
    private TextView textTitle, textCurrentPosition, textDuration;
    private View playback;
    private ViewTreeObserver vto; // Set Padding for recyclerView
    private enum PlaybackStatus {playing, paused, stopped}
    private PlaybackStatus status = PlaybackStatus.paused;
    public static final String BROADCAST_UPDATE_SEEKBAR = "UPDATE_SEEKBAR";
    public static final String BROADCAST_FINISH_PLAYING = "PLAY_FINISH";
    public static final String BROADCAST_START_PLAYING = "START_PLAYING";
    public static final String BROADCAST_PAUSED = "PAUSED";
    public static final String currentPosition = "current_position";
    public static final String KEY_DURATION = "duration";
    public static final String fileName = "file_name";
    private int durationMilis = 0;
    private int curMilis = 0;
    private String recordingName = "";
    // Update list when recordings has been successful but hasn't been saved yet
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(RecorderService.BROADCAST_RECORDING_INSERTED)) {
                Recording r = db.getLast();
                if (r != null) {
                    list.add(0, r);
                    recordingsAdapter.notifyItemInserted(0);
                    recyclerView.scrollToPosition(0);
                }
            }
            if (intent.getAction().equals(BROADCAST_START_PLAYING)) {
                status = PlaybackStatus.playing;
                seekBar.setProgress(0);
                playback.setVisibility(View.VISIBLE);
                playback.setEnabled(true);
                recordingName = intent.getStringExtra(fileName);
                textTitle.setText(recordingName);
                durationMilis = intent.getIntExtra(KEY_DURATION,0);
                textDuration.setText(seconds2String(Math.round((float)durationMilis/1000)));
                playRecordingButton.setImageResource(R.drawable.ic_pause_white);
            }
            if (intent.getAction().equals(BROADCAST_UPDATE_SEEKBAR)) {
//                int dur = intent.getIntExtra(KEY_DURATION,0);
                durationMilis = intent.getIntExtra(KEY_DURATION,0);
                curMilis = intent.getIntExtra(currentPosition,0);
                seekBar.setProgress(curMilis*100/durationMilis);
                String title = intent.getStringExtra(fileName);
                if (title != null && !title.equals(recordingName)) {
                    recordingName = title;
                    textTitle.setText(recordingName);
                }
                textCurrentPosition.setText(seconds2String(Math.round((float)curMilis/1000)));
                Log.d("dur",Integer.toString(durationMilis));
                textDuration.setText(seconds2String(Math.round((float)durationMilis/1000)));
                status = PlaybackStatus.playing;
            }
            if (intent.getAction().equals(BROADCAST_FINISH_PLAYING)) {
                seekBar.setProgress(100);
                status = PlaybackStatus.stopped;
                textCurrentPosition.setText("00:00:00");
                textDuration.setText("00:00:00");
                playback.setVisibility(View.INVISIBLE);
                playback.setEnabled(false);
                vto.dispatchOnGlobalLayout();
            }
            if (intent.getAction().equals(BROADCAST_PAUSED)) {
                status = PlaybackStatus.paused;
                playRecordingButton.setImageResource(R.drawable.ic_play);
            }
        }
    };

    public RecordingsFragment() {
        // Required empty public constructor
    }


    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
        // Save pref
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(fileName,recordingName);
        editor.putInt(KEY_DURATION,durationMilis);
        editor.putInt(currentPosition,curMilis);
        editor.apply();
        super.onPause();

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(fileName,recordingName);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_recordings, container, false);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_RECORDING_INSERTED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_UPDATE_SEEKBAR));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_FINISH_PLAYING));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_START_PLAYING));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_PAUSED));
        pref = getContext().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        if (savedInstanceState!= null) {
            recordingName = savedInstanceState.getString(fileName);
            textTitle.setText(recordingName);
        }
        db = new RecordingsDbHelper(getContext());
        list = db.getAll();
        seekBar = view.findViewById(R.id.seekBar);
        textTitle = view.findViewById(R.id.recordingTitle);
        textCurrentPosition = view.findViewById(R.id.recordingCurrent);
        textDuration = view.findViewById(R.id.recordingDuration);
        closeRecordingButton = view.findViewById(R.id.closeRecordingButton);
        playRecordingButton = view.findViewById(R.id.playRecordingButton);
        playRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (status == PlaybackStatus.playing) {
                    Intent playbackIntent = new Intent(getContext(), RecordingPlaybackService.class);
                    playbackIntent.setAction(RecordingPlaybackService.ACTION_PAUSE);
                    getContext().startService(playbackIntent);
                    playRecordingButton.setImageResource(R.drawable.ic_play);
                    status = PlaybackStatus.paused;
                } else if (status == PlaybackStatus.paused) {
                    Intent playbackIntent = new Intent(getContext(), RecordingPlaybackService.class);
                    playbackIntent.setAction(RecordingPlaybackService.ACTION_RESUME);
                    getContext().startService(playbackIntent);
                    playRecordingButton.setImageResource(R.drawable.ic_pause_white);
                    status = PlaybackStatus.playing;
                }
            }
        });
        closeRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent playerIntent = new Intent(getContext(), RecordingPlaybackService.class);
                getContext().stopService(playerIntent);
                status = PlaybackStatus.stopped;
            }
        });
        recyclerView = view.findViewById(R.id.list_recordings);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recordingsAdapter = new RecordingsAdapter(list, getContext());
        recyclerView.setAdapter(recordingsAdapter);

        playback = view.findViewById(R.id.playback);

        vto = playback.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (playback.getVisibility() == View.INVISIBLE)
                    recyclerView.setPadding(0, 0, 0, 0);
                else recyclerView.setPadding(0, 0, 0, playback.getHeight());
            }
        });
        int stt = pref.getInt(MainActivity.KEY_STATUS,1);
        if (!isServiceRunning(RecordingPlaybackService.class)){
            status = PlaybackStatus.stopped;
            playback.setEnabled(false);
            playback.setVisibility(View.INVISIBLE);
            vto.dispatchOnGlobalLayout();
        }else if (stt == 1){
            status = PlaybackStatus.paused;
            curMilis = pref.getInt(currentPosition,0);
            recordingName = pref.getString(fileName,"");
            durationMilis = pref.getInt(KEY_DURATION,0);
            textTitle.setText(recordingName);
            textDuration.setText(seconds2String(Math.round((float)durationMilis/1000)));
            textCurrentPosition.setText(seconds2String(Math.round((float)curMilis/1000)));
            seekBar.setProgress(curMilis*100/durationMilis);
            playRecordingButton.setImageResource(R.drawable.ic_play);
        } else{
            status = PlaybackStatus.playing;
            playRecordingButton.setImageResource(R.drawable.ic_pause_white);
        }
        return view;
    }

    private String seconds2String(int seconds){
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        return String.format("%02d:%02d:%02d",h,m,s);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
