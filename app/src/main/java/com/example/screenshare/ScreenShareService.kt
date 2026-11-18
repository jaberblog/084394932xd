package com.example.screenshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

class ScreenShareService : Service() {

    companion object {
        const val ACTION_START = "com.example.screenshare.START"
        const val ACTION_STOP = "com.example.screenshare.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val PORT = 8080
        const val CHANNEL_ID = "screen_share_channel"
        const val NOTIF_ID = 1

        @Volatile var isRunning = false
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var server: MjpegServer? = null
    private var lastFrameTime = 0L

    private val captureThread = HandlerThread("ScreenCapture").apply { start() }
    private val handler = Handler(captureThread.looper)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (data != null && projection == null) {
            startCapture(resultCode, data)
        } else if (data == null && projection == null) {
            // سرویس بعد از ری‌استارت سیستم بدون داده اجرا شده؛ خاموش می‌شود
            stopSelf()
            return START_NOT_STICKY
        }

        // START_STICKY باعث می‌شود سیستم سرویس را پس از کشته شدن دوباره اجرا کند
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels / 2
        val height = metrics.heightPixels / 2
        val dpi = metrics.densityDpi

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(resultCode, data)
        projection = mp

        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.elapsedRealtime()
                if (now - lastFrameTime >= 100) { // حدود ۱۰ فریم در ثانیه
                    lastFrameTime = now
                    val plane = image.planes[0]
                    val rowPadding = plane.rowStride - plane.pixelStride * width
                    val raw = Bitmap.createBitmap(
                        width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888
                    )
                    raw.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(raw, 0, 0, width, height)
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 1, out)
                    raw.recycle()
                    bitmap.recycle()
                    server?.updateFrame(out.toByteArray())
                }
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = mp.createVirtualDisplay(
            "ScreenShare",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )

        server = MjpegServer(PORT).also { it.start() }
        isRunning = true
    }

    override fun onDestroy() {
        isRunning = false
        server?.stop()
        server = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        captureThread.quitSafely()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "اشتراک صفحه", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("اشتراک صفحه فعال است")
            .setContentText("پورت $PORT")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openIntent)
            .addAction(0, "توقف", stopIntent)
            .setOngoing(true)
            .build()
    }
}
