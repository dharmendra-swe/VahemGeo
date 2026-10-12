package com.vahemgeo.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
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

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).toInt()
    }

    private fun dpF(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val dm = resources.displayMetrics
        val minScreenDimDp = (minOf(dm.widthPixels, dm.heightPixels) / dm.density)
        // Adaptive joystick diameter: 110dp on compact screens, 130dp on regular screens
        val joystickDp = if (minScreenDimDp < 360f) 108f else 126f

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16f)
            y = dp(110f)
        }

        val context = this

        // Neo-Emerald Backdrop: Deep Pine / CyberTeal backdrop (#D90B3028) with Electric Lime Border (#4094E70D)
        val cardBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#E60B3028"))
            cornerRadius = dpF(20f)
            setStroke(dp(1.5f), Color.parseColor("#6094E70D"))
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(dp(12f), dp(10f), dp(12f), dp(12f))
            elevation = dpF(8f)
        }

        // Header / Drag Handle Bar
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6f))
        }

        val dragTitle = TextView(context).apply {
            text = "⠿ VahemGeo"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 11f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        var isAutoWalkActive = VahemMockService.isAutoWalkEnabled.value

        val autoWalkBtn = TextView(context).apply {
            text = if (isAutoWalkActive) "AUTO ON" else "AUTO"
            textSize = 9.5f
            paint.isFakeBoldText = true
            setTextColor(if (isAutoWalkActive) Color.parseColor("#0A1412") else Color.parseColor("#8BA69C"))
            setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
            val btnBg = GradientDrawable().apply {
                setColor(if (isAutoWalkActive) Color.parseColor("#94E70D") else Color.parseColor("#122420"))
                cornerRadius = dpF(10f)
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(4f), 0, dp(4f), 0)
            }
            setOnClickListener {
                isAutoWalkActive = !isAutoWalkActive
                text = if (isAutoWalkActive) "AUTO ON" else "AUTO"
                setTextColor(if (isAutoWalkActive) Color.parseColor("#0A1412") else Color.parseColor("#8BA69C"))
                (background as GradientDrawable).setColor(
                    if (isAutoWalkActive) Color.parseColor("#94E70D") else Color.parseColor("#122420")
                )
                VahemMockService.setAutoWalk(context, isAutoWalkActive)
            }
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF453A"))
            textSize = 13f
            setPadding(dp(6f), dp(2f), dp(4f), dp(2f))
            setOnClickListener {
                stopSelf()
            }
        }

        headerLayout.addView(dragTitle)
        headerLayout.addView(autoWalkBtn)
        headerLayout.addView(closeBtn)

        // Dragging overlay position across screen with screen-bound clamping
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
                    val metrics = resources.displayMetrics
                    val maxX = (metrics.widthPixels - (overlayView?.width ?: dp(150f))).coerceAtLeast(0)
                    val maxY = (metrics.heightPixels - (overlayView?.height ?: dp(200f))).coerceAtLeast(0)
                    params?.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceIn(0, maxX)
                    params?.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceIn(0, maxY)
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        // Custom Canvas-rendered Circular Joystick View (Responsive DP sizing)
        val joystickSizePx = dp(joystickDp)
        val knobRadiusPx = dpF(20f)
        val joystickView = CircularJoystickCanvasView(context, joystickSizePx, knobRadiusPx) { bearing, intensity ->
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
            setPadding(0, dp(8f), 0, 0)
        }

        val speedButtons = mutableListOf<TextView>()

        SpeedMode.entries.forEach { mode ->
            val isSelected = activeSpeed == mode.speedKmH
            val btnBg = GradientDrawable().apply {
                setColor(if (isSelected) Color.parseColor("#94E70D") else Color.parseColor("#122420"))
                cornerRadius = dpF(10f)
            }

            val btn = TextView(context).apply {
                text = "${mode.label.take(4)} ${mode.speedKmH.toInt()}"
                textSize = 9.5f
                paint.isFakeBoldText = isSelected
                setTextColor(if (isSelected) Color.parseColor("#0A1412") else Color.parseColor("#8BA69C"))
                setPadding(dp(8f), dp(5f), dp(8f), dp(5f))
                background = btnBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(2f), 0, dp(2f), 0)
                }
                setOnClickListener {
                    activeSpeed = mode.speedKmH
                    speedButtons.forEachIndexed { idx, tv ->
                        val selected = SpeedMode.entries[idx].speedKmH == activeSpeed
                        (tv.background as GradientDrawable).setColor(
                            if (selected) Color.parseColor("#94E70D") else Color.parseColor("#122420")
                        )
                        tv.setTextColor(if (selected) Color.parseColor("#0A1412") else Color.parseColor("#8BA69C"))
                    }
                    VahemMockService.updateSpeed(context, activeSpeed)
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
 * Custom Canvas-rendered Neo-Emerald circular Joystick View for System Overlay (Density-Independent)
 */
private class CircularJoystickCanvasView(
    context: Context,
    private val viewSizePx: Int,
    private val knobRadius: Float,
    private val onMoved: (bearing: Float, intensity: Float) -> Unit
) : View(context) {

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2094E70D")
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94E70D")
        style = Paint.Style.STROKE
        strokeWidth = knobRadius * 0.12f
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94E70D")
        style = Paint.Style.FILL
    }

    private val centerCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val centerPos = viewSizePx / 2f
    private val maxRadius = (viewSizePx / 2f) - knobRadius

    private var knobX = centerPos
    private var knobY = centerPos

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSizePx, viewSizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Outer Luminous Ring
        canvas.drawCircle(centerPos, centerPos, maxRadius + (knobRadius / 2f), outerPaint)
        canvas.drawCircle(centerPos, centerPos, maxRadius + (knobRadius / 2f), strokePaint)

        // Inner Electric Lime Knob with White Cap
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint)
        canvas.drawCircle(knobX, knobY, knobRadius * 0.35f, centerCapPaint)
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
