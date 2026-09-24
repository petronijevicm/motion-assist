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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * Controller for the Shape preference with geometric glyphs dialog matching Google Pixel.
 */
public class MotionAssistShapePreferenceController extends BasePreferenceController {

    public static final String SETTING_KEY = "motion_assist_shape";
    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_SQUIRCLE = 1;
    public static final int SHAPE_PENTAGON = 2;
    public static final int SHAPE_DIAMOND = 3;

    public MotionAssistShapePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        int shape = Settings.Secure.getInt(mContext.getContentResolver(), SETTING_KEY, SHAPE_CIRCLE);
        switch (shape) {
            case SHAPE_SQUIRCLE:
                return mContext.getString(R.string.motion_assist_shape_squircle);
            case SHAPE_PENTAGON:
                return mContext.getString(R.string.motion_assist_shape_pentagon);
            case SHAPE_DIAMOND:
                return mContext.getString(R.string.motion_assist_shape_diamond);
            case SHAPE_CIRCLE:
            default:
                return mContext.getString(R.string.motion_assist_shape_circle);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            showShapeDialog(preference);
            return true;
        }
        return super.handlePreferenceTreeClick(preference);
    }

    private void showShapeDialog(Preference preference) {
        final int currentShape = Settings.Secure.getInt(mContext.getContentResolver(), SETTING_KEY, SHAPE_CIRCLE);
        final int[] selectedShape = {currentShape};

        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int) (16 * mContext.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad * 2, pad, pad * 2);

        final FrameLayout[] shapeContainers = new FrameLayout[4];

        for (int i = 0; i < 4; i++) {
            final int index = i;
            FrameLayout container = new FrameLayout(mContext);
            int size = (int) (52 * mContext.getResources().getDisplayMetrics().density);
            int margin = (int) (6 * mContext.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            container.setLayoutParams(lp);

            ShapeIconView shapeView = new ShapeIconView(mContext, i);
            int iconSize = (int) (36 * mContext.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
            shapeView.setLayoutParams(clp);

            container.addView(shapeView);
            shapeContainers[i] = container;

            updateShapeSelection(container, i == selectedShape[0]);

            container.setOnClickListener(v -> {
                selectedShape[0] = index;
                for (int j = 0; j < shapeContainers.length; j++) {
                    updateShapeSelection(shapeContainers[j], j == index);
                }
                // Live preview during selection
                Settings.Secure.putInt(mContext.getContentResolver(), SETTING_KEY, index);
            });

            layout.addView(container);
        }

        new AlertDialog.Builder(mContext)
                .setTitle(R.string.motion_assist_shape_title)
                .setView(layout)
                .setPositiveButton(R.string.motion_assist_dialog_save, (dialog, which) -> {
                    Settings.Secure.putInt(mContext.getContentResolver(), SETTING_KEY, selectedShape[0]);
                    preference.setSummary(getSummary());
                })
                .setNegativeButton(R.string.motion_assist_dialog_cancel, (dialog, which) -> {
                    // Revert to initial shape if cancelled
                    Settings.Secure.putInt(mContext.getContentResolver(), SETTING_KEY, currentShape);
                })
                .show();
    }

    private void updateShapeSelection(FrameLayout container, boolean isSelected) {
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

    private static class ShapeIconView extends View {
        private final int mShapeType;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mPath = new Path();

        ShapeIconView(Context context, int shapeType) {
            super(context);
            mShapeType = shapeType;
            mPaint.setColor(Color.rgb(0x56, 0x5F, 0x67));
            mPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) * 0.42f;

            switch (mShapeType) {
                case SHAPE_CIRCLE:
                    canvas.drawCircle(cx, cy, r, mPaint);
                    break;
                case SHAPE_SQUIRCLE:
                    RectF rf = new RectF(cx - r, cy - r, cx + r, cy + r);
                    canvas.drawRoundRect(rf, r * 0.35f, r * 0.35f, mPaint);
                    break;
                case SHAPE_PENTAGON:
                    mPath.reset();
                    for (int i = 0; i < 5; i++) {
                        double angle = Math.toRadians(i * 72 - 90);
                        float px = (float) (cx + r * Math.cos(angle));
                        float py = (float) (cy + r * Math.sin(angle));
                        if (i == 0) mPath.moveTo(px, py);
                        else mPath.lineTo(px, py);
                    }
                    mPath.close();
                    canvas.drawPath(mPath, mPaint);
                    break;
                case SHAPE_DIAMOND:
                    mPath.reset();
                    mPath.moveTo(cx, cy - r);
                    mPath.lineTo(cx + r, cy);
                    mPath.lineTo(cx, cy + r);
                    mPath.lineTo(cx - r, cy);
                    mPath.close();
                    canvas.drawPath(mPath, mPaint);
                    break;
            }
        }
    }
}
