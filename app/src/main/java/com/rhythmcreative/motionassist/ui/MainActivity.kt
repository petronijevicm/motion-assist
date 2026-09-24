/*
 * Copyright (C) 2026 rhythmcreative
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rhythmcreative.motionassist.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rhythmcreative.motionassist.R
import com.rhythmcreative.motionassist.databinding.ActivityMainBinding
import com.rhythmcreative.motionassist.engine.MotionEstimator
import com.rhythmcreative.motionassist.engine.MotionPreferences
import com.rhythmcreative.motionassist.service.MotionAssistService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: MotionPreferences
    private lateinit var motionEstimator: MotionEstimator

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = MotionPreferences(this)
        motionEstimator = MotionEstimator(this)

        setupUI()
        setupListeners()
        setupPreview()
    }

    private fun setupUI() {
        binding.switchMain.isChecked = prefs.isEnabled
        binding.switchAutoVehicle.isChecked = prefs.isVehicleAuto
        binding.switchRandomize.isChecked = prefs.isRandomize
        binding.switchSmoothAnimation.isChecked = prefs.isSmoothAnimation
        binding.sliderOpacity.value = prefs.opacity.toFloat()
        binding.textOpacityValue.text = "${getString(R.string.motion_assist_opacity_title)}: ${prefs.opacity}%"
        binding.sliderSensitivity.value = prefs.sensitivity.coerceIn(25, 200).toFloat()
        binding.textSensitivityValue.text = "${getString(R.string.motion_assist_sensitivity_title)}: ${prefs.sensitivity}%"

        // Select shape chip
        when (prefs.shapeIndex) {
            1 -> binding.chipShapeSquircle.isChecked = true
            2 -> binding.chipShapePentagon.isChecked = true
            3 -> binding.chipShapeDiamond.isChecked = true
            else -> binding.chipShapeCircle.isChecked = true
        }

        // Select color chip
        when (prefs.colorIndex) {
            1 -> binding.chipColorPink.isChecked = true
            2 -> binding.chipColorYellow.isChecked = true
            3 -> binding.chipColorGreen.isChecked = true
            4 -> binding.chipColorBlue.isChecked = true
            5 -> binding.chipColorAdaptive.isChecked = true
            else -> binding.chipColorSystem.isChecked = true
        }
    }

    private fun setupPreview() {
        // Configure interactive live cues view
        binding.previewCuesView.isTouchInteractive = true
        binding.previewCuesView.motionSource = { motionEstimator.latest }
        binding.previewCuesView.onFrameListener = { motion ->
            // Steering wheel follows the turn rate (left turn = counter-clockwise)
            val target = (-motion.yawRateRps * 60f + motion.lateral * 4f).coerceIn(-45f, 45f)
            val wheel = binding.imgCenterSteering
            wheel.rotation += (target - wheel.rotation) * 0.15f
        }

        // Preview mode switcher: default to Tutorial
        binding.togglePreviewMode.check(R.id.btnModeTutorial)
        binding.togglePreviewMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnModeTutorial -> {
                        binding.interactivePreviewContainer.visibility = View.GONE
                        binding.lottieTutorialView.visibility = View.VISIBLE
                        binding.lottieTutorialView.playAnimation()
                        binding.previewCardTitle.setText(R.string.motion_assist_preview_card_title)
                        binding.previewCardDesc.setText(R.string.motion_assist_preview_card_desc)
                    }
                    R.id.btnModeInteractive -> {
                        binding.lottieTutorialView.pauseAnimation()
                        binding.lottieTutorialView.visibility = View.GONE
                        binding.interactivePreviewContainer.visibility = View.VISIBLE
                        binding.previewCuesView.resetPhysics()
                        binding.previewCardTitle.setText(R.string.motion_assist_preview_tab_interactive)
                        binding.previewCardDesc.setText(R.string.motion_assist_preview_card_desc)
                    }
                }
            }
        }

        // Ensure Lottie animation gets dynamically themed with Material You 3 as soon as loaded
        binding.lottieTutorialView.addLottieOnCompositionLoadedListener {
            applyMaterialYouToLottie(binding.previewCuesView.getResolvedColor())
        }

        updatePreviewView()
    }

    private fun updatePreviewView() {
        // 1. Update preview cues view properties
        binding.previewCuesView.shapeIndex = prefs.shapeIndex
        binding.previewCuesView.colorIndex = prefs.colorIndex
        binding.previewCuesView.cueOpacity = prefs.opacity
        binding.previewCuesView.isRandomized = prefs.isRandomize
        binding.previewCuesView.isAdaptiveMode = (prefs.colorIndex == 5)
        binding.previewCuesView.sensitivity = prefs.sensitivity / 100f

        // 2. Synchronize Material You 3 colors into the official Lottie tutorial animation
        applyMaterialYouToLottie(binding.previewCuesView.getResolvedColor())

        // 3. Notify background overlay service if active
        if (prefs.isEnabled) {
            MotionAssistService.start(this)
        }
    }

    /**
     * Dynamically themes the official Google Motion Sickness tutorial Lottie animation
     * using Material You 3 dynamic color tokens extracted at runtime.
     */
    private fun applyMaterialYouToLottie(activeCueColor: Int) {
        val colorSurfaceContainer = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainer,
            Color.rgb(0x21, 0x1F, 0x26)
        )
        val colorSurfaceContainerHighest = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerHighest,
            Color.rgb(0x36, 0x34, 0x3B)
        )
        val colorOutline = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOutline,
            Color.rgb(0x93, 0x8F, 0x99)
        )
        val colorOutlineVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOutlineVariant,
            Color.rgb(0x49, 0x45, 0x4F)
        )

        // Tint Motion Cue Dots (.primary and .Primary) with the active cue color
        val cueFilter = PorterDuffColorFilter(activeCueColor, PorterDuff.Mode.SRC_ATOP)
        binding.lottieTutorialView.addValueCallback(
            KeyPath("**", ".primary", "**"),
            LottieProperty.COLOR_FILTER
        ) { cueFilter }
        binding.lottieTutorialView.addValueCallback(
            KeyPath("**", ".Primary", "**"),
            LottieProperty.COLOR_FILTER
        ) { cueFilter }

        // Tint phone outline to Material You Outline token
        val outlineFilter = PorterDuffColorFilter(colorOutline, PorterDuff.Mode.SRC_ATOP)
        binding.lottieTutorialView.addValueCallback(
            KeyPath("**", ".outline", "**"),
            LottieProperty.COLOR_FILTER
        ) { outlineFilter }
        binding.lottieTutorialView.addValueCallback(
            KeyPath("**", ".Outline", "**"),
            LottieProperty.COLOR_FILTER
        ) { outlineFilter }

        // Tint car steering wheel and interior dashboard to OutlineVariant
        val variantFilter = PorterDuffColorFilter(colorOutlineVariant, PorterDuff.Mode.SRC_ATOP)
        binding.lottieTutorialView.addValueCallback(
            KeyPath("**", ".outlineVariant", "**"),
            LottieProperty.COLOR_FILTER
        ) { variantFilter }

        // Tint clouds and dashboard body to SurfaceContainerHighest
        val containerHighestFilter = PorterDuffColorFilter(colorSurfaceContainerHighest, PorterDuff.Mode.SRC_ATOP)
        binding.lottieTutorialView.addValueCallback(
            KeyPath("**", ".sContainerHighest", "**"),
            LottieProperty.COLOR_FILTER
        ) { containerHighestFilter }

        // Tint background layer to SurfaceContainer
        val surfaceContainerFilter = PorterDuffColorFilter(colorSurfaceContainer, PorterDuff.Mode.SRC_ATOP)
        binding.lottieTutorialView.addValueCallback(
            KeyPath("**", ".surfaceContainer", "**"),
            LottieProperty.COLOR_FILTER
        ) { surfaceContainerFilter }
    }

    private fun setupListeners() {
        binding.switchMain.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !checkOverlayPermission()) {
                requestOverlayPermission()
                binding.switchMain.isChecked = false
                return@setOnCheckedChangeListener
            }

            prefs.isEnabled = isChecked
            if (isChecked) {
                MotionAssistService.start(this)
            } else {
                MotionAssistService.stop(this)
            }
        }

        binding.switchAutoVehicle.setOnCheckedChangeListener { _, isChecked ->
            prefs.isVehicleAuto = isChecked
            if (prefs.isEnabled) {
                MotionAssistService.start(this)
            }
        }

        binding.chipGroupShape.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val shapeIdx = when (checkedIds[0]) {
                    R.id.chipShapeSquircle -> 1
                    R.id.chipShapePentagon -> 2
                    R.id.chipShapeDiamond -> 3
                    else -> 0
                }
                prefs.shapeIndex = shapeIdx
                updatePreviewView()
            }
        }

        binding.chipGroupColor.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val colorIdx = when (checkedIds[0]) {
                    R.id.chipColorPink -> 1
                    R.id.chipColorYellow -> 2
                    R.id.chipColorGreen -> 3
                    R.id.chipColorBlue -> 4
                    R.id.chipColorAdaptive -> 5
                    else -> 0
                }
                prefs.colorIndex = colorIdx
                updatePreviewView()
            }
        }

        binding.sliderOpacity.addOnChangeListener { _, value, _ ->
            val opacityVal = value.toInt()
            prefs.opacity = opacityVal
            binding.textOpacityValue.text = "${getString(R.string.motion_assist_opacity_title)}: $opacityVal%"
            updatePreviewView()
        }

        binding.sliderSensitivity.addOnChangeListener { _, value, _ ->
            val sensitivityVal = value.toInt()
            prefs.sensitivity = sensitivityVal
            binding.textSensitivityValue.text = "${getString(R.string.motion_assist_sensitivity_title)}: $sensitivityVal%"
            binding.previewCuesView.sensitivity = sensitivityVal / 100f
        }

        binding.switchRandomize.setOnCheckedChangeListener { _, isChecked ->
            prefs.isRandomize = isChecked
            updatePreviewView()
        }

        binding.switchSmoothAnimation.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSmoothAnimation = isChecked
        }

        binding.btnQuickSettings.setOnClickListener {
            showQuickSettingsDialog()
        }

        binding.cardQuickSettings.setOnClickListener {
            showQuickSettingsDialog()
        }
    }

    private fun showQuickSettingsDialog() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val statusBarManager = getSystemService(android.app.StatusBarManager::class.java)
                val component = ComponentName(this, com.rhythmcreative.motionassist.qs.MotionAssistTileService::class.java)
                statusBarManager.requestAddTileService(
                    component,
                    getString(R.string.quick_settings_motion_assist_label),
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_qs_motion_assist),
                    mainExecutor
                ) { result ->
                    if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        Toast.makeText(this, R.string.motion_assist_shortcut_dialog_title, Toast.LENGTH_SHORT).show()
                    }
                }
                return
            } catch (e: Exception) {}
        }

        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_qs_motion_assist)
            .setTitle(R.string.motion_assist_shortcut_dialog_title)
            .setMessage(getString(R.string.motion_assist_shortcut_dialog_info_part1) + "\n\n" + getString(R.string.motion_assist_shortcut_dialog_info))
            .setPositiveButton(R.string.motion_assist_shortcut_dialog_pos_button_text, null)
            .show()
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.motion_assist_overlay_permission_title)
                .setMessage(R.string.motion_assist_overlay_permission_desc)
                .setPositiveButton(R.string.motion_assist_grant_permission) { _, _ ->
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        startActivity(fallback)
                    }
                }
                .setNeutralButton(R.string.motion_assist_open_app_info) { _, _ ->
                    try {
                        val appInfoIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(appInfoIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.lottieTutorialView.resumeAnimation()
        motionEstimator.start()
        updatePreviewView()
    }

    override fun onPause() {
        super.onPause()
        binding.lottieTutorialView.pauseAnimation()
        motionEstimator.stop()
    }
}
