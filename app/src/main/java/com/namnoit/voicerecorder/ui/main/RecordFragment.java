package com.namnoit.voicerecorder.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.namnoit.voicerecorder.R;

/**
 * A placeholder fragment containing a simple view.
 */
public class RecordFragment extends Fragment {
    private FloatingActionButton recordButton, pauseButton;
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_record, container, false);

        recordButton = root.findViewById(R.id.button_record);
        pauseButton = root.findViewById(R.id.button_pause);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        return root;
    }
}