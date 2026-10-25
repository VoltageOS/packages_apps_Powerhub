/*
 * Copyright (C) 2026 AxionOS
 * Copyright (C) 2026 VoltageOS
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
package com.power.hub.fragments;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.settings.R;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Shared drawing helpers for the unified spoofing UI. */
final class SpoofUi {

    static final int TONE_MUTED = 0;
    static final int TONE_ACCENT = 1;
    static final int TONE_ERROR = 2;

    private SpoofUi() {
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void setText(View root, int id, CharSequence text) {
        TextView view = root.findViewById(id);
        if (view != null) {
            view.setText(text);
        }
    }

    static void setVisible(View root, int id, boolean visible) {
        View view = root.findViewById(id);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    static void bindStatusPill(TextView pill, CharSequence text, int tone) {
        pill.setText(text);
        int background;
        @ColorRes int color;
        if (tone == TONE_ERROR) {
            background = R.drawable.powerhub_spoof_pill_error_bg;
            color = R.color.powerhub_spoof_error;
        } else if (tone == TONE_ACCENT) {
            background = R.drawable.powerhub_spoof_pill_accent_bg;
            color = R.color.powerhub_spoof_accent;
        } else {
            background = R.drawable.powerhub_spoof_pill_bg;
            color = R.color.powerhub_spoof_text_secondary;
        }
        pill.setBackgroundResource(background);
        pill.setTextColor(ContextCompat.getColor(pill.getContext(), color));
        pill.setVisibility(View.VISIBLE);
    }

    static void styleFilterPill(TextView pill, boolean selected) {
        pill.setBackgroundResource(selected
                ? R.drawable.powerhub_spoof_mode_selected
                : R.drawable.powerhub_spoof_mode_unselected);
        pill.setTextColor(ContextCompat.getColor(pill.getContext(), selected
                ? R.color.powerhub_spoof_on_accent
                : R.color.powerhub_spoof_text_secondary));
    }

    static void styleModePill(TextView pill, boolean selected) {
        pill.setBackgroundResource(selected
                ? R.drawable.powerhub_spoof_mode_selected
                : R.drawable.powerhub_spoof_mode_unselected);
        pill.setTextColor(ContextCompat.getColor(pill.getContext(), selected
                ? R.color.powerhub_spoof_on_accent
                : R.color.powerhub_spoof_text_secondary));
    }

    static void bindSwitchRow(View row, CharSequence title, CharSequence summary,
            boolean checked, boolean enabled) {
        TextView titleView = row.findViewById(R.id.row_title);
        TextView summaryView = row.findViewById(R.id.row_summary);
        MaterialSwitch toggle = row.findViewById(R.id.row_switch);
        if (titleView != null) {
            titleView.setText(title);
        }
        if (summaryView != null) {
            summaryView.setText(summary);
            summaryView.setVisibility(summary == null ? View.GONE : View.VISIBLE);
        }
        if (toggle != null) {
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(checked);
            toggle.setEnabled(enabled);
        }
    }

    static void bindActionRow(View row, @DrawableRes int iconRes, CharSequence title,
            CharSequence summary) {
        TextView titleView = row.findViewById(R.id.row_title);
        TextView summaryView = row.findViewById(R.id.row_summary);
        ImageView icon = row.findViewById(R.id.row_icon);
        if (titleView != null) {
            titleView.setText(title);
        }
        if (summaryView != null) {
            summaryView.setText(summary);
            summaryView.setVisibility(summary == null ? View.GONE : View.VISIBLE);
        }
        if (icon != null) {
            if (iconRes != 0) {
                icon.setImageResource(iconRes);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }
        }
    }

    static Drawable loadAppIcon(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return info.loadIcon(packageManager);
        } catch (Exception ignored) {
            return ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon);
        }
    }

    static String appLabel(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)).toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }

    static String formatDate(@Nullable Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);
    }

    static String patchSummary(Context context, @Nullable String config) {
        if (config == null || config.trim().isEmpty()) {
            return context.getString(R.string.spoof_patch_none);
        }
        int sections = 0;
        int values = 0;
        for (String raw : config.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                sections++;
            } else if (line.contains("=")) {
                values++;
            }
        }
        if (sections > 0) {
            return context.getString(R.string.spoof_patch_count, sections);
        }
        if (values > 0) {
            return context.getString(R.string.spoof_patch_count, values);
        }
        return context.getString(R.string.spoof_patch_custom);
    }
}
