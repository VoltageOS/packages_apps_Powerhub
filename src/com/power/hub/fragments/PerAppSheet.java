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

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.R;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Device profile and app-state options for a single app. */
public class PerAppSheet extends SpoofSheetFragment {

    public static final String RESULT_PACKAGE = "spoof:perapp_package";
    public static final String RESULT_ACTION = "spoof:perapp_action";

    public static final String ACTION_PROFILE = "profile";
    public static final String ACTION_PROPERTIES = "properties";
    public static final String ACTION_COPY_PIF = "copy_pif";
    public static final String ACTION_REMOVE = "remove";

    private static final String ARG_PACKAGE = "spoof:perapp_arg_package";
    private static final String ARG_APP_NAME = "spoof:perapp_arg_name";

    private AppSpoofStore mSpoofStore;
    private AppStateStore mStateStore;
    private String mPackageName;
    private String mAppName;
    private boolean mHideAccessibility;
    private boolean mIsolation;
    private boolean mShowRealSettings;
    private boolean mActionDelivered;

    public static PerAppSheet newInstance(String requestKey, String packageName,
            String appName) {
        PerAppSheet sheet = new PerAppSheet();
        Bundle args = baseArgs(requestKey, appName, packageName);
        args.putString(ARG_PACKAGE, packageName);
        args.putString(ARG_APP_NAME, appName);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSpoofStore = new AppSpoofStore(requireContext());
        mStateStore = new AppStateStore(requireContext());
    }

    @Override
    protected int getContentLayout() {
        return R.layout.sheet_spoof_form;
    }

    @Override
    protected void onContentReady(@NonNull View content) {
        LinearLayout form = content.findViewById(R.id.form_container);
        Bundle args = requireArguments();
        mPackageName = args.getString(ARG_PACKAGE);
        mAppName = args.getString(ARG_APP_NAME);
        if (mPackageName == null) {
            dismiss();
            return;
        }

        AppSpoofStore.Config config = mSpoofStore.findConfig(mPackageName);
        AppStateStore.Entry entry = mStateStore.findEntry(mPackageName);
        mHideAccessibility = entry != null && entry.hideAccessibility;
        mIsolation = entry != null && entry.isolation;
        mShowRealSettings = entry != null && entry.showRealSettings;

        addSection(form, R.string.per_app_section_device);

        View profileRow = inflateRow(form, R.layout.row_spoof_action);
        SpoofUi.bindActionRow(profileRow, R.drawable.ic_settings_device,
                getString(R.string.per_app_profile_label),
                config != null ? config.profileName
                        : getString(R.string.spoof_per_app_no_profile));
        profileRow.setOnClickListener(v -> deliver(ACTION_PROFILE));
        form.addView(profileRow);

        if (config != null) {
            View propertiesRow = inflateRow(form, R.layout.row_spoof_action);
            SpoofUi.bindActionRow(propertiesRow, R.drawable.ic_edit,
                    getString(R.string.spoof_edit_properties), null);
            propertiesRow.setOnClickListener(v -> deliver(ACTION_PROPERTIES));
            form.addView(propertiesRow);
        }

        View copyPifRow = inflateRow(form, R.layout.row_spoof_action);
        SpoofUi.bindActionRow(copyPifRow, R.drawable.ic_upload,
                getString(R.string.per_app_copy_pif_title),
                getString(R.string.per_app_copy_pif_summary));
        copyPifRow.setOnClickListener(v -> deliver(ACTION_COPY_PIF));
        form.addView(copyPifRow);

        addSection(form, R.string.per_app_section_state);

        addSwitchRow(form, R.string.app_spoof_hide_a11y_title,
                R.string.app_spoof_hide_a11y_summary, mHideAccessibility,
                checked -> mHideAccessibility = checked);
        addSwitchRow(form, R.string.app_spoof_isolation_title,
                R.string.app_spoof_isolation_summary, mIsolation,
                checked -> mIsolation = checked);
        addSwitchRow(form, R.string.app_spoof_real_settings_title,
                R.string.app_spoof_real_settings_summary, mShowRealSettings,
                checked -> mShowRealSettings = checked);

        addSection(form, R.string.per_app_section_apply);
        View restartRow = inflateRow(form, R.layout.row_spoof_action);
        SpoofUi.bindActionRow(restartRow, R.drawable.ic_run,
                getString(R.string.per_app_restart_title),
                getString(R.string.per_app_restart_summary));
        restartRow.setOnClickListener(v -> restartApp());
        form.addView(restartRow);

        if (config != null || entry != null) {
            addAction(getString(R.string.spoof_remove_app), true,
                    () -> deliver(ACTION_REMOVE));
        }
        addPrimaryAction(getString(R.string.spoof_done), this::dismiss);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (!mActionDelivered && mPackageName != null) {
            Bundle result = new Bundle();
            result.putString(RESULT_PACKAGE, mPackageName);
            result.putBoolean(RESULT_CLOSED, true);
            deliverResult(result);
        }
        super.onDismiss(dialog);
    }

    private View inflateRow(LinearLayout form, int layout) {
        return LayoutInflater.from(form.getContext()).inflate(layout, form, false);
    }

    private void addSection(LinearLayout form, int titleRes) {
        TextView view = new TextView(form.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_Section);
        view.setText(titleRes);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = SpoofUi.dp(form.getContext(), 14);
        params.bottomMargin = SpoofUi.dp(form.getContext(), 2);
        form.addView(view, params);
    }

    private interface FlagSetter {
        void set(boolean checked);
    }

    private void addSwitchRow(LinearLayout form, int titleRes, int summaryRes, boolean checked,
            FlagSetter setter) {
        View row = inflateRow(form, R.layout.row_spoof_switch);
        SpoofUi.bindSwitchRow(row, getString(titleRes), getString(summaryRes), checked, true);
        MaterialSwitch toggle = row.findViewById(R.id.row_switch);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setter.set(isChecked);
            saveState();
        });
        form.addView(row);
    }

    private void saveState() {
        if (!mHideAccessibility && !mIsolation && !mShowRealSettings) {
            if (mStateStore.findEntry(mPackageName) != null) {
                mStateStore.removeEntry(mPackageName);
            }
            return;
        }
        mStateStore.putEntry(mPackageName, mAppName, mHideAccessibility, mIsolation,
                mShowRealSettings);
    }

    private void restartApp() {
        if (mPackageName == null) {
            return;
        }
        try {
            ActivityManager activityManager = (ActivityManager) requireContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                activityManager.forceStopPackageAsUser(mPackageName, UserHandle.myUserId());
            }
            Toast.makeText(requireContext(),
                    getString(R.string.spoof_app_restarted, mAppName), Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void deliver(String action) {
        Bundle result = new Bundle();
        result.putString(RESULT_PACKAGE, mPackageName);
        result.putString(RESULT_ACTION, action);
        mActionDelivered = true;
        deliverResult(result);
        dismiss();
    }
}
