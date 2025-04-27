package com.anonymous.calcnotebook.drawingboard

import android.graphics.Paint
import android.graphics.PointF

data class StrokePoint(val x: Float, val y: Float, val pressure: Float)

class Stroke(val color: Int, val thicknessFactor: Float) {
  val points = ArrayList<StrokePoint>(100) // Preallocate capacity
  val translation = PointF(0f, 0f)
  val paint: Paint =
      Paint().apply {
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
      }

  fun widthFor(point: StrokePoint): Float = point.pressure * thicknessFactor + 1f
}
