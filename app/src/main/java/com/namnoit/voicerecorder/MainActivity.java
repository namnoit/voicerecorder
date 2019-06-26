package com.namnoit.voicerecorder;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;

import android.util.Log;
import android.view.MenuItem;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.namnoit.voicerecorder.ui.main.RecordFragment;
import com.namnoit.voicerecorder.ui.main.RecordingsFragment;
import com.namnoit.voicerecorder.ui.main.PagerAdapter;

import androidx.drawerlayout.widget.DrawerLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import android.view.Menu;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    String[] appPermissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int PERMISSION_REQUEST_CODE = 100;
    public static final String PREF_NAME = "config";
    public static final String KEY_QUALITY = "quality";
    public static final int QUALITY_GOOD = 0;
    public static final int QUALITY_SMALL = 1;
    private SharedPreferences pref;
    private int qualityChoosed;

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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        qualityChoosed = pref.getInt(KEY_QUALITY,QUALITY_GOOD);

        checkPermissions();

        PagerAdapter sectionsPagerAdapter = new PagerAdapter(getSupportFragmentManager());
        sectionsPagerAdapter.addFragment(new RecordFragment(),getResources().getString(R.string.tab_record));
        sectionsPagerAdapter.addFragment(new RecordingsFragment(),getResources().getString(R.string.tab_recordings));
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void checkPermissions() {
        List<String> listPermissionNeeded = new ArrayList<>();
        for (String permision : appPermissions) {
            if (ContextCompat.checkSelfPermission(this, permision) != PackageManager.PERMISSION_GRANTED)
                listPermissionNeeded.add(permision);
        }
        if (!listPermissionNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionNeeded.toArray(new String[listPermissionNeeded.size()]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_quality) {

            final CharSequence[] items = {"Good (AAC)","Small size (3GP)"};
            AlertDialog qualityDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(getResources().getString(R.string.text_quality_tiltle))
                    .setSingleChoiceItems(items, qualityChoosed, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            // Your code
                            qualityChoosed = item;
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences.Editor editor = pref.edit();
                            editor.putInt(KEY_QUALITY,qualityChoosed);
                            editor.apply();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            qualityChoosed = pref.getInt(KEY_QUALITY,QUALITY_GOOD);
                        }
                    })
                    .create();
            qualityDialog.show();

        } else if (id == R.id.nav_rate) {

        } else if (id == R.id.nav_view) {

        }

        item.setCheckable(false);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

    }
}
