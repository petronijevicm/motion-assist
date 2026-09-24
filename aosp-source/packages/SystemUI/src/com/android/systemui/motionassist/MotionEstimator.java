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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Fuses linear-acceleration, rotation-vector, and gyroscope data into world-space
 * motion vectors so the dot field moves like a fixed world coordinate frame you move through.
 */
public class MotionEstimator {

    public interface Callback {
        void onMotionUpdated(MotionVector motion);
        default void onVehicleStateChanged(boolean isMoving) {}
    }

    private static final float ACCEL_TIME_CONSTANT_SEC = 0.08f;
    private static final float GYRO_TIME_CONSTANT_SEC = 0.03f;
    private static final float FLAT_ROLL_GUARD_SQ = 0.04f;
    private static final float FLAT_PITCH_GUARD_SQ = 0.04f;
    private static final float STILL_ACCEL_MAG_SQ = 0.09f;
    private static final float STILL_GYRO_MAG_SQ = 0.0025f;
    private static final float STILL_SETTLE_SEC = 0.8f;
    private static final float BIAS_TRACK_SEC = 1.5f;
    private static final float GYRO_DEADBAND_RPS = 0.01f;

    private static final float VEHICLE_MOTION_THRESHOLD_SQ = 0.08f;
    private static final float VEHICLE_START_TIME_SEC = 2.0f;
    private static final float VEHICLE_STOP_TIME_SEC = 6.0f;

    private float mVehicleMovingAccumSec = 0f;
    private float mVehicleStillAccumSec = 0f;
    private boolean mIsVehicleMoving = false;

    private final SensorManager mSensorManager;
    private final Sensor mLinearAccel;
    private final Sensor mRotationVector;
    private final Sensor mGyroscope;
    private Callback mCallback;

    private final float[] mRotationMatrix = new float[9];
    private boolean mHasRotation = false;

    private float mFilteredX = 0f;
    private float mFilteredY = 0f;
    private float mFilteredZ = 0f;
    private float mLastRollRadians = 0f;
    private float mFilteredYawRate = 0f;
    private float mFilteredPitchRate = 0f;
    private long mLastAccelTsNs = 0L;
    private long mLastGyroTsNs = 0L;

    private float mGxBias = 0f;
    private float mGyBias = 0f;
    private float mGzBias = 0f;
    private float mStillAccumSec = 0f;
    private float mLastAccelMagSq = 0f;

