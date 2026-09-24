/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.data

import android.content.Context
import android.content.SharedPreferences
import com.petronijevicm.motionassist.cues.CuePalette
import com.petronijevicm.motionassist.cues.CueShape
import com.petronijevicm.motionassist.engine.MotionFilter

/**
 * All user settings, stored in one private SharedPreferences file.
 *
 * Integer "percent" settings are kept as whole numbers so they map 1:1 onto the sliders.
 */
class CueSettings(context: Context) {

    private val store: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Cues are wanted (manually on, or armed for auto start). */
    var enabled: Boolean
        get() = store.getBoolean(Key.ENABLED, false)
        set(value) = put(Key.ENABLED, value)

    /** Show cues only while vehicle motion is detected. */
    var autoStart: Boolean
        get() = store.getBoolean(Key.AUTO_START, false)
        set(value) = put(Key.AUTO_START, value)

    var shape: CueShape
        get() = CueShape.of(store.getInt(Key.SHAPE, 0))
        set(value) = put(Key.SHAPE, value.ordinal)

    var palette: CuePalette
        get() = CuePalette.of(store.getInt(Key.PALETTE, 0))
        set(value) = put(Key.PALETTE, value.ordinal)

    /** 10..100 */
    var opacity: Int
        get() = store.getInt(Key.OPACITY, 60)
        set(value) = put(Key.OPACITY, value)

    /** How far cues travel, 25..200 (100 = default). */
    var movement: Int
        get() = store.getInt(Key.MOVEMENT, 100)
        set(value) = put(Key.MOVEMENT, value)

    /** Cue size, 50..200 (100 = default). */
    var size: Int
        get() = store.getInt(Key.SIZE, 100)
        set(value) = put(Key.SIZE, value)

    /** 0 (calm) .. 100 (snappy), default 70. */
    var responsiveness: Int
        get() = store.getInt(Key.RESPONSIVENESS, 70)
        set(value) = put(Key.RESPONSIVENESS, value)

    /** Side band width in percent of screen width, 10..50 (50 = whole screen). */
    var area: Int
        get() = store.getInt(Key.AREA, 20)
        set(value) = put(Key.AREA, value)

    var horizon: Boolean
        get() = store.getBoolean(Key.HORIZON, false)
        set(value) = put(Key.HORIZON, value)

    /** Scatter cue positions and cycle shape and colour every few seconds. */
    var shuffle: Boolean
        get() = store.getBoolean(Key.SHUFFLE, false)
        set(value) = put(Key.SHUFFLE, value)

    /** Draw at the full display refresh rate (otherwise capped, and always capped in Battery Saver). */
    var smooth: Boolean
        get() = store.getBoolean(Key.SMOOTH, true)
        set(value) = put(Key.SMOOTH, value)

    val smoothingSec: Float
        get() = MotionFilter.smoothingSecForResponsiveness(responsiveness)

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        store.registerOnSharedPreferenceChangeListener(listener)

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        store.unregisterOnSharedPreferenceChangeListener(listener)

    private fun put(key: String, value: Boolean) = store.edit().putBoolean(key, value).apply()
    private fun put(key: String, value: Int) = store.edit().putInt(key, value).apply()

    object Key {
        const val ENABLED = "enabled"
        const val AUTO_START = "auto_start"
        const val SHAPE = "shape"
        const val PALETTE = "palette"
        const val OPACITY = "opacity"
        const val MOVEMENT = "movement"
        const val SIZE = "size"
        const val RESPONSIVENESS = "responsiveness"
        const val AREA = "area"
        const val HORIZON = "horizon"
        const val SHUFFLE = "shuffle"
        const val SMOOTH = "smooth"
    }

    private companion object {
        const val FILE = "cue_settings"
    }
}
