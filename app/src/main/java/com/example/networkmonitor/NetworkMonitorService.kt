package com.example.networkmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log

class NetworkMonitorService : Service() {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var telephonyManager: TelephonyManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val CHANNEL_ID = "NetworkMonitorChannel"
        const val ALERT_CHANNEL_ID = "NetworkAlertChannel"
        const val FOREGROUND_SERVICE_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createForegroundNotification("Monitoring 5G Network...")
        startForeground(FOREGROUND_SERVICE_ID, notification)

        startNetworkMonitoring()

        return START_STICKY
    }

    private fun startNetworkMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                checkNetworkType()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                sendAlertNotification("Network connection lost!")
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        
        // Check initial state
        checkNetworkType()
    }

    private fun checkNetworkType() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                telephonyManager.dataNetworkType
            } else {
                telephonyManager.networkType
            }
            
            Log.d("NetworkMonitor", "Current network type: $networkType")

            if (networkType != TelephonyManager.NETWORK_TYPE_NR) {
                // Not 5G
                sendAlertNotification("5G Network disconnected or dropped to lower generation!")
            } else {
                // It is 5G, we can update the persistent notification if needed
                updateForegroundNotification("Connected to 5G")
            }
        } else {
            sendAlertNotification("Permission to read phone state is missing.")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Network Monitor Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Network Alerts Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createForegroundNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("5G Monitor Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // using built-in icon for simplicity
            .build()
    }

    private fun updateForegroundNotification(content: String) {
        val notification = createForegroundNotification(content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(FOREGROUND_SERVICE_ID, notification)
    }

    private fun sendAlertNotification(message: String) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("5G Network Alert")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
