/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.accessibility.motionassist;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.SliderPreferenceController;
import com.android.settingslib.widget.SliderPreference;

/**
 * Sliders backed by a Motion Assist Settings.Secure key: opacity, size, movement and
 * responsiveness. The preference key selects the setting and its range.
 */
public class MotionAssistSliderPreferenceController extends SliderPreferenceController {

    private final String mSetting;
    private final int mMin;
    private final int mMax;
    private final int mDefault;

    public MotionAssistSliderPreferenceController(Context context, String key) {
        super(context, key);
        switch (key) {
            case "motion_assist_opacity":
                mSetting = MotionAssistKeys.OPACITY; mMin = 10; mMax = 100; mDefault = 60;
                break;
            case "motion_assist_size":
                mSetting = MotionAssistKeys.SIZE; mMin = 50; mMax = 200; mDefault = 100;
                break;
            case "motion_assist_movement":
                mSetting = MotionAssistKeys.MOVEMENT; mMin = 25; mMax = 200; mDefault = 100;
                break;
            case "motion_assist_responsiveness":
                mSetting = MotionAssistKeys.RESPONSIVENESS; mMin = 0; mMax = 100; mDefault = 70;
                break;
            default:
                mSetting = null; mMin = 0; mMax = 100; mDefault = 0;
        }
    }

    @Override
    public int getAvailabilityStatus() {
        return mSetting != null ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference preference = screen.findPreference(getPreferenceKey());
        if (preference instanceof SliderPreference) {
            final SliderPreference slider = (SliderPreference) preference;
            slider.setMin(mMin);
            slider.setMax(mMax);
            slider.setUpdatesContinuously(true);
        }
    }

    @Override
    public int getSliderPosition() {
        if (mSetting == null) return mMin;
        return Math.max(mMin, Math.min(mMax, MotionAssistKeys.get(mContext, mSetting, mDefault)));
    }

    @Override
    public boolean setSliderPosition(int position) {
        return mSetting != null && MotionAssistKeys.put(mContext, mSetting,
                Math.max(mMin, Math.min(mMax, position)));
    }

    @Override
    public int getMax() {
        return mMax;
    }

    @Override
    public int getMin() {
        return mMin;
    }
}
