package com.namnoit.voicerecorder.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.namnoit.voicerecorder.MainActivity;
import com.namnoit.voicerecorder.R;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class RecorderService extends Service {
    private MediaRecorder recorder;
    private String fileName = null;
    private static final String CHANNEL_ID = "Voice_Recorder";
    public static final String BROADCAST_FINISH_RECORDING = "RECORDING.FINISH";
    public static final String BROADCAST_UPDATE_TIME = "RECORDING.UPDATE.TIME";
    public static final String AAC = "aac";
    public static final String THREE_GPP = "3gp";

    private SharedPreferences pref;
    private Date dateNow;
    private Handler handler = new Handler();
    long timeInMilliseconds = 0L;
    private long initial_time;
    private int timer;
    private Intent broadcastUpdateTime = new Intent(BROADCAST_UPDATE_TIME);
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
        }
    };
    private IntentFilter shutdownFilter =
            new IntentFilter("android.intent.action.ACTION_SHUTDOWN");
    private IntentFilter powerOffFilter =
            new IntentFilter("android.intent.action.QUICKBOOT_POWEROFF");

    public RecorderService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Objects.equals(intent.getAction(), RecordingPlaybackService.ACTION_STOP_SERVICE))
            stopSelf();
        else {
            // Create foreground Notification
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setAction(Intent.ACTION_MAIN);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, 0, notificationIntent, 0);
            // Stop button
            Intent stopIntent = new Intent(this, RecorderService.class);
            stopIntent.setAction(RecordingPlaybackService.ACTION_STOP_SERVICE);
            PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager manager = getSystemService(NotificationManager.class);
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID,
                        "Voice Recorder",
                        NotificationManager.IMPORTANCE_LOW
                );
                manager.createNotificationChannel(serviceChannel);
            }
            Notification notification =
                    new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setContentTitle(getText(R.string.notification_title_recording))
                            .setContentText(getText(R.string.notification_text_recording))
                            .setSmallIcon(R.drawable.ic_mic)
                            .setContentIntent(pendingIntent)
                            .addAction(R.drawable.ic_stop, getResources().getString(R.string.stop), stopPendingIntent)
                            .build();

            registerReceiver(receiver, shutdownFilter);
            registerReceiver(receiver, powerOffFilter);

            SimpleDateFormat nameFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            dateNow = new Date();
            fileName = "Recording_" + nameFormat.format(dateNow);
            // Configure Media Recorder
            recorder = new MediaRecorder();
            recorder.setAudioChannels(2);
            recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            pref = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
            int audioFormat = pref.getInt(MainActivity.KEY_QUALITY, MainActivity.QUALITY_GOOD);

            switch (audioFormat) {
                case MainActivity.QUALITY_GOOD:
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    recorder.setAudioSamplingRate(48000);
                    recorder.setAudioEncodingBitRate(320000);
                    fileName += "." + AAC;
                    break;
                case MainActivity.QUALITY_SMALL:
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                    recorder.setAudioSamplingRate(16000);
                    recorder.setAudioEncodingBitRate(128000);
                    fileName += "." + THREE_GPP;
                    break;
                default:
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                    recorder.setAudioSamplingRate(16000);
                    recorder.setAudioEncodingBitRate(128000);
                    fileName += "." + AAC;
                    break;
            }
            recorder.setOutputFile(MainActivity.APP_DIR + File.separator + fileName);
            try {
                startForeground(1, notification);
                recorder.prepare();
                recorder.start();
                initial_time = SystemClock.uptimeMillis();
                handler.removeCallbacks(sendUpdatesToUI);
                handler.postDelayed(sendUpdatesToUI, 1000);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sendUpdatesToUI);
        recorder.stop();
        recorder.reset();
        recorder.release();
        recorder = null;
        unregisterReceiver(receiver);

        File file = new File(MainActivity.APP_DIR + File.separator + fileName);
        long length = file.length();
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(MainActivity.APP_DIR + File.separator + fileName);
        String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        String dateNoFormat = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            InputStream is = new FileInputStream(file);
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String md5 = bigInt.toString(16);
            // Fill to 32 chars
            md5 = String.format("%32s", md5).replace(' ', '0');
            String formattedDate;
            if (dateNoFormat != null) {
                SimpleDateFormat readDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.getDefault());
                readDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date inputDate = readDateFormat.parse(dateNoFormat);
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
                formattedDate = dateFormat.format(inputDate);
            } else{
                formattedDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss",Locale.getDefault()).format(dateNow);
            }
            RecordingsDbHelper dbHelper = new RecordingsDbHelper(getApplicationContext());
            dbHelper.insert(fileName, length, Integer.parseInt(duration), formattedDate, md5);
            Toast.makeText(getApplicationContext(), getResources().getText(R.string.toast_recording_saved).toString(), Toast.LENGTH_SHORT).show();
            Intent broadcast = new Intent(BROADCAST_FINISH_RECORDING);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcast);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    private Runnable sendUpdatesToUI = new Runnable() {
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - initial_time;
            timer = (int) timeInMilliseconds / 1000;
            broadcastUpdateTime.putExtra("time", timer);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcastUpdateTime);
            handler.postDelayed(this, 1000); // 1 seconds
        }
    };


}
