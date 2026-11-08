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

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.settings.R;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-app device property editor with an optional save-as-profile action. */
public class PropertyEditorSheet extends SpoofSheetFragment {

    public static final String RESULT_PROPERTIES = "spoof:app_properties";
    public static final String RESULT_PROFILE_NAME = "spoof:app_profile_name";
    public static final String RESULT_CONTEXT = "spoof:app_properties_context";

    private static final String ARG_PROPERTIES = "spoof:app_properties_arg";
    private static final String ARG_CONTEXT = "spoof:app_properties_context_arg";
    private static final String ARG_SHOW_PROFILE = "spoof:app_properties_show_profile";
    private static final String ARG_FINGERPRINT_HINT = "spoof:app_properties_fingerprint_hint";

    private static final String[] SUGGESTED_KEYS = {
            "MODEL", "MANUFACTURER", "BRAND", "DEVICE", "PRODUCT", "FINGERPRINT",
            "SECURITY_PATCH", "DEVICE_INITIAL_SDK_INT", "SDK_INT", "RELEASE", "ID",
            "INCREMENTAL", "TYPE", "TAGS", "HARDWARE", "BOARD", "BOOTLOADER", "DISPLAY",
            "HOST"};

    private LinearLayout mPropertyContainer;
    private EditText mProfileNameField;
    private TextView mDottedHint;

    public static PropertyEditorSheet newInstance(String requestKey, String title,
            @NonNull Map<String, String> properties) {
        return newInstance(requestKey, title, properties, null);
    }

    public static PropertyEditorSheet newInstance(String requestKey, String title,
            @NonNull Map<String, String> properties, @Nullable String context) {
        return newInstance(requestKey, title, properties, context, true);
    }

    public static PropertyEditorSheet newInstance(String requestKey, String title,
            @NonNull Map<String, String> properties, @Nullable String context,
            boolean showProfileAction) {
        return newInstance(requestKey, title, properties, context, showProfileAction, false);
    }

    public static PropertyEditorSheet newInstance(String requestKey, String title,
            @NonNull Map<String, String> properties, @Nullable String context,
            boolean showProfileAction, boolean showFingerprintHint) {
        PropertyEditorSheet sheet = new PropertyEditorSheet();
        Bundle args = baseArgs(requestKey, title, null);
        args.putSerializable(ARG_PROPERTIES, new LinkedHashMap<>(properties));
        args.putString(ARG_CONTEXT, context);
        args.putBoolean(ARG_SHOW_PROFILE, showProfileAction);
        args.putBoolean(ARG_FINGERPRINT_HINT, showFingerprintHint);
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

        mPropertyContainer = new LinearLayout(form.getContext());
        mPropertyContainer.setOrientation(LinearLayout.VERTICAL);
        form.addView(mPropertyContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mDottedHint = new TextView(form.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_RowSummary);
        mDottedHint.setVisibility(View.GONE);

        Serializable serialized = args == null ? null : args.getSerializable(ARG_PROPERTIES);
        if (serialized instanceof Map) {
            //noinspection unchecked
            Map<String, String> properties = (Map<String, String>) serialized;
            if (args != null && args.getBoolean(ARG_FINGERPRINT_HINT, false)
                    && properties.containsKey("FINGERPRINT")) {
                addHint(form, getString(R.string.pif_fingerprint_derived_hint));
            }
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                addPropertyRow(entry.getKey(), entry.getValue());
            }
        }

        TextView addProperty = (TextView) getLayoutInflater()
                .inflate(R.layout.view_spoof_button, form, false);
        addProperty.setText(R.string.app_spoof_add_prop);
        addProperty.setOnClickListener(v -> addPropertyRow("", ""));
        form.addView(addProperty);

        updateDottedHint();
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = SpoofUi.dp(form.getContext(), 6);
        form.addView(mDottedHint, hintParams);

        mProfileNameField = addField(form, getString(R.string.app_spoof_profile_name_hint),
                "", getString(R.string.app_spoof_profile_name_hint));
        mProfileNameField.setVisibility(View.GONE);

        addAction(getString(R.string.cancel), false, this::dismiss);
        if (args == null || args.getBoolean(ARG_SHOW_PROFILE, true)) {
            addAction(getString(R.string.spoof_profile_save_as), false, () -> {
                mProfileNameField.setVisibility(View.VISIBLE);
                focusField(mProfileNameField);
            });
        }
        addPrimaryAction(getString(R.string.save), this::save);
    }

