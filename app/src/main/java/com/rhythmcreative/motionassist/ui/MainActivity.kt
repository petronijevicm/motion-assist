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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rhythmcreative.motionassist.R
import com.rhythmcreative.motionassist.databinding.ActivityMainBinding
import com.rhythmcreative.motionassist.engine.MotionEstimator
import com.rhythmcreative.motionassist.engine.MotionPreferences
import com.rhythmcreative.motionassist.engine.MotionVector
import com.rhythmcreative.motionassist.service.MotionAssistService

class MainActivity : AppCompatActivity(), MotionEstimator.Callback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: MotionPreferences
    private lateinit var motionEstimator: MotionEstimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = MotionPreferences(this)
        motionEstimator = MotionEstimator(this)
        motionEstimator.setCallback(this)

        setupUI()
        setupListeners()
        setupPreviewTouch()
    }

    private fun setupUI() {
        binding.switchMain.isChecked = prefs.isEnabled
        binding.switchAutoVehicle.isChecked = prefs.isVehicleAuto
        binding.switchRandomize.isChecked = prefs.isRandomize
        binding.switchSmoothAnimation.isChecked = prefs.isSmoothAnimation
        binding.sliderOpacity.value = prefs.opacity.toFloat()
        binding.textOpacityValue.text = "${getString(R.string.motion_assist_opacity_title)}: ${prefs.opacity}%"

        // Select shape button
        when (prefs.shapeIndex) {
            1 -> binding.btnShapeSquircle.isChecked = true
            2 -> binding.btnShapePentagon.isChecked = true
            3 -> binding.btnShapeDiamond.isChecked = true
            else -> binding.btnShapeCircle.isChecked = true
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

        updatePreviewView()
    }

    private fun updatePreviewView() {
        binding.previewCuesView.shapeIndex = prefs.shapeIndex
        binding.previewCuesView.colorIndex = prefs.colorIndex
        binding.previewCuesView.cueOpacity = prefs.opacity
        binding.previewCuesView.isRandomized = prefs.isRandomize
        binding.previewCuesView.isAdaptiveMode = (prefs.colorIndex == 5)
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

        binding.toggleGroupShape.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val shapeIdx = when (checkedId) {
                    R.id.btnShapeSquircle -> 1
                    R.id.btnShapePentagon -> 2
                    R.id.btnShapeDiamond -> 3
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

    private fun setupPreviewTouch() {
        var startX = 0f
        var startY = 0f

        binding.previewCuesView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - startX) * 0.75f
                    val dy = (event.y - startY) * 0.75f
                    binding.previewCuesView.updateOffset(dx, dy)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.previewCuesView.updateOffset(0f, 0f)
                    true
                }
                else -> false
            }
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
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        motionEstimator.start()
    }

    override fun onPause() {
        super.onPause()
        motionEstimator.stop()
    }

    override fun onMotionUpdated(motion: MotionVector) {
        val dx = motion.x * 20f
        val dy = motion.y * 20f
        runOnUiThread {
            binding.previewCuesView.updateOffset(dx, dy)
        }
    }
}
