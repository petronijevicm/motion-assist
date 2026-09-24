/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.accessibility.motionassist;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

/**
 * On/off switches backed by a Motion Assist Settings.Secure key. The preference key names the
 * setting, so one controller serves the main switch, auto start, shuffle and smooth animation.
 */
public class MotionAssistTogglePreferenceController extends TogglePreferenceController {

    public MotionAssistTogglePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return settingKey() != null ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isChecked() {
        final String setting = settingKey();
        return setting != null && MotionAssistKeys.isOn(mContext, setting, defaultOn(setting));
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        final String setting = settingKey();
        return setting != null && MotionAssistKeys.put(mContext, setting, isChecked ? 1 : 0);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_accessibility;
    }

    private String settingKey() {
        switch (getPreferenceKey()) {
            case "motion_assist_main_switch":
                return MotionAssistKeys.ENABLED;
            case "motion_assist_auto_start":
                return MotionAssistKeys.VEHICLE_AUTO;
            case "motion_assist_shuffle":
                return MotionAssistKeys.SHUFFLE;
            case "motion_assist_smooth":
                return MotionAssistKeys.SMOOTH;
            default:
                return null;
        }
    }

    private static boolean defaultOn(String setting) {
        return MotionAssistKeys.SMOOTH.equals(setting);
    }
}
