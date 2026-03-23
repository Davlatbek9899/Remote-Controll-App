package com.example.remotecontrol

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class CircularTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0.33f // 0..1
    private var draggable = true
    private var onAngleChange: ((Float) -> Unit)? = null
    private var thumbAngle = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#E8EEF4")
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1B8EF8")
    }

    private val shader by lazy {
        SweepGradient(
            width / 2f, height / 2f,
            intArrayOf(
                Color.parseColor("#33FF33"),
                Color.parseColor("#CCFF00"),
                Color.parseColor("#CCFF00")
            ),
            floatArrayOf(0f, 0.6f, 1f)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.82f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Track (arka fon)
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // Progress arc — boshi xira, oxiri to'q
        val sweepAngle = progress * 360f
        
        // Gradient burchagi: arc boshidan (startAngle) oxirigacha
        val startAngle = -90f
        val endAngleDeg = startAngle + sweepAngle
        
        // SweepGradient doim 3-soat pozitsiyasidan boshlanadi (0 daraja)
        // Shuning uchun offset hisoblab gradient qo'yamiz
        val startRad = Math.toRadians(startAngle.toDouble())
        val endRad = Math.toRadians(endAngleDeg.toDouble())
        
        val gradientMatrix = Matrix()
        gradientMatrix.setRotate(startAngle, cx, cy)
        
        val gradient = SweepGradient(cx, cy,
            intArrayOf(
                Color.parseColor("#00A8D8"),  // boshi — xira
                Color.parseColor("#1B8EF8"),  // o'rta
                Color.parseColor("#0A5FCC")   // oxiri — to'q
            ),
            floatArrayOf(0f, 0.5f, 1f)
        )
        gradient.setLocalMatrix(gradientMatrix)
        progressPaint.shader = gradient
        canvas.drawArc(rect, startAngle, sweepAngle, false, progressPaint)

        // Thumb (sariq doira)
        thumbAngle = -90f + sweepAngle
        val thumbX = cx + radius * cos(Math.toRadians(thumbAngle.toDouble())).toFloat()
        val thumbY = cy + radius * sin(Math.toRadians(thumbAngle.toDouble())).toFloat()

        // Tashqi ring
        thumbPaint.color = Color.parseColor("#881B8EF8")
        canvas.drawCircle(thumbX, thumbY, 22f, thumbPaint)
        // Ichki doira
        thumbPaint.color = Color.parseColor("#1B8EF8")
        canvas.drawCircle(thumbX, thumbY, 14f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!draggable) return false
        val cx = width / 2f
        val cy = height / 2f
        val dx = event.x - cx
        val dy = event.y - cy

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                if (angle < 0) angle += 360f
                if (angle > 360f) angle -= 360f
                progress = angle / 360f
                onAngleChange?.invoke(angle)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    fun setDraggable(d: Boolean) { draggable = d }

    fun setOnAngleChangeListener(listener: (Float) -> Unit) {
        onAngleChange = listener
    }
}
