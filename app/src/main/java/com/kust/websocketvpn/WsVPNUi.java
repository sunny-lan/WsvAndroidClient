package com.kust.websocketvpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class WsVPNUi extends AppCompatActivity {
    boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startBtn = findViewById(R.id.start_btn);

        startBtn.setOnClickListener(v -> {
            Intent intent = VpnService.prepare(WsVPNUi.this);
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, RESULT_OK, null);
            }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            running = !running;

            if (running) {
                startService(getServiceIntent().setAction(WsVpnService2.ACTION_CONNECT));
            } else {

                startService(getServiceIntent().setAction(WsVpnService2.ACTION_DISCONNECT));
            }
        }
    }

    private Intent getServiceIntent() {
        return new Intent(this, WsVpnService2.class);
    }
}
