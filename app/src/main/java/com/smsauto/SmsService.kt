package com.smsauto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SmsService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_service_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var serviceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tasksJson = intent?.getStringExtra("tasks_json")
        if (tasksJson.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 启动前台服务通知
        startForeground(NOTIFICATION_ID, createNotification("准备发送短信...", "任务已接收"))

        // 在后台协程中执行发送任务
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = JSONArray(tasksJson)
                for (i in 0 until tasks.length()) {
                    val task = tasks.getJSONObject(i)
                    sendTaskMessages(task)
                }
                updateNotification("所有任务完成", "短信发送完成")
                delay(3000)
                stopSelf()
            } catch (e: Exception) {
                updateNotification("发送异常", e.message ?: "未知错误")
                delay(5000)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun sendTaskMessages(taskJson: JSONObject) {
        val taskId = taskJson.getInt("id")
        val contacts = taskJson.getJSONArray("contacts")
        val message = taskJson.getString("message")
        val serverUrl = taskJson.getString("server_url")

        val total = contacts.length()
        var completed = 0
        var failed = 0

        updateNotification("准备发送", "共 $total 条短信")

        for (i in 0 until total) {
            val contact = contacts.getJSONObject(i)
            val phone = contact.getString("phone")
            val name = contact.optString("name", "")

            // 替换变量
            var finalMessage = message.replace("{姓名}", name)

            try {
                sendSingleSms(phone, finalMessage)
                completed++
                (application as? SmsAutoApp)?.mainActivity?.appendLog("✅ 已发送给 $name($phone)")
            } catch (e: Exception) {
                failed++
                (application as? SmsAutoApp)?.mainActivity?.appendLog("❌ 发送失败 $name($phone): ${e.message}")
            }

            val progress = "进度: ${i + 1}/$total，成功: $completed，失败: $failed"
            updateNotification("正在发送短信", progress)

            // 上报进度到服务器
            reportProgress(serverUrl, taskId, completed, failed, i + 1, total)

            // 每条短信间隔 2 秒，避免被运营商判定为垃圾短信
            if (i < total - 1) {
                delay(2000)
            }
        }

        // 上报最终结果
        reportComplete(serverUrl, taskId, completed, failed)
        (application as? SmsAutoApp)?.mainActivity?.appendLog("📊 任务 $taskId 完成：成功 $completed，失败 $failed")
    }

    private fun sendSingleSms(phone: String, message: String) {
        val smsManager = SmsManager.getDefault()

        // 如果短信内容超过 70 字，自动分割
        val parts = smsManager.divideMessage(message)

        if (parts.size > 1) {
            // 长短信需要分段发送
            val sentIntents = parts.map { null }
            val deliveryIntents = parts.map { null }
            smsManager.sendMultipartTextMessage(
                phone,
                null,
                parts,
                sentIntents,
                deliveryIntents
            )
        } else {
            smsManager.sendTextMessage(
                phone,
                null,
                message,
                null,
                null
            )
        }
    }

    private fun reportProgress(serverUrl: String, taskId: Int, completed: Int, failed: Int, current: Int, total: Int) {
        try {
            val json = JSONObject().apply {
                put("status", "running")
                put("completed", completed)
                put("failed", failed)
                put("progress", (current * 100 / total))
            }
            val body = RequestBody.create(
                "application/json".toMediaType(),
                json.toString()
            )
            val request = Request.Builder()
                .url("$serverUrl/api/tasks/$taskId/status")
                .put(body)
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            // 上报失败不影响发送
        }
    }

    private fun reportComplete(serverUrl: String, taskId: Int, completed: Int, failed: Int) {
        try {
            val status = if (failed == 0) "completed" else "completed_with_errors"
            val json = JSONObject().apply {
                put("status", status)
                put("completed", completed)
                put("failed", failed)
                put("progress", 100)
            }
            val body = RequestBody.create(
                "application/json".toMediaType(),
                json.toString()
            )
            val request = Request.Builder()
                .url("$serverUrl/api/tasks/$taskId/status")
                .put(body)
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            // 上报失败不影响发送
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "短信发送服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台发送短信，不占用前台"
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
            .setSmallIcon(android.R.drawable.ic_dialog_email)
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
        serviceJob?.cancel()
    }
}
