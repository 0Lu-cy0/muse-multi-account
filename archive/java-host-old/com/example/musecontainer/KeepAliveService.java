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
 *  ads SDK, no heavy library. It proves foreground-service + notification
 *  plumbing. It does NOT keep any virtual Muse clone alive — no engine is
 *  integrated yet, and the notification text says so explicitly. Real
 *  notification delivery for active clones must use push (FCM forwarding
 *  via the engine), never a host polling loop. */
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
            .setContentText("No virtual clone is running yet (engine not integrated).")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build();
        startForeground(1, n);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
}
