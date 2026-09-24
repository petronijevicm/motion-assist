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

/**
 * 6-DOF Motion Vector describing horizontal acceleration, out-of-plane acceleration,
 * screen roll angle, and world angular rates (yaw and pitch).
 */
data class MotionVector(
    val x: Float,
    val y: Float,
    val outOfPlane: Float,
    val rollRadians: Float,
    val yawRateRps: Float,
    val pitchRateRps: Float
) {
    companion object {
        val ZERO = MotionVector(0f, 0f, 0f, 0f, 0f, 0f)
    }
}
