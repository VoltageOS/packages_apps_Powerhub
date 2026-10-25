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
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
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

/** Create or edit a custom device profile. */
public class ProfileEditorSheet extends SpoofSheetFragment {

    public static final String RESULT_NAME = "spoof:profile_name";
    public static final String RESULT_ORIGINAL = "spoof:profile_original";
    public static final String RESULT_PROPERTIES = "spoof:profile_properties";
    public static final String RESULT_CONTEXT = "spoof:profile_context";

    private static final String ARG_NAME = "spoof:profile_name_arg";
    private static final String ARG_ORIGINAL = "spoof:profile_original_arg";
    private static final String ARG_PROPERTIES = "spoof:profile_properties_arg";
    private static final String ARG_CONTEXT = "spoof:profile_context_arg";

    private EditText mNameField;
    private LinearLayout mPropertyContainer;

    public static ProfileEditorSheet newInstance(String requestKey, String title,
            @Nullable String originalName, @NonNull Map<String, String> properties) {
        return newInstance(requestKey, title, originalName, properties, null);
    }

    public static ProfileEditorSheet newInstance(String requestKey, String title,
            @Nullable String originalName, @NonNull Map<String, String> properties,
            @Nullable String context) {
        ProfileEditorSheet sheet = new ProfileEditorSheet();
        Bundle args = baseArgs(requestKey, title, null);
        args.putString(ARG_NAME, originalName);
        args.putString(ARG_ORIGINAL, originalName);
        args.putSerializable(ARG_PROPERTIES, new LinkedHashMap<>(properties));
        args.putString(ARG_CONTEXT, context);
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

        mNameField = addField(form, getString(R.string.app_spoof_profile_name_hint),
                args == null ? "" : args.getString(ARG_NAME, ""),
                getString(R.string.app_spoof_profile_name_hint));
        mNameField.setInputType(InputType.TYPE_CLASS_TEXT);
        focusField(mNameField);

        TextView section = new TextView(form.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_Section);
        section.setText(R.string.spoof_properties_title);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = SpoofUi.dp(form.getContext(), 16);
        form.addView(section, sectionParams);

        mPropertyContainer = new LinearLayout(form.getContext());
        mPropertyContainer.setOrientation(LinearLayout.VERTICAL);
        form.addView(mPropertyContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Serializable serialized = args == null ? null : args.getSerializable(ARG_PROPERTIES);
        if (serialized instanceof Map) {
            //noinspection unchecked
            Map<String, String> properties = (Map<String, String>) serialized;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                addPropertyRow(entry.getKey(), entry.getValue());
            }
        }

        TextView addProperty = (TextView) getLayoutInflater()
                .inflate(R.layout.view_spoof_button, form, false);
        addProperty.setText(R.string.app_spoof_add_prop);
        addProperty.setOnClickListener(v -> addPropertyRow("", ""));
        form.addView(addProperty);

        addAction(getString(R.string.cancel), false, this::dismiss);
        addPrimaryAction(getString(R.string.save), this::save);
    }

    private void addPropertyRow(String key, String value) {
        View row = LayoutInflater.from(mPropertyContainer.getContext())
                .inflate(R.layout.row_spoof_property, mPropertyContainer, false);
        EditText keyField = row.findViewById(R.id.prop_key);
        EditText valueField = row.findViewById(R.id.prop_value);
        keyField.setText(key);
        valueField.setText(value);
        bindIme(keyField);
        bindIme(valueField);

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
        delete.setBackgroundResource(android.R.drawable.list_selector_background);
        int size = SpoofUi.dp(wrapper.getContext(), 24);
        delete.setPadding(SpoofUi.dp(wrapper.getContext(), 2),
                SpoofUi.dp(wrapper.getContext(), 2),
                SpoofUi.dp(wrapper.getContext(), 2),
                SpoofUi.dp(wrapper.getContext(), 2));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(size, size);
        deleteParams.setMarginStart(SpoofUi.dp(wrapper.getContext(), 6));
        wrapper.addView(delete, deleteParams);
        delete.setOnClickListener(v -> mPropertyContainer.removeView(wrapper));

        mPropertyContainer.addView(wrapper);
    }

    private void save() {
        String name = mNameField.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.app_spoof_profile_name_empty,
                    Toast.LENGTH_SHORT).show();
            return;
        }
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
        Bundle result = new Bundle();
        result.putString(RESULT_NAME, name);
        result.putString(RESULT_ORIGINAL,
                getArguments() == null ? null : getArguments().getString(ARG_ORIGINAL));
        result.putSerializable(RESULT_PROPERTIES, properties);
        if (getArguments() != null) {
            result.putString(RESULT_CONTEXT, getArguments().getString(ARG_CONTEXT));
        }
        deliverResult(result);
        dismiss();
    }
}
