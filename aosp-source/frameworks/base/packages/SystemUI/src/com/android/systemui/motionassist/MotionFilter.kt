/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.motionassist

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure (Android-free) sensor fusion core, so it can be unit tested on the JVM.
 *
 * Everything is computed relative to the gravity direction and the screen axes, so the
 * compass heading never matters (magnetometer glitches inside a car cannot cause jumps).
 *
 * Inputs (all in the device sensor frame):
 *  - gravity:             "up" direction, from TYPE_GRAVITY or a low-passed accelerometer
 *  - linear acceleration: acceleration with gravity removed
 *  - gyroscope:           angular rate
 *
 * Outputs a [MotionVector] expressed in screen space:
 *  - lateral:      horizontal acceleration along the screen's right edge direction (m/s^2)
 *  - longitudinal: horizontal acceleration "away from the viewer" (m/s^2)
 *  - vertical:     acceleration along world up, i.e. bumps and dips (m/s^2)
 *  - yawRate:      rotation about the world vertical, positive = turning left (rad/s)
 *  - roll / pitch: attitude of the screen relative to gravity, for the horizon line
 *
 * Tilting the phone (looking up / down, rolling it in the hand) does not produce cue motion:
 *  - there is no gravity-tilt term, only true linear acceleration is used;
 *  - pitch/roll rotation is excluded from the yaw rate by projecting onto world vertical;
 *  - while the phone is being actively re-oriented in the hand, accelerometer input is
 *    frozen, because fused linear acceleration is unreliable during fast rotations.
 */
class MotionFilter {

    // Screen axes in device coordinates (depend on display rotation)
    private val screenX = floatArrayOf(1f, 0f, 0f)
    private val screenY = floatArrayOf(0f, 1f, 0f)

    // Unit "up" vector in device coordinates
    private var upX = 0f
    private var upY = 0f
    private var upZ = 1f
    var hasGravity = false
        private set

    // Horizontal screen basis (device coordinates), recomputed when gravity changes
    private var rightX = 1f
    private var rightY = 0f
    private var rightZ = 0f
    private var fwdX = 0f
    private var fwdY = 1f
    private var fwdZ = 0f

    // Acceleration pipeline state (screen frame)
    private var lpLateral = 0f
    private var lpLongitudinal = 0f
    private var lpVertical = 0f
    private var biasLateral = 0f
    private var biasLongitudinal = 0f
    private var biasVertical = 0f
    private var lastAccelTsNs = 0L

    // Gyro pipeline state. Bias is the mean rate over fixed windows in which the device is
    // completely at rest, blended into the running estimate once each window completes.
    private val gyroBias = FloatArray(3)
    private var gyroBiasKnown = false
    private val restSum = FloatArray(3)
    private var restSamples = 0
    private var restSec = 0f
    private var lastGyroTsNs = 0L
    private var yawRate = 0f
    private var tiltRate = 0f
    private var handlingHoldSec = 0f
    private var lastLinearMagSq = 0f

    // Vehicle detection state
    private var vehicleLpHoriz = 0f
    private var verticalEnergy = 0f
    private var vehicleEvidenceSec = 0f
    var isVehicleMoving = false
        private set

    /** Latest fused sample. */
    var output: MotionVector = MotionVector.ZERO
        private set

    /** Low-pass time constant for acceleration; lower = snappier, higher = calmer. */
    var accelSmoothingSec: Float = DEFAULT_ACCEL_SMOOTHING_SEC
        set(value) {
            field = value.coerceIn(MIN_ACCEL_SMOOTHING_SEC, MAX_ACCEL_SMOOTHING_SEC)
        }

    /**
     * Sets the display rotation (0, 1, 2, 3 = Surface.ROTATION_0 / 90 / 180 / 270), which
     * decides how device axes map to the on-screen x / y axes.
     */
    fun setDisplayRotation(rotation: Int) {
        when (rotation and 3) {
            1 -> { set(screenX, 0f, 1f, 0f); set(screenY, -1f, 0f, 0f) }
            2 -> { set(screenX, -1f, 0f, 0f); set(screenY, 0f, -1f, 0f) }
            3 -> { set(screenX, 0f, -1f, 0f); set(screenY, 1f, 0f, 0f) }
            else -> { set(screenX, 1f, 0f, 0f); set(screenY, 0f, 1f, 0f) }
        }
        updateBasis()
    }

