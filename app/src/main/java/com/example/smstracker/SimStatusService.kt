package com.example.smstracker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Timer
import java.util.TimerTask

class SimStatusService : Service() {
    private val TAG = "SimStatusService"
    private lateinit var simStatusMonitor: SimStatusMonitor
    private val timer = Timer()
    private val checkInterval = 120000L // 2 minutes

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sim_status_channel"

        fun startService(context: Context) {
            val intent = Intent(context, SimStatusService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, SimStatusService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SimStatus Service created")

        // Create notification channel for Android 8+
        createNotificationChannel()

        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Initialize SIM status monitor
        simStatusMonitor = SimStatusMonitor(this)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SimStatus Service started")

        // Start monitoring
        try {
            simStatusMonitor.startMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SIM monitoring: ${e.message}", e)
        }

        // Return sticky so service restarts if killed
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "SimStatus Service stopped")
        simStatusMonitor.stopMonitoring()
        timer.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SIM Status Service"
            val descriptionText = "Monitors SIM status in background"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SIM Status Monitor")
            .setContentText("Monitoring SIM status in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure to add this icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}