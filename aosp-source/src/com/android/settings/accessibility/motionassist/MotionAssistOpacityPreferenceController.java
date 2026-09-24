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

package com.android.settings.accessibility.motionassist;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.SliderPreferenceController;
import com.android.settings.widget.SeekBarPreference;

/**
 * Controller for the Opacity slider preference matching Google Pixel.
 */
public class MotionAssistOpacityPreferenceController extends SliderPreferenceController {

    public static final String SETTING_KEY = "motion_assist_opacity";
    private static final int DEFAULT_OPACITY = 100;
    private static final int MIN_OPACITY = 10;
    private static final int MAX_OPACITY = 100;

    public MotionAssistOpacityPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public int getSliderPosition() {
        return Settings.Secure.getInt(mContext.getContentResolver(), SETTING_KEY, DEFAULT_OPACITY);
    }

    @Override
    public boolean setSliderPosition(int position) {
        return Settings.Secure.putInt(mContext.getContentResolver(), SETTING_KEY, position);
    }

    @Override
    public int getMax() {
        return MAX_OPACITY;
    }

    @Override
    public int getMin() {
        return MIN_OPACITY;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (preference instanceof SeekBarPreference) {
            ((SeekBarPreference) preference).setProgress(getSliderPosition());
        }
    }
}
