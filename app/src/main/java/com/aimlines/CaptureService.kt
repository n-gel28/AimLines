package com.aimlines

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var overlay: OverlayView? = null
    private var wm: WindowManager? = null
    private val thread = HandlerThread("aim").apply { start() }
    private lateinit var handler: Handler
    @Volatile private var running = false
    private val scale = 0.5f
    private var capW = 0
    private var capH = 0

    override fun onBind(i: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("aim", "AimLines", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "aim")
            .setContentTitle("AimLines running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(1, n)

        if (!OpenCVLoader.initLocal()) { Log.e("aim", "OpenCV init failed"); stopSelf(); return START_NOT_STICKY }

        val code = intent?.getIntExtra("code", 0) ?: 0
        val data: Intent = intent?.getParcelableExtra("data") ?: run { stopSelf(); return START_NOT_STICKY }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, null)

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm!!.defaultDisplay.getRealMetrics(dm)
        capW = (dm.widthPixels * scale).toInt()
        capH = (dm.heightPixels * scale).toInt()

        reader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
        display = projection!!.createVirtualDisplay(
            "aim", capW, capH, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null
        )

        overlay = OverlayView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm!!.addView(overlay, lp)

        handler = Handler(thread.looper)
        running = true
        handler.post(object : Runnable {
            override fun run() {
                if (!running) return
                try { step() } catch (e: Exception) { Log.e("aim", "step failed", e) }
                handler.postDelayed(this, 100)
            }
        })
        return START_NOT_STICKY
    }

    private fun step() {
        val img = reader?.acquireLatestImage() ?: return
        val plane = img.planes[0]
        val rowPx = plane.rowStride / plane.pixelStride
        val w = img.width
        val h = img.height
        val full = Bitmap.createBitmap(rowPx, h, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(plane.buffer)
        img.close()
        val bmp = Bitmap.createBitmap(full, 0, 0, w, h)

        val mat = Mat()
        Utils.bitmapToMat(bmp, mat)

        val p = getSharedPreferences("aim", Context.MODE_PRIVATE)
        val r = p.getFloat("radius", 28f) * scale
        val scene = Detector.detect(mat, r)
        mat.release()

        val table = RectF(
            w * p.getFloat("left", 8f) / 100f,
            h * p.getFloat("top", 18f) / 100f,
            w * p.getFloat("right", 92f) / 100f,
            h * p.getFloat("bottom", 82f) / 100f
        )
        val result = if (scene.cue != null && scene.aim != null)
            Predictor.predict(scene.cue, scene.aim, scene.balls, table, r) else null

        val status = "circles=${scene.count} cue=${scene.cue != null} aim=${scene.aim != null}"
        val debug = p.getBoolean("debug", true)
        overlay?.post { overlay?.update(result, scale, table, status, debug, r) }
    }

    override fun onDestroy() {
        running = false
        overlay?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        display?.release()
        reader?.close()
        projection?.stop()
        thread.quitSafely()
        super.onDestroy()
    }
}
