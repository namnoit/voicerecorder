package com.namnoit.voicerecorder.ui.main;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;

import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.RecorderPlayerService;
import com.namnoit.voicerecorder.RecorderService;
import com.namnoit.voicerecorder.RecordingsAdapter;
import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private MediaPlayer mediaPlayer = new MediaPlayer();
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



    private void playMp3(byte[] mp3SoundByteArray) {
        try {
            // create temp file that will hold byte array
            File tempMp3 = File.createTempFile("kurchina", "mp3", getContext().getCacheDir());
            tempMp3.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempMp3);
            fos.write(mp3SoundByteArray);
            fos.close();

            // resetting mediaplayer instance to evade problems
            mediaPlayer.reset();

            // In case you run into issues with threading consider new instance like:
            // MediaPlayer mediaPlayer = new MediaPlayer();

            // Tried passing path directly, but kept getting
            // "Prepare failed.: status=0x1"
            // so using file descriptor instead
            FileInputStream fis = new FileInputStream(tempMp3);
            mediaPlayer.setDataSource(fis.getFD());

            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException ex) {
            String s = ex.toString();
            ex.printStackTrace();
        }
    }

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
                Intent playerIntent = new Intent(getContext(), RecorderPlayerService.class);
                playerIntent.setAction("PAUSE");
                getContext().startService(playerIntent);
            }
        });
        closeRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent playerIntent = new Intent(getContext(), RecorderPlayerService.class);
                getContext().stopService(playerIntent);
            }
        });
        recyclerView = view.findViewById(R.id.list_recordings);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), RecyclerView.VERTICAL,false);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recordingsAdapter = new RecordingsAdapter(list,getContext(),mediaPlayer);
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
