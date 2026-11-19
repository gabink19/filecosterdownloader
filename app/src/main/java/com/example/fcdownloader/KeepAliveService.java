package com.example.fcdownloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Layanan latar depan: menjaga CPU dan Wi-Fi tetap aktif walau layar mati,
 * dan menampilkan kemajuan di notifikasi. Dipakai selama memindai/mencari video.
 */
public class KeepAliveService extends Service {
    static final String CHANNEL = "fc_run";
    static final String ACTION_STOP = "com.example.fcdownloader.STOP";
    static final int NOTIF_ID = 11;
    static final long MAX_HOLD_MS = 6L * 60 * 60 * 1000;

    /** Dipasang oleh MainActivity: dijalankan saat tombol Hentikan di notifikasi ditekan. */
    static volatile Runnable stopHook;
    static volatile boolean running;

    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;

    static void update(Context c, String text, int pct) {
        Intent i = new Intent(c, KeepAliveService.class);
        i.putExtra("text", text);
        i.putExtra("pct", pct);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    static void halt(Context c) {
        c.stopService(new Intent(c, KeepAliveService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            final Runnable hook = stopHook;
            if (hook != null) new Handler(Looper.getMainLooper()).post(hook);
            stopSelf();
            return START_NOT_STICKY;
        }
        String text = intent == null ? "Berjalan" : intent.getStringExtra("text");
        int pct = intent == null ? -1 : intent.getIntExtra("pct", -1);

        Notification n = build(text == null ? "Berjalan" : text, pct);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, n);
        }
        if (!running) {
            running = true;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fcdownloader:run");
            wake.setReferenceCounted(false);
            wake.acquire(MAX_HOLD_MS);
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "fcdownloader:wifi");
                wifi.setReferenceCounted(false);
                wifi.acquire();
            }
        }
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Notification build(String text, int pct) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Proses unduhan",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Menjaga pemindaian dan pencarian video tetap berjalan saat layar mati");
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Intent stop = new Intent(this, KeepAliveService.class).setAction(ACTION_STOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, flags);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, flags);
        b.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Filecoster Downloader")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                        "Hentikan", stopPi).build());
        if (pct >= 0) b.setProgress(100, Math.min(100, pct), false);
        else b.setProgress(0, 0, true);
        return b.build();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (wake != null && wake.isHeld()) wake.release();
        if (wifi != null && wifi.isHeld()) wifi.release();
        super.onDestroy();
    }
}
