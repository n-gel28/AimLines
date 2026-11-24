package com.aimlines

import android.graphics.PointF
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.min

object Detector {
    data class Ball(val x: Float, val y: Float)
    data class Scene(val balls: List<Ball>, val cue: Ball?, val aim: PointF?, val count: Int)

    // Tunables: WHITE_MIN is how bright the cue ball is, LINE_MIN_MULT is the minimum aim-line length in ball radii
    private const val WHITE_MIN = 200.0
    private const val LINE_WHITE = 225.0
    private const val LINE_MIN_MULT = 3.0

    fun detect(rgba: Mat, r: Float): Scene {
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.5)

        val circles = Mat()
        Imgproc.HoughCircles(
            gray, circles, Imgproc.HOUGH_GRADIENT, 1.0, (r * 1.6).toDouble(),
            100.0, 18.0, (r * 0.85).toInt(), (r * 1.15).toInt()
        )

        val all = ArrayList<Ball>()
        var cue: Ball? = null
        var best = 0.0
        for (i in 0 until circles.cols()) {
            val c = circles.get(0, i)
            val b = Ball(c[0].toFloat(), c[1].toFloat())
            all.add(b)
            val m = meanMinChannel(rgba, b.x.toDouble(), b.y.toDouble(), r * 0.7)
            if (m > WHITE_MIN && m > best) { best = m; cue = b }
        }
        val count = all.size
        val others = all.filter { it !== cue }

        var aim: PointF? = null
        if (cue != null) {
            val mask = Mat()
            Core.inRange(rgba, Scalar(LINE_WHITE, LINE_WHITE, LINE_WHITE, 0.0), Scalar(255.0, 255.0, 255.0, 255.0), mask)
            val lines = Mat()
            Imgproc.HoughLinesP(mask, lines, 1.0, Math.PI / 180.0, 30, r * LINE_MIN_MULT.toDouble(), r * 0.5)
            var bestLen = 0.0
            for (i in 0 until lines.rows()) {
                val l = lines.get(i, 0)
                val d = distToSegment(cue.x.toDouble(), cue.y.toDouble(), l[0], l[1], l[2], l[3])
                if (d > r * 2.2) continue
                val len = hypot(l[2] - l[0], l[3] - l[1])
                if (len > bestLen) {
                    bestLen = len
                    val d1 = hypot(l[0] - cue.x, l[1] - cue.y)
                    val d2 = hypot(l[2] - cue.x, l[3] - cue.y)
                    val fx = if (d1 > d2) l[0] else l[2]
                    val fy = if (d1 > d2) l[1] else l[3]
                    val dx = fx - cue.x
                    val dy = fy - cue.y
                    val n = hypot(dx, dy)
                    if (n > 1e-3) aim = PointF((dx / n).toFloat(), (dy / n).toFloat())
                }
            }
            mask.release(); lines.release()
        }
        gray.release(); circles.release()
        return Scene(others, cue, aim, count)
    }

    private fun meanMinChannel(m: Mat, cx: Double, cy: Double, rad: Double): Double {
        var sum = 0.0
        var n = 0
        val step = rad / 2.0
        var oy = -rad
        while (oy <= rad) {
            var ox = -rad
            while (ox <= rad) {
                if (hypot(ox, oy) <= rad) {
                    val x = (cx + ox).toInt()
                    val y = (cy + oy).toInt()
                    if (x in 0 until m.cols() && y in 0 until m.rows()) {
                        val px = m.get(y, x)
                        sum += min(px[0], min(px[1], px[2])); n++
                    }
                }
                ox += step
            }
            oy += step
        }
        return if (n > 0) sum / n else 0.0
    }

    private fun distToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
    }
}
