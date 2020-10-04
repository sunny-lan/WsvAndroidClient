package com.kust.websocketvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import wsvmobile.Wsvmobile;

public class WsvService extends VpnService {
    private static final String TAG = "WsvService";

    public static final String ACTION_CONNECT = "com.kust.websocketvpn2.START";
    private static final int VPN_STATUS_NOTIFICATION = 1;

    public static final String STR_SERVER = "com.kust.WsVPN.START.SERVER";
    public static final String STR_TUNADDR = "com.kust.WsVPN.START.TUNADDR";
    public static final String STR_TUNADDR6 = "com.kust.WsVPN.START.TUNADDR6";
    public static final String STR_DNSADDR = "com.kust.WsVPN.START.DNSADDR";
    public static final String BOOL_IPV6 = "com.kust.WsVPN.START.IPV6";

    //status variables
    private AtomicBoolean vpnRunning = new AtomicBoolean(false);
    private AtomicBoolean stopping = new AtomicBoolean(false);
    private AtomicBoolean starting = new AtomicBoolean(false);

    private volatile int currentTunFD;

    private Thread goThread;


    private String wsUrl;
    private String tunAddr, tunAddrIpv6;
    private String dnsAddr;
    private boolean allowIpv6;

    public interface Listener{
        void onStateChanged();
    }
    private void onStateChanged(){
        for(Listener l:api.listeners){
            l.onStateChanged();
        }
    }
    public class API extends Binder {
        public boolean isRunning() {
            return vpnRunning.get();
        }

        public void stop(){
            Log.i(TAG, "Bound client requested to destroy service");
            stopStage1();
        }

        private ArrayList<Listener> listeners=new ArrayList<>();
        public void onStateChanged(Listener listener){
            listeners.add(listener);
        }

        public boolean isStopping(){
            return stopping.get();
        }

        public boolean isStarting(){
            return starting.get();
        }
    }

    private final API api = new API();

    @Override
    public IBinder onBind(Intent intent) {
        return api;
    }

    private void loadProxySettings(SharedPreferences prefs) {
        wsUrl = prefs.getString(STR_SERVER, null);
        if (wsUrl == null)
            throw new IllegalArgumentException("Wsv server not set");
        tunAddr = prefs.getString(STR_TUNADDR, "26.26.26.1");
        tunAddrIpv6 = prefs.getString(STR_TUNADDR6, "fdfe:dcba:9876::1");
        dnsAddr = prefs.getString(STR_DNSADDR, "8.8.8.8");
        allowIpv6 = prefs.getBoolean(BOOL_IPV6, false);//TODO
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_CONNECT.equals(intent.getAction())) {
            if (vpnRunning.get())
                return START_STICKY;

            //load proxy settings
            loadProxySettings(getSharedPreferences("defaultSettings", MODE_PRIVATE));

            startVPN();

            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void startVPN() {
        Log.i(TAG, "start vpn requested");
        starting.set(true);
        onStateChanged();

        Builder builder = new Builder();

        builder.addAddress(tunAddr, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dnsAddr)
                .setBlocking(true);

        //prevent VPN connections from looping to itself
        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("this should never happen");
        }

        if (allowIpv6) {
            builder.addAddress(tunAddrIpv6, 126)
                    .addRoute("::", 0);
        }


        ParcelFileDescriptor localTunnel = builder.establish();
        if (localTunnel == null) {
            throw new NullPointerException("Unable to establish localTunnel");
        }
        currentTunFD = localTunnel.detachFd();

        Log.d(TAG, "VPN on TUN FD=" + currentTunFD);
        vpnRunning.set(true);

        goThread = new Thread(this::goThread, "WsVPNThread");
        goThread.start();
    }

    private void toast(int id, int len){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(getApplicationContext(), id,len).show());
    }

    private void goThread() {
        Log.d(TAG, "Go thread begin");
        updateForegroundNotification(getString(R.string.vpnRunning));
        starting.set(false);
        onStateChanged();

        try {
            Wsvmobile.begin(currentTunFD, wsUrl);
            Log.i(TAG, "go code exited without error");
            toast(R.string.serviceStoppedNormally, Toast.LENGTH_SHORT);
        } catch (Exception e) {
            Log.e(TAG, "Error from Go", e);
            toast( R.string.serviceStoppedUnexpectedly, Toast.LENGTH_LONG);
        }

        stopStage2();
    }

    // stopStage1 requests go thread to stop
    private void stopStage1(){
        if (!vpnRunning.get())
            throw new RuntimeException("Tried to destroy non-serviceRunning program");

        updateForegroundNotification(getString(R.string.vpnStopping));
        stopping.set(true);
        onStateChanged();

        if (goThread.isAlive()) {
            try {
                Log.i(TAG, "trying to kill go");
                Wsvmobile.close();
            } catch (InterruptedException e) {
                throw new RuntimeException("Service interrupted before child thread could be " +
                        "gracefully killed", e);
            } catch (Exception e) {
                Log.e(TAG, "go thread was not running in the first place", e);
            }
        }
    }

    //stops service completely
    //called only by go thread upon completion
    private void stopStage2() {
        Log.d(TAG, "completely shutting down");
        goThread = null;

        try {
            Wsvmobile.closeFD(currentTunFD);
        } catch (Exception e) {
            throw new RuntimeException("Unable to close FD", e);
        }

        vpnRunning.set(false);


        stopForeground(true);

        Log.d(TAG, "success stopping service");

        stopping.set(false);
        onStateChanged();
    }

    private void updateForegroundNotification(final String message) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        String NOTIFICATION_CHANNEL_ID="WsVpn.Status";
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(VPN_STATUS_NOTIFICATION, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentText(message)
                .build());
    }
}
