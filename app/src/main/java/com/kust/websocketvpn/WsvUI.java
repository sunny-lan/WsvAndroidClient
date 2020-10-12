package com.kust.websocketvpn;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class WsvUI extends AppCompatActivity {

    private static final String TAG = "WsvUI";

    private Spinner server;
    private Button startBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.start_btn);
        server = findViewById(R.id.server);

        startBtn.setOnClickListener(this::onStartClick);
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

    private void updateStateFromThread(){
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
                startBtn.setEnabled(true);
                if (api.isRunning()) {
                    startBtn.setText(R.string.stop);
                } else {
                    startBtn.setText(R.string.start);
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
            getSharedPreferences("defaultSettings", MODE_PRIVATE).edit()
                    .putString(WsvService.STR_SERVER, server.getSelectedItem().toString())
                    .apply();
            startService(getServiceIntent()
                    .setAction(WsvService.ACTION_CONNECT)
            );
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private Intent getServiceIntent() {
        return new Intent(this, WsvService.class);
    }
}
