package com.smsauto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SyncService : Service() {

    companion object {
        const val CHANNEL_ID = "sync_service_channel"
        const val NOTIFICATION_ID = 1002
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var syncJob: Job? = null
    private var isRunning = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("同步服务运行中", "等待同步任务..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPeriodicSync()
        return START_STICKY
    }

    private fun startPeriodicSync() {
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning && isActive) {
                try {
                    syncTasks()
                } catch (e: Exception) {
                    // 同步失败，继续重试
                }
                // 每 30 秒同步一次
                delay(30_000)
            }
        }
    }

    private suspend fun syncTasks() {
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        if (serverUrl.isEmpty()) return

        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val url = "$serverUrl/api/tasks?deviceId=$deviceId"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val tasks = JSONArray(body)

                if (tasks.length() > 0) {
                    updateNotification("发现新任务", "共 ${tasks.length()} 个任务待执行")

                    // 启动短信发送服务
                    val intent = Intent(this@SyncService, SmsService::class.java).apply {
                        putExtra("tasks_json", body)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }

                    (application as? SmsAutoApp)?.mainActivity?.appendLog("🔄 同步到 ${tasks.length()} 个任务")
                } else {
                    updateNotification("同步服务运行中", "无待执行任务")
                }
            }
        } catch (e: Exception) {
            updateNotification("同步失败", e.message ?: "未知错误")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "任务同步服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "自动同步短信发送任务"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        syncJob?.cancel()
    }
}
