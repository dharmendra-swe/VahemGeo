package com.vahemgeo.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.vahemgeo.app.engine.SpeedMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class JoystickOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var activeSpeed = SpeedMode.WALK.speedKmH

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            _isOverlayRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent?.action
        if (action == ACTION_STOP_OVERLAY) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayView == null) {
            showOverlay()
        }

        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 250
        }

        val context = this

        // Main Container Card with Rounded Corners & Semi-transparent Dark Background
        val cardBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#EE121212"))
            cornerRadius = 48f
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(28, 20, 28, 24)
            elevation = 20f
        }

        // Header / Drag handle bar
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val dragTitle = TextView(context).apply {
            text = "StealthGPS"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 15f
            setPadding(16, 4, 16, 4)
            setOnClickListener {
                stopSelf()
            }
        }

        headerLayout.addView(dragTitle)
        headerLayout.addView(closeBtn)

        // Dragging overlay position across screen
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        headerLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        // Custom Drawn Circular Joystick View
        val joystickSizePx = 240
        val joystickView = CircularJoystickCanvasView(context, joystickSizePx) { bearing, intensity ->
            VahemMockService.updateMovement(this, bearing, intensity, activeSpeed)
        }

        val joystickFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(joystickSizePx, joystickSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        joystickFrame.addView(joystickView)

        // Speed Preset Buttons Bar
        val speedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val speedButtons = mutableListOf<TextView>()

        SpeedMode.entries.forEach { mode ->
            val isSelected = activeSpeed == mode.speedKmH
            val btnBg = GradientDrawable().apply {
                setColor(if (isSelected) Color.parseColor("#2196F3") else Color.parseColor("#2A2A2A"))
                cornerRadius = 24f
            }

            val btn = TextView(context).apply {
                text = "${mode.label} ${mode.speedKmH.toInt()}k"
                textSize = 10f
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#B0B0B0"))
                setPadding(20, 12, 20, 12)
                background = btnBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(6, 0, 6, 0)
                }
                setOnClickListener {
                    activeSpeed = mode.speedKmH
                    speedButtons.forEachIndexed { idx, tv ->
                        val selected = SpeedMode.entries[idx].speedKmH == activeSpeed
                        (tv.background as GradientDrawable).setColor(
                            if (selected) Color.parseColor("#2196F3") else Color.parseColor("#2A2A2A")
                        )
                        tv.setTextColor(if (selected) Color.WHITE else Color.parseColor("#B0B0B0"))
                    }
                    VahemMockService.updateMovement(context, 0f, 0f, activeSpeed)
                }
            }
            speedButtons.add(btn)
            speedLayout.addView(btn)
        }

        rootLayout.addView(headerLayout)
        rootLayout.addView(joystickFrame)
        rootLayout.addView(speedLayout)

        overlayView = rootLayout
        windowManager.addView(overlayView, params)
        _isOverlayRunning.value = true
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayView = null
        _isOverlayRunning.value = false
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_OVERLAY = "com.vahemgeo.app.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.vahemgeo.app.STOP_OVERLAY"

        private val _isOverlayRunning = MutableStateFlow(false)
        val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

        fun startOverlay(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, JoystickOverlayService::class.java).apply {
                    action = ACTION_START_OVERLAY
                }
                context.startService(intent)
            }
        }

        fun stopOverlay(context: Context) {
            val intent = Intent(context, JoystickOverlayService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            context.startService(intent)
        }
    }
}

/**
 * Custom Canvas-rendered circular Joystick View for System Overlay
 */
private class CircularJoystickCanvasView(
    context: Context,
    private val viewSizePx: Int,
    private val onMoved: (bearing: Float, intensity: Float) -> Unit
) : View(context) {

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }

    private val centerPos = viewSizePx / 2f
    private val knobRadius = 40f
    private val maxRadius = (viewSizePx / 2f) - knobRadius

    private var knobX = centerPos
    private var knobY = centerPos

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSizePx, viewSizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Outer Base Circle
        canvas.drawCircle(centerPos, centerPos, maxRadius + (knobRadius / 2f), outerPaint)
        canvas.drawCircle(centerPos, centerPos, maxRadius + (knobRadius / 2f), strokePaint)

        // Inner Knob
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerPos
                val dy = event.y - centerPos
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val clampedDist = dist.coerceAtMost(maxRadius)
                val angle = atan2(dy.toDouble(), dx.toDouble())

                knobX = centerPos + (clampedDist * cos(angle)).toFloat()
                knobY = centerPos + (clampedDist * sin(angle)).toFloat()
                invalidate()

                val intensity = (clampedDist / maxRadius).coerceIn(0f, 1f)
                var bearing = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                if (bearing < 0) bearing += 360f

                onMoved(bearing, intensity)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobX = centerPos
                knobY = centerPos
                invalidate()
                onMoved(0f, 0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
