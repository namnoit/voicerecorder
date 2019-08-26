package com.namnoit.voicerecorder;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.namnoit.voicerecorder.data.Recording;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;
import com.namnoit.voicerecorder.ui.main.RecordingsFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;


public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder>{
    public static final String HASH_ALGORITHM = "SHA-256";
    private int mSelectedPosition = RecyclerView.NO_POSITION;
    private ArrayList<Recording> mRecordingsList;
    private Context mContext;
    private RecordingsDbHelper mDb;
    private String appDir;


    public RecordingsAdapter(ArrayList<Recording> recordings, Context c){
        mRecordingsList = recordings;
        mContext = c;
        mDb = new RecordingsDbHelper(mContext);
        if (isServiceRunning(RecordingPlaybackService.class)) {
            mSelectedPosition = SharedPreferenceManager
                    .getInstance()
                    .getInt(SharedPreferenceManager.Key.CURRENT_POSITION_KEY,RecyclerView.NO_POSITION);
        }
        appDir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q?
                Objects.requireNonNull(mContext.getExternalFilesDir(null)).getAbsolutePath() +
                        File.separator +
                        MainActivity.APP_FOLDER :
                MainActivity.APP_DIR;
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
        holder.textName.setText(mRecordingsList.get(position).getName());
        if (mRecordingsList.get(position).getLocation()!=Recording.LOCATION_ON_DRIVE) {
            final int seconds = Math.round((float) mRecordingsList.get(position).getDuration() / 1000);
            long s = seconds % 60;
            long m = (seconds / 60) % 60;
            long h = (seconds / (60 * 60)) % 24;
            final String dur = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
            String durationTime = dur + "  " + mRecordingsList.get(position).getDate();
            holder.textDuration.setText(durationTime);
            if (mRecordingsList.get(position).getLocation()==Recording.LOCATION_PHONE_DRIVE)
                holder.line.setVisibility(View.VISIBLE);
            else holder.line.setVisibility(View.INVISIBLE);
            holder.buttonMore.setImageResource(R.drawable.ic_more);
        } else{
            holder.textDuration.setText(R.string.on_drive);
            holder.line.setVisibility(View.INVISIBLE);
            holder.buttonMore.setImageResource(R.drawable.ic_download);
        }

        if (position == mSelectedPosition) holder.icon.setImageResource(R.drawable.ic_play_red);
        else holder.icon.setImageResource(R.drawable.ic_mic);
        holder.itemView.setEnabled(true);
        holder.buttonMore.setEnabled(true);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRecordingsList.get(position).getLocation()==Recording.LOCATION_ON_DRIVE){
                    if (isInternetAvailable()) {
                        holder.itemView.setEnabled(false);
                        holder.buttonMore.setImageResource(R.drawable.ic_sync_grey);
                        holder.buttonMore.setEnabled(false);
                        Intent broadcast = new Intent(RecordingsFragment.BROADCAST_DOWNLOAD_REQUEST);
                        broadcast.putExtra(RecordingsFragment.KEY_FILE_NAME, mRecordingsList.get(position).getName());
                        broadcast.putExtra(RecordingsFragment.KEY_FILE_ID, mRecordingsList.get(position).getHashValue());
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);
                    }
                    return;
                }
                File file = new File(appDir, mRecordingsList.get(position).getName());
                if (!isFileChanged(file, mRecordingsList.get(position).getHashValue(),position)){
                    notifyItemChanged(mSelectedPosition);
                    mSelectedPosition = position;
                    notifyItemChanged(mSelectedPosition);
                    SharedPreferenceManager.getInstance()
                            .put(SharedPreferenceManager.Key.CURRENT_POSITION_ADAPTER_KEY,position);
                    if (isServiceRunning(RecorderService.class)) {
                        Intent stopServiceIntent = new Intent(mContext, RecorderService.class);
                        mContext.stopService(stopServiceIntent);
                    }
                    Intent intent = new Intent(mContext, RecordingPlaybackService.class);
                    intent.putExtra(RecordingsFragment.KEY_FILE_NAME, mRecordingsList.get(position).getName());
                    intent.setAction("PLAY");
                    mContext.startService(intent);
                }
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                holder.buttonMore.callOnClick();
                return true;
            }
        });
        holder.buttonMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRecordingsList.get(position).getLocation()==Recording.LOCATION_ON_DRIVE){
                    if (isInternetAvailable()) {
                        holder.buttonMore.setEnabled(false);
                        holder.buttonMore.setImageResource(R.drawable.ic_sync_grey);
                        holder.itemView.setEnabled(false);
                        Intent broadcast = new Intent(RecordingsFragment.BROADCAST_DOWNLOAD_REQUEST);
                        broadcast.putExtra(RecordingsFragment.KEY_FILE_NAME, mRecordingsList.get(position).getName());
                        broadcast.putExtra(RecordingsFragment.KEY_FILE_ID, mRecordingsList.get(position).getHashValue());
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);
                    }
                    return;
                }
                final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
                LayoutInflater inflater = LayoutInflater.from(mContext);
                final View convertView = inflater.inflate(R.layout.dialog_menu_about,null);
                View shareMenu = convertView.findViewById(R.id.menu_share);
                View renameMenu = convertView.findViewById(R.id.menu_rename);
                View detailsMenu = convertView.findViewById(R.id.menu_details);
                View deleteMenu = convertView.findViewById(R.id.menu_delete);
                final String[] fileName = mRecordingsList.get(position).getName().split("\\.");
                dialogBuilder.setTitle(holder.textName.getText())
                        .setView(convertView)
                        .setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

                final AlertDialog dialog = dialogBuilder.create();
                shareMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.cancel();
                        File recording = new File(
                                appDir,
                                holder.textName.getText().toString());
                        if (!isFileChanged(recording, mRecordingsList.get(position).getHashValue(), position)) {
                            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                            StrictMode.setVmPolicy(builder.build());
                            Uri uri = Uri.fromFile(recording);
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.putExtra(Intent.EXTRA_STREAM, uri);
                            share.setType("audio/*");
                            mContext.startActivity(
                                    Intent.createChooser(share, mContext.getResources().getString(R.string.menu_share)));
                        }
                    }
                });
                renameMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.cancel();
                        final File oldFile = new File(
                                appDir, holder.textName.getText().toString());
                        if (!isFileChanged(oldFile, mRecordingsList.get(position).getHashValue(), position)) {
                            final EditText textFileName = new EditText(mContext);
                            textFileName.setText(fileName[0]);
                            textFileName.selectAll();
                            textFileName.requestFocus();
                            AlertDialog alertDialog = new AlertDialog.Builder(mContext)
                                    .setTitle(R.string.file_name)
                                    .setView(textFileName)
                                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            String newName = textFileName.getText() + "." + fileName[1];
                                            File newFile = new File(
                                                    appDir, newName);
                                            if (oldFile.renameTo(newFile)) {
                                                mDb.updateName(mRecordingsList.get(position).getID(), newName);
                                                mRecordingsList.get(position).setName(newName);
                                                notifyItemChanged(position);
                                                Toast.makeText(mContext,
                                                        R.string.rename_success,
                                                        Toast.LENGTH_SHORT)
                                                        .show();
                                            } else
                                                Toast.makeText(mContext,
                                                        R.string.rename_failed,
                                                        Toast.LENGTH_SHORT)
                                                        .show();
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    })
                                    .create();
                            Objects.requireNonNull(alertDialog.getWindow())
                                    .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                            alertDialog.show();
                        }
                    }
                });
                detailsMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.cancel();
                        final AlertDialog.Builder detailBuilder = new AlertDialog.Builder(mContext);
                        LayoutInflater inflater = LayoutInflater.from(mContext);
                        final View detailsDialogLayout = inflater.inflate(R.layout.dialog_details, null);
                        // File name
                        TextView textNameDetails = detailsDialogLayout.findViewById(R.id.text_details_title);
                        textNameDetails.setText(mRecordingsList.get(position).getName());
                        // Duration
                        TextView textDurationDetails = detailsDialogLayout.findViewById(R.id.text_details_duration);
                        textDurationDetails.setText(holder.textDuration.getText().subSequence(0,8));
                        // Size
                        TextView textSize = detailsDialogLayout.findViewById(R.id.text_details_size);
                        long fileSize = mRecordingsList.get(position).getSize();
                        String strSize;
                        if (fileSize < 1024) {
                            strSize = fileSize + " bytes";
                        } else if (fileSize < 1024 * 1024) {
                            strSize = long2Decimal(fileSize) + " KB";
                        } else if (fileSize < 1024 * 1024 * 1024) {
                            strSize = long2Decimal(fileSize / 1024) + " MB";
                        } else {
                            strSize = long2Decimal(fileSize / 1024 / 1024) + " GB";
                        }
                        textSize.setText(strSize);
                        // Time
                        TextView textTime = detailsDialogLayout.findViewById(R.id.text_details_time);
                        textTime.setText(mRecordingsList.get(position).getDate());
                        // Format
                        TextView textFormat = detailsDialogLayout.findViewById(R.id.text_details_format);
                        if (fileName[1].equals(RecorderService.AAC)) {
                            textFormat.setText(mContext.getResources().getString(R.string.quality_good));
                        } else
                            textFormat.setText(mContext.getResources().getString(R.string.quality_small));
                        // Location
                        ImageView drive = detailsDialogLayout.findViewById(R.id.image_drive);
                        drive.setVisibility(mRecordingsList.get(position).getLocation() == Recording.LOCATION_ON_PHONE ?
                                View.INVISIBLE :
                                View.VISIBLE);
                        detailBuilder.setView(detailsDialogLayout)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                });
                        detailBuilder.create().show();
                    }
                });
                deleteMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.cancel();
                        final File deleteFile = new File(appDir, holder.textName.getText().toString());
                        if (!isFileChanged(deleteFile, mRecordingsList.get(position).getHashValue(), position)) {
                            AlertDialog.Builder deleteDialogBuilder = new AlertDialog.Builder(mContext);
                            deleteDialogBuilder.setTitle(R.string.delete_title)
                                    .setMessage(fileName[0] + " "
                                            + mContext.getResources().getString(R.string.will_be_deleted))
                                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            mDb.delete(mRecordingsList.get(position).getID());
                                            mRecordingsList.remove(position);
                                            notifyItemRemoved(position);
                                            notifyItemRangeChanged(position, getItemCount());
                                            // Delete file
                                            if (deleteFile.delete()) {
                                                if (mSelectedPosition == position)
                                                    mSelectedPosition = RecyclerView.NO_POSITION;
                                                else if (mSelectedPosition > position)
                                                    mSelectedPosition--;
                                            } else
                                                Toast.makeText(mContext,
                                                        mContext.getResources().getString(R.string.delete_failed),
                                                        Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    })
                                    .create().show();
                        }
                    }
                });
                dialog.show();
            }
        });
    }

    private String long2Decimal(long size){
        String strSize = Long.toString(size/1024);
        int fractions = Math.round((size % 1024)*10f/1024);
        if (fractions != 0)
            strSize += "." + fractions;
        return strSize;
    }

    @Override
    public int getItemCount() {
        return mRecordingsList.size();
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void updateSelectedPosition(){
        if (mSelectedPosition != RecyclerView.NO_POSITION)
            mSelectedPosition++;
    }

    class ViewHolder extends RecyclerView.ViewHolder{
        private TextView textName, textDuration;
        private ImageButton buttonMore;
        private ImageView icon, line;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.image_record);
            buttonMore = itemView.findViewById(R.id.buttonMore);
            textName = itemView.findViewById(R.id.item_recording_name);
            textDuration = itemView.findViewById(R.id.item_recording_duration);
            line = itemView.findViewById(R.id.line);
        }

    }

    private boolean isFileChanged(File file, String md5, final int position){
        boolean isChanged = true;
        if (file.exists()) {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance(HASH_ALGORITHM);
                byte[] buffer = new byte[8192];
                int read;
                InputStream is = new FileInputStream(file);
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                byte[] md5sum = digest.digest();
                BigInteger bigInt = new BigInteger(1, md5sum);
                String newMD5 = bigInt.toString(16);
                // Fill to 32 chars
                newMD5 = String.format("%32s", newMD5).replace(' ', '0');
                isChanged = !newMD5.equals(md5);
            } catch (NoSuchAlgorithmException | IOException e) {
                e.printStackTrace();
            }
        }
        if (isChanged){
            final AlertDialog dialog = new AlertDialog.Builder(mContext)
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
                            mDb.delete(mRecordingsList.get(position).getID());
                            mRecordingsList.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, getItemCount());
                            if (mSelectedPosition == position)
                                mSelectedPosition = RecyclerView.NO_POSITION;
                            else if (mSelectedPosition > position)
                                mSelectedPosition--;
                        }
                    }).create();
            dialog.show();
        }
        return isChanged;
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean internet = cm != null && cm.getActiveNetworkInfo() != null;
        if (!internet) {
            Toast.makeText(mContext,R.string.connection_failed,Toast.LENGTH_LONG).show();
        }
        return internet;
    }
}
