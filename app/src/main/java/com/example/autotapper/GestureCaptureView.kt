package com.example.autotapper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class GestureCaptureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.success)
        style = Paint.Style.FILL
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.danger)
        style = Paint.Style.FILL
    }

    private var points: List<GesturePoint> = emptyList()

    fun showPath(pathPoints: List<GesturePoint>) {
        points = pathPoints
        invalidate()
    }

    fun clearPath() {
        points = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        val path = Path().apply {
            moveTo(points.first().x.toFloat(), points.first().y.toFloat())
            points.drop(1).forEach { point ->
                lineTo(point.x.toFloat(), point.y.toFloat())
            }
        }
        canvas.drawPath(path, pathPaint)
        val radius = 8f * density
        val start = points.first()
        val end = points.last()
        canvas.drawCircle(start.x.toFloat(), start.y.toFloat(), radius, startPaint)
        canvas.drawCircle(end.x.toFloat(), end.y.toFloat(), radius, endPaint)
    }
}
