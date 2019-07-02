package com.namnoit.voicerecorder.ui.main;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.SeekBar;

import com.namnoit.voicerecorder.MainActivity;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.RecordingPlaybackService;
import com.namnoit.voicerecorder.RecorderService;
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
    private ImageButton closeRecordingButton, playRecordingButton;
    private SeekBar seekBar;
    private View playback;
    private enum PlaybackStatus {playing, paused, stopped}
    private PlaybackStatus status = PlaybackStatus.playing;
    // Update list when recordings has been successful but hasn't been saved yet
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Recording r = db.getLast();
            if (r != null) {
                list.add(0, r);
                recordingsAdapter.notifyItemInserted(0);
                recyclerView.scrollToPosition(0);
            }
        }
    };
    // Set Padding for recyclerView
    private ViewTreeObserver vto;

    public RecordingsFragment() {
        // Required empty public constructor
    }


    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
        super.onPause();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_recordings, container, false);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_RECORDING_INSERTED));
        db = new RecordingsDbHelper(getContext());
        list = db.getAll();
        seekBar = view.findViewById(R.id.seekBar);

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
                }
                else if (status == PlaybackStatus.paused){
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
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), RecyclerView.VERTICAL,false);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recordingsAdapter = new RecordingsAdapter(list,getContext());
        recyclerView.setAdapter(recordingsAdapter);

        playback = view.findViewById(R.id.playback);

        vto = playback.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (playback.getVisibility()==View.INVISIBLE)
                    recyclerView.setPadding(0,0,0,0);
                else recyclerView.setPadding(0,0,0,playback.getHeight());
            }

        });
        return view;
    }

}
