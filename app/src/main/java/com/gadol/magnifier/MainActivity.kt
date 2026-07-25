package com.gadol.magnifier

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gadol.magnifier.databinding.ActivityMainBinding

/**
 * The control panel. The user comes here to switch the magnifier on and to set
 * the text size, then leaves; the floating button does the rest.
 *
 * Everything on this screen is deliberately oversized and states its current
 * status in words, because a user who cannot read small text cannot read a
 * settings screen either.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.magnifierButton.setOnClickListener { openAccessibilitySettings() }
        binding.fontBigger.setOnClickListener { changeFont(bigger = true) }
        binding.fontSmaller.setOnClickListener { changeFont(bigger = false) }
        binding.fontReset.setOnClickListener { resetFont() }
        binding.zoomLess.setOnClickListener { changeZoom(-1) }
        binding.zoomMore.setOnClickListener { changeZoom(+1) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------------------------------------------------------------- render

    private fun render() {
        renderMagnifierStatus()
        renderFontStatus()
        renderZoomStatus()
    }

    private fun renderMagnifierStatus() {
        val on = MagnifierService.isRunning()
        binding.magnifierStatus.text = getString(
            if (on) R.string.magnifier_status_on else R.string.magnifier_status_off
        )
        binding.magnifierStatus.setTextColor(
            getColor(if (on) R.color.status_on else R.color.status_off)
        )
        binding.magnifierButton.text = getString(
            if (on) R.string.magnifier_button_turn_off else R.string.magnifier_button_turn_on
        )
        binding.magnifierHint.text = getString(
            if (on) R.string.magnifier_hint_on else R.string.magnifier_hint_off
        )
        binding.zoomRow.isEnabled = on
    }

    private fun renderFontStatus() {
        binding.fontValue.text = getString(R.string.font_value, FontScale.currentPercent(this))
        binding.fontBigger.isEnabled = !FontScale.isAtMaximum(this)
        binding.fontSmaller.isEnabled = !FontScale.isAtMinimum(this)
    }

    private fun renderZoomStatus() {
        val zoom = Prefs(this).zoom
        // Trims "2.0" to "2" but keeps "2.5" intact.
        val label = if (zoom % 1f == 0f) zoom.toInt().toString() else zoom.toString()
        binding.zoomValue.text = getString(R.string.zoom_value, label)
    }

    // ---------------------------------------------------------------- actions

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        runCatching { startActivity(intent) }
            .onSuccess { toast(R.string.toast_find_app_in_list) }
            .onFailure { toast(R.string.toast_could_not_open_settings) }
    }

    private fun changeFont(bigger: Boolean) {
        if (!FontScale.canWrite(this)) {
            requestWriteSettings()
            return
        }
        val changed = if (bigger) FontScale.bigger(this) else FontScale.smaller(this)
        if (changed) {
            renderFontStatus()
        } else {
            // Some manufacturers refuse the write even with the permission
            // granted. Hand the user off to the system screen rather than
            // failing silently.
            toast(R.string.toast_font_blocked)
            openDisplaySettings()
        }
    }

    private fun resetFont() {
        if (!FontScale.canWrite(this)) {
            requestWriteSettings()
            return
        }
        FontScale.reset(this)
        renderFontStatus()
    }

    private fun changeZoom(direction: Int) {
        val prefs = Prefs(this)
        val steps = Prefs.ZOOM_STEPS
        val currentIndex = steps.indexOfFirst { it == prefs.zoom }.takeIf { it >= 0 } ?: 1
        val nextIndex = (currentIndex + direction).coerceIn(0, steps.lastIndex)
        prefs.zoom = steps[nextIndex]
        MagnifierService.onZoomPreferenceChanged(steps[nextIndex])
        renderZoomStatus()
    }

    private fun requestWriteSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
            .onSuccess { toast(R.string.toast_allow_change_settings) }
            .onFailure { openDisplaySettings() }
    }

    private fun openDisplaySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            .onFailure { toast(R.string.toast_could_not_open_settings) }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}
