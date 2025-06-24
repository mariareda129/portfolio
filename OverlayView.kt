package com.example.arabicsignlanguage

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var modelInputSize = 300 // Default to TensorFlow model size

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.parseColor("#FFFFFF") // White background for text on cyan
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.parseColor("#26C6DA") // Cyan text on white background
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = Color.WHITE // White bounding boxes on cyan background
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        android.util.Log.d("OverlayView", "Drawing ${results.size} bounding boxes")

        results.forEach { detection ->
            // Coordinates are normalized (0-1), so we scale them directly to view size
            val left = detection.x1 * width
            val top = detection.y1 * height
            val right = detection.x2 * width
            val bottom = detection.y2 * height
            
            android.util.Log.d("OverlayView", "Drawing box: ${detection.clsName} at [$left, $top, $right, $bottom] (view: ${width}x${height})")

            // Draw bounding box
            canvas.drawRect(left, top, right, bottom, boxPaint)
            
            // Draw label with confidence
            val confidence = (detection.cnf * 100).toInt()
            val drawableText = "${detection.clsName} ${confidence}%"

            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            
            // Draw text background
            canvas.drawRect(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )
            
            // Draw text
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
        }
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}