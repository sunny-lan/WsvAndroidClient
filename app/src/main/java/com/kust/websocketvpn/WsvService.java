package com.kust.websocketvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import wsvmobile.Wsvmobile;

public class WsvService extends VpnService {
    private static final String TAG = "WsvService";

    public static final String ACTION_CONNECT = "com.kust.websocketvpn2.START";
    public static final String NOTIFICATION_CHANNEL_ID = "WsVpn";
    private static final int VPN_STATUS_NOTIFICATION = 1;

    public static final String STR_SERVER = "com.kust.websocketvpn2.START.SERVER";
    public static final String STR_TUNADDR = "com.kust.websocketvpn2.START.TUNADDR";
    public static final String STR_TUNADDR6 = "com.kust.websocketvpn2.START.TUNADDR6";
    public static final String STR_DNSADDR = "com.kust.websocketvpn2.START.DNSADDR";
    public static final String BOOL_IPV6 = "com.kust.websocketvpn2.START.IPV6";
    private static final String INT_KILLTIMEOUT = "com.kust.websocketvpn2.START.KILL_TIMEOUT";

    //status variables
    private AtomicBoolean vpnRunning = new AtomicBoolean(false);

    public boolean getVpnRunning() {
        return vpnRunning.get();
    }

    private ParcelFileDescriptor localTunnel;
    private volatile int currentTunFD;

    private Thread goThread;


    private String wsUrl = "ws://192.168.2.111:80/";//"wss://sheltered-beach-23795.herokuapp.com/";
    private String TUN_ADDR = "26.26.26.1", TUN_ADDR_IPV6 = "fdfe:dcba:9876::1";
    private String DNS_ADDR = "8.8.8.8";
    private boolean ALLOW_IPV6 = true;

    private long KILL_TIMEOUT = 10000;

    public class LocalBinder extends Binder {
        WsvService getService() {
            return WsvService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
    }

    private void loadProxySettings(SharedPreferences prefs) {
        wsUrl = prefs.getString(STR_SERVER, null);
        if (wsUrl == null)
            throw new IllegalArgumentException("Wsv server not set");
        TUN_ADDR = prefs.getString(STR_TUNADDR, "26.26.26.1");
        TUN_ADDR_IPV6 = prefs.getString(STR_TUNADDR6, "fdfe:dcba:9876::1");
        DNS_ADDR = prefs.getString(STR_DNSADDR, "8.8.8.8");
        ALLOW_IPV6 = prefs.getBoolean(BOOL_IPV6, false);//TODO
        KILL_TIMEOUT = prefs.getInt(INT_KILLTIMEOUT, 5000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_CONNECT.equals(intent.getAction())) {
            if (vpnRunning.get())
                return START_STICKY;

            //load proxy settings
            loadProxySettings(getSharedPreferences("defaultSettings", MODE_PRIVATE));

            setupVPN();

            goThread = new Thread(this::goThread, "WsVPNThread");
            goThread.start();

            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void setupVPN() {
        Log.i(TAG, "setupVPN called");

        Builder builder = new Builder();

        builder.addAddress(TUN_ADDR, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(DNS_ADDR)
                .setBlocking(true);

        //prevent VPN connections from looping to itself
        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("this should never happen");
        }

        if (ALLOW_IPV6) {
            builder.addAddress(TUN_ADDR_IPV6, 126)
                    .addRoute("::", 0);
        }


        localTunnel = builder.establish();
        if (localTunnel == null) {
            throw new NullPointerException("Unable to establish localTunnel");
        }
        currentTunFD = localTunnel.detachFd();

        Log.d(TAG, "VPN on TUN FD=" + currentTunFD);
        vpnRunning.set(true);
        updateForegroundNotification("VPN on");

    }

    private void goThread() {
        Log.d(TAG, "Thread begin");

        try {
            Wsvmobile.begin(currentTunFD, wsUrl);
            Log.i(TAG, "go code exited without error");
        } catch (Exception e) {
            Log.e(TAG, "Error from Go", e);
        }
    }

    private void stopBlocking(){
        if (!vpnRunning.get())
            throw new RuntimeException("Tried to destroy non-serviceRunning program");

        if (goThread.isAlive()) {
            try {
                Log.i(TAG, "trying to kill go");
                Wsvmobile.close();
                goThread.join();//TODO timeout not effective
            } catch (InterruptedException e) {
                throw new RuntimeException("Service interrupted before child thread could be " +
                        "gracefully killed", e);
            } catch (Exception e) {
                Log.e(TAG, "go thread was not running in the first place", e);
            }
        }

        if (goThread.isAlive()) {
            Log.w(TAG, "Go thread still running, force interrupt");
            goThread.interrupt();
        }
        goThread = null;

        vpnRunning.set(false);

        Log.d(TAG, "success stopping service");

        stopForeground(true);
        stopSelf();
    }

    public void stop() {
        Log.i(TAG, "requested to destroy service");

        if (!vpnRunning.get())
            throw new RuntimeException("Tried to destroy non-serviceRunning program");

        new Thread(this::stopBlocking).start();
    }

    @Override
    public void onRevoke() {
        stop();
    }

    private void updateForegroundNotification(final String message) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(VPN_STATUS_NOTIFICATION, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentText(message)
                .build());
    }
}
