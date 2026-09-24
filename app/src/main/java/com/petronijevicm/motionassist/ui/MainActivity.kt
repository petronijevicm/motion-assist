/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.ui

import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.petronijevicm.motionassist.R
import com.petronijevicm.motionassist.cues.CuePalette
import com.petronijevicm.motionassist.cues.CueShape
import com.petronijevicm.motionassist.data.CueSettings
import com.petronijevicm.motionassist.databinding.ActivityMainBinding
import com.petronijevicm.motionassist.engine.MotionEstimator
import com.petronijevicm.motionassist.overlay.CueOverlayService
import com.petronijevicm.motionassist.overlay.CueTileService

class MainActivity : AppCompatActivity() {

    private lateinit var ui: ActivityMainBinding
    private lateinit var settings: CueSettings
    private lateinit var estimator: MotionEstimator

    private val shapeChips by lazy {
        mapOf(
            CueShape.CIRCLE to ui.chipCircle,
            CueShape.SQUIRCLE to ui.chipSquircle,
            CueShape.PENTAGON to ui.chipPentagon,
            CueShape.DIAMOND to ui.chipDiamond
        )
    }

    private val paletteChips by lazy {
        mapOf(
            CuePalette.SYSTEM to ui.chipSystem,
            CuePalette.SALMON to ui.chipSalmon,
            CuePalette.AMBER to ui.chipAmber,
            CuePalette.MINT to ui.chipMint,
            CuePalette.SKY to ui.chipSky,
            CuePalette.CONTRAST to ui.chipContrast
        )
    }

    /** Restarts the tutorial each time it finishes a pass (belt and braces next to repeatCount). */
    private val tutorialLoop = object : Animatable2.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
            (drawable as? AnimatedVectorDrawable)?.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        settings = CueSettings(this)
        estimator = MotionEstimator(this)