    private final SensorEventListener mListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ROTATION_VECTOR:
                    SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                    mHasRotation = true;
                    break;
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    if (!mHasRotation) return;
                    updateAccel(event.values[0], event.values[1], event.values[2], event.timestamp);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    if (!mHasRotation) return;
                    updateGyro(event.values[0], event.values[1], event.values[2], event.timestamp);
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    public MotionEstimator(Context context) {
        mSensorManager = context.getSystemService(SensorManager.class);
        if (mSensorManager != null) {
            mLinearAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        } else {
            mLinearAccel = null;
            mRotationVector = null;
            mGyroscope = null;
        }
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    private void updateAccel(float ax, float ay, float az, long tsNs) {
        float ux = mRotationMatrix[6];
        float uy = mRotationMatrix[7];
        float uz = mRotationMatrix[8];

        float dot = ax * ux + ay * uy + az * uz;
        float hx = ax - dot * ux;
        float hy = ay - dot * uy;
        float hz = az - dot * uz;

        float dt = (mLastAccelTsNs == 0L) ? 0.02f : Math.max(0.001f, Math.min(0.2f, (tsNs - mLastAccelTsNs) / 1e9f));
        mLastAccelTsNs = tsNs;
        float alpha = dt / (ACCEL_TIME_CONSTANT_SEC + dt);
        mFilteredX += alpha * (hx - mFilteredX);
        mFilteredY += alpha * (hy - mFilteredY);
        mFilteredZ += alpha * (hz - mFilteredZ);

        mLastAccelMagSq = hx * hx + hy * hy + hz * hz;

        float projLenSq = ux * ux + uy * uy;
        if (projLenSq > FLAT_ROLL_GUARD_SQ) {
            mLastRollRadians = (float) Math.atan2(ux, uy);
        }

        publish();
    }

    private void updateGyro(float gx, float gy, float gz, long tsNs) {
        float dt = (mLastGyroTsNs == 0L) ? 0.01f : Math.max(0.001f, Math.min(0.2f, (tsNs - mLastGyroTsNs) / 1e9f));
        mLastGyroTsNs = tsNs;

        float rawGyroMagSq = gx * gx + gy * gy + gz * gz;
        boolean isStill = mLastAccelMagSq < STILL_ACCEL_MAG_SQ && rawGyroMagSq < STILL_GYRO_MAG_SQ;
        if (isStill) {
            mStillAccumSec += dt;
            if (mStillAccumSec > STILL_SETTLE_SEC) {
                float biasAlpha = dt / (BIAS_TRACK_SEC + dt);
                mGxBias += biasAlpha * (gx - mGxBias);
                mGyBias += biasAlpha * (gy - mGyBias);
                mGzBias += biasAlpha * (gz - mGzBias);
            }
        } else {
            mStillAccumSec = 0f;
        }

        // Vehicle detection logic: sustained motion vs sustained standstill
        boolean movingCandidate = mLastAccelMagSq > VEHICLE_MOTION_THRESHOLD_SQ || rawGyroMagSq > 0.04f;
        if (movingCandidate) {
            mVehicleMovingAccumSec += dt;
            mVehicleStillAccumSec = 0f;
            if (!mIsVehicleMoving && mVehicleMovingAccumSec >= VEHICLE_START_TIME_SEC) {
                mIsVehicleMoving = true;
                if (mCallback != null) {
                    mCallback.onVehicleStateChanged(true);
                }
            }
        } else if (isStill) {
            mVehicleStillAccumSec += dt;
            mVehicleMovingAccumSec = 0f;
            if (mIsVehicleMoving && mVehicleStillAccumSec >= VEHICLE_STOP_TIME_SEC) {
                mIsVehicleMoving = false;
                if (mCallback != null) {
                    mCallback.onVehicleStateChanged(false);
                }
            }
        }

        float gxd = gx - mGxBias;
        float gyd = gy - mGyBias;
        float gzd = gz - mGzBias;

        float owx = mRotationMatrix[0] * gxd + mRotationMatrix[1] * gyd + mRotationMatrix[2] * gzd;
        float owy = mRotationMatrix[3] * gxd + mRotationMatrix[4] * gyd + mRotationMatrix[5] * gzd;
        float owz = mRotationMatrix[6] * gxd + mRotationMatrix[7] * gyd + mRotationMatrix[8] * gzd;

        float yawRate = owz;

        float fx = -mRotationMatrix[2];
        float fy = -mRotationMatrix[5];
        float fLenSq = fx * fx + fy * fy;
        float pitchRate;
        if (fLenSq > FLAT_PITCH_GUARD_SQ) {
            float invLen = 1f / (float) Math.sqrt(fLenSq);
            float sideX = -fy * invLen;
            float sideY = fx * invLen;
            pitchRate = owx * sideX + owy * sideY;
        } else {
            pitchRate = 0f;
        }

        float alpha = dt / (GYRO_TIME_CONSTANT_SEC + dt);
        mFilteredYawRate += alpha * (yawRate - mFilteredYawRate);
        mFilteredPitchRate += alpha * (pitchRate - mFilteredPitchRate);

        publish();
    }

    private float deadband(float v, float threshold) {
        if (v > threshold) return v - threshold;
        if (v < -threshold) return v + threshold;
        return 0f;
    }

    private void publish() {
        if (mCallback != null) {
            MotionVector vector = new MotionVector(
                    mFilteredX,
                    mFilteredY,
                    mFilteredZ,
                    mLastRollRadians,
                    deadband(mFilteredYawRate, GYRO_DEADBAND_RPS),
                    deadband(mFilteredPitchRate, GYRO_DEADBAND_RPS)
            );
            mCallback.onMotionUpdated(vector);
        }
    }

    public void start() {
        if (mSensorManager != null) {
            if (mLinearAccel != null) {
                mSensorManager.registerListener(mListener, mLinearAccel, SensorManager.SENSOR_DELAY_GAME);
            }
            if (mRotationVector != null) {
                mSensorManager.registerListener(mListener, mRotationVector, SensorManager.SENSOR_DELAY_GAME);
            }
            if (mGyroscope != null) {
                mSensorManager.registerListener(mListener, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    public void stop() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mListener);
        }
        mHasRotation = false;
        mLastAccelTsNs = 0L;
        mLastGyroTsNs = 0L;
        mFilteredX = 0f;
        mFilteredY = 0f;
        mFilteredZ = 0f;
        mLastRollRadians = 0f;
        mFilteredYawRate = 0f;
        mFilteredPitchRate = 0f;
        mStillAccumSec = 0f;
        mLastAccelMagSq = 0f;
        mVehicleMovingAccumSec = 0f;
        mVehicleStillAccumSec = 0f;
        mIsVehicleMoving = false;
        if (mCallback != null) {
            mCallback.onMotionUpdated(MotionVector.ZERO);
        }
    }

    public boolean isVehicleMoving() {
        return mIsVehicleMoving;
    }
}
