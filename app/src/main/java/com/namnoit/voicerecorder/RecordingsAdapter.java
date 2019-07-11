package com.namnoit.voicerecorder;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder>{
    private int selectedPosition = RecyclerView.NO_POSITION;
    private ArrayList<Recording> recordingsList;
    private Context context;
    private SharedPreferences pref;
    private RecordingsDbHelper db;
    private static final String APPLICATION_FOLDER = "Voice Recorder";

    public RecordingsAdapter(ArrayList<Recording> recordings, Context c){
        recordingsList = recordings;
        context = c;
        db = new RecordingsDbHelper(context);
        pref = context.getSharedPreferences(MainActivity.PREF_NAME,Context.MODE_PRIVATE);
        if (isServiceRunning(RecordingPlaybackService.class))
            selectedPosition = pref.getInt(RecordingsFragment.KEY_CURRENT_POSITION_ADAPTER,RecyclerView.NO_POSITION);
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
                editor.putInt(RecordingsFragment.KEY_CURRENT_POSITION_ADAPTER,position);
                editor.apply();
                if (isServiceRunning(RecorderService.class)) {
                    Intent stopServiceIntent = new Intent(context, RecorderService.class);
                    context.stopService(stopServiceIntent);
                }
                try {
                    // create temp file that will hold byte array
                    File tempMp3 = new File(context.getCacheDir().getAbsolutePath()
                            + RecordingPlaybackService.CACHE_FILE_NAME);
                    tempMp3.deleteOnExit();
                    FileOutputStream fos = new FileOutputStream(tempMp3);
                    byte[] soundByteArray = new RecordingsDbHelper(context)
                            .getAudio(holder.id);
                    fos.write(soundByteArray);
                    fos.close();

                    Intent intent = new Intent(context, RecordingPlaybackService.class);
                    intent.putExtra(RecordingsFragment.KEY_FILE_NAME,holder.textName.getText().toString());
                    intent.setAction("PLAY");
                    context.startService(intent);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        holder.buttonMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);

                LayoutInflater inflater = LayoutInflater.from(context);
                View convertView = inflater.inflate(R.layout.dialog_menu, null);
                ListView lv = convertView.findViewById(R.id.listView);
                ListDialogAdapter listAdapter = new ListDialogAdapter(context);
                lv.setAdapter(listAdapter);
                lv.setDividerHeight(0);
                dialogBuilder.setTitle(holder.textName.getText())
//                        .setCustomTitle(dialogTitle)
                        .setView(convertView)
                        .setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

                final AlertDialog dialog = dialogBuilder.create();
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int which, long id) {
                        final String[] fileName = holder.textName.getText().toString().split("\\.");
                        switch(which){
                            // Share
                            case 0:
                                File folder = new File(
                                        Environment.getExternalStorageDirectory().getAbsolutePath(),
                                        APPLICATION_FOLDER);
                                if (!folder.exists())
                                    folder.mkdirs();
                                File recording = new File(
                                        Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+APPLICATION_FOLDER,
                                        holder.textName.getText().toString());
                                FileOutputStream outputStream;
                                byte[] recordingByteArray = new RecordingsDbHelper(context)
                                        .getAudio(holder.id);
                                try {
                                    outputStream = new FileOutputStream(recording);
                                    outputStream.write(recordingByteArray);
                                    outputStream.close();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                                StrictMode.setVmPolicy(builder.build());
                                Uri uri = Uri.fromFile(recording);
                                Intent share = new Intent(Intent.ACTION_SEND);
                                share.putExtra(Intent.EXTRA_STREAM, uri);
                                share.setType("audio/*");
                                context.startActivity(
                                        Intent.createChooser(share, context.getResources().getString(R.string.menu_share)));
                                break;
                            // Export
                            case 1:
                                final EditText textFileName = new EditText(context);
                                textFileName.setText(fileName[0]);
                                AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                                alertDialog.setTitle(R.string.file_name)
                                        .setView(textFileName)
                                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                File folder = new File(
                                                        Environment.getExternalStorageDirectory().getAbsolutePath(),
                                                        APPLICATION_FOLDER);
                                                if (!folder.exists())
                                                    folder.mkdirs();
                                                File recording = new File(
                                                        Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+APPLICATION_FOLDER,
                                                        textFileName.getText().toString() + "." + fileName[1]);
                                                FileOutputStream fos;
                                                byte[] soundByteArray = new RecordingsDbHelper(context)
                                                        .getAudio(holder.id);
                                                try {
                                                    fos = new FileOutputStream(recording);
                                                    fos.write(soundByteArray);
                                                    fos.close();
                                                } catch (FileNotFoundException e) {
                                                    e.printStackTrace();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        })
                                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                            }
                                        })
                                        .create().show();
                                break;
                            // Show details
                            case 2:
                                File tempMp3 = new File(context.getCacheDir().getAbsolutePath()
                                        + RecordingPlaybackService.CACHE_FILE_NAME);
                                tempMp3.deleteOnExit();
                                FileOutputStream fos;
                                byte[] soundByteArray = new RecordingsDbHelper(context)
                                    .getAudio(holder.id);
                                try {
                                    fos = new FileOutputStream(tempMp3);
                                    fos.write(soundByteArray);
                                    fos.close();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                MediaMetadataRetriever meta = new MediaMetadataRetriever();
                                meta.setDataSource(tempMp3.getAbsolutePath());
                                final AlertDialog.Builder detailBuilder = new AlertDialog.Builder(context);
                                LayoutInflater inflater = LayoutInflater.from(context);
                                final View detailsDialogLayout = inflater.inflate(R.layout.dialog_details, null);
                                // File name
                                TextView textNameDetails = detailsDialogLayout.findViewById(R.id.text_details_title);
                                textNameDetails.setText(holder.textName.getText());
                                // Duration
                                TextView textDurationDetails = detailsDialogLayout.findViewById(R.id.text_details_duration);
                                textDurationDetails.setText(holder.textDuration.getText());
                                // Size
                                TextView textSize = detailsDialogLayout.findViewById(R.id.text_details_size);
                                int fileSize = soundByteArray.length;
                                String strSize;
                                if (fileSize < 1024){
                                    strSize = fileSize + " bytes";
                                } else if ((fileSize=fileSize/1024) < 1024){
                                    strSize = fileSize + " KB";
                                } else if ((fileSize=fileSize/1024) < 1024){
                                    strSize = fileSize + " MB";
                                } else {
                                    strSize = fileSize + " GB";
                                }
                                textSize.setText(strSize);
                                // Time
                                TextView textTime = detailsDialogLayout.findViewById(R.id.text_details_time);
                                textTime.setText(holder.textDate.getText());
                                // Format
                                TextView textFormat = detailsDialogLayout.findViewById(R.id.text_details_format);
//                                textFormat.setText(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
                                if (fileName[1].equals(RecorderService.AAC)){
                                    textFormat.setText(context.getResources().getString(R.string.quality_good));
                                } else
                                    textFormat.setText(context.getResources().getString(R.string.quality_small));
                                detailBuilder.setView(detailsDialogLayout)
                                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                });
                                detailBuilder.create().show();
                                break;
                            // Delete
                            case 3:
                                AlertDialog.Builder deleteDialogBuilder = new AlertDialog.Builder(context);
                                deleteDialogBuilder.setTitle(R.string.delete_title)
                                        .setMessage(fileName[0] + " "
                                                + context.getResources().getString(R.string.will_be_deleted))
                                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                db.delete(holder.id);
                                                recordingsList.remove(position);
                                                notifyItemRemoved(position);
                                                notifyItemRangeChanged(position, getItemCount());
                                                if (selectedPosition == position)
                                                    selectedPosition = RecyclerView.NO_POSITION;
                                                else if (selectedPosition > position)
                                                    selectedPosition--;
                                            }
                                        })
                                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                            }
                                        })
                                        .create().show();

                                break;
                        }
                        dialog.cancel();
                    }
                });
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
