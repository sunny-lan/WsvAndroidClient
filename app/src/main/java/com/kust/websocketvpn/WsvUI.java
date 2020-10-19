package com.kust.websocketvpn;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class WsvUI extends AppCompatActivity {

    private static final String TAG = "WsvUI";

    private Spinner profiles;
    private Button startBtn;
    private Button editProfile;

    private SharedPreferences globalPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.start_btn);
        startBtn.setOnClickListener(this::onStartClick);

        profiles = findViewById(R.id.profile_spinner);
        profiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editProfile.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                editProfile.setEnabled(false);
            }
        });


        Button addProfile = findViewById(R.id.add_profile);
        addProfile.setOnClickListener(this::onAddProfileClick);

        editProfile =findViewById(R.id.edit_profile);
        editProfile.setOnClickListener(this::onEditProfile);

        globalPrefs = getSharedPreferences(WsvUI.S.GLOBAL_SETTINGS, MODE_PRIVATE);
    }

    private WsvService.API api;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            WsvUI.this.api = ((WsvService.API) service);
            api.onStateChanged(WsvUI.this::updateStateFromThread);

            updateState();
            Toast.makeText(WsvUI.this, R.string.serviceBound,
                    Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            api = null;
            updateState();
            Toast.makeText(WsvUI.this, R.string.serviceUnbound,
                    Toast.LENGTH_SHORT).show();
        }
    };
    private boolean serviceBound;

    private void updateStateFromThread() {
        runOnUiThread(this::updateState);
    }

    //updates ui state based on server state
    private void updateState() {
        //not bound yet
        if (api == null) {
            //disable all controls
            startBtn.setEnabled(false);
            startBtn.setText(R.string.waitingVpn);
        } else {
            if (api.isStarting()) {
                startBtn.setText(R.string.starting);
                startBtn.setEnabled(false);
            } else if (api.isStopping()) {
                startBtn.setText(R.string.stopping);
                startBtn.setEnabled(false);
            } else {
                if (api.isRunning()) {
                    startBtn.setEnabled(true);
                    startBtn.setText(R.string.stop);
                } else {
                    if(profiles.getSelectedItem()==null){
                        startBtn.setText(R.string.selectprofiletobegin);
                        startBtn.setEnabled(false);
                    }else{
                        startBtn.setText(R.string.start);
                        startBtn.setEnabled(true);
                    }
                }
            }
        }
    }

    void doBindService() {
        Log.d(TAG, "attempt to bind to WsvService");
        if (bindService(new Intent(this, WsvService.class),
                mConnection, Context.BIND_AUTO_CREATE)) {
            serviceBound = true;
        } else {
            Log.e(TAG, "Error: Bind WsvService failed");
            Toast.makeText(this, R.string.failedBind, Toast.LENGTH_LONG).show();
        }
    }

    void doUnbindService() {
        unbindService(mConnection);
        serviceBound = false;
        api = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateState();
        if (!serviceBound)
            doBindService();
        else
            updateState();

        //load list of profiles
        @NonNull Set<String> profileList = Objects.requireNonNull(globalPrefs.getStringSet(S.PROFILE_LIST, new HashSet<>()));
        Log.i(TAG,"load profile list: "+profileList.toString());
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new ArrayList<>(profileList));
        profiles.setAdapter(spinnerArrayAdapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (serviceBound)
            doUnbindService();
    }

    private void startService() {
        Intent intent = VpnService.prepare(WsvUI.this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    private void onStartClick(View view) {
        if (api == null)
            throw new RuntimeException("This should never happen");

        if (api.isRunning()) {
            Log.d(TAG, "try to tell service to stop");
            api.stop();
        } else {
            startService();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result == RESULT_OK) {
            Object item = profiles.getSelectedItem();
            if (item == null)
                item = S.DEFAULT_SETTINGS;
            globalPrefs.edit().putString(WsvService.S.SELECTED_PROFILE, item.toString()).apply();
            startService(getServiceIntent().setAction(WsvService.ACTION_CONNECT));
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private Intent getServiceIntent() {
        return new Intent(this, WsvService.class);
    }

    public static class S {
        public static final String PROFILE_LIST = "PROFILE_LIST";
        public static final String GLOBAL_SETTINGS = "globalSettings";
        public static final String DEFAULT_SETTINGS = "defaultSettings";

    }

    private void onEditProfile(View view) {
        Intent i = new Intent(this, VPNSettingsEditor.class);
        i.setAction(VPNSettingsEditor.I.EDIT);
        i.putExtra(VPNSettingsEditor.I.PROFILE_NAME, profiles.getSelectedItem().toString());
        startActivity(i);
    }

    private void onAddProfileClick(View v) {
        Intent i = new Intent(this, VPNSettingsEditor.class);
        i.setAction(VPNSettingsEditor.I.CREATE_NEW);
        startActivity(i);
    }
}
