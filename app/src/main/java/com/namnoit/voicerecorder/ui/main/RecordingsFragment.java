package com.namnoit.voicerecorder.ui.main;


import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.namnoit.voicerecorder.DriveChecker;
import com.namnoit.voicerecorder.MainActivity;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.RecordingsAdapter;
import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;


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
    private int status = STATUS_PAUSED;
    public static final int STATUS_PLAYING = 0;
    public static final int STATUS_PAUSED = 1;
    public static final int STATUS_STOPPED = 2;
    public static final String BROADCAST_UPDATE_SEEKBAR = "UPDATE_SEEKBAR";
    public static final String BROADCAST_FINISH_PLAYING = "PLAY_FINISH";
    public static final String BROADCAST_FILE_DOWNLOADED = "FILE_DOWNLOADED";
    public static final String BROADCAST_FILE_UPLOADED = "FILE_UPLOADED";
    public static final String BROADCAST_START_PLAYING = "START_PLAYING";
    public static final String BROADCAST_PAUSED = "PAUSED";
    public static final String KEY_CURRENT_POSITION = "current_position";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_POSITION = "position";
    public static final String KEY_HASH_VALUE = "hash_value";
    public static final String KEY_FILE_NAME = "file_name";
    public static final String KEY_SEEK_TO_POSITION = "seek";
    // To show current item selected in list
    public static final String KEY_CURRENT_POSITION_ADAPTER = "selected_position";
    private int durationMillis = 0;
    private int curMillis = 0;
    private String recordingName = "";
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null &&
                    intent.getAction().equals(RecorderService.BROADCAST_FINISH_RECORDING)) {
                Recording r = db.getLast();
                if (r != null) {
                    list.add(0, r);
                    recordingsAdapter.notifyItemInserted(0);
                    recordingsAdapter.updateSelectedPosition();
                    recordingsAdapter.notifyItemRangeChanged(1,recordingsAdapter.getItemCount());
                    recyclerView.scrollToPosition(0);
                }
            }
            if (intent.getAction() != null &&
                    intent.getAction().equals(BROADCAST_FILE_DOWNLOADED)){
                Recording r = db.getRecordingWithHash(intent.getStringExtra(KEY_HASH_VALUE));
                if (r != null) {
                    r.setOnGoogleDrive(true);
                    list.add(0, r);
                    recordingsAdapter.notifyItemInserted(0);
                    recordingsAdapter.updateSelectedPosition();
                    recordingsAdapter.notifyItemRangeChanged(1,recordingsAdapter.getItemCount());
                    recyclerView.scrollToPosition(0);
                }
            }
            if (intent.getAction() != null &&
                    intent.getAction().equals(BROADCAST_FILE_UPLOADED)){
                int pos = intent.getIntExtra(KEY_POSITION,-1);
                if (pos>=0 && pos<list.size()){
                    list.get(pos).setOnGoogleDrive(true);
                    recordingsAdapter.notifyItemChanged(pos);
                }
            }
            if (intent.getAction() != null &&
                    intent.getAction().equals(BROADCAST_START_PLAYING)) {
                status = STATUS_PLAYING;
                seekBar.setProgress(0);
                playback.setVisibility(View.VISIBLE);
                playback.setEnabled(true);
                recordingName = intent.getStringExtra(KEY_FILE_NAME);
                textTitle.setText(recordingName);
                durationMillis = intent.getIntExtra(KEY_DURATION,0);
                textDuration.setText(seconds2String(Math.round((float)durationMillis/1000)));
                playRecordingButton.setImageResource(R.drawable.ic_pause_white);
            }
            if (intent.getAction() != null &&
                    intent.getAction().equals(BROADCAST_UPDATE_SEEKBAR)) {
                if (status != STATUS_PLAYING){
                    status = STATUS_PLAYING;
                    playRecordingButton.setImageResource(R.drawable.ic_pause_white);
                }
                curMillis = intent.getIntExtra(KEY_CURRENT_POSITION,0);
                seekBar.setProgress(curMillis*100/durationMillis);
                textCurrentPosition.setText(seconds2String(Math.round((float)curMillis/1000)));
            }
            if (intent.getAction() != null &&
                    intent.getAction().equals(BROADCAST_FINISH_PLAYING)) {
                seekBar.setProgress(100);
                status = STATUS_STOPPED;
                textCurrentPosition.setText("00:00:00");
                textDuration.setText("00:00:00");
                playback.setVisibility(View.INVISIBLE);
                playback.setEnabled(false);
                vto.dispatchOnGlobalLayout();
            }
            if (intent.getAction() != null &&
                    intent.getAction().equals(BROADCAST_PAUSED)) {
                status = STATUS_PAUSED;
                playRecordingButton.setImageResource(R.drawable.ic_play);
            }
        }
    };

    public RecordingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_FINISH_RECORDING));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_UPDATE_SEEKBAR));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_FINISH_PLAYING));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_START_PLAYING));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_PAUSED));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_FILE_DOWNLOADED));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_FILE_UPLOADED));
        status = pref.getInt(MainActivity.KEY_STATUS,STATUS_STOPPED);
        if (!isServiceRunning(RecordingPlaybackService.class)){
            status = STATUS_STOPPED;
            playback.setEnabled(false);
            playback.setVisibility(View.INVISIBLE);
            vto.dispatchOnGlobalLayout();
        }else if (status == STATUS_PAUSED){
            curMillis = pref.getInt(KEY_CURRENT_POSITION,0);
            recordingName = pref.getString(KEY_FILE_NAME,"");
            durationMillis = pref.getInt(KEY_DURATION,0);
            textTitle.setText(recordingName);
            textDuration.setText(seconds2String(Math.round((float)durationMillis/1000)));
            textCurrentPosition.setText(seconds2String(Math.round((float)curMillis/1000)));
            seekBar.setProgress(curMillis*100/durationMillis);
            playRecordingButton.setImageResource(R.drawable.ic_play);
        } else{
            durationMillis = pref.getInt(KEY_DURATION,0);
            textTitle.setText(pref.getString(KEY_FILE_NAME,""));
            textDuration.setText(seconds2String(Math.round((float)pref.getInt(KEY_DURATION,0)/1000)));
            playRecordingButton.setImageResource(R.drawable.ic_pause_white);
        }

    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        super.onPause();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recordings, container, false);

        pref = requireContext().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        db = new RecordingsDbHelper(getContext());
        list = db.getAll();
        seekBar = view.findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser){
                    textCurrentPosition.setText(seconds2String(Math.round((float)progress/100000*durationMillis)));
                    Intent playbackIntent = new Intent(getContext(), RecordingPlaybackService.class);
                    playbackIntent.setAction(RecordingPlaybackService.ACTION_SEEK);
                    playbackIntent.putExtra(KEY_SEEK_TO_POSITION,progress);
                    requireContext().startService(playbackIntent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        textTitle = view.findViewById(R.id.recordingTitle);
        textCurrentPosition = view.findViewById(R.id.recordingCurrent);
        textDuration = view.findViewById(R.id.recordingDuration);
        closeRecordingButton = view.findViewById(R.id.closeRecordingButton);
        playRecordingButton = view.findViewById(R.id.playRecordingButton);
        playRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (status == STATUS_PLAYING) {
                    Intent playbackIntent = new Intent(getContext(), RecordingPlaybackService.class);
                    playbackIntent.setAction(RecordingPlaybackService.ACTION_PAUSE);
                    requireContext().startService(playbackIntent);
                    playRecordingButton.setImageResource(R.drawable.ic_play);
                    status = STATUS_PAUSED;
                } else if (status == STATUS_PAUSED) {
                    Intent playbackIntent = new Intent(getContext(), RecordingPlaybackService.class);
                    playbackIntent.setAction(RecordingPlaybackService.ACTION_RESUME);
                    requireContext().startService(playbackIntent);
                    playRecordingButton.setImageResource(R.drawable.ic_pause_white);
                    status = STATUS_PLAYING;
                }
            }
        });
        closeRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent playerIntent = new Intent(getContext(), RecordingPlaybackService.class);
                requireContext().stopService(playerIntent);
                status = STATUS_STOPPED;
            }
        });
        recyclerView = view.findViewById(R.id.list_recordings);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recordingsAdapter = new RecordingsAdapter(list, getContext());
        recyclerView.setAdapter(recordingsAdapter);
        playback = view.findViewById(R.id.playback);

        updateUI();

        vto = playback.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (playback.getVisibility() == View.INVISIBLE)
                    recyclerView.setPadding(0, 0, 0, 0);
                else recyclerView.setPadding(0, 0, 0, playback.getHeight());
            }
        });
        return view;
    }

    private void updateUI(){
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && cm.getActiveNetworkInfo() != null) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            if (account != null) {
                final GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        requireContext(), Arrays.asList(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE));
                credential.setBackOff(new ExponentialBackOff());
                credential.setSelectedAccount(account.getAccount());

                final com.google.api.services.drive.Drive googleDriveService =
                        new com.google.api.services.drive.Drive.Builder(
                                AndroidHttp.newCompatibleTransport(),
                                new GsonFactory(),
                                credential)
                                .setApplicationName(getResources().getString(R.string.app_name))
                                .build();
                DriveChecker checker = new DriveChecker(requireContext(), googleDriveService, list, recordingsAdapter);
                checker.check();
            }
        }
    }

    private String seconds2String(int seconds){
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        return String.format(Locale.getDefault(),"%02d:%02d:%02d",h,m,s);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
