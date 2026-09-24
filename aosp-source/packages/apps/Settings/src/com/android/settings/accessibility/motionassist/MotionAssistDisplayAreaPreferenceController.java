/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.accessibility.motionassist;

import android.content.Context;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;

/** Where cues appear: side bands (keeps the reading area clear) or the whole screen. */
public class MotionAssistDisplayAreaPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    public MotionAssistDisplayAreaPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (preference instanceof ListPreference) {
            final ListPreference list = (ListPreference) preference;
            list.setValue(String.valueOf(MotionAssistKeys.get(mContext, MotionAssistKeys.MODE, 0)));
            list.setSummary(list.getEntry());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final int mode = Integer.parseInt((String) newValue);
        if (!MotionAssistKeys.put(mContext, MotionAssistKeys.MODE, mode)) return false;
        if (preference instanceof ListPreference) {
            final ListPreference list = (ListPreference) preference;
            final int index = list.findIndexOfValue((String) newValue);
            if (index >= 0) list.setSummary(list.getEntries()[index]);
        }
        return true;
    }
}
