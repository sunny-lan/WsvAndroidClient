package com.kust.websocketvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.net.URISyntaxException;

import wsvmobile.Protector;
import wsvmobile.Wsvmobile;

public class WsVpnService2 extends VpnService implements Runnable {
    private static final String TAG = "WsVpnService2";
    private boolean running = false;
    public static final String ACTION_CONNECT = "com.kust.websocketvpn2.START";
    public static final String ACTION_DISCONNECT = "com.kust.websocketvpn2.STOP";
    private Thread connectingThread;
    private PendingIntent mConfigureIntent;

    @Override
    public void onCreate() {
        // Create the intent to "configure" the connection (just start ToyVpnClient).
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, WsVPNUi.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
            return START_NOT_STICKY;
        } else {
            connect();
            return START_STICKY;
        }
    }

    private void connect() {
        if (running) {
            throw new RuntimeException("");
        }
        connectingThread = new Thread(this, "WsVPNThread");
        connectingThread.start();
        updateForegroundNotification(R.string.connecting);
    }

    private void disconnect() {
        if (!running) {
            throw new RuntimeException("");
        }
        connectingThread.interrupt();
        stopForeground(true);
    }

    @Override
    public void onDestroy() {
        if (running)
            disconnect();
    }

    private void updateForegroundNotification(final int message) {
        final String NOTIFICATION_CHANNEL_ID = "WsVpn";
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(1, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build());
    }


    public void runInternal() throws IOException, URISyntaxException {
        Log.d(TAG, "Thread begin");
        running = true;
        String wsUrl = "ws://192.168.2.111:80/";//"wss://sheltered-beach-23795.herokuapp.com/";


        // Configure a new interface from our VpnService instance. This must be done
        // from inside a VpnService.
        Builder builder = new Builder();

        String tunAddress = "26.26.26.1", tunAddrV6 = "fdfe:dcba:9876::1";
        String dnsAddress = "8.8.8.8";
        builder.addAddress(tunAddress, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dnsAddress)
                .setBlocking(true);

        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("this should never happen");
        }

        boolean ipv6 = true;
        if (ipv6) {
            builder.addAddress(tunAddrV6, 126)
                    .addRoute("::", 0);
        }


        ParcelFileDescriptor localTunnel = builder.establish();
        if (localTunnel == null) {
            throw new NullPointerException("Unable to establish localTunnel");
        }


        long fd = localTunnel.detachFd();

        Log.i(TAG, "fd=" + fd);
        try {
            Wsvmobile.connectFd(fd, wsUrl,  null);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    @Override
    public void run() {
        try {
            runInternal();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);//TODO
        }
    }
}
