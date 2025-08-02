package com.example.smstracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.util.Timer;

public class SimStatusService extends Service {
    private static final String TAG = "SimStatusService";
    private SimStatusMonitor simStatusMonitor;
    private final Timer timer = new Timer(); // Although unused in this version, kept for potential future use

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "sim_status_channel";

    public static void startService(Context context) {
        Intent intent = new Intent(context, SimStatusService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopService(Context context) {
        context.stopService(new Intent(context, SimStatusService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SimStatus Service created");

        // Create notification channel for Android 8.0 (Oreo) and higher
        createNotificationChannel();

        // Start as a foreground service to ensure it's not killed by the system
        startForeground(NOTIFICATION_ID, createNotification());

        // Initialize the SIM status monitor
        simStatusMonitor = new SimStatusMonitor(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "SimStatus Service started");

        // Start monitoring for SIM state changes
        try {
            simStatusMonitor.startMonitoring();
        } catch (Exception e) {
            Log.e(TAG, "Error starting SIM monitoring: " + e.getMessage(), e);
        }

        // If the service is killed, it will be automatically restarted
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "SimStatus Service stopped");
        if (simStatusMonitor != null) {
            simStatusMonitor.stopMonitoring();
        }
        timer.cancel();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        // Notification channels are only available on Android 8.0 (API 26) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "SIM Status Service";
            String description = "Monitors SIM status in the background";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        // Use a standard launcher icon that is guaranteed to exist
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SIM Status Monitor")
                .setContentText("Monitoring SIM status in background")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}