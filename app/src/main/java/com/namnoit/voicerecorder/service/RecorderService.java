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
import android.media.AudioManager;
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
import com.namnoit.voicerecorder.SharedPreferenceManager;
import com.namnoit.voicerecorder.data.RecordingsDbHelper;
import com.namnoit.voicerecorder.ui.main.RecordFragment;
import com.namnoit.voicerecorder.ui.main.RecordingsFragment;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecorderService extends Service {
    public static final String BROADCAST_FINISH_RECORDING = "RECORDING.FINISH";
    public static final String BROADCAST_UPDATE_TIME = "RECORDING.UPDATE.TIME";
    public static final String AAC = "aac";
    public static final String THREE_GPP = "3gp";
    public static final String SERVICE_CHANNEL_ID = "Voice_Recorder_Service";
    public static final String ACTION_START_RECORDING = "START_RECORDING";
    public static final String ACTION_PAUSE_RECORDING = "PAUSE_RECORDING";
    public static final String ACTION_RESUME_RECORDING = "RESUME_RECORDING";
    private SharedPreferenceManager mPref;
    private String appDir;
    private long timeInMilliseconds = 0L;
    private MediaRecorder recorder;
    private String fileName = null;
    private Date dateNow;
    private Handler handler = new Handler();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private long initial_time;
    private Intent broadcastUpdateTime = new Intent(BROADCAST_UPDATE_TIME);
    private Intent broadcastPause = new Intent(ACTION_PAUSE_RECORDING);
    private Intent broadcastResume = new Intent(ACTION_RESUME_RECORDING);
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
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) pause();
                        else stop();
                    }
                }
            };
    private Runnable sendUpdatesToUI = new Runnable() {
        public void run() {
            int timer = (int) Math.round((timeInMilliseconds + SystemClock.uptimeMillis() - initial_time)/1000.0);
            broadcastUpdateTime.putExtra("time", timer);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcastUpdateTime);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        mPref = SharedPreferenceManager.getInstance();
        audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        appDir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q?
                Objects.requireNonNull(getApplicationContext().getExternalFilesDir(null)).getAbsolutePath() +
                        File.separator +
                        MainActivity.APP_FOLDER :
                MainActivity.APP_DIR;
        if (Objects.equals(intent.getAction(), RecordingPlaybackService.ACTION_STOP_SERVICE)) {
            stop();
        }
        else if (Objects.equals(intent.getAction(), ACTION_START_RECORDING)) {
            int result = audioManager.requestAudioFocus(afChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                executor.execute(setUpRunnable);
                registerReceiver(receiver, shutdownFilter);
                registerReceiver(receiver, powerOffFilter);
            }
        }
        else if (Objects.equals(intent.getAction(), ACTION_PAUSE_RECORDING)){
            audioManager.abandonAudioFocus(afChangeListener);
            pause();
        }
        else if (Objects.equals(intent.getAction(), ACTION_RESUME_RECORDING)){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                int result = audioManager.requestAudioFocus(afChangeListener,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    initial_time = SystemClock.uptimeMillis();
                    recorder.resume();
                    handler.postDelayed(sendUpdatesToUI, 0);
                    mPref.put(SharedPreferenceManager.Key.RECORD_STATUS_KEY, RecordFragment.STATUS_RECORDING);
                    createNotification(RecordFragment.STATUS_RECORDING);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcastResume);
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        unregisterReceiver(receiver);
        if (recorder != null) {
            audioManager.abandonAudioFocus(afChangeListener);
            handler.removeCallbacks(sendUpdatesToUI);
            recorder.stop();
            recorder.reset();
            recorder.release();
            recorder = null;
            mPref.put(SharedPreferenceManager.Key.RECORD_STATUS_KEY,RecordFragment.STATUS_STOPPED);
            File file = new File(appDir, fileName);
            long length = file.length();
            MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(appDir + File.separator + fileName);
            String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String dateNoFormat = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                InputStream is = new FileInputStream(file);
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                byte[] hashSum = digest.digest();
                BigInteger bigInt = new BigInteger(1, hashSum);
                String hashValue = bigInt.toString(16);
                // Fill to 32 chars
                hashValue = String.format("%32s", hashValue).replace(' ', '0');
                String formattedDate;
                if (dateNoFormat != null) {
                    SimpleDateFormat readDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.getDefault());
                    readDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                    Date inputDate = readDateFormat.parse(dateNoFormat);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
                    formattedDate = dateFormat.format(inputDate);
                } else {
                    formattedDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault()).format(dateNow);
                }
                RecordingsDbHelper dbHelper = new RecordingsDbHelper(getApplicationContext());
                dbHelper.insert(fileName, length, Integer.parseInt(duration), formattedDate, hashValue);
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
        }
        super.onDestroy();
    }

    private void pause(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            handler.removeCallbacks(sendUpdatesToUI);
            recorder.pause();
            timeInMilliseconds += SystemClock.uptimeMillis() - initial_time;
            mPref.put(SharedPreferenceManager.Key.RECORD_STATUS_KEY,RecordFragment.STATUS_PAUSED);
            mPref.put(SharedPreferenceManager.Key.PAUSE_POSITION,Math.round(timeInMilliseconds/1000f));

            createNotification(RecordFragment.STATUS_PAUSED);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcastPause);
        }
    }

    private void stop(){
        audioManager.abandonAudioFocus(afChangeListener);
        handler.removeCallbacks(sendUpdatesToUI);
        recorder.stop();
        recorder.reset();
        recorder.release();
        recorder = null;
        executor.execute(saveFileRunnable);
    }

    private Runnable setUpRunnable = new Runnable() {
        @Override
        public void run() {
            createNotification(RecordFragment.STATUS_RECORDING);
            SimpleDateFormat nameFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            dateNow = new Date();
            fileName = "Recording_" + nameFormat.format(dateNow);
            // Configure Media Recorder
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            int audioFormat = mPref.getInt(SharedPreferenceManager.Key.QUALITY_KEY,MainActivity.QUALITY_GOOD);
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
            recorder.setOutputFile(appDir + File.separator + fileName);
            try {
                recorder.prepare();
                recorder.start();
                mPref.put(SharedPreferenceManager.Key.RECORD_STATUS_KEY,RecordFragment.STATUS_RECORDING);
                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                getResources().getText(R.string.toast_start_recording).toString(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
                initial_time = SystemClock.uptimeMillis();
                handler.removeCallbacks(sendUpdatesToUI);
                handler.postDelayed(sendUpdatesToUI, 0);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private Runnable saveFileRunnable = new Runnable() {
        @Override
        public void run() {
            File file = new File(appDir, fileName);
            long length = file.length();
            MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(appDir + File.separator + fileName);
            String duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String dateNoFormat = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                InputStream is = new FileInputStream(file);
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                byte[] hashSum = digest.digest();
                BigInteger bigInt = new BigInteger(1, hashSum);
                String hashValue = bigInt.toString(16);
                // Fill to 32 chars
                hashValue = String.format("%32s", hashValue).replace(' ', '0');
                String formattedDate;
                if (dateNoFormat != null) {
                    SimpleDateFormat readDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.getDefault());
                    readDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                    Date inputDate = readDateFormat.parse(dateNoFormat);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
                    formattedDate = dateFormat.format(inputDate);
                } else {
                    formattedDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault()).format(dateNow);
                }
                RecordingsDbHelper dbHelper = new RecordingsDbHelper(getApplicationContext());
                dbHelper.insert(fileName, length, Integer.parseInt(duration), formattedDate, hashValue);
                Intent broadcast = new Intent(BROADCAST_FINISH_RECORDING);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcast);
                stopSelf();
                mPref.put(SharedPreferenceManager.Key.RECORD_STATUS_KEY,RecordFragment.STATUS_STOPPED);
                mPref.put(RecordingsFragment.KEY_IS_LATEST_LOADED,false);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    };

    private void createNotification(int status) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel serviceChannel = new NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    getResources().getString(R.string.service_channel),
                    NotificationManager.IMPORTANCE_LOW
            );
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
        Intent notificationIntent = new Intent(RecorderService.this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(RecorderService.this, 0, notificationIntent, 0);

        // Stop button
        Intent stopIntent = new Intent(RecorderService.this, RecorderService.class);
        stopIntent.setAction(RecordingPlaybackService.ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                RecorderService.this,
                0,
                stopIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(RecorderService.this, SERVICE_CHANNEL_ID)
                        .setContentTitle(getText(R.string.notification_title_recording))
                        .setContentText(getText(R.string.notification_text_recording))
                        .setSmallIcon(R.drawable.ic_mic)
                        .setContentIntent(pendingIntent);

        // Pause/resume button
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (status == RecordFragment.STATUS_RECORDING) {
                Intent pauseIntent = new Intent(this, RecorderService.class);
                pauseIntent.setAction(ACTION_PAUSE_RECORDING);
                PendingIntent pausePendingIntent = PendingIntent.getService(
                        RecorderService.this,
                        0,
                        pauseIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
                builder.addAction(R.drawable.ic_pause_white,getResources().getString(R.string.pause),pausePendingIntent);
            }
            else if(status == RecordFragment.STATUS_PAUSED){
                Intent resumeIntent = new Intent(this, RecorderService.class);
                resumeIntent.setAction(ACTION_RESUME_RECORDING);
                PendingIntent resumePendingIntent = PendingIntent.getService(
                        RecorderService.this,
                        0,
                        resumeIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
                builder.addAction(R.drawable.ic_record,getResources().getString(R.string.resume),resumePendingIntent);
            }
        }
        builder.addAction(R.drawable.ic_stop, getResources().getString(R.string.stop), stopPendingIntent);
        Notification notification = builder.build();
        startForeground(1, notification);

    }
}
