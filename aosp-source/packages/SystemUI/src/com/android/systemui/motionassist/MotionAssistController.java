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

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.app.motioncues.MotionCuesSettings;
import android.app.motioncues.MotionCuesVisualStyle;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.motioncues.MotionCuesUi;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.res.R;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * SystemUI Controller for Motion Assist.
 * Connects the physical 6-DOF motion estimator directly into Google's official
 * MotionCuesUi rendering and animation engine.
 */
@SysUISingleton
public class MotionAssistController implements CoreStartable, DarkIconDispatcher.DarkReceiver {

    private static final String TAG = "MotionAssistController";
    private static final long RANDOMIZE_INTERVAL_MS = 3500L;

    private static final int[] PALETTE = {
            0,                           // 0: System dynamic Material Expressive (resolved at runtime)
            Color.rgb(0xFF, 0xAB, 0x91), // 1: Salmon pink
            Color.rgb(0xFF, 0xC1, 0x07), // 2: Amber yellow
            Color.rgb(0x81, 0xC7, 0x84), // 3: Mint green
            Color.rgb(0x90, 0xCA, 0xF9)  // 4: Soft blue
    };

    private final Context mContext;
    private final Handler mMainHandler;
    private final MotionCuesUi mMotionCuesUi;
    private final DarkIconDispatcher mDarkIconDispatcher;
    private final MotionEstimator mMotionEstimator;

    private boolean mIsActive = false;
    private boolean mIsRandomizing = false;
    private int mAdaptiveColor = Color.WHITE;
    private final ContentObserver mSettingsObserver;

