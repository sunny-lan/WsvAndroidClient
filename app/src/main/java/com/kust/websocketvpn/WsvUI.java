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

    private WsvService mBoundService;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((WsvService.LocalBinder) service).getService();

            // Tell the user about this for our demo.
            Toast.makeText(WsvUI.this, "VPN service detected",
                    Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            // Tell the user about this for our demo.
            Toast.makeText(WsvUI.this, "VPN service stopped",
                    Toast.LENGTH_SHORT).show();
        }
    };
    private boolean mShouldUnbind;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.start_btn);
        server = findViewById(R.id.server);

        startBtn.setOnClickListener(this::onStartClick);
    }

    void doBindService() {
        // Attempts to establish a connection with the service.  We use an
        // explicit class name because we want a specific service
        // implementation that we know will be running in our own process
        // (and thus won't be supporting component replacement by other
        // applications).
        if (bindService(new Intent(this, WsvService.class),
                mConnection, Context.BIND_AUTO_CREATE)) {
            mShouldUnbind = true;
        } else {
            Log.e(TAG, "Error: The requested service doesn't " +
                    "exist, or this client isn't allowed access to it.");
        }
    }

    private void onStartClick(View view) {

        if (!isMyServiceRunning()) {
            Intent intent = VpnService.prepare(WsvUI.this);
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, RESULT_OK, null);
            }
            doBindService();
        } else {
            Log.d(TAG, "try to tell service to stop");
            mBoundService.stop();
//            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(WsVpnService2.ACTION_DISCONNECT));
            startBtn.setText(getString(R.string.startService));
        }
    }


    private boolean isMyServiceRunning() {
        if(mBoundService==null)return false;
        return mBoundService.getVpnRunning();
    }

    @Override
    protected void onResume() {
        super.onResume();
        doBindService();
        if (isMyServiceRunning()) {
            startBtn.setText(getString(R.string.stopService));
        } else {

            startBtn.setText(getString(R.string.startService));
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            getSharedPreferences("defaultSettings", MODE_PRIVATE).edit()
                    .putString(WsvService.STR_SERVER, server.getSelectedItem().toString())
                    .apply();
            startService(getServiceIntent()
                    .setAction(WsvService.ACTION_CONNECT)
            );
            startBtn.setText(getString(R.string.stopService));

        }
    }

    void doUnbindService() {
        if (mShouldUnbind) {
            // Release information about the service's state.
            unbindService(mConnection);
            mShouldUnbind = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    private Intent getServiceIntent() {
        return new Intent(this, WsvService.class);
    }
}
