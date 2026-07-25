package com.gadol.magnifier

import android.content.Context
import android.provider.Settings

/**
 * Reads and writes the device-wide font scale — the same value the Settings app
 * exposes as "Font size", buried several screens deep.
 *
 * Writing it needs WRITE_SETTINGS, which the user grants on a system screen.
 * Some manufacturers lock the value down regardless, so every write reports
 * whether it actually took effect and the caller falls back to opening the
 * system display settings.
 */
object FontScale {

    /** Font scales offered by the +/- buttons, from Android's own small to 200%. */
    val STEPS = floatArrayOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.75f, 2.0f)

    const val DEFAULT = 1.0f

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun current(context: Context): Float =
        Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, DEFAULT)

    /** Index of the step nearest the current value, so +/- always moves one notch. */
    fun currentStepIndex(context: Context): Int {
        val value = current(context)
        var nearest = 0
        var smallestGap = Float.MAX_VALUE
        STEPS.forEachIndexed { index, step ->
            val gap = kotlin.math.abs(step - value)
            if (gap < smallestGap) {
                smallestGap = gap
                nearest = index
            }
        }
        return nearest
    }

    fun set(context: Context, scale: Float): Boolean = runCatching {
        Settings.System.putFloat(context.contentResolver, Settings.System.FONT_SCALE, scale)
    }.getOrDefault(false)

    fun bigger(context: Context): Boolean {
        val next = (currentStepIndex(context) + 1).coerceAtMost(STEPS.lastIndex)
        return set(context, STEPS[next])
    }

    fun smaller(context: Context): Boolean {
        val next = (currentStepIndex(context) - 1).coerceAtLeast(0)
        return set(context, STEPS[next])
    }

    fun reset(context: Context): Boolean = set(context, DEFAULT)

    /** The current scale as a percentage, for display. */
    fun currentPercent(context: Context): Int = Math.round(current(context) * 100)

    fun isAtMaximum(context: Context): Boolean = currentStepIndex(context) == STEPS.lastIndex

    fun isAtMinimum(context: Context): Boolean = currentStepIndex(context) == 0
}
