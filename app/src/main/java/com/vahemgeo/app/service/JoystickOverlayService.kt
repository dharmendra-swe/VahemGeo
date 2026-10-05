package com.vahemgeo.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.vahemgeo.app.engine.SpeedMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

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
            x = 100
            y = 300
        }

        val context = this
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC1E1E1E.toInt())
            setPadding(20, 16, 20, 20)
            elevation = 16f
        }

        // Header / Drag Handle
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }

        val dragTitle = TextView(context).apply {
            text = "❖ StealthGPS"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(0xFFFF5252.toInt())
            textSize = 16f
            setPadding(12, 0, 12, 0)
            setOnClickListener {
                stopSelf()
            }
        }

        headerLayout.addView(dragTitle)
        headerLayout.addView(closeBtn)

        // Dragging the overlay around the screen
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

        // Joystick Area (Outer Circle + Inner Knob)
        val joystickSizePx = 280
        val knobSizePx = 100
        val maxRadiusPx = (joystickSizePx - knobSizePx) / 2.0f

        val joystickContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(joystickSizePx, joystickSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setBackgroundColor(0x33FFFFFF.toInt())
        }

        val knobView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(knobSizePx, knobSizePx).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundColor(0xFF2196F3.toInt())
        }
        joystickContainer.addView(knobView)

        // Touch listener for joystick movement
        joystickContainer.setOnTouchListener { v, event ->
            val centerX = v.width / 2.0f
            val centerY = v.height / 2.0f

            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - centerX
                    val dy = event.y - centerY
                    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                    val clampedDistance = distance.coerceAtMost(maxRadiusPx)
                    val angleRad = atan2(dy.toDouble(), dx.toDouble()).toFloat()

                    val knobX = (clampedDistance * kotlin.math.cos(angleRad.toDouble())).toFloat()
                    val knobY = (clampedDistance * kotlin.math.sin(angleRad.toDouble())).toFloat()

                    knobView.translationX = knobX
                    knobView.translationY = knobY

                    val intensity = (clampedDistance / maxRadiusPx).coerceIn(0f, 1f)

                    // Convert screen dx, dy to geographical bearing (0 degrees = North)
                    var bearingDegrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                    if (bearingDegrees < 0) bearingDegrees += 360f

                    VahemMockService.updateMovement(this, bearingDegrees, intensity, activeSpeed)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    knobView.translationX = 0f
                    knobView.translationY = 0f
                    VahemMockService.updateMovement(this, 0f, 0f, activeSpeed)
                    true
                }
                else -> false
            }
        }

        // Speed preset buttons bar
        val speedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        SpeedMode.entries.forEach { mode ->
            val btn = Button(context).apply {
                text = "${mode.icon} ${mode.speedKmH.toInt()}"
                textSize = 10f
                setPadding(8, 0, 8, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    80
                ).apply {
                    setMargins(4, 0, 4, 0)
                }
                setOnClickListener {
                    activeSpeed = mode.speedKmH
                    VahemMockService.updateMovement(context, 0f, 0f, activeSpeed)
                }
            }
            speedLayout.addView(btn)
        }

        rootLayout.addView(headerLayout)
        rootLayout.addView(joystickContainer)
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
