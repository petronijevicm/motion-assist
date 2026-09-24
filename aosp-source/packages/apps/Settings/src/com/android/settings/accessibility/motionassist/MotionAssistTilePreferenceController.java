/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.accessibility.motionassist;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/** "Add tile to Quick Settings": adds the tile, then explains where to find it. */
public class MotionAssistTilePreferenceController extends BasePreferenceController {

    private static final String QS_TILES = "sysui_qs_tiles";
    private static final String TILE_SPEC = "motion_assist";
    private static final String ICON_PLACEHOLDER = "%s";

    public MotionAssistTilePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return super.handlePreferenceTreeClick(preference);
        }
        addTile();
        showHowToUse(preference.getContext());
        return true;
    }

    private void addTile() {
        // SystemUI's auto-add list reacts to this key; writing the tile list covers the case
        // where the tile was added and removed before.
        MotionAssistKeys.put(mContext, MotionAssistKeys.ADD_TILE, 1);
        final String tiles = Settings.Secure.getString(mContext.getContentResolver(), QS_TILES);
        if (TextUtils.isEmpty(tiles)) {
            return; // SystemUI still uses its defaults, which include the tile
        }
        for (String spec : tiles.split(",")) {
            if (TILE_SPEC.equals(spec.trim())) return;
        }
        Settings.Secure.putString(mContext.getContentResolver(), QS_TILES, tiles + "," + TILE_SPEC);
    }

    private void showHowToUse(Context context) {
        final String template = context.getString(R.string.motion_assist_tile_dialog_edit);
        final SpannableStringBuilder text = new SpannableStringBuilder(
                context.getString(R.string.motion_assist_tile_dialog_use));
        text.append("\n\n");
        final int start = text.length() + template.indexOf(ICON_PLACEHOLDER);
        text.append(template);
        final Drawable pencil = context.getDrawable(R.drawable.ic_motion_assist_edit);
        if (pencil != null && template.contains(ICON_PLACEHOLDER)) {
            final int size = Math.round(context.getResources().getDisplayMetrics().density * 18);
            pencil.setBounds(0, 0, size, size);
            text.setSpan(new ImageSpan(pencil, ImageSpan.ALIGN_BOTTOM), start,
                    start + ICON_PLACEHOLDER.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        new AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_motion_assist)
                .setTitle(R.string.motion_assist_tile_dialog_title)
                .setMessage(text)
                .setPositiveButton(R.string.motion_assist_got_it, null)
                .show();
    }
}
