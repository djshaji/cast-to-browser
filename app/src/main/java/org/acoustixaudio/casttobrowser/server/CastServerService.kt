package org.acoustixaudio.casttobrowser.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CastServerService : Service() {
    private var ktorServer: KtorServer? = null

    override fun onCreate() {
        super.onCreate()
        ktorServer = KtorServer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ip = NetworkUtils.getLocalIpAddress()
        ServerState.setServerIp(ip)
        ServerState.setServerError(null)
        
        val notification = createNotification(ip, 8080)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        tryStartServer(8080)
        
        return START_STICKY
    }

    private fun tryStartServer(startPort: Int) {
        var currentPort = startPort
        val maxTries = 10
        var success = false
        
        for (i in 0 until maxTries) {
            try {
                ktorServer?.start(currentPort)
                ServerState.setServerPort(currentPort)
                updateNotification(NetworkUtils.getLocalIpAddress(), currentPort)
                success = true
                break
            } catch (e: Exception) {
                if (e.message?.contains("Address already in use", ignoreCase = true) == true || 
                    e is java.net.BindException) {
                    currentPort++
                } else {
                    ServerState.setServerError(e.message ?: "Failed to start server")
                    break
                }
            }
        }
        
        if (!success) {
            ServerState.setServerError("Could not find an available port to start the server.")
        }
    }

    private fun updateNotification(ip: String?, port: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(ip, port))
    }

    override fun onDestroy() {
        ktorServer?.stop()
        ServerState.setServerIp(null)
        ServerState.setServerError(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(ip: String?, port: Int): Notification {
        val channelId = "cast_server_channel"
        val channelName = "Cast Server"
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        )

        val contentText = if (ip != null) "Connect to http://$ip:$port" else "Listening on port $port"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Cast Server Running")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
