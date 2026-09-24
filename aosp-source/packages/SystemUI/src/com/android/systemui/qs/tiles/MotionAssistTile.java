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

package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.UserSettingObserver;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.settings.SecureSettings;

import javax.inject.Inject;

/**
 * Quick Settings tile for Motion Assist (Vehicle Motion Cues).
 */
public class MotionAssistTile extends QSTileImpl<QSTile.BooleanState> {

    public static final String TILE_SPEC = "motion_assist";
    private static final String SETTING_ENABLED = "motion_assist_enabled";

    private final UserSettingObserver mEnabledSetting;

    @Inject
    public MotionAssistTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            SecureSettings secureSettings,
            UserTracker userTracker
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager,
                metricsLogger, statusBarStateController, activityStarter, qsLogger);

        mEnabledSetting = new UserSettingObserver(secureSettings, mHandler, SETTING_ENABLED,
                userTracker.getUserId()) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        boolean isCurrentlyEnabled = mState.value;
        if (isCurrentlyEnabled) {
            Settings.Secure.putInt(mContext.getContentResolver(), SETTING_ENABLED, 0);
        } else {
            Settings.Secure.putInt(mContext.getContentResolver(), SETTING_ENABLED, 1);
            Settings.Secure.putInt(mContext.getContentResolver(), "motion_assist_vehicle_auto", 0);
        }
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        Intent intent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
        intent.setComponent(new android.content.ComponentName(
                "com.android.settings", "com.android.settings.SubSettings"));
        intent.putExtra(":settings:show_fragment",
                "com.android.settings.accessibility.motionassist.MotionAssistSettings");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean enabled = Settings.Secure.getInt(mContext.getContentResolver(), SETTING_ENABLED, 0) == 1;

        state.label = mContext.getString(R.string.quick_settings_motion_assist_label);
        state.icon = maybeLoadResourceIcon(R.drawable.ic_qs_motion_assist);
        state.contentDescription = state.label;
        state.expandedAccessibilityClassName = Switch.class.getName();

        if (enabled) {
            state.value = true;
            state.state = Tile.STATE_ACTIVE;
            state.secondaryLabel = mContext.getString(R.string.quick_settings_motion_assist_secondary_label_on);
        } else {
            state.value = false;
            state.state = Tile.STATE_INACTIVE;
            state.secondaryLabel = mContext.getString(R.string.quick_settings_motion_assist_secondary_label_off);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_motion_assist_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_CUSTOM;
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        mEnabledSetting.setListening(listening);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mEnabledSetting.setListening(false);
    }
}
