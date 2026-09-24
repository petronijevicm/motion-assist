/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.accessibility.motionassist;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * Colour and shape pickers. Each shows a row of swatches; picking one applies it at once so
 * running cues update live, Save keeps it and Cancel restores the previous choice.
 */
public class MotionAssistChoicePreferenceController extends BasePreferenceController {

    private static final String KEY_COLOR = "motion_assist_color";
    private static final String KEY_SHAPE = "motion_assist_shape";

    /** Index 0 (wallpaper accent) and the last (high contrast) are resolved at runtime. */
    private static final int[] PALETTE = {
            0,
            Color.rgb(0xF2, 0x9C, 0x86),
            Color.rgb(0xF5, 0xB7, 0x2A),
            Color.rgb(0x7C, 0xC9, 0x96),
            Color.rgb(0x86, 0xBC, 0xEE),
            0,
    };
    private static final int[] COLOR_NAMES = {
            R.string.motion_assist_color_system,
            R.string.motion_assist_color_salmon,
            R.string.motion_assist_color_amber,
            R.string.motion_assist_color_mint,
            R.string.motion_assist_color_sky,
            R.string.motion_assist_color_contrast,
    };
    private static final int[] SHAPE_NAMES = {
            R.string.motion_assist_shape_circle,
            R.string.motion_assist_shape_squircle,
            R.string.motion_assist_shape_pentagon,
            R.string.motion_assist_shape_diamond,
    };

    public MotionAssistChoicePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return isColor() || isShape() ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        final int[] names = isColor() ? COLOR_NAMES : SHAPE_NAMES;
        return mContext.getString(names[clamp(current(), names.length)]);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return super.handlePreferenceTreeClick(preference);
        }
        showPicker(preference);
        return true;
    }

    private void showPicker(Preference preference) {
        final Context context = preference.getContext();
        final String setting = setting();
        final int original = current();
        final int count = isColor() ? COLOR_NAMES.length : SHAPE_NAMES.length;
        final int[] chosen = {clamp(original, count)};

        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        final int pad = dp(context, 16);
        row.setPadding(pad, pad, pad, pad);

        final Swatch[] swatches = new Swatch[count];
        for (int i = 0; i < count; i++) {
            final int index = i;
            final Swatch swatch = new Swatch(context, isColor() ? colorFor(context, i) : 0,
                    isColor() ? -1 : i);
            swatch.setContentDescription(context.getString(
                    (isColor() ? COLOR_NAMES : SHAPE_NAMES)[i]));
            final LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44));
            lp.setMarginStart(dp(context, 4));
            lp.setMarginEnd(dp(context, 4));
            row.addView(swatch, lp);
            swatches[i] = swatch;
            swatch.setOnClickListener(v -> {
                chosen[0] = index;
                for (int j = 0; j < count; j++) swatches[j].setSelectedRing(j == index);
                MotionAssistKeys.put(mContext, setting, index); // live preview
            });
            swatch.setSelectedRing(i == chosen[0]);
        }

        new AlertDialog.Builder(context)
                .setTitle(preference.getTitle())
                .setView(row)
                .setPositiveButton(R.string.motion_assist_save, (d, w) -> {
                    MotionAssistKeys.put(mContext, setting, chosen[0]);
                    preference.setSummary(getSummary());
                })
                .setNegativeButton(android.R.string.cancel,
                        (d, w) -> MotionAssistKeys.put(mContext, setting, original))
                .setOnCancelListener(d -> MotionAssistKeys.put(mContext, setting, original))
                .show();
    }

    private boolean isColor() {
        return KEY_COLOR.equals(getPreferenceKey());
    }

    private boolean isShape() {
        return KEY_SHAPE.equals(getPreferenceKey());
    }

    private String setting() {
        return isColor() ? MotionAssistKeys.COLOR : MotionAssistKeys.SHAPE;
    }

    private int current() {
        return MotionAssistKeys.get(mContext, setting(), 0);
    }

    private static int clamp(int value, int count) {
        return value < 0 || value >= count ? 0 : value;
    }

    private static int dp(Context context, int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }

    private static int colorFor(Context context, int index) {
        final boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (index == MotionAssistKeys.COLOR_SYSTEM) {
            return context.getColor(night
                    ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        }
        if (index == MotionAssistKeys.COLOR_CONTRAST) {
            return night ? Color.rgb(0xF4, 0xF4, 0xF4) : Color.rgb(0x1C, 0x1C, 0x1C);
        }
        return PALETTE[index];
    }

    /** A colour dot or a shape glyph, with a ring when selected. */
    private static final class Swatch extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mPath = new Path();
        private final int mShape;

        Swatch(Context context, int color, int shape) {
            super(context);
            mShape = shape;
            final TypedValue value = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true);
            mPaint.setColor(shape >= 0 ? context.getColor(value.resourceId) : color);
            setClickable(true);
            setFocusable(true);
        }

        void setSelectedRing(boolean selected) {
            setSelected(selected);
            if (!selected) {
                setBackground(null);
                return;
            }
            final GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(dp(getContext(), 2),
                    getContext().getColor(android.R.color.system_accent1_400));
            setBackground(ring);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            final float r = Math.min(getWidth(), getHeight()) * 0.32f;
            switch (mShape) {
                case 1:
                    canvas.drawRoundRect(cx - r, cy - r, cx + r, cy + r, r * 0.38f, r * 0.38f,
                            mPaint);
                    break;
                case 2:
                case 3:
                    mPath.rewind();
                    final int corners = mShape == 2 ? 5 : 4;
                    for (int i = 0; i < corners; i++) {
                        final double a = Math.toRadians(-90.0 + 360.0 / corners * i);
                        final float px = cx + (float) (r * Math.cos(a));
                        final float py = cy + (float) (r * Math.sin(a));
                        if (i == 0) mPath.moveTo(px, py); else mPath.lineTo(px, py);
                    }
                    mPath.close();
                    canvas.drawPath(mPath, mPaint);
                    break;
                default:
                    canvas.drawCircle(cx, cy, r, mPaint);
            }
        }
    }
}
