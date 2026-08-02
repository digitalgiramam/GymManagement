package com.gymmanager.ui.progress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Lightweight Canvas-based line chart for weight/BMI trends over time.
 * Deliberately avoids a third-party charting dependency (e.g. MPAndroidChart)
 * to keep the build free of extra repositories/dependencies.
 */
class ProgressChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Point(val label: String, val value: Float)

    private var points: List<Point> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        textSize = 28f
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
    }

    fun setData(newPoints: List<Point>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (points.size < 2) {
            val message = if (points.isEmpty()) "No entries yet" else "Add one more entry to see a trend"
            canvas.drawText(message, width / 2f, height / 2f, emptyPaint)
            return
        }

        val paddingLeft   = 90f
        val paddingRight  = 30f
        val paddingTop    = 30f
        val paddingBottom = 60f

        val chartWidth  = (width - paddingLeft - paddingRight).coerceAtLeast(1f)
        val chartHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1f)

        val maxVal = points.maxOf { it.value }
        val minVal = points.minOf { it.value }
        val range  = (maxVal - minVal).takeIf { it > 0f } ?: 1f

        // Horizontal grid lines with value labels
        val steps = 4
        for (i in 0..steps) {
            val y = paddingTop + chartHeight * i / steps
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
            val value = maxVal - (range * i / steps)
            canvas.drawText(String.format("%.1f", value), 8f, y + 10f, labelPaint)
        }

        val stepX = chartWidth / (points.size - 1)
        val coords = points.mapIndexed { i, p ->
            val x = paddingLeft + stepX * i
            val y = paddingTop + chartHeight * (1 - (p.value - minVal) / range)
            x to y
        }

        for (i in 0 until coords.size - 1) {
            canvas.drawLine(coords[i].first, coords[i].second, coords[i + 1].first, coords[i + 1].second, linePaint)
        }
        coords.forEach { (x, y) -> canvas.drawCircle(x, y, 9f, dotPaint) }

        // Show first / middle / last date labels to avoid crowding
        val labelIndices = setOf(0, points.size / 2, points.size - 1)
        labelIndices.forEach { i ->
            canvas.drawText(points[i].label, coords[i].first - 40f, height - 15f, labelPaint)
        }
    }
}
