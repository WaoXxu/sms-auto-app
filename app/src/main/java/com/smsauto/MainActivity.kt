package com.smsauto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.smsauto.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val PERMISSIONS_REQUEST_CODE = 100
    private val SERVER_URL_KEY = "server_url"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as? SmsAutoApp)?.mainActivity = this
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()
        setupButtons()
        checkAndRequestPermissions()
        startSyncService()
    }

    private fun setupButtons() {
        // 保存服务器地址
        binding.btnSaveServer.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                getSharedPreferences("config", MODE_PRIVATE)
                    .edit()
                    .putString(SERVER_URL_KEY, url)
                    .apply()
                Toast.makeText(this, "服务器地址已保存", Toast.LENGTH_SHORT).show()
            }
        }

        // 手动同步任务
        binding.btnSync.setOnClickListener {
            syncTasks()
        }

        // 查看发送日志
        binding.btnViewLog.setOnClickListener {
            loadLogs()
        }

        // 申请忽略电池优化（保活）
        binding.btnIgnoreBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }
    }

    private fun loadSettings() {
        val savedUrl = getSharedPreferences("config", MODE_PRIVATE)
            .getString(SERVER_URL_KEY, "")
        if (!savedUrl.isNullOrEmpty()) {
            binding.etServerUrl.setText(savedUrl)
        } else {
            // 默认地址（同一WiFi下电脑的IP）
            binding.etServerUrl.setText("http://192.168.2.11:8080")
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun startSyncService() {
        val intent = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "同步服务已启动", Toast.LENGTH_SHORT).show()
    }

    private fun syncTasks() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请先输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val url = "$serverUrl/api/tasks?deviceId=$deviceId"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "[]"
                    val tasks = JSONArray(body)

                    withContext(Dispatchers.Main) {
                        binding.tvStatus.text = "同步成功，发现 ${tasks.length()} 个任务"
                        Toast.makeText(this@MainActivity, "同步成功", Toast.LENGTH_SHORT).show()
                    }

                    // 启动发送服务
                    if (tasks.length() > 0) {
                        val intent = Intent(this@MainActivity, SmsService::class.java).apply {
                            putExtra("tasks_json", body)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.tvStatus.text = "同步失败: ${response.code}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "同步失败: ${e.message}"
                    Toast.makeText(this@MainActivity, "同步失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadLogs() {
        val prefs = getSharedPreferences("logs", MODE_PRIVATE)
        val logs = prefs.getString("log_text", "暂无日志")
        binding.tvLogs.text = logs
    }

    fun appendLog(text: String) {
        runOnUiThread {
            val current = binding.tvLogs.text.toString()
            val newText = "$current\n${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())} $text"
            binding.tvLogs.text = newText
            // 保存到 SharedPreferences
            getSharedPreferences("logs", MODE_PRIVATE)
                .edit()
                .putString("log_text", newText)
                .apply()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "所有权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "部分权限被拒绝，功能可能受限", Toast.LENGTH_LONG).show()
            }
        }
    }
}
