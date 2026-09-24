/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Display

/**
 * Android glue around [MotionFilter]: registers sensors on a background thread, tracks the
 * display rotation, and exposes the latest [MotionVector] for the render loop to sample.
 *
 * Sensor events are never forwarded one-by-one to the UI thread; the cue view pulls
 * [latest] once per display frame instead.
 *
 * In [lowPower] mode (cues hidden, only vehicle detection running) sensors are sampled at a
 * low rate and batched in the sensor hub, so the application processor can stay asleep.
 */
class MotionEstimator(context: Context) {

    interface Callback {
        fun onVehicleStateChanged(isMoving: Boolean) {}
    }

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val linearAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val filter = MotionFilter()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var callback: Callback? = null

    // Fallback gravity estimate when TYPE_GRAVITY / TYPE_LINEAR_ACCELERATION are missing
    private val lpGravity = FloatArray(3)
    private var lpGravityInit = false
    private var lastRawAccelTsNs = 0L

    @Volatile
    var latest: MotionVector = MotionVector.ZERO
        private set

    @Volatile
    var isVehicleMoving = false
        private set

    val isRunning: Boolean
        get() = sensorThread != null

    /** Low-rate, hardware-batched sampling; enough for vehicle detection, not for cues. */
    var lowPower: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (isRunning) registerSensors()
        }

    /** Acceleration smoothing, see [MotionFilter.accelSmoothingSec]. */
    var smoothingSec: Float = MotionFilter.DEFAULT_ACCEL_SMOOTHING_SEC
        set(value) {
            field = value
            sensorHandler?.post { filter.accelSmoothingSec = value } ?: run { filter.accelSmoothingSec = value }
        }

    /** True when the device has the sensors needed for useful cues. */
    val isSupported: Boolean
        get() = accelerometer != null || linearAccel != null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY -> filter.onGravity(v[0], v[1], v[2])
                Sensor.TYPE_LINEAR_ACCELERATION -> feedLinear(v[0], v[1], v[2], event.timestamp)
                Sensor.TYPE_ACCELEROMETER -> onRawAccel(v[0], v[1], v[2], event.timestamp)
                Sensor.TYPE_GYROSCOPE -> {
                    filter.onGyroscope(v[0], v[1], v[2], event.timestamp)
                    latest = filter.output
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) syncDisplayRotation()
        }
    }

    fun setCallback(cb: Callback?) {
        callback = cb
    }

    private fun onRawAccel(x: Float, y: Float, z: Float, tsNs: Long) {
        if (gravitySensor != null && linearAccel != null) return
        // Only used on devices without fused virtual sensors
        val dt = if (lastRawAccelTsNs == 0L) 0.02f else ((tsNs - lastRawAccelTsNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastRawAccelTsNs = tsNs
        if (!lpGravityInit) {
            lpGravity[0] = x; lpGravity[1] = y; lpGravity[2] = z
            lpGravityInit = true
        } else {
            val a = dt / (FALLBACK_GRAVITY_SEC + dt)
            lpGravity[0] += a * (x - lpGravity[0])
            lpGravity[1] += a * (y - lpGravity[1])
            lpGravity[2] += a * (z - lpGravity[2])
        }
        if (gravitySensor == null) filter.onGravity(lpGravity[0], lpGravity[1], lpGravity[2])
        if (linearAccel == null) {
            feedLinear(x - lpGravity[0], y - lpGravity[1], z - lpGravity[2], tsNs)
        }
    }

    private fun feedLinear(x: Float, y: Float, z: Float, tsNs: Long) {
        val changed = filter.onLinearAcceleration(x, y, z, tsNs)
        latest = filter.output
        if (changed) {
            val moving = filter.isVehicleMoving
            isVehicleMoving = moving
            mainHandler.post { callback?.onVehicleStateChanged(moving) }
        }
    }

    private fun syncDisplayRotation() {
        val rotation = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        val handler = sensorHandler
        if (handler != null) handler.post { filter.setDisplayRotation(rotation) } else filter.setDisplayRotation(rotation)
    }

    fun start() {
        val sm = sensorManager ?: return
        if (sensorThread != null) return
        val thread = HandlerThread("MotionAssistSensors").apply { start() }
        val handler = Handler(thread.looper)
        sensorThread = thread
        sensorHandler = handler

        val smoothing = smoothingSec
        handler.post {
            filter.reset()
            filter.accelSmoothingSec = smoothing
        }
        syncDisplayRotation()
        displayManager?.registerDisplayListener(displayListener, mainHandler)
        registerSensors()
    }

    private fun registerSensors() {
        val sm = sensorManager ?: return
        val handler = sensorHandler ?: return
        sm.unregisterListener(sensorListener)
        val periodUs = if (lowPower) LOW_POWER_PERIOD_US else ACTIVE_PERIOD_US
        val latencyUs = if (lowPower) LOW_POWER_BATCH_US else 0
        fun register(sensor: Sensor?) {
            sensor ?: return
            sm.registerListener(sensorListener, sensor, periodUs, latencyUs, handler)
        }
        register(gravitySensor)
        register(linearAccel)
        if (gravitySensor == null || linearAccel == null) register(accelerometer)
        register(gyroscope)
    }

    fun stop() {
        sensorManager?.unregisterListener(sensorListener)
        displayManager?.unregisterDisplayListener(displayListener)
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null
        lpGravityInit = false
        lastRawAccelTsNs = 0L
        latest = MotionVector.ZERO
        isVehicleMoving = false
    }

    companion object {
        private const val FALLBACK_GRAVITY_SEC = 0.5f
        // 50 Hz while cues are on screen
        private const val ACTIVE_PERIOD_US = 20_000
        // 10 Hz, delivered in 2 s batches while only watching for vehicle motion
        private const val LOW_POWER_PERIOD_US = 100_000
        private const val LOW_POWER_BATCH_US = 2_000_000
    }
}