        setUpPreview()
        setUpActivation()
        setUpAppearance()
        setUpMotionAndDisplay()
    }

    override fun onResume() {
        super.onResume()
        // Cues may have been switched from the tile or the notification meanwhile
        ui.switchEnabled.isChecked = settings.enabled
        if (settings.enabled && Settings.canDrawOverlays(this)) CueOverlayService.start(this)
        estimator.smoothingSec = settings.smoothingSec
        estimator.start()
        (ui.tutorialImage.drawable as? AnimatedVectorDrawable)?.start()
    }

    override fun onPause() {
        super.onPause()
        estimator.stop()
        (ui.tutorialImage.drawable as? AnimatedVectorDrawable)?.stop()
    }

    // ---- Preview ----------------------------------------------------------------------------

    private fun setUpPreview() {
        (ui.tutorialImage.drawable as? AnimatedVectorDrawable)?.registerAnimationCallback(tutorialLoop)

        ui.cueField.apply {
            draggable = true
            motionSource = { estimator.latest }
            onFrame = { motion ->
                // Wheel follows the turn: left turn (positive yaw) turns it anticlockwise
                val target = (-motion.yawRateRps * 60f + motion.lateral * 4f).coerceIn(-45f, 45f)
                ui.wheelIcon.rotation += (target - ui.wheelIcon.rotation) * 0.15f
            }
        }
        applyLookToPreview()

        ui.previewModes.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val live = checkedId == R.id.modeLive
            ui.livePreview.visibility = if (live) View.VISIBLE else View.GONE
            ui.tutorialImage.visibility = if (live) View.GONE else View.VISIBLE
            ui.previewTitle.setText(if (live) R.string.preview_live_title else R.string.preview_guide_title)
            ui.previewBody.setText(if (live) R.string.preview_live_body else R.string.preview_guide_body)
            if (live) ui.cueField.resetMotion()
        }
    }

    private fun applyLookToPreview() {
        ui.cueField.apply {
            shape = settings.shape
            palette = settings.palette
            opacityPercent = settings.opacity
            sizeScale = settings.size / 100f
            movementScale = settings.movement / 100f
            bandFraction = settings.area / 100f
            showHorizon = settings.horizon
            scattered = settings.shuffle
        }
    }

    // ---- Activation -------------------------------------------------------------------------

    private fun setUpActivation() {
        ui.switchEnabled.isChecked = settings.enabled
        ui.switchEnabled.setOnCheckedChangeListener { button, on ->
            if (on == settings.enabled) return@setOnCheckedChangeListener
            if (on && !Settings.canDrawOverlays(this)) {
                button.isChecked = false
                askForOverlayPermission()
                return@setOnCheckedChangeListener
            }
            settings.enabled = on
            if (on) CueOverlayService.start(this) else CueOverlayService.stop(this)
            CueOverlayService.requestTileRefresh(this)
        }

        ui.switchAuto.isChecked = settings.autoStart
        ui.switchAuto.setOnCheckedChangeListener { _, on ->
            settings.autoStart = on
            CueOverlayService.requestTileRefresh(this)
        }

        ui.tileButton.setOnClickListener { offerTile() }
        ui.tileCard.setOnClickListener { offerTile() }
    }

    private fun askForOverlayPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_title)
            .setMessage(R.string.permission_body)
            .setPositiveButton(R.string.permission_open) { _, _ ->
                openSettingsScreen(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            }
            .setNeutralButton(R.string.permission_app_info) { _, _ ->
                openSettingsScreen(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openSettingsScreen(action: String) {
        try {
            startActivity(Intent(action, Uri.fromParts("package", packageName, null)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** Asks the system to add our tile (Android 13+), otherwise explains how to add it. */
    private fun offerTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBar = getSystemService(StatusBarManager::class.java)
            if (statusBar != null) {
                statusBar.requestAddTileService(
                    ComponentName(this, CueTileService::class.java),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_tile),
                    mainExecutor
                ) { }
                return
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tile_manual_title)
            .setMessage(R.string.tile_manual_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---- Appearance, motion, display --------------------------------------------------------

    private fun setUpAppearance() {
        shapeChips[settings.shape]?.isChecked = true
        ui.shapeChips.setOnCheckedStateChangeListener { _, ids ->
            val shape = shapeChips.entries.firstOrNull { it.value.id == ids.firstOrNull() }?.key ?: return@setOnCheckedStateChangeListener
            settings.shape = shape
            ui.cueField.shape = shape
        }

        tintPaletteChips()
        paletteChips[settings.palette]?.isChecked = true
        ui.paletteChips.setOnCheckedStateChangeListener { _, ids ->
            val palette = paletteChips.entries.firstOrNull { it.value.id == ids.firstOrNull() }?.key ?: return@setOnCheckedStateChangeListener
            settings.palette = palette
            ui.cueField.palette = palette
        }

        bindSlider(ui.opacitySlider, ui.opacityLabel, R.string.opacity_title, settings.opacity) {
            settings.opacity = it
            ui.cueField.opacityPercent = it
        }
        bindSlider(ui.sizeSlider, ui.sizeLabel, R.string.size_title, settings.size) {
            settings.size = it
            ui.cueField.sizeScale = it / 100f
        }
    }

    private fun setUpMotionAndDisplay() {
        bindSlider(ui.movementSlider, ui.movementLabel, R.string.movement_title, settings.movement) {
            settings.movement = it
            ui.cueField.movementScale = it / 100f
        }
        bindSlider(ui.responsivenessSlider, ui.responsivenessLabel, R.string.responsiveness_title, settings.responsiveness) {
            settings.responsiveness = it
            estimator.smoothingSec = settings.smoothingSec
        }
        bindSlider(ui.areaSlider, ui.areaLabel, R.string.area_title, settings.area, ::areaLabel) {
            settings.area = it
            ui.cueField.bandFraction = it / 100f
        }

        ui.switchHorizon.isChecked = settings.horizon
        ui.switchHorizon.setOnCheckedChangeListener { _, on ->
            settings.horizon = on
            ui.cueField.showHorizon = on
        }
        ui.switchShuffle.isChecked = settings.shuffle
        ui.switchShuffle.setOnCheckedChangeListener { _, on ->
            settings.shuffle = on
            ui.cueField.scattered = on
        }
        ui.switchSmooth.isChecked = settings.smooth
        ui.switchSmooth.setOnCheckedChangeListener { _, on -> settings.smooth = on }
    }

    private fun tintPaletteChips() {
        for ((palette, chip) in paletteChips) {
            chip.chipIconTint = ColorStateList.valueOf(palette.resolve(this))
        }
    }

    private fun areaLabel(value: Int): String =
        if (value >= 50) "${getString(R.string.area_title)}: ${getString(R.string.area_full)}"
        else getString(R.string.label_percent, getString(R.string.area_title), value)

    /**
     * Wires a slider to a setting. The stored value is snapped onto the slider's range and
     * step first, because Slider throws on values that are off its grid.
     */
    private fun bindSlider(
        slider: Slider,
        label: TextView,
        @StringRes title: Int,
        stored: Int,
        format: (Int) -> String = { getString(R.string.label_percent, getString(title), it) },
        onChange: (Int) -> Unit
    ) {
        val step = if (slider.stepSize > 0f) slider.stepSize else 1f
        val clamped = stored.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
        slider.value = (slider.valueFrom + Math.round((clamped - slider.valueFrom) / step) * step)
            .coerceIn(slider.valueFrom, slider.valueTo)
        label.text = format(slider.value.toInt())
        slider.addOnChangeListener { _, value, fromUser ->
            label.text = format(value.toInt())
            if (fromUser) onChange(value.toInt())
        }
    }
}
