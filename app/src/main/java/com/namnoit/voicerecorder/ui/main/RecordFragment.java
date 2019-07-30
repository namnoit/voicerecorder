package com.namnoit.voicerecorder.ui.main;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.namnoit.voicerecorder.MainActivity;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;

import java.util.Locale;

/**
 * A placeholder fragment containing a simple view.
 */
public class RecordFragment extends Fragment {
    private FloatingActionButton recordPauseButton, stopButton;
    private TextView textTime;
    public static final String KEY_RECORD_STATUS = "RECORD_STATUS";
    public static final int STATUS_RECORDING = 2;
    public static final int STATUS_PAUSED = 1;
    public static final int STATUS_STOPPED = 0;
    private int recordStatus;
    // Prevent double click
    private long mRecordLastClickTime = 0;
    private long mStopLastClickTime = 0;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals(RecorderService.BROADCAST_FINISH_RECORDING)) {
                recordStatus = STATUS_STOPPED;
                stopButton.hide();
                recordPauseButton.setImageResource(R.drawable.ic_record);
                recordPauseButton.setEnabled(true);
                textTime.setText("00:00:00");
                Toast.makeText(getContext(), getResources().getText(R.string.toast_recording_saved).toString(), Toast.LENGTH_SHORT).show();
            }
            else if (intent.getAction() != null && intent.getAction().equals(RecorderService.BROADCAST_UPDATE_TIME)) {
                if (recordStatus != STATUS_RECORDING){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        recordPauseButton.setImageResource(R.drawable.ic_pause);
                    } else
                        recordPauseButton.setEnabled(false);
                }
                recordStatus = STATUS_RECORDING;
                stopButton.show();
                int seconds = intent.getIntExtra("time", 0);
                String dur = String.format(Locale.getDefault(),
                        "%02d:%02d:%02d",
                        (seconds / (60 * 60)) % 24,
                        (seconds / 60) % 60,
                        seconds % 60);
                textTime.setText(dur);
            }
            else if (intent.getAction() != null && intent.getAction().equals(RecorderService.ACTION_PAUSE_RECORDING)) {
                recordStatus = STATUS_PAUSED;
                recordPauseButton.setImageResource(R.drawable.ic_record);
            }
            else if (intent.getAction() != null && intent.getAction().equals(RecorderService.ACTION_RESUME_RECORDING)){
                recordStatus = STATUS_RECORDING;
                recordPauseButton.setImageResource(R.drawable.ic_pause);
            }
        }
    };

    @Override
    public void onResume() {
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_UPDATE_TIME));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_FINISH_RECORDING));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.ACTION_PAUSE_RECORDING));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.ACTION_RESUME_RECORDING));

        if (isServiceRunning(RecorderService.class)) {
            stopButton.show();
            SharedPreferences pref = requireContext().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
            recordStatus = pref.getInt(KEY_RECORD_STATUS,STATUS_RECORDING);
            if (recordStatus == STATUS_RECORDING){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    recordPauseButton.setImageResource(R.drawable.ic_pause);

                } else{
                    recordPauseButton.setEnabled(false);
                }
            }
            // Paused
            else {
                recordPauseButton.setImageResource(R.drawable.ic_record);

            }

        } else {
            recordPauseButton.setImageResource(R.drawable.ic_record);
            recordPauseButton.setEnabled(true);
            textTime.setText("00:00:00");
            stopButton.hide();
            recordStatus = STATUS_STOPPED;
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        super.onPause();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_record, container, false);
        recordPauseButton = root.findViewById(R.id.button_record_pause);
        stopButton = root.findViewById(R.id.button_stop);
        textTime = root.findViewById(R.id.textTime);

        recordPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SystemClock.elapsedRealtime() - mRecordLastClickTime < 1000) {
                    return;
                }
                mRecordLastClickTime = SystemClock.elapsedRealtime();
                // Start recording
                if (recordStatus == STATUS_STOPPED) {
                    // Stop playback if playing recordings
                    if (isServiceRunning(RecordingPlaybackService.class)) {
                        Intent stopIntent = new Intent(getContext(), RecordingPlaybackService.class);
                        requireContext().stopService(stopIntent);
                    }
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    intent.setAction(RecorderService.ACTION_START_RECORDING);
                    requireContext().startService(intent);

                }
                // Pause recording
                else if (recordStatus == STATUS_RECORDING){
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    intent.setAction(RecorderService.ACTION_PAUSE_RECORDING);
                    requireContext().startService(intent);
                }
                else {
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    intent.setAction(RecorderService.ACTION_RESUME_RECORDING);
                    requireContext().startService(intent);
                }
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (SystemClock.elapsedRealtime() - mStopLastClickTime < 1000) {
                    return;
                }
                mStopLastClickTime = SystemClock.elapsedRealtime();
                Intent intent = new Intent(getContext(), RecorderService.class);
                intent.setAction(RecordingPlaybackService.ACTION_STOP_SERVICE);
                requireContext().startService(intent);
            }
        });
        return root;
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}