    fun onGravity(gx: Float, gy: Float, gz: Float) {
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        if (norm < 1f) return
        upX = gx / norm
        upY = gy / norm
        upZ = gz / norm
        hasGravity = true
        updateBasis()
    }

    /**
     * Feeds a linear acceleration sample (gravity already removed). Returns true when
     * the vehicle-moving state changed.
     */
    fun onLinearAcceleration(ax: Float, ay: Float, az: Float, tsNs: Long): Boolean {
        val dt = stepDt(lastAccelTsNs, tsNs, 0.02f)
        lastAccelTsNs = tsNs
        lastLinearMagSq = ax * ax + ay * ay + az * az
        if (!hasGravity) return false

        val vertical = ax * upX + ay * upY + az * upZ
        val hx = ax - vertical * upX
        val hy = ay - vertical * upY
        val hz = az - vertical * upZ

        val lateral = hx * rightX + hy * rightY + hz * rightZ
        val longitudinal = hx * fwdX + hy * fwdY + hz * fwdZ

        val stateChanged = updateVehicleDetection(lateral, longitudinal, vertical, dt)

        if (handlingHoldSec > 0f) {
            // The phone is being moved in the hand: freeze input, relax towards neutral.
            handlingHoldSec -= dt
            val decay = dt / (HANDLING_DECAY_SEC + dt)
            lpLateral -= decay * lpLateral
            lpLongitudinal -= decay * lpLongitudinal
            lpVertical -= decay * lpVertical
        } else {
            val alpha = dt / (accelSmoothingSec + dt)
            lpLateral += alpha * (lateral - lpLateral)
            lpLongitudinal += alpha * (longitudinal - lpLongitudinal)
            lpVertical += alpha * (vertical - lpVertical)

            // Very slow high-pass: removes sensor offset and residual gravity leakage
            val beta = dt / (ACCEL_BIAS_SEC + dt)
            biasLateral += beta * (lpLateral - biasLateral)
            biasLongitudinal += beta * (lpLongitudinal - biasLongitudinal)
            biasVertical += beta * (lpVertical - biasVertical)
        }

        publish()
        return stateChanged
    }

    fun onGyroscope(gx: Float, gy: Float, gz: Float, tsNs: Long) {
        val dt = stepDt(lastGyroTsNs, tsNs, 0.01f)
        lastGyroTsNs = tsNs

        trackGyroBias(gx, gy, gz, dt)
        val wx = gx - gyroBias[0]
        val wy = gy - gyroBias[1]
        val wz = gz - gyroBias[2]

        // Rotation about the world vertical (vehicle turning) vs. everything else (hand tilt)
        val yaw = wx * upX + wy * upY + wz * upZ
        val tx = wx - yaw * upX
        val ty = wy - yaw * upY
        val tz = wz - yaw * upZ
        val tilt = sqrt(tx * tx + ty * ty + tz * tz)

        val alpha = dt / (GYRO_SMOOTHING_SEC + dt)
        yawRate += alpha * (yaw - yawRate)
        tiltRate += alpha * (tilt - tiltRate)

        if (tiltRate > HANDLING_TILT_RPS || abs(yaw) > HANDLING_YAW_RPS) {
            handlingHoldSec = HANDLING_HOLD_SEC
        }

        publish()
    }

    /** Clears motion state. Gyro bias is a hardware property and is kept. */
    fun reset() {
        lpLateral = 0f
        lpLongitudinal = 0f
        lpVertical = 0f
        biasLateral = 0f
        biasLongitudinal = 0f
        biasVertical = 0f
        lastAccelTsNs = 0L
        lastGyroTsNs = 0L
        yawRate = 0f
        tiltRate = 0f
        handlingHoldSec = 0f
        restSamples = 0
        restSec = 0f
        lastLinearMagSq = 0f
        vehicleLpHoriz = 0f
        verticalEnergy = 0f
        vehicleEvidenceSec = 0f
        isVehicleMoving = false
        hasGravity = false
        output = MotionVector.ZERO
    }

