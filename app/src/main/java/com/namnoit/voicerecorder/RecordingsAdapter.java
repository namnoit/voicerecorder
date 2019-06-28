package com.namnoit.voicerecorder;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder>{
    private ArrayList<Recording> recordingsList;
    private Context context;
    private MediaPlayer mediaPlayer = new MediaPlayer();

    public RecordingsAdapter(ArrayList<Recording> recordings, Context c){
        recordingsList = recordings;
        context = c;
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
        int seconds = recordingsList.get(position).getDuration()/1000;
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        String dur = String.format("%02d:%02d:%02d",h,m,s);
        holder.textDuration.setText(dur);
        holder.textDate.setText(recordingsList.get(position).getDate());
        holder.id = recordingsList.get(position).getID();
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // create temp file that will hold byte array
                    File tempMp3 = File.createTempFile("kurchina", "mp3", holder.textDate.getContext().getCacheDir());
                    tempMp3.deleteOnExit();
                    FileOutputStream fos = new FileOutputStream(tempMp3);
                    byte[] soundByteArray = new RecordingsDbHelper(holder.textDate.getContext())
                            .getAudio(holder.id);
                    fos.write(soundByteArray);
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
                    ex.printStackTrace();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return recordingsList.size();
    }


    public class ViewHolder extends RecyclerView.ViewHolder{
        int id;
        private TextView textName, textDuration, textDate;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.item_recording_name);
            textDuration = itemView.findViewById(R.id.item_recording_duration);
            textDate = itemView.findViewById(R.id.item_recording_date);
        }

    }
}
