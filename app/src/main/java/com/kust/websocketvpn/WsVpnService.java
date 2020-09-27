package com.kust.websocketvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.CountDownLatch;


public class WsVpnService extends VpnService implements Runnable {
    private static final String TAG = "SocksVpnService";
    private static final int MAX_PACKET_SIZE = 4096;
    private static final int CHUNK_SIZE = 1024;
    private static final long IDLE_INTERVAL_MS = 100;
    private boolean running = false;
    public static final String ACTION_CONNECT = "com.kust.websocketvpn.START";
    public static final String ACTION_DISCONNECT = "com.kust.websocketvpn.STOP";
    private Thread connectingThread;
    private PendingIntent mConfigureIntent;

    @Override
    public void onCreate() {

        // Create the intent to "configure" the connection (just start ToyVpnClient).
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, WsvUI.class),
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

    FileOutputStream out;

    public void runInternal() throws IOException, URISyntaxException {
        Log.d(TAG, "Thread begin");
        running = true;
        String wsUrl = "ws://192.168.2.111:80";
        int wsTimeout = 1000;
        VpnServerConnection ws = new NvWsConnection(new URI(wsUrl), wsTimeout);
        final CountDownLatch latch = new CountDownLatch(1);

        ws.setListener(new VpnServerConnection.Listener() {
            @Override
            public void read(byte[] data) {
                try {
//                    Log.d(TAG, Arrays.toString(data));
                    out.write(data);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write received bytes to local TUN", e);
                }
            }

            @Override
            public void onOpen() {
                latch.countDown();
            }

            @Override
            public void protect(Socket s) {
                if (!WsVpnService.this.protect(s)) {
                    throw new RuntimeException("Unable to protect socket");
                }
            }
        });

        ws.connect();
        try {
            latch.await();
        } catch (InterruptedException e) {
            running = false;
            return;
        }
        Log.d(TAG, "Connected");


        // Configure a new interface from our VpnService instance. This must be done
        // from inside a VpnService.
        VpnService.Builder builder = new VpnService.Builder();

        String tunAddress = "26.26.26.1", tunAddrV6 = "fdfe:dcba:9876::1";
        String dnsAddress = "8.8.8.8";
        builder.addAddress(tunAddress, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dnsAddress);

        boolean ipv6 = false;
        if (ipv6) {
            builder.addAddress(tunAddrV6, 126)
                    .addRoute("::", 0);
        }

        ParcelFileDescriptor localTunnel = builder.establish();
        if (localTunnel == null) {
            throw new NullPointerException("Unable to establish localTunnel");
        }

        // Packets to be sent are queued in this input stream.
        FileInputStream in = new FileInputStream(localTunnel.getFileDescriptor());
        // Packets received need to be written to this output stream.
        out = new FileOutputStream(localTunnel.getFileDescriptor());

        FileChannel c=in.getChannel();



        int sz = 256;
        ByteBuffer header = ByteBuffer.allocate(4);
        byte[] buffer = new byte[1024];
        ByteBuffer tt=ByteBuffer.allocate(1024);
        while (running) {
            c.read(tt);
            ws.send(tt.array());
        }
        Log.d(TAG, "disconnected (thread stopped)");
        ws.disconnect();
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
