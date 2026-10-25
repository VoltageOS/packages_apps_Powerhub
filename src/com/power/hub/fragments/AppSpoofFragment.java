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
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.settings.R;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-app device property spoofing and app-state options in one page. */
public class AppSpoofFragment extends Fragment {

    private static final String REQ_PICK_APP = "spoof:perapp_pick";
    private static final String REQ_APP_SHEET = "spoof:perapp_sheet";
    private static final String REQ_APP_REMOVE = "spoof:perapp_remove";
    private static final String REQ_PROFILE_PICK = "spoof:perapp_profile_pick";
    private static final String REQ_PROFILE_MANAGE = "spoof:perapp_manage";
    private static final String REQ_PROFILE_EDIT = "spoof:perapp_profile_edit";
    private static final String REQ_PROFILE_DELETE = "spoof:perapp_profile_delete";
    private static final String REQ_PROPERTY_EDIT = "spoof:perapp_props";
    private static final Set<String> PIF_ONLY_KEYS = new LinkedHashSet<>(Arrays.asList(
            "spoofBuild", "spoofProps", "spoofProvider", "spoofSignature",
            "spoofVendingBuild", "spoofVendingSdk", "DEBUG", "verboseLogs"));

    private AppSpoofStore mStore;
    private AppStateStore mStateStore;
    private PifManager mPifManager;
    private TextView mHeroSummary;
    private TextView mStatusPill;
    private LinearLayout mAppsContainer;
    private TextView mEmptyView;
    private View mRowMaster;
    private View mRowDebug;
    private View mRowAdd;
    private View mRowManage;
    private MaterialSwitch mMasterSwitch;
    private String mReopenPackage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStore = new AppSpoofStore(requireContext());
        mStateStore = new AppStateStore(requireContext());
        mPifManager = new PifManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spoof_apps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mHeroSummary = view.findViewById(R.id.hero_summary);
        mStatusPill = view.findViewById(R.id.app_status_pill);
        mAppsContainer = view.findViewById(R.id.apps_container);
        mEmptyView = view.findViewById(R.id.apps_empty);
        mRowMaster = view.findViewById(R.id.row_master);
        mRowDebug = view.findViewById(R.id.row_debug);
        mRowAdd = view.findViewById(R.id.row_add);
        mRowManage = view.findViewById(R.id.row_manage_profiles);

        mMasterSwitch = mRowMaster.findViewById(R.id.row_switch);
        mRowMaster.setOnClickListener(v -> mMasterSwitch.toggle());

        mRowAdd.setOnClickListener(v -> {
            if (isEnabled()) {
                showAppPicker();
            } else {
                toast(R.string.spoof_enable_first);
            }
        });
        mRowManage.setOnClickListener(v -> {
            if (isEnabled()) {
                showManageProfiles();
            } else {
                toast(R.string.spoof_enable_first);
            }
        });

