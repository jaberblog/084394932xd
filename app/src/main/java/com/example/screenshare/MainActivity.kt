package com.example.screenshare

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var tvInfo: TextView

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenShareService::class.java).apply {
                action = ScreenShareService.ACTION_START
                putExtra(ScreenShareService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenShareService.EXTRA_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, intent)
            tvInfo.postDelayed({ updateInfo() }, 1000)
        } else {
            Toast.makeText(this, "دسترسی ضبط صفحه داده نشد", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { requestProjection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvInfo = findViewById(R.id.tvInfo)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestProjection()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            startService(
                Intent(this, ScreenShareService::class.java)
                    .setAction(ScreenShareService.ACTION_STOP)
            )
            tvInfo.postDelayed({ updateInfo() }, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        updateInfo()
    }

    private fun requestProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun updateInfo() {
        tvInfo.text = if (ScreenShareService.isRunning) {
            "اشتراک فعاله:\nhttp://${getLocalIpAddress() ?: "?"}:${ScreenShareService.PORT}"
        } else {
            "متوقف است"
        }
    }
}