/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.accessibility.motionassist;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/** Entry in the Accessibility list; its summary shows whether cues are on, off or automatic. */
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
        if (!MotionAssistKeys.isOn(mContext, MotionAssistKeys.ENABLED, false)) {
            return mContext.getString(R.string.motion_assist_state_off);
        }
        return mContext.getString(MotionAssistKeys.isOn(mContext, MotionAssistKeys.VEHICLE_AUTO, false)
                ? R.string.motion_assist_state_auto
                : R.string.motion_assist_state_on);
    }
}
