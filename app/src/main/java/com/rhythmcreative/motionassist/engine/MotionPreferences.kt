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

package com.rhythmcreative.motionassist.engine

import android.content.Context
import android.content.SharedPreferences

class MotionPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var isVehicleAuto: Boolean
        get() = prefs.getBoolean(KEY_VEHICLE_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_VEHICLE_AUTO, value).apply()

    var colorIndex: Int
        get() = prefs.getInt(KEY_COLOR, 0)
        set(value) = prefs.edit().putInt(KEY_COLOR, value).apply()

    var shapeIndex: Int
        get() = prefs.getInt(KEY_SHAPE, 0)
        set(value) = prefs.edit().putInt(KEY_SHAPE, value).apply()

    var opacity: Int
        get() = prefs.getInt(KEY_OPACITY, 60)
        set(value) = prefs.edit().putInt(KEY_OPACITY, value).apply()

    var isRandomize: Boolean
        get() = prefs.getBoolean(KEY_RANDOMIZE, false)
        set(value) = prefs.edit().putBoolean(KEY_RANDOMIZE, value).apply()

    var isSmoothAnimation: Boolean
        get() = prefs.getBoolean(KEY_SMOOTH_ANIMATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SMOOTH_ANIMATION, value).apply()

    /** Cue travel distance in percent (25..200, default 100). */
    var sensitivity: Int
        get() = prefs.getInt(KEY_SENSITIVITY, 100)
        set(value) = prefs.edit().putInt(KEY_SENSITIVITY, value).apply()

    /** Cue size in percent (50..200, default 100). */
    var sizePercent: Int
        get() = prefs.getInt(KEY_SIZE, 100)
        set(value) = prefs.edit().putInt(KEY_SIZE, value).apply()

    /** 0 = calm / heavily smoothed, 100 = snappy (default 70). */
    var responsiveness: Int
        get() = prefs.getInt(KEY_RESPONSIVENESS, 70)
        set(value) = prefs.edit().putInt(KEY_RESPONSIVENESS, value).apply()

    /** Width of each side band with cues, percent of screen width (10..50, 50 = full screen). */
    var cueAreaPercent: Int
        get() = prefs.getInt(KEY_CUE_AREA, 20)
        set(value) = prefs.edit().putInt(KEY_CUE_AREA, value).apply()

    var isHorizon: Boolean
        get() = prefs.getBoolean(KEY_HORIZON, false)
        set(value) = prefs.edit().putBoolean(KEY_HORIZON, value).apply()

    /** Acceleration smoothing time constant derived from [responsiveness]. */
    val smoothingSec: Float
        get() = MotionFilter.smoothingSecForResponsiveness(responsiveness)

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS_NAME = "motion_assist_prefs"
        const val KEY_ENABLED = "motion_assist_enabled"
        const val KEY_VEHICLE_AUTO = "motion_assist_vehicle_auto"
        const val KEY_COLOR = "motion_assist_color"
        const val KEY_SHAPE = "motion_assist_shape"
        const val KEY_OPACITY = "motion_assist_opacity"
        const val KEY_RANDOMIZE = "motion_assist_randomize"
        const val KEY_SMOOTH_ANIMATION = "motion_assist_smooth_animation"
        const val KEY_SENSITIVITY = "motion_assist_sensitivity"
        const val KEY_SIZE = "motion_assist_size"
        const val KEY_RESPONSIVENESS = "motion_assist_responsiveness"
        const val KEY_CUE_AREA = "motion_assist_cue_area"
        const val KEY_HORIZON = "motion_assist_horizon"
    }
}
