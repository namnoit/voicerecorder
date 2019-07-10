package com.namnoit.voicerecorder.ui.main;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.namnoit.voicerecorder.MainActivity;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;

/**
 * A placeholder fragment containing a simple view.
 */
public class RecordFragment extends Fragment {
    private FloatingActionButton recordStopButton;
    private TextView textTime;
    private boolean recording = false;
    public static final String RECORD_STATUS_SAVED = "record_status";
    public static final int RECORDING_NOT_SAVED = 1;
    public static final int RECORDING_SAVED = 0;
    private SharedPreferences pref;
    // Prevent double click
    private long mLastClickTime = 0;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(RecorderService.BROADCAST_FINISH_RECORDING)){
                recordStopButton.setImageResource(R.drawable.ic_circle);
                recording = false;
                textTime.setText("00:00:00");

            } else {
                int seconds = intent.getIntExtra("time", 0);
                String dur = String.format("%02d:%02d:%02d", (seconds / (60 * 60)) % 24, (seconds / 60) % 60, seconds % 60);
                textTime.setText(dur);
            }
        }
    };

    @Override
    public void onResume() {
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_UPDATE_TIME));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_FINISH_RECORDING));

        if (isServiceRunning(RecorderService.class)){
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
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
        super.onPause();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_record, container, false);
        pref = getContext().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        recordStopButton = root.findViewById(R.id.button_record_stop);
        textTime = root.findViewById(R.id.textTime);

        recordStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SystemClock.elapsedRealtime() - mLastClickTime < 1000){
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();
                // Start recording
                if (!recording) {
                    // Stop playback if playing recordings
                    if (isServiceRunning(RecordingPlaybackService.class)){
                        Intent stopIntent = new Intent(getContext(),RecordingPlaybackService.class);
                        getActivity().stopService(stopIntent);
                    }
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    getContext().startService(intent);
                    recordStopButton.setImageResource(R.drawable.square);
                    recording = true;
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putInt(RECORD_STATUS_SAVED,RECORDING_NOT_SAVED);
                    editor.apply();
                }
                // Stop recording
                else{
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    getContext().stopService(intent);
                }
            }
        });
        return root;
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