    private fun publish() {
        val handling = handlingHoldSec > 0f
        // Attitude for the horizon: "up" expressed in screen axes, and the screen normal's
        // elevation (device +z always points out of the display, whatever the rotation).
        val upOnScreenX = upX * screenX[0] + upY * screenX[1] + upZ * screenX[2]
        val upOnScreenY = upX * screenY[0] + upY * screenY[1] + upZ * screenY[2]
        output = MotionVector(
            lateral = softDeadband(lpLateral - biasLateral, ACCEL_DEADBAND),
            longitudinal = softDeadband(lpLongitudinal - biasLongitudinal, ACCEL_DEADBAND),
            vertical = softDeadband(lpVertical - biasVertical, VERTICAL_DEADBAND),
            // Hand rotations are not vehicle turns: don't let a wrist flick scroll the field
            yawRateRps = if (handling) 0f else softDeadband(yawRate, YAW_DEADBAND_RPS),
            rollRadians = if (hasGravity) atan2(upOnScreenX, upOnScreenY) else 0f,
            pitchRadians = if (hasGravity) asin(upZ.coerceIn(-1f, 1f)) else 0f,
            levelConfidence = if (hasGravity) sqrt(upOnScreenX * upOnScreenX + upOnScreenY * upOnScreenY) else 0f,
            isHandling = handling
        )
    }

    private fun trackGyroBias(gx: Float, gy: Float, gz: Float, dt: Float) {
        val atRest = lastLinearMagSq < REST_ACCEL_MAG_SQ &&
            gx * gx + gy * gy + gz * gz < REST_GYRO_MAG_SQ
        if (!atRest) {
            restSamples = 0
            restSec = 0f
            restSum.fill(0f)
            return
        }
        restSum[0] += gx
        restSum[1] += gy
        restSum[2] += gz
        restSamples++
        restSec += dt
        if (restSec < REST_WINDOW_SEC) return

        val blend = if (gyroBiasKnown) REST_WINDOW_BLEND else 1f
        for (i in 0..2) {
            val mean = restSum[i] / restSamples
            gyroBias[i] += blend * (mean - gyroBias[i])
        }
        gyroBiasKnown = true
        restSamples = 0
        restSec = 0f
        restSum.fill(0f)
    }

    private fun updateVehicleDetection(lateral: Float, longitudinal: Float, vertical: Float, dt: Float): Boolean {
        val horiz = sqrt(lateral * lateral + longitudinal * longitudinal)
        val a = dt / (VEHICLE_LP_SEC + dt)
        vehicleLpHoriz += a * (horiz - vehicleLpHoriz)
        verticalEnergy += a * (vertical * vertical - verticalEnergy)

        val walking = verticalEnergy > WALKING_VERTICAL_ENERGY
        val handling = handlingHoldSec > 0f
        if (!walking && !handling && vehicleLpHoriz > VEHICLE_ACCEL_THRESHOLD) {
            vehicleEvidenceSec = min(VEHICLE_EVIDENCE_MAX_SEC, vehicleEvidenceSec + dt)
        } else {
            val drain = if (walking) VEHICLE_WALKING_DRAIN else VEHICLE_IDLE_DRAIN
            vehicleEvidenceSec = max(0f, vehicleEvidenceSec - dt * drain)
        }

        val before = isVehicleMoving
        if (!isVehicleMoving && vehicleEvidenceSec >= VEHICLE_START_EVIDENCE_SEC) {
            isVehicleMoving = true
        } else if (isVehicleMoving && vehicleEvidenceSec <= 0f) {
            isVehicleMoving = false
        }
        return before != isVehicleMoving
    }

