package com.namnoit.voicerecorder;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.api.services.drive.DriveScopes;
import com.namnoit.voicerecorder.service.RecorderService;
import com.namnoit.voicerecorder.service.RecordingPlaybackService;
import com.namnoit.voicerecorder.ui.main.PagerAdapter;
import com.namnoit.voicerecorder.ui.main.RecordFragment;
import com.namnoit.voicerecorder.ui.main.RecordingsFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private String[] appPermissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int SIGN_IN_REQUEST_CODE = 101;
    private static int BACKUP_SIGN_IN_REQUEST_CODE = 102;
    private static int RESTORE_SIGN_IN_REQUEST_CODE = 103;
    private SharedPreferenceManager mPref;
    public static final int QUALITY_GOOD = 0;
    public static final int QUALITY_SMALL = 1;
    private static final String DIR = Environment.getExternalStorageDirectory().getAbsolutePath();
    public static final String APP_FOLDER = "Ez Voice Recorder";
    public static final String APP_DIR = DIR + File.separator + APP_FOLDER;
    private int mQualityChosen;
    private TextView mNavigationEmailText, mNavigationProfileNameText;
    private ImageView mProfileImage;
    private GoogleSignInClient mGoogleSignInClient;


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        for (int grantResult: grantResults){
            if (grantResult < 0){
                // There are some permissions was not granted
                // Close app
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("Permission request");
                alertDialog.setMessage("You must grant all permission to use this app");
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Exit",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                });
                alertDialog.show();
            }
        }
        // Create application folder
        File folder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            folder = new File(Objects.requireNonNull(getApplicationContext().getExternalFilesDir(null)).getAbsolutePath(),APP_FOLDER);
        else
            folder = new File(APP_DIR);
        if (!folder.exists() && !folder.mkdir()) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.create_directory_failed),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
            if (requestCode == BACKUP_SIGN_IN_REQUEST_CODE || requestCode == RESTORE_SIGN_IN_REQUEST_CODE) {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    updateUI(account);
                    sync(requestCode == BACKUP_SIGN_IN_REQUEST_CODE);
                } catch (ApiException e) {
                    updateUI(null);
                }
            }
            else if (requestCode == SIGN_IN_REQUEST_CODE){
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    updateUI(account);
                } catch (ApiException e) {
                    updateUI(null);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        checkPermissions();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA),new Scope(DriveScopes.DRIVE_FILE))
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        mPref = SharedPreferenceManager.getInstance(getApplicationContext());
        mQualityChosen = mPref.getInt(SharedPreferenceManager.Key.QUALITY_KEY,QUALITY_GOOD);

        PagerAdapter sectionsPagerAdapter = new PagerAdapter(getSupportFragmentManager());
        sectionsPagerAdapter.addFragment(new RecordFragment(),getResources().getString(R.string.tab_record));
        sectionsPagerAdapter.addFragment(new RecordingsFragment(),getResources().getString(R.string.tab_recordings));
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);
        mNavigationEmailText = headerView.findViewById(R.id.nav_email);
        mNavigationProfileNameText = headerView.findViewById(R.id.nav_profile_name);
        mProfileImage = headerView.findViewById(R.id.avatar);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        updateUI(account);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);

        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> listPermissionNeeded = new ArrayList<>();
            for (String permission : appPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                    listPermissionNeeded.add(permission);
            }
            if (!listPermissionNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        listPermissionNeeded.toArray(new String[0]),
                        PERMISSION_REQUEST_CODE);
            }
        }
        else {
            File folder = new File(APP_DIR);
            if (!folder.exists() && !folder.mkdir())
                Toast.makeText(getApplicationContext(),
                        getResources().getString(R.string.create_directory_failed),
                        Toast.LENGTH_SHORT).show();
        }
    }


    private void updateUI(GoogleSignInAccount account){
        if (account != null){
            mNavigationProfileNameText.setText(account.getDisplayName());
            mNavigationEmailText.setText(account.getEmail());
            Glide.with(this).load(account.getPhotoUrl()).apply(RequestOptions.circleCropTransform()).into(mProfileImage);
            sync(false);
        }
        else{
            mNavigationProfileNameText.setText(getResources().getString(R.string.app_name));
            mNavigationEmailText.setText(getResources().getString(R.string.nav_header_subtitle));
            mProfileImage.setImageResource(R.mipmap.ic_launcher_round);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (isServiceRunning(RecordingPlaybackService.class)
                    || isServiceRunning(RecorderService.class))
                moveTaskToBack(true);
            else super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (id == R.id.nav_quality) {
            drawer.closeDrawer(GravityCompat.START,false);
            final CharSequence[] items = {getResources().getString(R.string.quality_good),getResources().getString(R.string.quality_small)};
            AlertDialog qualityDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(getResources().getString(R.string.text_quality_title))
                    .setSingleChoiceItems(items, mQualityChosen, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            mQualityChosen = item;
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mPref.put(SharedPreferenceManager.Key.QUALITY_KEY, mQualityChosen);
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            mQualityChosen = mPref.getInt(SharedPreferenceManager.Key.QUALITY_KEY,QUALITY_GOOD);
                        }
                    })
                    .create();
            qualityDialog.show();

        }
        else if (id == R.id.nav_account) {
            drawer.closeDrawer(GravityCompat.START,false);
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
            LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
            View convertView = inflater.inflate(R.layout.dialog_account, drawer,false);
            TextView textAccountName = convertView.findViewById(R.id.textAccountName);
            TextView textAccountEmail = convertView.findViewById(R.id.textAccountEmail);
            ImageView profilePicture = convertView.findViewById(R.id.profilePicture);
            if (account == null){
                textAccountName.setText(R.string.not_signed_in);
                textAccountEmail.setText("");
                profilePicture.setVisibility(View.GONE);
            }
            else{
                textAccountName.setText(account.getDisplayName());
                textAccountEmail.setText(account.getEmail());
                profilePicture.setVisibility(View.VISIBLE);
                Glide.with(this).load(account.getPhotoUrl()).apply(RequestOptions.circleCropTransform()).into(profilePicture);
            }
            final AlertDialog.Builder accountDialogBuilder = dialogBuilder.setView(convertView)
                    .setTitle(R.string.account)
                    .setNeutralButton(account==null?R.string.sign_in:R.string.switch_account, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (isInternetAvailable()){
                                mGoogleSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                                        startActivityForResult(signInIntent, SIGN_IN_REQUEST_CODE);
                                    }
                                });
                            }
                        }
                    })
                    .setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    });
            if (account != null){
                accountDialogBuilder.setNegativeButton(R.string.sign_out, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mGoogleSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                updateUI(null);
                                Intent broadcast = new Intent(RecordingsFragment.BROADCAST_SIGNED_OUT);
                                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(broadcast);
                            }
                        });
                    }
                });
            }
            accountDialogBuilder.create().show();
        }
        else if (id == R.id.nav_backup){
            sync(true);
        }
        else if (id == R.id.nav_sync) {
            sync(false);
        }
        else if (id == R.id.nav_rate) {
            Uri uri = Uri.parse("market://details?id=" + getPackageName());
            Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(goToMarket);
        }
        else if (id == R.id.nav_feed_back) {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getResources().getString(R.string.my_email)});
            startActivity(intent);
        }
        else if (id == R.id.nav_version) {
            drawer.closeDrawer(GravityCompat.START,false);
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
            LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
            View convertView = inflater.inflate(R.layout.dialog_about, drawer,false);
            dialogBuilder.setView(convertView)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    })
                    .create().show();
        }

        item.setCheckable(false);

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void sync(final boolean backup) {
        if (isInternetAvailable()) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account == null) {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, backup ? BACKUP_SIGN_IN_REQUEST_CODE : RESTORE_SIGN_IN_REQUEST_CODE);
            }
            else {
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent broadcast = new Intent(backup?RecordingsFragment.BROADCAST_BACKUP_REQUEST:RecordingsFragment.BROADCAST_SYNC_REQUEST);
                        LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(broadcast);
                    }
                },200);
            }
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean internet = cm != null && cm.getActiveNetworkInfo() != null;
        if (!internet) {
            View view = findViewById(R.id.drawer_layout);
            Snackbar.make(view, R.string.connection_failed, Snackbar.LENGTH_LONG).show();
        }
        return internet;
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
