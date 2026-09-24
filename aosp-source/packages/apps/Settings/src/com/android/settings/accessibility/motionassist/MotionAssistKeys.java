/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.accessibility.motionassist;

import android.content.Context;
import android.provider.Settings;

/**
 * Settings.Secure keys for Motion Assist, shared with SystemUI's MotionAssistController.
 * Keep names and defaults in sync with that class.
 */
final class MotionAssistKeys {

    static final String ENABLED = "motion_assist_enabled";
    static final String VEHICLE_AUTO = "motion_assist_vehicle_auto";
    static final String COLOR = "motion_assist_color";
    static final String SHAPE = "motion_assist_shape";
    static final String OPACITY = "motion_assist_opacity";
    static final String SIZE = "motion_assist_size";
    static final String MOVEMENT = "motion_assist_movement";
    static final String RESPONSIVENESS = "motion_assist_responsiveness";
    static final String MODE = "motion_assist_mode";
    static final String SHUFFLE = "motion_assist_randomize";
    static final String SMOOTH = "motion_assist_smooth_animation";
    /** Watched by SystemUI's Quick Settings auto-add list. */
    static final String ADD_TILE = "motion_assist_add_qs_tile";

    static final int COLOR_SYSTEM = 0;
    static final int COLOR_CONTRAST = 5;

    private MotionAssistKeys() {}

    static int get(Context context, String key, int def) {
        return Settings.Secure.getInt(context.getContentResolver(), key, def);
    }

    static boolean put(Context context, String key, int value) {
        return Settings.Secure.putInt(context.getContentResolver(), key, value);
    }

    static boolean isOn(Context context, String key, boolean def) {
        return get(context, key, def ? 1 : 0) == 1;
    }
}
