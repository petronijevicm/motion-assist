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
 * Motion sample in screen space, produced by [MotionFilter].
 *
 * @property lateral horizontal acceleration towards the screen's right edge, m/s^2
 * @property longitudinal horizontal acceleration away from the viewer (vehicle forward), m/s^2
 * @property yawRateRps rotation about world vertical, positive = turning left, rad/s
 * @property isHandling true while the phone itself is being re-oriented in the hand
 */
data class MotionVector(
    val lateral: Float,
    val longitudinal: Float,
    val yawRateRps: Float,
    val isHandling: Boolean = false
) {
    companion object {
        val ZERO = MotionVector(0f, 0f, 0f)
    }
}
