package com.namnoit.voicerecorder.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.RecorderService;

/**
 * A placeholder fragment containing a simple view.
 */
public class RecordFragment extends Fragment {
    private FloatingActionButton recordStopButton, pauseButton;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_record, container, false);

        recordStopButton = root.findViewById(R.id.button_record_stop);
        pauseButton = root.findViewById(R.id.button_pause);

        recordStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), RecorderService.class);
                getContext().startService(intent);
            }
        });
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), RecorderService.class);
                getContext().stopService(intent);
            }
        });

        return root;
    }
}