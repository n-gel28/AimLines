package com.aimlines

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("aim", Context.MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val sv = ScrollView(this).apply { addView(col) }
        setContentView(sv)

        addSlider(col, "Ball radius (px)", "radius", 8f, 90f, 28f)
        addSlider(col, "Table left (% of width)", "left", 0f, 50f, 8f)
        addSlider(col, "Table top (% of height)", "top", 0f, 50f, 18f)
        addSlider(col, "Table right (% of width)", "right", 50f, 100f, 92f)
        addSlider(col, "Table bottom (% of height)", "bottom", 50f, 100f, 82f)

        val sw = Switch(this).apply {
            text = "Show table box and debug text"
            isChecked = prefs.getBoolean("debug", true)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("debug", v).apply() }
        }
        col.addView(sw)

        col.addView(Button(this).apply {
            text = "Start overlay"
            setOnClickListener { start() }
        })
        col.addView(Button(this).apply {
            text = "Stop overlay"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java).setAction("STOP"))
            }
        })
    }

    private fun addSlider(root: ViewGroup, label: String, key: String, min: Float, max: Float, def: Float) {
        val cur = prefs.getFloat(key, def)
        val tv = TextView(this).apply { text = "$label: ${"%.0f".format(cur)}" }
        val sb = SeekBar(this).apply {
            this.max = (max - min).toInt()
            progress = (cur - min).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    val v = min + p
                    prefs.edit().putFloat(key, v).apply()
                    tv.text = "$label: ${"%.0f".format(v)}"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(tv)
        root.addView(sb)
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 100)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
            startForegroundService(i)
        }
    }
}
