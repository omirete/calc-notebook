package com.anonymous.calcnotebook.drawingboard

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF

data class StrokePoint(val x: Float, val y: Float, val pressure: Float)

class Stroke(
    val color: Int,
    val thicknessFactor: Float
) {
    val points = mutableListOf<StrokePoint>()
    val translation = PointF(0f, 0f)
    val paint: Paint = Paint().apply {
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }

    /** Re-compute the stroke’s Path and Paint before drawing */
    fun toPath(): Path {
        paint.color = color
        val path = Path()
        points.forEachIndexed { idx, p ->
            val px = p.x + translation.x
            val py = p.y + translation.y
            if (idx == 0) path.moveTo(px, py)
            else path.lineTo(px, py)
        }
        return path
    }

    fun widthFor(point: StrokePoint): Float =
        point.pressure * thicknessFactor + 1f
}