        registerListeners();
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadAll();
        refresh();
    }

    private void reloadAll() {
        mStore.reload();
        mStateStore.reload();
    }

    private boolean isEnabled() {
        return mStore.isEnabled() || mStateStore.isEnabled();
    }

    private void registerListeners() {
        getChildFragmentManager().setFragmentResultListener(REQ_PICK_APP,
                getViewLifecycleOwner(), (key, result) -> {
                    String packageName = result.getString(SpoofAppPickerSheet.RESULT_PACKAGE);
                    if (packageName != null) {
                        post(() -> showPerAppSheet(packageName));
                    }
                });

        getChildFragmentManager().setFragmentResultListener(REQ_APP_SHEET,
                getViewLifecycleOwner(), (key, result) -> {
                    String packageName = result.getString(PerAppSheet.RESULT_PACKAGE);
                    String action = result.getString(PerAppSheet.RESULT_ACTION);
                    if (packageName != null && action != null) {
                        mReopenPackage = packageName;
                        if (PerAppSheet.ACTION_PROFILE.equals(action)) {
                            post(() -> showProfilePicker(packageName));
                        } else if (PerAppSheet.ACTION_PROPERTIES.equals(action)) {
                            post(() -> showPropertyEditor(packageName));
                        } else if (PerAppSheet.ACTION_COPY_PIF.equals(action)) {
                            copyPifProfile(packageName);
                        } else if (PerAppSheet.ACTION_REMOVE.equals(action)) {
                            mReopenPackage = null;
                            post(() -> showRemoveConfirm(packageName));
                        }
                        return;
                    }
                    if (result.getBoolean(SpoofSheetFragment.RESULT_CLOSED, false)) {
                        reloadAll();
                        refresh();
                    }
                });

        getChildFragmentManager().setFragmentResultListener(REQ_APP_REMOVE,
                getViewLifecycleOwner(), (key, result) -> {
                    String packageName = result.getString(SpoofConfirmSheet.RESULT_CONTEXT);
                    if (packageName == null
                            || !result.getBoolean(SpoofConfirmSheet.RESULT_CONFIRMED)) {
                        return;
                    }
                    mStore.removeConfig(packageName);
                    mStateStore.removeEntry(packageName);
                    reloadAll();
                    refresh();
                    toast(R.string.spoof_app_removed);
                });

        getChildFragmentManager().setFragmentResultListener(REQ_PROFILE_PICK,
                getViewLifecycleOwner(), (key, result) -> {
                    String packageName = result.getString(SpoofChoiceSheet.RESULT_CONTEXT);
                    if (packageName == null) {
                        return;
                    }
                    if (result.getBoolean(SpoofChoiceSheet.RESULT_ACTION, false)) {
                        showProfileEditor(null, packageName);
                        return;
                    }
                    String profileName = result.getString(SpoofChoiceSheet.RESULT_ID);
                    if (profileName == null) {
                        return;
                    }
                    AppSpoofStore.Profile profile = mStore.findProfile(profileName);
                    if (profile == null) {
                        toast(R.string.spoof_profiles_empty);
                        return;
                    }
                    boolean replacing = mStore.findConfig(packageName) != null;
                    String label = SpoofUi.appLabel(requireContext(), packageName);
                    mStore.applyProfile(packageName, label, profile);
                    restartApp(packageName);
                    reloadAll();
                    refresh();
                    toast(replacing ? R.string.spoof_app_updated : R.string.spoof_app_added, label);
                    reopenIfPending(packageName);
                });

        getChildFragmentManager().setFragmentResultListener(REQ_PROPERTY_EDIT,
                getViewLifecycleOwner(), (key, result) -> {
                    String packageName = result.getString(PropertyEditorSheet.RESULT_CONTEXT);
                    Serializable serialized = result.getSerializable(
                            PropertyEditorSheet.RESULT_PROPERTIES);
                    if (packageName == null || !(serialized instanceof Map)) {
                        return;
                    }
                    //noinspection unchecked
                    Map<String, String> properties = (Map<String, String>) serialized;
                    String profileName = result.getString(PropertyEditorSheet.RESULT_PROFILE_NAME);
                    if (profileName != null) {
                        mStore.saveProfile(null, profileName, properties);
                    }
                    mStore.updateProperties(packageName, properties);
                    restartApp(packageName);
                    reloadAll();
                    refresh();
                    toast(R.string.game_spoof_config_saved);
                    reopenIfPending(packageName);
                });

        getChildFragmentManager().setFragmentResultListener(REQ_PROFILE_MANAGE,
                getViewLifecycleOwner(), (key, result) -> {
                    if (result.getBoolean(SpoofChoiceSheet.RESULT_ACTION, false)) {
                        showProfileEditor(null, null);
                        return;
                    }
                    String deleted = result.getString(SpoofChoiceSheet.RESULT_DELETE);
                    if (deleted != null) {
                        showDeleteProfileConfirm(deleted);
                        return;
                    }
                    String profileName = result.getString(SpoofChoiceSheet.RESULT_ID);
                    if (profileName == null) {
                        return;
                    }
                    AppSpoofStore.Profile profile = mStore.findProfile(profileName);
                    if (profile == null) {
                        return;
                    }
                    if (!profile.isCustom) {
                        toast(R.string.spoof_profile_builtin_locked);
                        return;
                    }
                    showProfileEditor(profile, null);
                });

        getChildFragmentManager().setFragmentResultListener(REQ_PROFILE_EDIT,
                getViewLifecycleOwner(), (key, result) -> {
                    String name = result.getString(ProfileEditorSheet.RESULT_NAME);
                    String original = result.getString(ProfileEditorSheet.RESULT_ORIGINAL);
                    String context = result.getString(ProfileEditorSheet.RESULT_CONTEXT);
                    Serializable serialized = result.getSerializable(
                            ProfileEditorSheet.RESULT_PROPERTIES);
                    if (name == null || !(serialized instanceof Map)) {
                        return;
                    }
                    //noinspection unchecked
                    Map<String, String> properties = (Map<String, String>) serialized;
                    mStore.saveProfile(original, name, properties);
                    mStore.reload();
                    if (context != null) {
                        AppSpoofStore.Profile profile = mStore.findProfile(name);
                        if (profile != null) {
                            boolean replacing = mStore.findConfig(context) != null;
                            String label = SpoofUi.appLabel(requireContext(), context);
                            mStore.applyProfile(context, label, profile);
                            mStore.reload();
                            restartApp(context);
                            toast(replacing ? R.string.spoof_app_updated
                                    : R.string.spoof_app_added, label);
                            reopenIfPending(context);
                        }
                    } else {
                        toast(original == null ? R.string.game_spoof_preset_saved_named
                                : R.string.game_spoof_preset_updated_named, name);
                    }
                    refresh();
                });

        getChildFragmentManager().setFragmentResultListener(REQ_PROFILE_DELETE,
                getViewLifecycleOwner(), (key, result) -> {
                    String name = result.getString(SpoofConfirmSheet.RESULT_CONTEXT);
                    if (name == null || !result.getBoolean(SpoofConfirmSheet.RESULT_CONFIRMED)) {
                        return;
                    }
                    mStore.deleteProfile(name);
                    mStore.reload();
                    refresh();
                    toast(R.string.app_spoof_profile_deleted);
                });
    }

    private void refresh() {
        if (!isAdded() || getView() == null) {
            return;
        }
        boolean enabled = isEnabled();
        List<PerAppRow> rows = buildRows();

        mHeroSummary.setText(getString(R.string.app_spoof_configured_apps_count, rows.size()));
        SpoofUi.bindStatusPill(mStatusPill,
                getString(enabled ? R.string.spoof_status_enabled_count
                        : R.string.spoof_status_disabled_count, rows.size()),
                enabled ? SpoofUi.TONE_ACCENT : SpoofUi.TONE_MUTED);

        SpoofUi.bindSwitchRow(mRowMaster, getString(R.string.app_spoof_enabled_title),
                getString(R.string.app_spoof_enabled_summary), enabled, true);
        mMasterSwitch = mRowMaster.findViewById(R.id.row_switch);
        mMasterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mStore.setEnabled(isChecked);
            mStateStore.setEnabled(isChecked);
            refresh();
        });

        SpoofUi.bindSwitchRow(mRowDebug, getString(R.string.app_spoof_debug_title),
                getString(R.string.app_spoof_debug_summary), mStore.isDebug(), true);
        MaterialSwitch debugSwitch = mRowDebug.findViewById(R.id.row_switch);
        debugSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                mStore.setDebug(isChecked));

        SpoofUi.bindActionRow(mRowAdd, R.drawable.ic_plus, getString(R.string.app_spoof_add_app),
                getString(R.string.app_spoof_add_app_summary));
        SpoofUi.bindActionRow(mRowManage, R.drawable.ic_edit,
                getString(R.string.app_spoof_manage_profiles_title),
                getString(R.string.app_spoof_manage_profiles_summary));
        setRowEnabled(mRowAdd, enabled);
        setRowEnabled(mRowManage, enabled);

        mAppsContainer.removeAllViews();
        mEmptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        for (PerAppRow info : rows) {
            View row = LayoutInflater.from(mAppsContainer.getContext())
                    .inflate(R.layout.row_spoof_app, mAppsContainer, false);
            ImageView icon = row.findViewById(R.id.row_icon);
            TextView title = row.findViewById(R.id.row_title);
            TextView summary = row.findViewById(R.id.row_summary);
            icon.setImageDrawable(SpoofUi.loadAppIcon(requireContext(), info.packageName));
            title.setText(info.appName);
            summary.setText(rowSummary(info));
            row.setAlpha(enabled ? 1f : 0.45f);
            row.setOnClickListener(v -> {
                if (isEnabled()) {
                    showPerAppSheet(info.packageName);
                }
            });
            mAppsContainer.addView(row);
        }
    }

    private List<PerAppRow> buildRows() {
        Map<String, PerAppRow> rows = new LinkedHashMap<>();
        for (AppSpoofStore.Config config : mStore.getConfigs()) {
            PerAppRow info = rows.get(config.packageName);
            if (info == null) {
                info = new PerAppRow(config.packageName, config.appName);
                rows.put(config.packageName, info);
            }
            info.profileName = config.profileName;
        }
        for (AppStateStore.Entry entry : mStateStore.getEntries()) {
            PerAppRow info = rows.get(entry.packageName);
            if (info == null) {
                info = new PerAppRow(entry.packageName, entry.appName);
                rows.put(entry.packageName, info);
            }
            info.hideAccessibility = entry.hideAccessibility;
            info.isolation = entry.isolation;
            info.showRealSettings = entry.showRealSettings;
        }
        List<PerAppRow> sorted = new ArrayList<>(rows.values());
        Collections.sort(sorted, (left, right) ->
                left.appName.compareToIgnoreCase(right.appName));
        return sorted;
    }

    private String rowSummary(PerAppRow info) {
        String profile = info.profileName != null ? info.profileName
                : getString(R.string.spoof_per_app_no_profile);
        List<String> flags = new ArrayList<>();
        if (info.hideAccessibility) {
            flags.add(getString(R.string.app_spoof_hide_a11y_title));
        }
        if (info.isolation) {
            flags.add(getString(R.string.app_spoof_isolation_title));
        }
        if (info.showRealSettings) {
            flags.add(getString(R.string.app_spoof_real_settings_title));
        }
        String flagText = flags.isEmpty() ? getString(R.string.spoof_app_state_no_flags)
                : TextUtils.join(" · ", flags);
        return profile + " · " + flagText;
    }

    private void setRowEnabled(View row, boolean enabled) {
        row.setAlpha(enabled ? 1f : 0.45f);
    }

    private void copyPifProfile(String packageName) {
        Map<String, String> properties = new LinkedHashMap<>(mPifManager.getCurrentProperties());
        for (String key : PIF_ONLY_KEYS) {
            properties.remove(key);
        }
        if (properties.isEmpty()) {
            toast(R.string.pif_no_props);
            return;
        }
        String label = SpoofUi.appLabel(requireContext(), packageName);
        mStore.setProperties(packageName, label, properties);
        restartApp(packageName);
        reloadAll();
        refresh();
        toast(R.string.app_spoof_pif_copied, label);
        reopenIfPending(packageName);
    }

    private void restartApp(String packageName) {
        try {
            ActivityManager activityManager = (ActivityManager)
                    requireContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                activityManager.forceStopPackageAsUser(packageName, UserHandle.myUserId());
            }
        } catch (Exception ignored) {
        }
    }

    private void showAppPicker() {
        Set<String> excluded = new LinkedHashSet<>();
        for (AppSpoofStore.Config config : mStore.getConfigs()) {
            excluded.add(config.packageName);
        }
        for (AppStateStore.Entry entry : mStateStore.getEntries()) {
            excluded.add(entry.packageName);
        }
        SpoofAppPickerSheet.newInstance(REQ_PICK_APP, getString(R.string.spoof_app_picker_title),
                SpoofAppAdapter.MODE_SINGLE, null, new ArrayList<>(excluded), false, false, false)
                .show(getChildFragmentManager(), "perapp_picker");
    }

    private void showPerAppSheet(String packageName) {
        PerAppSheet.newInstance(REQ_APP_SHEET, packageName,
                SpoofUi.appLabel(requireContext(), packageName))
                .show(getChildFragmentManager(), "perapp_sheet");
    }

    private void showRemoveConfirm(String packageName) {
        SpoofConfirmSheet.newInstance(REQ_APP_REMOVE,
                getString(R.string.spoof_remove_app_title),
                getString(R.string.spoof_remove_app_message),
                getString(R.string.action_delete), packageName)
                .show(getChildFragmentManager(), "perapp_remove");
    }

    private void showProfilePicker(String packageName) {
        List<AppSpoofStore.Profile> profiles = mStore.getProfiles();
        if (profiles.isEmpty()) {
            toast(R.string.spoof_profiles_empty);
            return;
        }
        ArrayList<SpoofChoiceSheet.Item> items = new ArrayList<>();
        for (AppSpoofStore.Profile profile : profiles) {
            items.add(new SpoofChoiceSheet.Item(profile.name, profile.name,
                    getString(profile.isCustom ? R.string.spoof_custom_profile
                            : R.string.spoof_builtin_profile)));
        }
        SpoofChoiceSheet.newInstance(REQ_PROFILE_PICK,
                getString(R.string.spoof_choose_profile),
                SpoofUi.appLabel(requireContext(), packageName),
                items, getString(R.string.app_spoof_profile_add), packageName)
                .show(getChildFragmentManager(), "perapp_profiles");
    }

    private void showPropertyEditor(String packageName) {
        AppSpoofStore.Config config = mStore.findConfig(packageName);
        Map<String, String> properties = new LinkedHashMap<>();
        if (config != null && !config.props.isEmpty()) {
            properties.putAll(config.props);
        } else {
            properties.put("MODEL", "");
            properties.put("MANUFACTURER", "");
        }
        String label = config != null ? config.appName
                : SpoofUi.appLabel(requireContext(), packageName);
        PropertyEditorSheet.newInstance(REQ_PROPERTY_EDIT,
                getString(R.string.spoof_property_editor_title, label), properties, packageName)
                .show(getChildFragmentManager(), "perapp_props");
    }

    private void showManageProfiles() {
        List<AppSpoofStore.Profile> profiles = mStore.getProfiles();
        ArrayList<SpoofChoiceSheet.Item> items = new ArrayList<>();
        for (AppSpoofStore.Profile profile : profiles) {
            items.add(new SpoofChoiceSheet.Item(profile.name, profile.name,
                    getString(profile.isCustom ? R.string.spoof_custom_profile
                            : R.string.spoof_builtin_profile), 0, false, profile.isCustom));
        }
        SpoofChoiceSheet.newInstance(REQ_PROFILE_MANAGE, getString(R.string.spoof_profiles_title),
                null, items, getString(R.string.app_spoof_profile_add))
                .show(getChildFragmentManager(), "perapp_manage");
    }

    private void showProfileEditor(@Nullable AppSpoofStore.Profile profile,
            @Nullable String packageName) {
        Map<String, String> properties = profile == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(profile.props);
        if (properties.isEmpty()) {
            properties.put("MODEL", "");
            properties.put("MANUFACTURER", "");
        }
        String title = getString(profile == null ? R.string.app_spoof_profile_add
                : R.string.app_spoof_profile_edit);
        ProfileEditorSheet.newInstance(REQ_PROFILE_EDIT, title,
                profile == null ? null : profile.name, properties, packageName)
                .show(getChildFragmentManager(), "perapp_profile_edit");
    }

    private void showDeleteProfileConfirm(String name) {
        SpoofConfirmSheet.newInstance(REQ_PROFILE_DELETE,
                getString(R.string.app_spoof_profile_delete),
                getString(R.string.app_spoof_profile_delete_confirm_msg, name),
                getString(R.string.action_delete), name)
                .show(getChildFragmentManager(), "perapp_profile_delete");
    }

    private void reopenIfPending(String packageName) {
        if (packageName.equals(mReopenPackage)) {
            mReopenPackage = null;
            post(() -> showPerAppSheet(packageName));
        }
    }

    private void post(Runnable action) {
        View view = getView();
        if (view != null) {
            view.post(action);
        }
    }

    private void toast(int resId, Object... args) {
        if (isAdded()) {
            Toast.makeText(requireContext(), getString(resId, args), Toast.LENGTH_SHORT).show();
        }
    }

    private static final class PerAppRow {
        final String packageName;
        final String appName;
        String profileName;
        boolean hideAccessibility;
        boolean isolation;
        boolean showRealSettings;

        PerAppRow(String packageName, String appName) {
            this.packageName = packageName;
            this.appName = appName;
        }
    }
}
