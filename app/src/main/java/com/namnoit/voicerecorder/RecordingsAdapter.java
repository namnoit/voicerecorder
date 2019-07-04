package com.namnoit.voicerecorder;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;
import com.namnoit.voicerecorder.ui.main.RecordingsFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder>{
    private int selectedPosition = RecyclerView.NO_POSITION;
    private ArrayList<Recording> recordingsList;
    private Context context;
    private SharedPreferences pref;
    private RecordingsDbHelper db;

    public RecordingsAdapter(ArrayList<Recording> recordings, Context c){
        recordingsList = recordings;
        context = c;
        db = new RecordingsDbHelper(context);
        pref = context.getSharedPreferences(MainActivity.PREF_NAME,Context.MODE_PRIVATE);
        if (isServiceRunning(RecordingPlaybackService.class))
            selectedPosition = pref.getInt(RecordingsFragment.currentPositionInAdaper,RecyclerView.NO_POSITION);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View itemView = layoutInflater.inflate(R.layout.item_recordings_row,parent,false);

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        holder.textName.setText(recordingsList.get(position).getName());
        final int seconds = Math.round((float)recordingsList.get(position).getDuration()/1000);
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        String dur = String.format("%02d:%02d:%02d",h,m,s);
        holder.textDuration.setText(dur);
        holder.textDate.setText(recordingsList.get(position).getDate());
        holder.id = recordingsList.get(position).getID();
Log.d("pos",Integer.toString(position));
Log.d("sel",Integer.toString(selectedPosition));
        if (position == selectedPosition) holder.icon.setImageResource(R.drawable.ic_play_red);
        else holder.icon.setImageResource(R.drawable.ic_record);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                notifyItemChanged(selectedPosition);
                selectedPosition = position;
//                holder.itemView.setSelected(true);
                notifyItemChanged(selectedPosition);
//                notifyDataSetChanged();
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt(RecordingsFragment.currentPositionInAdaper,position);
                editor.apply();
                if (isServiceRunning(RecorderService.class)) {
                    Intent stopServiceIntent = new Intent(context, RecorderService.class);
                    context.stopService(stopServiceIntent);
                    Log.d("stop","service");
                }
                try {
                    // create temp file that will hold byte array
                    File tempMp3 = new File(holder.textName.getContext().getCacheDir().getAbsolutePath()
                            + RecordingPlaybackService.cacheFile);
//                    tempMp3.deleteOnExit();
                    FileOutputStream fos = new FileOutputStream(tempMp3);
                    byte[] soundByteArray = new RecordingsDbHelper(holder.textDate.getContext())
                            .getAudio(holder.id);
                    fos.write(soundByteArray);
                    fos.close();

                    Intent intent = new Intent(context, RecordingPlaybackService.class);
                    intent.putExtra(RecordingsFragment.fileName,holder.textName.getText().toString());
                    intent.setAction("PLAY");
                    context.startService(intent);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        holder.buttonMore.setOnClickListener(new View.OnClickListener() {
            final CharSequence[] items = {"Delete","Export","Detail"};
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                alertDialog.setTitle("Your title");
                alertDialog.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:
                                db.delete(holder.id);
                                Log.d("Delete position",Integer.toString(position));
                                recordingsList.remove(position);
                                notifyItemRemoved(position);
                                notifyItemRangeChanged(position, getItemCount());
                                if (selectedPosition == position)
                                    selectedPosition = RecyclerView.NO_POSITION;
                                else if (selectedPosition > position)
                                    selectedPosition--;
                                break;
                        }
                    }
                })
                .setPositiveButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                AlertDialog dialog = alertDialog.create();
                dialog.show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return recordingsList.size();
    }
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void updateSelectedPosition(){
        if (selectedPosition != RecyclerView.NO_POSITION)
            selectedPosition++;
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        private int id;
        private TextView textName, textDuration, textDate;
        private ImageButton buttonMore;
        private ImageView icon;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.image_record);
            buttonMore = itemView.findViewById(R.id.buttonMore);
            textName = itemView.findViewById(R.id.item_recording_name);
            textDuration = itemView.findViewById(R.id.item_recording_duration);
            textDate = itemView.findViewById(R.id.item_recording_date);
        }

    }
}
