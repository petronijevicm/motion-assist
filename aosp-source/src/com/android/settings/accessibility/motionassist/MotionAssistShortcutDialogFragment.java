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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.android.settings.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Bottom sheet dialog shown when the Motion Assist Quick Settings tile is added.
 * Matches Google's official design with tutorial animation and instructions.
 */
public class MotionAssistShortcutDialogFragment extends BottomSheetDialogFragment {

    public static final String TAG = "MotionAssistShortcutDialogFragment";

    public static void show(FragmentManager fragmentManager) {
        if (fragmentManager == null || fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }
        MotionAssistShortcutDialogFragment fragment = new MotionAssistShortcutDialogFragment();
        fragment.show(fragmentManager, TAG);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.motion_assist_shortcut_bottom_sheet, container, false);

        TextView textPart2 = view.findViewById(R.id.shortcut_info_part2);
        Context context = getContext();
        if (context != null && textPart2 != null) {
            int textSize = (int) textPart2.getTextSize();
            int currentTextColor = textPart2.getCurrentTextColor();
            String template = context.getString(R.string.motion_assist_shortcut_dialog_info);
            int indexOf = template.indexOf("%s");
            if (indexOf != -1) {
                SpannableStringBuilder ssb = new SpannableStringBuilder(template);
                Drawable drawable = context.getDrawable(R.drawable.ic_edit_pencil);
                if (drawable != null) {
                    drawable.setTint(currentTextColor);
                    drawable.setBounds(0, 0, textSize, textSize);
                    ssb.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE),
                            indexOf, indexOf + 2, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    ssb.replace(indexOf, indexOf + 2,
                            context.getString(R.string.motion_assist_edit_icon_alt_text));
                }
                textPart2.setText(ssb);
            } else {
                textPart2.setText(template);
            }
        }

        Button dismissButton = view.findViewById(R.id.shortcut_dismiss_button);
        if (dismissButton != null) {
            dismissButton.setOnClickListener(v -> dismiss());
        }

        return view;
    }
}
