/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.motionassist;

/**
 * 6-DOF Motion Vector describing world-horizontal acceleration, out-of-plane acceleration,
 * screen roll angle, and world angular rates (yaw and pitch).
 */
public final class MotionVector {
    public static final MotionVector ZERO = new MotionVector(0f, 0f, 0f, 0f, 0f, 0f);

    public final float x;
    public final float y;
    public final float outOfPlane;
    public final float rollRadians;
    public final float yawRateRps;
    public final float pitchRateRps;

    public MotionVector(float x, float y, float outOfPlane, float rollRadians, float yawRateRps, float pitchRateRps) {
        this.x = x;
        this.y = y;
        this.outOfPlane = outOfPlane;
        this.rollRadians = rollRadians;
        this.yawRateRps = yawRateRps;
        this.pitchRateRps = pitchRateRps;
    }
}
