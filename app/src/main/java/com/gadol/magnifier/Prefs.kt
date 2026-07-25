package com.gadol.magnifier

import android.content.Context

/** The handful of settings the app remembers between runs. */
class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gadol", Context.MODE_PRIVATE)

    /** Zoom levels offered in the control panel, as plain multipliers. */
    companion object {
        val ZOOM_STEPS = floatArrayOf(1.5f, 2f, 2.5f, 3f, 4f, 5f)
        const val DEFAULT_ZOOM = 2f
    }

    var zoom: Float
        get() = prefs.getFloat("zoom", DEFAULT_ZOOM)
        set(value) = prefs.edit().putFloat("zoom", value).apply()

    val bubbleX: Int get() = prefs.getInt("bubble_x", 24)
    val bubbleY: Int get() = prefs.getInt("bubble_y", 400)

    fun setBubblePosition(x: Int, y: Int) {
        prefs.edit().putInt("bubble_x", x).putInt("bubble_y", y).apply()
    }
}
