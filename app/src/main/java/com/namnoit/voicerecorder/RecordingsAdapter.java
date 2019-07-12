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
import android.widget.Toast;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


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
        final String dur = String.format("%02d:%02d:%02d",h,m,s);
        holder.textDuration.setText(dur);
        holder.textDate.setText(recordingsList.get(position).getDate());
        holder.id = recordingsList.get(position).getID();
        holder.size = recordingsList.get(position).getSize();
        if (position == selectedPosition) holder.icon.setImageResource(R.drawable.ic_play_red);
        else holder.icon.setImageResource(R.drawable.ic_record);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if file exist
                boolean shouldDelete = false;
                File file = new File(MainActivity.APP_DIR + File.separator + holder.textName.getText());
                if (file.exists()){
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(MainActivity.APP_DIR + File.separator + holder.textName.getText());
                    String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    String dateNoFormat = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
                    String formattedDate = null;
                    try {
                        if (dateNoFormat != null) {
                            SimpleDateFormat readDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.getDefault());
                            readDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                            Date inputDate = readDateFormat.parse(dateNoFormat);
                            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
                            formattedDate = dateFormat.format(inputDate);
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    if (Integer.parseInt(duration) != recordingsList.get(position).getDuration() ||
                    formattedDate != null && !formattedDate.equals(holder.textDate.getText().toString()))
                        shouldDelete = true;
                } else shouldDelete = true;
                if (shouldDelete){
                    final AlertDialog dialog = new AlertDialog.Builder(context)
                            .setTitle(R.string.recording_not_found)
                            .setMessage(R.string.recording_not_found_message)
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    db.delete(holder.id);
                                    recordingsList.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, getItemCount());
                                    if (selectedPosition == position)
                                        selectedPosition = RecyclerView.NO_POSITION;
                                    else if (selectedPosition > position)
                                        selectedPosition--;
                                }
                            }).create();
                    dialog.show();
                } else {
                    notifyItemChanged(selectedPosition);
                    selectedPosition = position;
                    notifyItemChanged(selectedPosition);
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putInt(RecordingsFragment.KEY_CURRENT_POSITION_ADAPTER, position);
                    editor.apply();
                    if (isServiceRunning(RecorderService.class)) {
                        Intent stopServiceIntent = new Intent(context, RecorderService.class);
                        context.stopService(stopServiceIntent);
                    }
                    Intent intent = new Intent(context, RecordingPlaybackService.class);
                    intent.putExtra(RecordingsFragment.KEY_FILE_NAME, holder.textName.getText().toString());
                    intent.setAction("PLAY");
                    context.startService(intent);
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
                                File recording = new File(
                                        MainActivity.APP_DIR,
                                        holder.textName.getText().toString());
                                StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                                StrictMode.setVmPolicy(builder.build());
                                Uri uri = Uri.fromFile(recording);
                                Intent share = new Intent(Intent.ACTION_SEND);
                                share.putExtra(Intent.EXTRA_STREAM, uri);
                                share.setType("audio/*");
                                context.startActivity(
                                        Intent.createChooser(share, context.getResources().getString(R.string.menu_share)));
                                break;
                            // Rename
                            case 1:
                                final EditText textFileName = new EditText(context);
                                textFileName.setText(fileName[0]);
                                AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                                alertDialog.setTitle(R.string.file_name)
                                        .setView(textFileName)
                                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                String newName = textFileName.getText() + "." + fileName[1];
                                                File newFile = new File(
                                                        MainActivity.APP_DIR + File.separator + newName);
                                                File oldFile = new File(
                                                        MainActivity.APP_DIR + File.separator + holder.textName.getText());
                                                if (oldFile.renameTo(newFile)){
                                                    db.updateName(holder.id,newName);
                                                    recordingsList.get(position).setName(newName);
                                                    notifyItemChanged(position);
                                                    Toast.makeText(context,R.string.rename_success,Toast.LENGTH_SHORT).show();
                                                } else
                                                    Toast.makeText(context,R.string.rename_failed,Toast.LENGTH_SHORT).show();
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
                                long fileSize = holder.size;
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
                                                // Delete file
                                                File file = new File(MainActivity.APP_DIR + File.separator + holder.textName.getText());
                                                if (file.exists()) file.delete();
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
        private long size;
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