    private void addHint(LinearLayout form, CharSequence text) {
        TextView view = new TextView(form.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_RowSummary);
        view.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = SpoofUi.dp(form.getContext(), 4);
        form.addView(view, params);
    }

    private void addPropertyRow(String key, String value) {
        View row = LayoutInflater.from(mPropertyContainer.getContext())
                .inflate(R.layout.row_spoof_property, mPropertyContainer, false);
        EditText keyField = row.findViewById(R.id.prop_key);
        EditText valueField = row.findViewById(R.id.prop_value);
        if (keyField instanceof AutoCompleteTextView) {
            AutoCompleteTextView autoKey = (AutoCompleteTextView) keyField;
            autoKey.setAdapter(new ArrayAdapter<>(autoKey.getContext(),
                    android.R.layout.simple_dropdown_item_1line, SUGGESTED_KEYS));
            autoKey.setThreshold(1);
        }
        keyField.setText(key);
        valueField.setText(value);
        bindIme(keyField);
        bindIme(valueField);
        keyField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateDottedHint();
            }
        });

        LinearLayout wrapper = new LinearLayout(mPropertyContainer.getContext());
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(android.view.Gravity.CENTER_VERTICAL);
        wrapper.addView(row, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView delete = new ImageView(wrapper.getContext());
        delete.setImageResource(R.drawable.ic_delete);
        delete.setImageTintList(ContextCompat.getColorStateList(wrapper.getContext(),
                R.color.powerhub_spoof_error));
        delete.setContentDescription(getString(R.string.action_delete));
        int size = SpoofUi.dp(wrapper.getContext(), 24);
        delete.setPadding(SpoofUi.dp(wrapper.getContext(), 2),
                SpoofUi.dp(wrapper.getContext(), 2),
                SpoofUi.dp(wrapper.getContext(), 2),
                SpoofUi.dp(wrapper.getContext(), 2));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(size, size);
        deleteParams.setMarginStart(SpoofUi.dp(wrapper.getContext(), 6));
        wrapper.addView(delete, deleteParams);
        delete.setOnClickListener(v -> {
            mPropertyContainer.removeView(wrapper);
            updateDottedHint();
        });

        mPropertyContainer.addView(wrapper);
        updateDottedHint();
    }

    private void updateDottedHint() {
        if (mDottedHint == null || mPropertyContainer == null) {
            return;
        }
        boolean dotted = false;
        for (int i = 0; i < mPropertyContainer.getChildCount() && !dotted; i++) {
            EditText keyField = mPropertyContainer.getChildAt(i).findViewById(R.id.prop_key);
            dotted = keyField != null && keyField.getText().toString().contains(".");
        }
        Bundle args = getArguments();
        boolean perApp = args != null && args.getString(ARG_CONTEXT) != null;
        mDottedHint.setText(perApp
                ? R.string.spoof_dotted_key_per_app_hint : R.string.spoof_dotted_key_pif_hint);
        mDottedHint.setVisibility(dotted ? View.VISIBLE : View.GONE);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void save() {
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        for (int i = 0; i < mPropertyContainer.getChildCount(); i++) {
            View wrapper = mPropertyContainer.getChildAt(i);
            EditText keyField = wrapper.findViewById(R.id.prop_key);
            EditText valueField = wrapper.findViewById(R.id.prop_value);
            String key = keyField == null ? "" : keyField.getText().toString().trim();
            String value = valueField == null ? "" : valueField.getText().toString().trim();
            if (!key.isEmpty()) {
                properties.put(key, value);
            }
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (("DEVICE_INITIAL_SDK_INT".equals(entry.getKey())
                    || "SDK_INT".equals(entry.getKey()))
                    && !entry.getValue().isEmpty() && !isInteger(entry.getValue())) {
                Toast.makeText(requireContext(), R.string.pif_value_not_integer,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        String profileName = null;
        if (mProfileNameField.getVisibility() == View.VISIBLE) {
            profileName = mProfileNameField.getText().toString().trim();
            if (profileName.isEmpty()) {
                Toast.makeText(requireContext(), R.string.app_spoof_profile_name_empty,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Bundle result = new Bundle();
        result.putSerializable(RESULT_PROPERTIES, properties);
        result.putString(RESULT_PROFILE_NAME, profileName);
        if (getArguments() != null) {
            result.putString(RESULT_CONTEXT, getArguments().getString(ARG_CONTEXT));
        }
        deliverResult(result);
        dismiss();
    }
}