    private fun updateBasis() {
        // Right = screen x projected onto the horizontal plane
        var rx = screenX[0]; var ry = screenX[1]; var rz = screenX[2]
        val dx = rx * upX + ry * upY + rz * upZ
        rx -= dx * upX; ry -= dx * upY; rz -= dx * upZ
        var lenSq = rx * rx + ry * ry + rz * rz

        if (lenSq < MIN_AXIS_LEN_SQ) {
            // Screen x points (nearly) straight up/down: derive "right" from screen y instead
            var sx = screenY[0]; var sy = screenY[1]; var sz = screenY[2]
            val dy = sx * upX + sy * upY + sz * upZ
            sx -= dy * upX; sy -= dy * upY; sz -= dy * upZ
            // right = yh x up
            rx = sy * upZ - sz * upY
            ry = sz * upX - sx * upZ
            rz = sx * upY - sy * upX
            lenSq = rx * rx + ry * ry + rz * rz
            if (lenSq < 1e-6f) return
        }

        val inv = 1f / sqrt(lenSq)
        rightX = rx * inv; rightY = ry * inv; rightZ = rz * inv
        // forward = up x right: away from the viewer, level with the ground
        fwdX = upY * rightZ - upZ * rightY
        fwdY = upZ * rightX - upX * rightZ
        fwdZ = upX * rightY - upY * rightX
    }

    private fun set(v: FloatArray, x: Float, y: Float, z: Float) {
        v[0] = x; v[1] = y; v[2] = z
    }

    private fun stepDt(lastNs: Long, nowNs: Long, fallback: Float): Float {
        if (lastNs == 0L || nowNs <= lastNs) return fallback
        return min(MAX_DT_SEC, (nowNs - lastNs) / 1e9f)
    }

    companion object {
        private const val MAX_DT_SEC = 0.1f

        const val DEFAULT_ACCEL_SMOOTHING_SEC = 0.25f
        const val MIN_ACCEL_SMOOTHING_SEC = 0.08f
        const val MAX_ACCEL_SMOOTHING_SEC = 0.8f
        const val ACCEL_BIAS_SEC = 20f
        const val ACCEL_DEADBAND = 0.08f
        const val VERTICAL_DEADBAND = 0.15f
        private const val HANDLING_DECAY_SEC = 0.6f

        private const val GYRO_SMOOTHING_SEC = 0.08f
        const val YAW_DEADBAND_RPS = 0.03f
        const val HANDLING_TILT_RPS = 0.45f
        const val HANDLING_YAW_RPS = 1.2f
        const val HANDLING_HOLD_SEC = 0.5f

        // At rest: < 0.25 m/s^2 linear and < ~3.6 deg/s rotation for a whole window
        private const val REST_ACCEL_MAG_SQ = 0.0625f
        private const val REST_GYRO_MAG_SQ = 0.004f
        private const val REST_WINDOW_SEC = 1f
        private const val REST_WINDOW_BLEND = 0.3f

        private const val MIN_AXIS_LEN_SQ = 0.1f

        private const val VEHICLE_LP_SEC = 1.0f
        private const val VEHICLE_ACCEL_THRESHOLD = 0.35f
        private const val WALKING_VERTICAL_ENERGY = 2.5f
        const val VEHICLE_START_EVIDENCE_SEC = 3f
        private const val VEHICLE_EVIDENCE_MAX_SEC = 6f
        // Evidence drains at 3% of real time: ~3 minutes without any acceleration to switch off
        private const val VEHICLE_IDLE_DRAIN = 0.03f
        private const val VEHICLE_WALKING_DRAIN = 0.5f

        /** Maps the 0..100 responsiveness setting: 0 -> 0.6 s, 70 -> 0.25 s, 100 -> 0.1 s. */
        fun smoothingSecForResponsiveness(responsiveness: Int): Float =
            0.6f - 0.005f * responsiveness.coerceIn(0, 100)

        fun softDeadband(v: Float, threshold: Float): Float = when {
            v > threshold -> v - threshold
            v < -threshold -> v + threshold
            else -> 0f
        }
    }
}
