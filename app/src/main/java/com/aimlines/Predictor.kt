package com.aimlines

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

object Predictor {
    data class Result(
        val cuePath: List<PointF>,
        val ghost: PointF?,
        val target: Pair<PointF, PointF>?,
        val deflect: Pair<PointF, PointF>?
    )

    private class Wall(val t: Float, val vertical: Boolean)

    private fun wall(px: Float, py: Float, dx: Float, dy: Float, b: RectF): Wall {
        val inf = Float.MAX_VALUE
        var tx = inf
        var ty = inf
        if (dx > 1e-6f) tx = (b.right - px) / dx else if (dx < -1e-6f) tx = (b.left - px) / dx
        if (dy > 1e-6f) ty = (b.bottom - py) / dy else if (dy < -1e-6f) ty = (b.top - py) / dy
        if (tx <= 1e-3f) tx = inf
        if (ty <= 1e-3f) ty = inf
        return if (tx < ty) Wall(tx, true) else Wall(ty, false)
    }

    private fun rayCircle(px: Float, py: Float, dx: Float, dy: Float, cx: Float, cy: Float, rad: Float): Float? {
        val fx = px - cx
        val fy = py - cy
        val b = 2f * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - rad * rad
        val disc = b * b - 4f * c
        if (disc < 0f) return null
        val t = (-b - sqrt(disc)) / 2f
        return if (t > 1e-3f) t else null
    }

    fun predict(cue: Detector.Ball, aim: PointF, balls: List<Detector.Ball>, table: RectF, r: Float): Result {
        val inner = RectF(table.left + r, table.top + r, table.right - r, table.bottom - r)
        var px = cue.x
        var py = cue.y
        var dx = aim.x
        var dy = aim.y
        val pts = arrayListOf(PointF(px, py))

        repeat(4) {
            val w = wall(px, py, dx, dy, inner)
            var tb = Float.MAX_VALUE
            var hit: Detector.Ball? = null
            for (b in balls) {
                val t = rayCircle(px, py, dx, dy, b.x, b.y, 2f * r)
                if (t != null && t < tb) { tb = t; hit = b }
            }
            if (hit != null && tb < w.t) {
                val hx = px + dx * tb
                val hy = py + dy * tb
                pts.add(PointF(hx, hy))
                var nx = hit.x - hx
                var ny = hit.y - hy
                val len = hypot(nx, ny)
                nx /= len; ny /= len
                val te = wall(hit.x, hit.y, nx, ny, inner).t
                val end = if (te == Float.MAX_VALUE) 0f else te
                val target = Pair(PointF(hit.x, hit.y), PointF(hit.x + nx * end, hit.y + ny * end))
                val dot = dx * nx + dy * ny
                var tx = dx - dot * nx
                var ty = dy - dot * ny
                val tl = hypot(tx, ty)
                var deflect: Pair<PointF, PointF>? = null
                if (tl > 1e-3f) {
                    tx /= tl; ty /= tl
                    deflect = Pair(PointF(hx, hy), PointF(hx + tx * r * 6f, hy + ty * r * 6f))
                }
                return Result(pts, PointF(hx, hy), target, deflect)
            }
            if (w.t == Float.MAX_VALUE) return Result(pts, null, null, null)
            px += dx * w.t
            py += dy * w.t
            pts.add(PointF(px, py))
            if (w.vertical) dx = -dx else dy = -dy
        }
        return Result(pts, null, null, null)
    }
}
