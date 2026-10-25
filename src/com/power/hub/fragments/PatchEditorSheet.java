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

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.settings.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/** Editor for the TrickyStore security patch configuration. */
public class PatchEditorSheet extends SpoofSheetFragment {

    public static final String RESULT_PATCH = "spoof:patch";
    public static final String RESULT_CLEARED = "spoof:patch_cleared";
    public static final String RESULT_AUTO = "spoof:patch_auto";

    private static final String ARG_PATCH = "spoof:patch_value";
    private static final String ARG_AUTO = "spoof:patch_auto_value";
    private static final String ARG_PROFILE_PATCH = "spoof:profile_patch";

    private static final Pattern PATCH_TEMPLATE = Pattern.compile(
            "^(YYYY|\\d{4})-(MM|\\d{2})(?:-(DD|\\d{2}))?$");
    private static final Pattern PATCH_DATE = Pattern.compile(
            "(?:[0-9]{6}|[0-9]{8}|[0-9]{4}-[0-9]{2}|[0-9]{4}-[0-9]{2}-[0-9]{2})");

    private EditText mField;
    private TextView mPreview;
    private MaterialSwitch mAutoToggle;
    private MaterialButton mClearButton;
    private String mManualValue;
    private String mProfilePatch;

    public static PatchEditorSheet newInstance(String requestKey, String title,
            String currentPatch, boolean auto, String profilePatch) {
        PatchEditorSheet sheet = new PatchEditorSheet();
        Bundle args = baseArgs(requestKey, title, null);
        args.putString(ARG_PATCH, currentPatch);
        args.putBoolean(ARG_AUTO, auto);
        args.putString(ARG_PROFILE_PATCH, profilePatch);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.sheet_spoof_form;
    }

