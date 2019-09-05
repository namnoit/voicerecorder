package com.namnoit.voicerecorder.ui.main;


import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.RecordingsAdapter;
import com.namnoit.voicerecorder.SharedPreferenceManager;
import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import com.namnoit.voicerecorder.drive.DriveServiceHelper;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

public class RecordingsFragment extends Fragment {
    private RecyclerView recyclerView;
    private RecordingsAdapter recordingsAdapter;
    private ArrayList<Recording> list;
    private RecordingsDbHelper db;
    private DriveServiceHelper mDriveHelper;
    private SharedPreferenceManager mPref;
    private ImageButton playRecordingButton;
    private SeekBar seekBar;
    private TextView textTitle, textCurrentPosition, textDuration;
    private View playback;
    private int status = STATUS_PAUSED;
    public static final int STATUS_PLAYING = 0;
    public static final int STATUS_PAUSED = 1;
    public static final int STATUS_STOPPED = 2;
    public static final String BROADCAST_UPDATE_SEEK_BAR = "UPDATE_SEEK_BAR";
    public static final String BROADCAST_FINISH_PLAYING = "PLAY_FINISH";
    public static final String BROADCAST_FILE_DOWNLOADED = "FILE_DOWNLOADED";
    public static final String BROADCAST_FILE_UPLOADED = "FILE_UPLOADED";
    public static final String BROADCAST_START_PLAYING = "START_PLAYING";
    public static final String BROADCAST_SIGNED_OUT = "USER_SIGNED_OUT";
    public static final String BROADCAST_SYNC_REQUEST = "REQUEST_SYNC";
    public static final String BROADCAST_BACKUP_REQUEST = "REQUEST_BACKUP";
    public static final String BROADCAST_DOWNLOAD_REQUEST = "REQUEST_DOWNLOAD";
    public static final String BROADCAST_PAUSED = "PAUSED";
    public static final String BROADCAST_START_SELECTING = "START_SELECTING";
    public static final String BROADCAST_CANCEL_SELECTING = "CANCEL_SELECTING";
    public static final String KEY_CURRENT_POSITION = "current_position";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_HASH_VALUE = "hash_value";
    public static final String KEY_FILE_NAME = "file_name";
    public static final String KEY_FILE_ID = "file_id";
    public static final String KEY_SEEK_TO_POSITION = "seek";
    public static final String KEY_IS_LATEST_LOADED = "is_latest_loaded";
    // To show current item selected in list
    private int durationMillis = 0;
    private int curMillis = 0;
    private String recordingName = "";
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(RecorderService.BROADCAST_FINISH_RECORDING)) {
                    Recording r = db.getLast();
                    if (r != null) {
                        list.add(0, r);
                        recordingsAdapter.notifyItemInserted(0);
                        recordingsAdapter.increasePosition();
                        recordingsAdapter.notifyItemRangeChanged(1, recordingsAdapter.getItemCount());
                        recyclerView.scrollToPosition(0);
                        mPref.put(KEY_IS_LATEST_LOADED,true);
                    }
                }
                else if (intent.getAction().equals(BROADCAST_FILE_DOWNLOADED)) {
                    Recording r = db.getRecordingWithHash(intent.getStringExtra(KEY_HASH_VALUE));
                    if (r != null) {
                        r.setLocation(Recording.LOCATION_PHONE_DRIVE);
                        for (int i = 0; i<list.size();i++){
                            if (list.get(i).getName().equals(r.getName())){
                                list.set(i,r);
                                recordingsAdapter.notifyItemChanged(i);
                            }
                        }
                    }
                }
                else if (intent.getAction().equals(BROADCAST_FILE_UPLOADED)) {
                    String hash = intent.getStringExtra(KEY_HASH_VALUE);
                    for (int i = 0; i < list.size(); i++){
                        if (list.get(i).getHashValue().equals(hash)) {
                            list.get(i).setLocation(Recording.LOCATION_PHONE_DRIVE);
                            recordingsAdapter.notifyItemChanged(i);
                        }
                    }
                }
                else if (intent.getAction().equals(BROADCAST_SIGNED_OUT)) {
                    list.clear();
                    list.addAll(db.getAll());
                    recordingsAdapter.notifyDataSetChanged();
                }
                else if (intent.getAction().equals(BROADCAST_SYNC_REQUEST)) {
                    sync(false);
                }
                else if (intent.getAction().equals(BROADCAST_BACKUP_REQUEST)) {
                    sync(true);
                }
                else if (intent.getAction().equals(BROADCAST_DOWNLOAD_REQUEST)) {
                    if (isInternetAvailable()) {
                        if (mDriveHelper == null) {
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
                                mDriveHelper = new DriveServiceHelper(requireContext(), googleDriveService, list, recordingsAdapter);
                            }
                        }
                        mDriveHelper.downloadFile(intent.getStringExtra(KEY_FILE_ID), intent.getStringExtra(KEY_FILE_NAME));
                    }
                }
                else if (intent.getAction().equals(BROADCAST_START_PLAYING)) {
                    status = STATUS_PLAYING;
                    durationMillis = intent.getIntExtra(KEY_DURATION, 0);
                    seekBar.setMax(durationMillis);
                    seekBar.setProgress(0);
                    playback.setVisibility(View.VISIBLE);
                    playback.setEnabled(true);
                    textTitle.setText(recordingName = intent.getStringExtra(KEY_FILE_NAME));
                    textDuration.setText(milliSeconds2String(durationMillis));
                    playRecordingButton.setImageResource(R.drawable.ic_pause_white);
                }
                else if (intent.getAction().equals(BROADCAST_UPDATE_SEEK_BAR)) {
                    if (status != STATUS_PLAYING) {
                        status = STATUS_PLAYING;
                        playRecordingButton.setImageResource(R.drawable.ic_pause_white);
                    }
                    curMillis = intent.getIntExtra(KEY_CURRENT_POSITION, 0);
                    seekBar.setProgress(curMillis);
                    textCurrentPosition.setText(milliSeconds2String(curMillis));
                }
                else if (intent.getAction().equals(BROADCAST_FINISH_PLAYING)) {
                    recordingsAdapter.resetPlayingPosition();
                    seekBar.setProgress(durationMillis);
                    status = STATUS_STOPPED;
                    textCurrentPosition.setText(getResources().getString(R.string.start_time));
                    textDuration.setText(getResources().getString(R.string.start_time));
                    playback.setVisibility(View.GONE);
                    playback.setEnabled(false);
                }
                else if (intent.getAction().equals(BROADCAST_PAUSED)) {
                    status = STATUS_PAUSED;
                    playRecordingButton.setImageResource(R.drawable.ic_play);
                }
                else if (intent.getAction().equals(BROADCAST_START_SELECTING)) {
                    mActionMode = Objects.requireNonNull(getActivity())
                            .startActionMode(actionModeCallback);
                }
                else if (intent.getAction().equals(BROADCAST_CANCEL_SELECTING)) {
                    if (mActionMode != null) {
                        mActionMode.finish();
                        mActionMode = null;
                    }
                }
            }
        }
    };

    private ActionMode mActionMode;
    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.context_menu, menu);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.select_all_context_menu:
                    for (Recording recording: list){
                        recording.setSelected(true);
                        recordingsAdapter.notifyDataSetChanged();
                    }
                    return true;
                case R.id.delete_context_menu:
                    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                            .setTitle(getResources().getString(R.string.delete_files_title))
                            .setMessage(getResources().getString(R.string.delete_files_message))
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    int pos = RecyclerView.NO_POSITION;
                                    int count = 0;
                                    for (Iterator<Recording> iterator = list.iterator(); iterator.hasNext();){
                                        Recording recording = iterator.next();
                                        pos++;
                                        if (recording.isSelected()) {
                                            db.delete(recording.getID());
                                            iterator.remove();
                                            recordingsAdapter.notifyItemRemoved(pos);
                                            recordingsAdapter.notifyItemRangeChanged(pos,list.size());
                                            recordingsAdapter.updatePositionAfterDeletion(pos);
                                            pos--;
                                            count++;
                                        }
                                    }
                                    Toast.makeText(requireContext(),
                                            count + " file" + (count > 1 ? "s ":" ") + getResources().getString(R.string.was_deleted),
                                            Toast.LENGTH_SHORT).show();
                                    mode.finish();
                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                }
                            });
                    builder.create().show();
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            for (Recording recording: list){
                recording.setSelected(false);
                recordingsAdapter.notifyDataSetChanged();
            }
            recordingsAdapter.setSelecting(false);
            mActionMode = null;
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
                new IntentFilter(RecordingsFragment.BROADCAST_UPDATE_SEEK_BAR));
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
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_SIGNED_OUT));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_SYNC_REQUEST));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_BACKUP_REQUEST));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_DOWNLOAD_REQUEST));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_START_SELECTING));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver,
                new IntentFilter(RecordingsFragment.BROADCAST_CANCEL_SELECTING));
        boolean isLatestLoaded = mPref.getBoolean(KEY_IS_LATEST_LOADED);
        if (!isLatestLoaded){
            Recording r = db.getLast();
            if (r != null) {
                list.add(0, r);
                recordingsAdapter.notifyItemInserted(0);
                recordingsAdapter.increasePosition();
                recordingsAdapter.notifyItemRangeChanged(1, recordingsAdapter.getItemCount());
                recyclerView.scrollToPosition(0);
                mPref.put(KEY_IS_LATEST_LOADED,true);
            }
        }
        status = mPref.getInt(SharedPreferenceManager.Key.STATUS_KEY,STATUS_STOPPED);
        if (!isServiceRunning()){
            status = STATUS_STOPPED;
            playback.setEnabled(false);
            playback.setVisibility(View.GONE);
        }else if (status == STATUS_PAUSED){
            curMillis = mPref.getInt(SharedPreferenceManager.Key.CURRENT_POSITION_KEY);
            recordingName = mPref.getString(SharedPreferenceManager.Key.FILE_NAME_KEY);
            durationMillis = mPref.getInt(SharedPreferenceManager.Key.DURATION_KEY);
            seekBar.setMax(durationMillis);
            textTitle.setText(recordingName);
            textDuration.setText(milliSeconds2String(durationMillis));
            textCurrentPosition.setText(milliSeconds2String(curMillis));
            seekBar.setProgress(curMillis);
            playRecordingButton.setImageResource(R.drawable.ic_play);
        } else{
            durationMillis = mPref.getInt(SharedPreferenceManager.Key.DURATION_KEY);
            seekBar.setMax(durationMillis);
            textTitle.setText(mPref.getString(SharedPreferenceManager.Key.FILE_NAME_KEY));
            textDuration.setText(milliSeconds2String(durationMillis));
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

        mPref = SharedPreferenceManager.getInstance(requireContext());
        db = new RecordingsDbHelper(getContext());
        list = db.getAll();
        mPref.put(KEY_IS_LATEST_LOADED,true);
        seekBar = view.findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser){
                    textCurrentPosition.setText(milliSeconds2String(progress));
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
        View closeRecordingButton = view.findViewById(R.id.closeRecordingButton);
        View playRecordingView = view.findViewById(R.id.playRecordingView);
        playRecordingButton = view.findViewById(R.id.playRecordingButton);
        playRecordingView.setOnClickListener(new View.OnClickListener() {
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
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recordingsAdapter = new RecordingsAdapter(list, getContext());
        recyclerView.setAdapter(recordingsAdapter);
        playback = view.findViewById(R.id.playback);
        return view;
    }


    private void sync(boolean backup) {
        if (isInternetAvailable()) {
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
                if (mDriveHelper == null)
                    mDriveHelper = new DriveServiceHelper(requireContext(),googleDriveService,list,recordingsAdapter);
                if (backup)
                    mDriveHelper.backUp();
                else mDriveHelper.sync();
            }
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetworkInfo() != null;
    }

    private String milliSeconds2String(int milliSeconds){
        int seconds = Math.round(milliSeconds/1000f);
        return String.format(Locale.getDefault(),
                "%02d:%02d:%02d",
                (seconds / (60 * 60)) % 24,
                (seconds / 60) % 60,
                seconds % 60);
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (RecordingPlaybackService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
