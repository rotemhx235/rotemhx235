package com.gadol.magnifier

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView

/**
 * The product itself. The launcher activity is only a control panel; everything
 * the user actually interacts with day to day lives here.
 *
 * Two jobs:
 *  - draw a floating button that is always visible, so there is no gesture to
 *    learn and nothing to remember,
 *  - zoom the screen — including other apps' screens — through the platform
 *    magnification API.
 *
 * The button is a TYPE_ACCESSIBILITY_OVERLAY window, which an accessibility
 * service may add without the separate "draw over other apps" permission. That
 * keeps setup down to a single permission, which is the step most of our users
 * would otherwise abandon.
 */
class MagnifierService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: MagnifierService? = null

        /** True while the user has the service enabled in accessibility settings. */
        fun isRunning(): Boolean = instance != null

        /**
         * Applies a zoom level chosen in the control panel while the user is
         * already zoomed in, so the change is visible immediately.
         */
        fun onZoomPreferenceChanged(zoom: Float) {
            instance?.let { service ->
                if (service.magnified) service.applyScale(zoom)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var bubble: View? = null

    private var magnified = false
    private var touchSlop = 0

    // Drag bookkeeping for the floating button.
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var dragging = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        showBubble()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (magnified) applyScale(1f)
        removeBubble()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeBubble()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The floating button is driven entirely by touch, so there is nothing
        // to do per event. The service still subscribes to window-state changes
        // so Android keeps it bound.
    }

    override fun onInterrupt() = Unit

    // ---------------------------------------------------------------- bubble

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showBubble() {
        if (bubble != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        val prefs = Prefs(this)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.bubbleX
            y = prefs.bubbleY
        }

        view.setOnTouchListener { _, event -> onBubbleTouch(view, event) }

        windowManager.addView(view, layoutParams)
        bubble = view
        renderBubble()
    }

    private fun removeBubble() {
        bubble?.let {
            runCatching { windowManager.removeView(it) }
            bubble = null
        }
    }

    private fun onBubbleTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downX = layoutParams.x
                downY = layoutParams.y
                dragging = false
                view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    layoutParams.x = downX + dx.toInt()
                    layoutParams.y = downY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(view, layoutParams) }
                    // While zoomed, dragging the button also pans the magnified
                    // view: the button is the thing you are looking through.
                    if (magnified) panToBubble()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                if (dragging) {
                    Prefs(this).setBubblePosition(layoutParams.x, layoutParams.y)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    toggleMagnification()
                }
                dragging = false
                return true
            }
        }
        return false
    }

    /** Keeps the button's icon and tint in step with the current state. */
    private fun renderBubble() {
        val icon = bubble?.findViewById<ImageView>(R.id.bubble_icon) ?: return
        if (magnified) {
            icon.setImageResource(R.drawable.ic_bubble_close)
            icon.contentDescription = getString(R.string.bubble_zoom_out)
            icon.setBackgroundResource(R.drawable.bg_bubble_active)
        } else {
            icon.setImageResource(R.drawable.ic_bubble_zoom)
            icon.contentDescription = getString(R.string.bubble_zoom_in)
            icon.setBackgroundResource(R.drawable.bg_bubble_idle)
        }
    }

    // --------------------------------------------------------- magnification

    private fun toggleMagnification() {
        if (magnified) {
            applyScale(1f)
            magnified = false
        } else {
            magnified = true
            applyScale(Prefs(this).zoom)
            // Centre on the button so the first thing the user sees is the part
            // of the screen they just pointed at.
            Handler(Looper.getMainLooper()).post { panToBubble() }
        }
        renderBubble()
    }

    @Suppress("DEPRECATION")
    private fun applyScale(scale: Float) {
        runCatching { magnificationController.setScale(scale, true) }
    }

    /**
     * Maps the button's position on screen onto the magnification centre, so
     * moving the button moves what is under the magnifier.
     */
    @Suppress("DEPRECATION")
    private fun panToBubble() {
        val view = bubble ?: return
        val centreX = (layoutParams.x + view.width / 2f).coerceAtLeast(0f)
        val centreY = (layoutParams.y + view.height / 2f).coerceAtLeast(0f)
        runCatching { magnificationController.setCenter(centreX, centreY, false) }
    }
}
