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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * Controller for the Color preference with circular swatch dialog matching Google Pixel.
 */
public class MotionAssistColorPreferenceController extends BasePreferenceController {

    public static final String SETTING_KEY = "motion_assist_color";
    public static final int COLOR_SYSTEM = 0;
    public static final int COLOR_PINK = 1;
    public static final int COLOR_YELLOW = 2;
    public static final int COLOR_GREEN = 3;
    public static final int COLOR_BLUE = 4;

    private static final int[] COLOR_VALUES = {
            Color.rgb(0x56, 0x5F, 0x67), // 0: Based on system (Theme dark slate)
            Color.rgb(0xFF, 0xAB, 0x91), // 1: Salmon pink
            Color.rgb(0xFF, 0xC1, 0x07), // 2: Amber yellow
            Color.rgb(0x81, 0xC7, 0x84), // 3: Mint green
            Color.rgb(0x90, 0xCA, 0xF9)  // 4: Soft blue
    };

    public MotionAssistColorPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        int color = Settings.Secure.getInt(mContext.getContentResolver(), SETTING_KEY, COLOR_SYSTEM);
        switch (color) {
            case COLOR_PINK:
                return mContext.getString(R.string.motion_assist_color_pink);
            case COLOR_YELLOW:
                return mContext.getString(R.string.motion_assist_color_yellow);
            case COLOR_GREEN:
                return mContext.getString(R.string.motion_assist_color_green);
            case COLOR_BLUE:
                return mContext.getString(R.string.motion_assist_color_blue);
            case COLOR_SYSTEM:
            default:
                return mContext.getString(R.string.motion_assist_color_system);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            showColorDialog(preference);
            return true;
        }
        return super.handlePreferenceTreeClick(preference);
    }

    private void showColorDialog(Preference preference) {
        final int currentColor = Settings.Secure.getInt(mContext.getContentResolver(), SETTING_KEY, COLOR_SYSTEM);
        final int[] selectedColor = {currentColor};

        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int) (16 * mContext.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad * 2, pad, pad * 2);

        final FrameLayout[] swatchContainers = new FrameLayout[COLOR_VALUES.length];

        for (int i = 0; i < COLOR_VALUES.length; i++) {
            final int index = i;
            FrameLayout container = new FrameLayout(mContext);
            int size = (int) (48 * mContext.getResources().getDisplayMetrics().density);
            int margin = (int) (6 * mContext.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            container.setLayoutParams(lp);

            View circle = new View(mContext);
            GradientDrawable circleDrawable = new GradientDrawable();
            circleDrawable.setShape(GradientDrawable.OVAL);
            int swatchColor;
            if (i == COLOR_SYSTEM) {
                try {
                    swatchColor = mContext.getColor(android.R.color.system_accent1_500);
                } catch (Throwable e) {
                    try {
                        swatchColor = com.android.settingslib.Utils.getColorAccentDefaultColor(mContext);
                    } catch (Throwable e2) {
                        swatchColor = Color.rgb(0x1A, 0x73, 0xE8);
                    }
                }
            } else {
                swatchColor = COLOR_VALUES[i];
            }
            circleDrawable.setColor(swatchColor);
            circle.setBackground(circleDrawable);

            int circleSize = (int) (36 * mContext.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER);
            circle.setLayoutParams(clp);

            container.addView(circle);
            swatchContainers[i] = container;

            updateSwatchSelection(container, i == selectedColor[0]);

            container.setOnClickListener(v -> {
                selectedColor[0] = index;
                for (int j = 0; j < swatchContainers.length; j++) {
                    updateSwatchSelection(swatchContainers[j], j == index);
                }
                // Live preview during selection
                Settings.Secure.putInt(mContext.getContentResolver(), SETTING_KEY, index);
            });

            layout.addView(container);
        }

        new AlertDialog.Builder(mContext)
                .setTitle(R.string.motion_assist_color_title)
                .setView(layout)
                .setPositiveButton(R.string.motion_assist_dialog_save, (dialog, which) -> {
                    Settings.Secure.putInt(mContext.getContentResolver(), SETTING_KEY, selectedColor[0]);
                    preference.setSummary(getSummary());
                })
                .setNegativeButton(R.string.motion_assist_dialog_cancel, (dialog, which) -> {
                    // Revert to initial color if cancelled
                    Settings.Secure.putInt(mContext.getContentResolver(), SETTING_KEY, currentColor);
                })
                .show();
    }

    private void updateSwatchSelection(FrameLayout container, boolean isSelected) {
        if (isSelected) {
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.OVAL);
            border.setColor(Color.TRANSPARENT);
            border.setStroke((int) (3 * mContext.getResources().getDisplayMetrics().density), Color.rgb(0x7C, 0x8B, 0xA1));
            container.setBackground(border);
        } else {
            container.setBackground(null);
        }
    }
}
