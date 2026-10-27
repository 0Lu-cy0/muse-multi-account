package com.example.musecontainer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Prototype keep-alive service. In v1 it only proves the foreground-service
 *  + notification plumbing and the battery-exemption request flow. It does
 *  NOT keep any virtual Muse clone alive, because no engine is integrated
 *  yet — the notification text says so explicitly. */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("keepalive", "Keep-alive", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, "keepalive")
            .setContentTitle("Muse Container — prototype keep-alive")
            .setContentText("No virtual clone is running yet (engine not integrated).")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
