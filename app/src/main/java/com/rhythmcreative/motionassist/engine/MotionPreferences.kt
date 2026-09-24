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
    }
}
