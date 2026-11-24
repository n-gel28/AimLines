package com.aimlines

import android.content.Context
import android.graphics.*
import android.view.View

class OverlayView(ctx: Context) : View(ctx) {
    private var result: Predictor.Result? = null
    private var scale = 0.5f
    private var table = RectF()
    private var status = ""
    private var debug = true
    private var radius = 14f

    private val line = Paint().apply {
        color = Color.rgb(0, 255, 80); style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val tgt = Paint(line).apply { color = Color.rgb(255, 200, 0) }
    private val defl = Paint(line).apply { color = Color.rgb(0, 200, 255) }
    private val box = Paint().apply {
        color = Color.argb(160, 255, 0, 255); style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val txt = Paint().apply { color = Color.rgb(255, 0, 255); textSize = 36f }

    fun update(r: Predictor.Result?, s: Float, t: RectF, st: String, dbg: Boolean, rad: Float) {
        result = r; scale = s; table = t; status = st; debug = dbg; radius = rad
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val inv = 1f / scale
        line.strokeWidth = 4f * scale
        tgt.strokeWidth = 4f * scale
        defl.strokeWidth = 3f * scale
        box.strokeWidth = 2f * scale

        c.save()
        c.scale(inv, inv)
        if (debug) c.drawRect(table, box)
        val r = result
        if (r != null) {
            for (i in 0 until r.cuePath.size - 1) {
                val a = r.cuePath[i]; val b = r.cuePath[i + 1]
                c.drawLine(a.x, a.y, b.x, b.y, line)
            }
            r.ghost?.let { c.drawCircle(it.x, it.y, radius, line) }
            r.target?.let { c.drawLine(it.first.x, it.first.y, it.second.x, it.second.y, tgt) }
            r.deflect?.let { c.drawLine(it.first.x, it.first.y, it.second.x, it.second.y, defl) }
        }
        c.restore()
        if (debug) c.drawText(status, 24f, 60f, txt)
    }
}
