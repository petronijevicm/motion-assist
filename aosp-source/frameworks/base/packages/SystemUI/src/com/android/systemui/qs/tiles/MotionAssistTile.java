/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles;

import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.motionassist.MotionAssistController;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
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

/** Quick Settings tile that switches Motion Assist (built-in motion cues) on and off. */
public class MotionAssistTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "motion_assist";

    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SETTINGS_FRAGMENT =
            "com.android.settings.accessibility.motionassist.MotionAssistSettings";

    private final UserSettingObserver mEnabled;
    private final UserSettingObserver mAutoStart;

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
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        final int user = userTracker.getUserId();
        mEnabled = new UserSettingObserver(secureSettings, mHandler,
                MotionAssistController.Keys.ENABLED, user) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                refreshState();
            }
        };
        mAutoStart = new UserSettingObserver(secureSettings, mHandler,
                MotionAssistController.Keys.VEHICLE_AUTO, user) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                refreshState();
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        mEnabled.setValue(mState.value ? 0 : 1);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(SETTINGS_PACKAGE, SETTINGS_PACKAGE + ".SubSettings"))
                .putExtra(":settings:show_fragment", SETTINGS_FRAGMENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean on = mEnabled.getValue() == 1;
        final boolean auto = mAutoStart.getValue() == 1;
        state.value = on;
        state.state = on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.label = mContext.getString(R.string.quick_settings_motion_assist_label);
        state.icon = maybeLoadResourceIcon(
                on ? R.drawable.ic_qs_motion_assist : R.drawable.ic_qs_motion_assist_off);
        state.secondaryLabel = mContext.getString(!on
                ? R.string.quick_settings_motion_assist_secondary_label_off
                : auto
                        ? R.string.quick_settings_motion_assist_secondary_label_auto
                        : R.string.quick_settings_motion_assist_secondary_label_on);
        state.contentDescription = mContext.getString(on
                ? R.string.accessibility_quick_settings_motion_assist_on
                : R.string.accessibility_quick_settings_motion_assist_off);
        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_motion_assist_label);
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        mEnabled.setListening(listening);
        mAutoStart.setListening(listening);
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
        mEnabled.setUserId(newUserId);
        mAutoStart.setUserId(newUserId);
        super.handleUserSwitch(newUserId);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mEnabled.setListening(false);
        mAutoStart.setListening(false);
    }
}