    private final Runnable mRandomizeRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsActive && mIsRandomizing) {
                applyRandomStyle();
                mMainHandler.postDelayed(this, RANDOMIZE_INTERVAL_MS);
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent != null ? intent.getAction() : null;
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mMainHandler.removeCallbacks(mRandomizeRunnable);
                if (mIsActive) {
                    mMotionCuesUi.stop();
                    mIsActive = false;
                }
                mMotionEstimator.stop();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)
                    || PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)
                    || Intent.ACTION_WALLPAPER_CHANGED.equals(action)
                    || Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                updateState();
            }
        }
    };

    @Inject
    public MotionAssistController(
            Context context,
            @Main Handler mainHandler,
            MotionCuesUi motionCuesUi,
            DarkIconDispatcher darkIconDispatcher) {
        mContext = context;
        mMainHandler = mainHandler;
        mMotionCuesUi = motionCuesUi;
        mDarkIconDispatcher = darkIconDispatcher;
        mMotionEstimator = new MotionEstimator(context);

        mMotionEstimator.setCallback(new MotionEstimator.Callback() {
            @Override
            public void onMotionUpdated(MotionVector motion) {
                if (mIsActive && mMotionCuesUi.isStarted()) {
                    // Scale physical acceleration vectors to pixel displacement for MotionCuesUi
                    float dx = motion.x * 20.0f;
                    float dy = motion.y * 20.0f;
                    mMainHandler.post(() -> mMotionCuesUi.updateBubblePos(dx, dy));
                }
            }

            @Override
            public void onVehicleStateChanged(boolean isMoving) {
                mMainHandler.post(MotionAssistController.this::updateStateInternal);
            }
        });

        mSettingsObserver = new ContentObserver(mMainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateState();
            }
        };
    }

    @Override
    public void start() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_WALLPAPER_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        try {
            WallpaperManager wm = mContext.getSystemService(WallpaperManager.class);
            if (wm != null) {
                wm.addOnColorsChangedListener((colors, which) -> {
                    if ((which & WallpaperManager.FLAG_SYSTEM) != 0) {
                        updateState();
                    }
                }, mMainHandler);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register OnColorsChangedListener", t);
        }

        ContentResolver cr = mContext.getContentResolver();
        cr.registerContentObserver(Settings.Secure.getUriFor("motion_assist_enabled"), false, mSettingsObserver, UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Secure.getUriFor("motion_assist_vehicle_auto"), false, mSettingsObserver, UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Secure.getUriFor("motion_assist_color"), false, mSettingsObserver, UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Secure.getUriFor("motion_assist_shape"), false, mSettingsObserver, UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Secure.getUriFor("motion_assist_opacity"), false, mSettingsObserver, UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Secure.getUriFor("motion_assist_randomize"), false, mSettingsObserver, UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Secure.getUriFor("motion_assist_smooth_animation"), false, mSettingsObserver, UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Secure.getUriFor("motion_assist_mode"), false, mSettingsObserver, UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Secure.getUriFor("theme_customization_overlay_packages"), false, mSettingsObserver, UserHandle.USER_ALL);

        mDarkIconDispatcher.addDarkReceiver(this);

        updateState();
    }

    private void updateState() {
        mMainHandler.post(this::updateStateInternal);
    }

    private synchronized void updateStateInternal() {
        ContentResolver cr = mContext.getContentResolver();
        boolean enabled = Settings.Secure.getIntForUser(cr, "motion_assist_enabled", 0, UserHandle.USER_CURRENT) == 1;

        boolean shouldMonitor = enabled;
        boolean shouldShowCues = enabled;

        int colorIdx = Settings.Secure.getIntForUser(cr, "motion_assist_color", 0, UserHandle.USER_CURRENT);
        int shapeIdx = Settings.Secure.getIntForUser(cr, "motion_assist_shape", 0, UserHandle.USER_CURRENT);
        int opacity = Settings.Secure.getIntForUser(cr, "motion_assist_opacity", 60, UserHandle.USER_CURRENT);
        boolean randomize = Settings.Secure.getIntForUser(cr, "motion_assist_randomize", 0, UserHandle.USER_CURRENT) == 1;
        boolean smoothSetting = Settings.Secure.getIntForUser(cr, "motion_assist_smooth_animation", 1, UserHandle.USER_CURRENT) == 1;

        PowerManager pm = mContext.getSystemService(PowerManager.class);
        boolean isPowerSave = pm != null && pm.isPowerSaveMode();
        boolean smoothActive = smoothSetting && !isPowerSave;

        if (shouldMonitor) {
            mMotionEstimator.start();
        } else {
            mMotionEstimator.stop();
        }

        if (shouldShowCues) {
            mMotionCuesUi.setSmoothAnimation(smoothActive);
            mMotionCuesUi.setRandomized(randomize);

            if (!mIsActive) {
                MotionCuesSettings settings = new MotionCuesSettings.Builder()
                        .setHorizontalSpacingDp(60)
                        .setVerticalSpacingDp(140)
                        .setMarginSizeDp(20)
                        .setRadiusDp(15)
                        .build();
                mMotionCuesUi.start(settings, UserHandle.myUserId(), "com.android.systemui");
                mIsActive = true;
            }

            mIsRandomizing = randomize;
            if (randomize) {
                mMainHandler.removeCallbacks(mRandomizeRunnable);
                mMainHandler.post(mRandomizeRunnable);
            } else {
                mMainHandler.removeCallbacks(mRandomizeRunnable);
                applyStyle(colorIdx, shapeIdx, opacity);
            }
        } else {
            mIsRandomizing = false;
            mMainHandler.removeCallbacks(mRandomizeRunnable);
            if (mIsActive) {
                mMotionCuesUi.stop();
                mIsActive = false;
            }
        }
    }

    private void applyStyle(int colorIdx, int shapeIdx, int opacity) {
        boolean adaptive = (colorIdx == 5);
        mMotionCuesUi.setAdaptiveMode(adaptive);

        int baseColor;
        if (adaptive) {
            // Adaptive mode: Dynamically adapts to the wallpaper colors and system theme
            baseColor = getAdaptiveColor();
        } else if (colorIdx == 0) {
            // System default: Material You primary accent extracted from wallpaper
            baseColor = getSystemAccentColor();
        } else if (colorIdx > 0 && colorIdx < PALETTE.length) {
            baseColor = PALETTE[colorIdx];
        } else {
            baseColor = getSystemAccentColor();
        }
        int alpha = Math.max(25, Math.min(255, (int) (opacity * 2.55f)));
        int colorWithAlpha = (alpha << 24) | (baseColor & 0x00FFFFFF);

        int shapeRes = getShapeDrawableRes(shapeIdx);
        mMotionCuesUi.updateMotionCuesVisualStyle(new MotionCuesVisualStyle(colorWithAlpha, shapeRes));
    }

    @Override
    public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
        // darkIntensity > 0.5f means icons in status bar are dark (Color.BLACK)
        // darkIntensity <= 0.5f means icons in status bar are light (Color.WHITE)
        int newAdaptiveColor = (darkIntensity > 0.5f) ? Color.BLACK : Color.WHITE;
        if (mAdaptiveColor != newAdaptiveColor) {
            mAdaptiveColor = newAdaptiveColor;
        }
        if (mIsActive) {
            mMotionCuesUi.setLuminanceRange(darkIntensity, darkIntensity);
            if (isAdaptiveMode()) {
                mMainHandler.post(this::reapplyCurrentStyle);
            }
        }
    }

    private boolean isAdaptiveMode() {
        ContentResolver cr = mContext.getContentResolver();
        return Settings.Secure.getIntForUser(cr, "motion_assist_color", 0, UserHandle.USER_CURRENT) == 5;
    }

    private void reapplyCurrentStyle() {
        if (!mIsActive || mIsRandomizing) return;
        ContentResolver cr = mContext.getContentResolver();
        int colorIdx = Settings.Secure.getIntForUser(cr, "motion_assist_color", 0, UserHandle.USER_CURRENT);
        int shapeIdx = Settings.Secure.getIntForUser(cr, "motion_assist_shape", 0, UserHandle.USER_CURRENT);
        int opacity = Settings.Secure.getIntForUser(cr, "motion_assist_opacity", 60, UserHandle.USER_CURRENT);
        applyStyle(colorIdx, shapeIdx, opacity);
    }

    private int getAdaptiveColor() {
        return mAdaptiveColor;
    }

    private void applyRandomStyle() {
        if (!mIsActive) return;
        ContentResolver cr = mContext.getContentResolver();
        int opacity = Settings.Secure.getIntForUser(cr, "motion_assist_opacity", 60, UserHandle.USER_CURRENT);
        int randomShapeIdx = (int) (Math.random() * 4); // 0..3
        int randomColorIdx = (int) (Math.random() * PALETTE.length); // 0..4
        applyStyle(randomColorIdx, randomShapeIdx, opacity);
    }

    private int getShapeDrawableRes(int shapeIdx) {
        switch (shapeIdx) {
            case 1:
                return R.drawable.motion_assist_shape_squircle;
            case 2:
                return R.drawable.motion_assist_shape_pentagon;
            case 3:
                return R.drawable.motion_assist_shape_diamond;
            case 0:
            default:
                return R.drawable.motion_assist_shape_circle;
        }
    }

    private int getSystemAccentColor() {
        try {
            int c = mContext.getColor(android.R.color.system_accent1_500);
            if (c != 0) return c;
        } catch (Throwable t) {
            try {
                int c = mContext.getColor(android.R.color.system_accent1_600);
                if (c != 0) return c;
            } catch (Throwable t2) {
                try {
                    return com.android.settingslib.Utils.getColorAccentDefaultColor(mContext);
                } catch (Throwable t3) {
                    return Color.rgb(0x1A, 0x73, 0xE8);
                }
            }
        }
        return Color.rgb(0x1A, 0x73, 0xE8);
    }
}
