package com.example.musecontainer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** Prototype keep-alive only, deliberately minimal (performance requirement):
 *  one notification, no loop, no polling, no timer, no clone preloading, no
 *  ads SDK, no heavy library.
 *  v3 correction: the v2 text wrongly said "engine not integrated" and
 *  "no virtual clone is running yet". The engine IS integrated in v2/v3.
 *  This service still does NOT, by itself, keep a virtual clone alive
 *  or deliver its notifications: guest notifications are posted through
 *  the engine's notification proxy, and their delay must be measured
 *  per clone on a real phone. This service only keeps a host
 *  foreground service alive so Android is less likely to kill the host
 *  process; it does not repost, label, or poll for clone notifications,
 *  and it must never claim to do so. */
public class KeepAliveService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("keepalive", "Keep-alive", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(this, "keepalive")
            .setContentTitle("Muse Container — prototype keep-alive")
            .setContentText("Host foreground service only. Clone notifications come via the engine, not this service.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build();
        startForeground(1, n);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
}
