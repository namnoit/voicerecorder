package com.namnoit.voicerecorder.ui.main;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;

import java.util.Locale;

/**
 * A placeholder fragment containing a simple view.
 */
public class RecordFragment extends Fragment {
    private FloatingActionButton recordStopButton;
    private TextView textTime;
    private boolean recording = false;
    // Prevent double click
    private long mLastClickTime = 0;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals(RecorderService.BROADCAST_FINISH_RECORDING)) {
                recordStopButton.setImageResource(R.drawable.ic_circle);
                recording = false;
                textTime.setText("00:00:00");
                Toast.makeText(getContext(), getResources().getText(R.string.toast_recording_saved).toString(), Toast.LENGTH_SHORT).show();
            } else {
                int seconds = intent.getIntExtra("time", 0);
                String dur = String.format(Locale.getDefault(),
                        "%02d:%02d:%02d",
                        (seconds / (60 * 60)) % 24,
                        (seconds / 60) % 60,
                        seconds % 60);
                textTime.setText(dur);
            }
        }
    };

    @Override
    public void onResume() {
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_UPDATE_TIME));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_FINISH_RECORDING));

        if (isServiceRunning(RecorderService.class)) {
            recordStopButton.setImageResource(R.drawable.square);
            recording = true;
        } else {
            recordStopButton.setImageResource(R.drawable.ic_circle);
            textTime.setText("00:00:00");
            recording = false;
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
        recordStopButton = root.findViewById(R.id.button_record_stop);
        textTime = root.findViewById(R.id.textTime);

        recordStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();
                // Start recording
                if (!recording) {
                    // Stop playback if playing recordings
                    if (isServiceRunning(RecordingPlaybackService.class)) {
                        Intent stopIntent = new Intent(getContext(), RecordingPlaybackService.class);
                        requireContext().stopService(stopIntent);
                    }
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    requireContext().startService(intent);
                    recording = true;
                    recordStopButton.setImageResource(R.drawable.square);
                }
                // Stop recording
                else {
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    intent.setAction(RecordingPlaybackService.ACTION_STOP_SERVICE);
                    requireContext().startService(intent);
                }
            }
        });
        return root;
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