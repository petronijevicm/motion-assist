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

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * Entry controller displayed under Accessibility settings pointing to Motion Assist.
 */
public class MotionAssistEntryPreferenceController extends BasePreferenceController {

    public MotionAssistEntryPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        boolean isEnabled = Settings.Secure.getInt(
                mContext.getContentResolver(), "motion_assist_enabled", 0) == 1;
        return isEnabled
                ? mContext.getString(R.string.motion_assist_start_in_vehicle_title)
                : mContext.getString(R.string.switch_off_text);
    }
}
