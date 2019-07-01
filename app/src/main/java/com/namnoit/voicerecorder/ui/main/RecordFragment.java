package com.namnoit.voicerecorder.ui.main;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.RecorderService;
import com.namnoit.voicerecorder.data.Recording;

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
            int seconds = intent.getIntExtra("time",0);
            long s = seconds % 60;
            long m = (seconds / 60) % 60;
            long h = (seconds / (60 * 60)) % 24;
            String dur = String.format("%02d:%02d:%02d",h,m,s);
            textTime.setText(dur);
        }
    };

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_record, container, false);

        recordStopButton = root.findViewById(R.id.button_record_stop);
        textTime = root.findViewById(R.id.textTime);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver,
                new IntentFilter(RecorderService.BROADCAST_UPDATE_TIME));
        if (isRecorderServiceRunning(RecorderService.class)){
            recording = true;
            recordStopButton.setImageResource(R.drawable.square);
        }

        recordStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SystemClock.elapsedRealtime() - mLastClickTime < 1000){
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();
                // Start recording
                if (!recording) {
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    getContext().startService(intent);
                    recordStopButton.setImageResource(R.drawable.square);
                    recording = true;
                }
                // Stop recording
                else{
                    Intent intent = new Intent(getContext(), RecorderService.class);
                    getContext().stopService(intent);
                    recordStopButton.setImageResource(R.drawable.ic_circle);
                    recording = false;
                    Toast.makeText(getContext(),getResources().getText(R.string.toast_recording_saved).toString(),Toast.LENGTH_SHORT).show();
                    textTime.setText("00:00:00");
                }
            }
        });
        return root;
    }

    private boolean isRecorderServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}