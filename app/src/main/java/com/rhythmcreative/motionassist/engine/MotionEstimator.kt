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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Fuses linear-acceleration, rotation-vector, and gyroscope data into world-space
 * motion vectors so the cue field moves like a fixed world coordinate frame you move through.
 */
class MotionEstimator(context: Context) {

    interface Callback {
        fun onMotionUpdated(motion: MotionVector)
        fun onVehicleStateChanged(isMoving: Boolean) {}
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val linearAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var callback: Callback? = null
    private val rotationMatrix = FloatArray(9)
    private var hasRotation = false

    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f
    private var lastRollRadians = 0f
    private var filteredYawRate = 0f
    private var filteredPitchRate = 0f
    private var lastAccelTsNs = 0L
    private var lastGyroTsNs = 0L

    private var gxBias = 0f
    private var gyBias = 0f
    private var gzBias = 0f
    private var stillAccumSec = 0f
    private var lastAccelMagSq = 0f

    private var vehicleMovingAccumSec = 0f
    private var vehicleStillAccumSec = 0f
    var isVehicleMoving = false
        private set

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    hasRotation = true
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    if (!hasRotation) return
                    updateAccel(event.values[0], event.values[1], event.values[2], event.timestamp)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (!hasRotation) return
                    updateGyro(event.values[0], event.values[1], event.values[2], event.timestamp)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun setCallback(cb: Callback) {
        callback = cb
    }

    private fun updateAccel(ax: Float, ay: Float, az: Float, tsNs: Long) {
        val ux = rotationMatrix[6]
        val uy = rotationMatrix[7]
        val uz = rotationMatrix[8]

        val dot = ax * ux + ay * uy + az * uz
        val hx = ax - dot * ux
        val hy = ay - dot * uy
        val hz = az - dot * uz

        val dt = if (lastAccelTsNs == 0L) 0.02f else max(0.001f, min(0.2f, (tsNs - lastAccelTsNs) / 1e9f))
        lastAccelTsNs = tsNs
        val alpha = dt / (ACCEL_TIME_CONSTANT_SEC + dt)
        filteredX += alpha * (hx - filteredX)
        filteredY += alpha * (hy - filteredY)
        filteredZ += alpha * (hz - filteredZ)

        lastAccelMagSq = hx * hx + hy * hy + hz * hz

        val projLenSq = ux * ux + uy * uy
        if (projLenSq > FLAT_ROLL_GUARD_SQ) {
            lastRollRadians = atan2(ux, uy)
        }

        publish()
    }

    private fun updateGyro(gx: Float, gy: Float, gz: Float, tsNs: Long) {
        val dt = if (lastGyroTsNs == 0L) 0.01f else max(0.001f, min(0.2f, (tsNs - lastGyroTsNs) / 1e9f))
        lastGyroTsNs = tsNs

        val rawGyroMagSq = gx * gx + gy * gy + gz * gz
        val isStill = lastAccelMagSq < STILL_ACCEL_MAG_SQ && rawGyroMagSq < STILL_GYRO_MAG_SQ
        if (isStill) {
            stillAccumSec += dt
            if (stillAccumSec > STILL_SETTLE_SEC) {
                val biasAlpha = dt / (BIAS_TRACK_SEC + dt)
                gxBias += biasAlpha * (gx - gxBias)
                gyBias += biasAlpha * (gy - gyBias)
                gzBias += biasAlpha * (gz - gzBias)
            }
        } else {
            stillAccumSec = 0f
        }

        // Vehicle detection logic: sustained movement vs sustained standstill
        val movingCandidate = lastAccelMagSq > VEHICLE_MOTION_THRESHOLD_SQ || rawGyroMagSq > 0.04f
        if (movingCandidate) {
            vehicleMovingAccumSec += dt
            vehicleStillAccumSec = 0f
            if (!isVehicleMoving && vehicleMovingAccumSec >= VEHICLE_START_TIME_SEC) {
                isVehicleMoving = true
                callback?.onVehicleStateChanged(true)
            }
        } else if (isStill) {
            vehicleStillAccumSec += dt
            vehicleMovingAccumSec = 0f
            if (isVehicleMoving && vehicleStillAccumSec >= VEHICLE_STOP_TIME_SEC) {
                isVehicleMoving = false
                callback?.onVehicleStateChanged(false)
            }
        }

        val gxd = gx - gxBias
        val gyd = gy - gyBias
        val gzd = gz - gzBias

        val owx = rotationMatrix[0] * gxd + rotationMatrix[1] * gyd + rotationMatrix[2] * gzd
        val owy = rotationMatrix[3] * gxd + rotationMatrix[4] * gyd + rotationMatrix[5] * gzd
        val owz = rotationMatrix[6] * gxd + rotationMatrix[7] * gyd + rotationMatrix[8] * gzd

        val yawRate = owz
        val fx = -rotationMatrix[2]
        val fy = -rotationMatrix[5]
        val fLenSq = fx * fx + fy * fy
        val pitchRate = if (fLenSq > FLAT_PITCH_GUARD_SQ) {
            val invLen = 1f / sqrt(fLenSq)
            val sideX = -fy * invLen
            val sideY = fx * invLen
            owx * sideX + owy * sideY
        } else {
            0f
        }

        val alpha = dt / (GYRO_TIME_CONSTANT_SEC + dt)
        filteredYawRate += alpha * (yawRate - filteredYawRate)
        filteredPitchRate += alpha * (pitchRate - filteredPitchRate)

        publish()
    }

    private fun deadband(v: Float, threshold: Float): Float {
        return when {
            v > threshold -> v - threshold
            v < -threshold -> v + threshold
            else -> 0f
        }
    }

    private fun publish() {
        val vector = MotionVector(
            filteredX,
            filteredY,
            filteredZ,
            lastRollRadians,
            deadband(filteredYawRate, GYRO_DEADBAND_RPS),
            deadband(filteredPitchRate, GYRO_DEADBAND_RPS)
        )
        callback?.onMotionUpdated(vector)
    }

    fun start() {
        sensorManager?.let { sm ->
            linearAccel?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
            rotationVector?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
            gyroscope?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(sensorListener)
        hasRotation = false
        lastAccelTsNs = 0L
        lastGyroTsNs = 0L
        filteredX = 0f
        filteredY = 0f
        filteredZ = 0f
        lastRollRadians = 0f
        filteredYawRate = 0f
        filteredPitchRate = 0f
        stillAccumSec = 0f
        lastAccelMagSq = 0f
        vehicleMovingAccumSec = 0f
        vehicleStillAccumSec = 0f
        isVehicleMoving = false
        callback?.onMotionUpdated(MotionVector.ZERO)
    }

    companion object {
        private const val ACCEL_TIME_CONSTANT_SEC = 0.08f
        private const val GYRO_TIME_CONSTANT_SEC = 0.03f
        private const val FLAT_ROLL_GUARD_SQ = 0.04f
        private const val FLAT_PITCH_GUARD_SQ = 0.04f
        private const val STILL_ACCEL_MAG_SQ = 0.09f
        private const val STILL_GYRO_MAG_SQ = 0.0025f
        private const val STILL_SETTLE_SEC = 0.8f
        private const val BIAS_TRACK_SEC = 1.5f
        private const val GYRO_DEADBAND_RPS = 0.01f

        private const val VEHICLE_MOTION_THRESHOLD_SQ = 0.08f
        private const val VEHICLE_START_TIME_SEC = 2.0f
        private const val VEHICLE_STOP_TIME_SEC = 6.0f
    }
}