    @Override
    protected void onContentReady(@NonNull View content) {
        LinearLayout form = content.findViewById(R.id.form_container);
        Bundle args = getArguments();
        mManualValue = args == null ? "" : args.getString(ARG_PATCH, "");
        mProfilePatch = args == null ? "" : args.getString(ARG_PROFILE_PATCH, "");
        boolean auto = args != null && args.getBoolean(ARG_AUTO, false);

        View autoRow = getLayoutInflater().inflate(R.layout.row_spoof_switch, form, false);
        SpoofUi.bindSwitchRow(autoRow, getString(R.string.spoof_patch_auto_title),
                getString(R.string.spoof_patch_auto_summary), auto, true);
        form.addView(autoRow);
        mAutoToggle = autoRow.findViewById(R.id.row_switch);
        mAutoToggle.setOnCheckedChangeListener((buttonView, isChecked) -> applyMode(isChecked));

        TextView hint = new TextView(form.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_RowSummary);
        hint.setText(R.string.spoof_patch_hint);
        form.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mField = new EditText(form.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_TextField);
        mField.setTypeface(Typeface.MONOSPACE);
        mField.setGravity(Gravity.TOP | Gravity.START);
        mField.setSingleLine(false);
        mField.setMinLines(8);
        mField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mField.setHint("[com.example.app]\nall=2026-01-01");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = SpoofUi.dp(form.getContext(), 10);
        form.addView(mField, params);

        mPreview = new TextView(form.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_RowSummary);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        previewParams.topMargin = SpoofUi.dp(form.getContext(), 4);
        form.addView(mPreview, previewParams);
        mField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePreview();
            }
        });

        addAction(getString(R.string.cancel), false, this::dismiss);
        mClearButton = addAction(getString(R.string.spoof_clear), false, () -> {
            Bundle result = new Bundle();
            result.putString(RESULT_PATCH, "");
            result.putBoolean(RESULT_AUTO, false);
            result.putBoolean(RESULT_CLEARED, true);
            deliverResult(result);
            dismiss();
        });
        addPrimaryAction(getString(R.string.save), this::save);

        mField.setText(auto ? autoPatch() : mManualValue);
        mField.setEnabled(!auto);
        mClearButton.setVisibility(auto ? View.GONE : View.VISIBLE);
        updatePreview();
        if (!auto) {
            focusField(mField);
        }
    }

    private void updatePreview() {
        String text = mField.getText().toString();
        if (text.trim().isEmpty()) {
            mPreview.setVisibility(View.GONE);
            return;
        }
        try {
            List<String> entries = parsePatch(text);
            mPreview.setText(TextUtils.join("\n", entries));
            mPreview.setTextColor(getResources().getColor(R.color.powerhub_spoof_text_secondary,
                    requireContext().getTheme()));
        } catch (IllegalArgumentException e) {
            mPreview.setText(e.getMessage());
            mPreview.setTextColor(getResources().getColor(R.color.powerhub_spoof_error,
                    requireContext().getTheme()));
        }
        mPreview.setVisibility(View.VISIBLE);
    }

    private List<String> parsePatch(String content) {
        List<String> entries = new ArrayList<>();
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            return entries;
        }
        if (text.startsWith("{")) {
            try {
                JSONObject root = new JSONObject(text);
                addJsonSection(entries, null, root);
                JSONObject packages = root.optJSONObject("packages");
                if (packages != null) {
                    Iterator<String> names = packages.keys();
                    while (names.hasNext()) {
                        String pkg = names.next();
                        JSONObject obj = packages.optJSONObject(pkg);
                        if (obj != null) {
                            addJsonSection(entries, pkg, obj);
                        }
                    }
                }
            } catch (JSONException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            return entries;
        }

        String pkg = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                pkg = line.substring(1, line.length() - 1).trim();
                if (pkg.isEmpty()) {
                    throw new IllegalArgumentException(line);
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                String key = line.substring(0, eq).trim().toLowerCase(Locale.US);
                if ("system".equals(key) || "vendor".equals(key) || "boot".equals(key)
                        || "all".equals(key)) {
                    addEntry(entries, pkg, key, line.substring(eq + 1).trim());
                }
            } else {
                addEntry(entries, pkg, "all", line);
            }
        }
        return entries;
    }

    private void addJsonSection(List<String> entries, String pkg, JSONObject obj) {
        for (String field : new String[] {"system", "vendor", "boot", "all"}) {
            if (!obj.has(field) || obj.isNull(field)) {
                continue;
            }
            addEntry(entries, pkg, field, obj.optString(field, ""));
        }
    }

    private void addEntry(List<String> entries, String pkg, String field, String value) {
        if (!isValidPatchValue(value)) {
            throw new IllegalArgumentException(pkg == null
                    ? getString(R.string.spoof_patch_invalid_value, field + "=" + value)
                    : getString(R.string.spoof_patch_invalid_value_pkg, field + "=" + value, pkg));
        }
        entries.add((pkg == null ? "" : "[" + pkg + "] ") + field + "="
                + resolvePatchTemplate(value));
    }

    private static String resolvePatchTemplate(String value) {
        String trimmed = value == null ? "" : value.trim();
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        if ("today".equalsIgnoreCase(trimmed)) {
            return String.format(Locale.US, "%04d-%02d-%02d", now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        }
        java.util.regex.Matcher matcher = PATCH_TEMPLATE.matcher(trimmed);
        if (!matcher.matches()) {
            return trimmed;
        }
        boolean yearPlaceholder = "YYYY".equals(matcher.group(1));
        boolean monthPlaceholder = "MM".equals(matcher.group(2));
        boolean dayPlaceholder = "DD".equals(matcher.group(3));
        if (!yearPlaceholder && !monthPlaceholder && !dayPlaceholder) {
            return trimmed;
        }
        int year = yearPlaceholder ? now.get(Calendar.YEAR) : Integer.parseInt(matcher.group(1));
        int month = monthPlaceholder ? now.get(Calendar.MONTH) + 1
                : Integer.parseInt(matcher.group(2));
        if (year < 1 || year > 9999 || month < 1 || month > 12) {
            return trimmed;
        }
        if (matcher.group(3) == null) {
            return String.format(Locale.US, "%04d-%02d", year, month);
        }
        int day = dayPlaceholder ? now.get(Calendar.DAY_OF_MONTH)
                : Integer.parseInt(matcher.group(3));
        int maxDay = java.time.YearMonth.of(year, month).lengthOfMonth();
        if (day < 1) {
            return trimmed;
        }
        day = Math.min(day, maxDay);
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    private static boolean isValidPatchValue(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if ("no".equalsIgnoreCase(trimmed) || "prop".equalsIgnoreCase(trimmed)) {
            return true;
        }
        String resolved = resolvePatchTemplate(trimmed);
        if (!PATCH_DATE.matcher(resolved).matches()) {
            return false;
        }
        String digits = resolved.replace("-", "");
        try {
            int year = Integer.parseInt(digits.substring(0, 4));
            int month = Integer.parseInt(digits.substring(4, 6));
            int day = digits.length() == 8 ? Integer.parseInt(digits.substring(6, 8)) : 1;
            return year >= 1 && year <= 9999 && month >= 1 && month <= 12 && day >= 1
                    && day <= java.time.YearMonth.of(year, month).lengthOfMonth();
        } catch (Exception e) {
            return false;
        }
    }

    private String autoPatch() {
        if (mProfilePatch == null || mProfilePatch.trim().isEmpty()) {
            return "";
        }
        return "all=" + mProfilePatch.trim();
    }

    private void applyMode(boolean auto) {
        if (auto) {
            mManualValue = mField.getText().toString();
            mField.setText(autoPatch());
            mField.clearFocus();
        } else {
            mField.setText(mManualValue);
            focusField(mField);
        }
        mField.setEnabled(!auto);
        mClearButton.setVisibility(auto ? View.GONE : View.VISIBLE);
    }

    private void save() {
        String patch = mField.getText().toString().trim();
        if (!patch.isEmpty()) {
            try {
                parsePatch(patch);
            } catch (IllegalArgumentException e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        }
        Bundle result = new Bundle();
        result.putString(RESULT_PATCH, patch);
        result.putBoolean(RESULT_AUTO, mAutoToggle.isChecked());
        result.putBoolean(RESULT_CLEARED, false);
        deliverResult(result);
        dismiss();
    }
}
