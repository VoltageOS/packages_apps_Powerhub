/*
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
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class AppStateFragment extends SettingsPreferenceFragment {

    private static final String TAG = "AppState";

    static final String CONFIG_KEY = "spoof_appstate_config";
    private static final String LEGACY_GAMEPROPS_KEY = "spoof_gameprops_config";
    private static final String KEY_STATUS = "app_state_status";
    private static final String KEY_ENABLED = "app_state_enabled";
    private static final String KEY_ADD_APP = "app_state_add_app";
    private static final String KEY_APP_CATEGORY = "app_state_app_list_category";

    private final List<AppEntry> mEntries = new ArrayList<>();

    private boolean mEnabled;
    private Preference mStatusPreference;
    private SwitchPreferenceCompat mEnabledSwitch;
    private Preference mAddAppPreference;
    private PreferenceCategory mAppCategory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().setTitle(R.string.app_state_screen_title);
        migrateFromGamePropsIfNeeded(requireContext());
        loadConfig();
        addPreferencesFromResource(R.xml.app_state_settings);

        mStatusPreference = findPreference(KEY_STATUS);
        mEnabledSwitch = findPreference(KEY_ENABLED);
        mAddAppPreference = findPreference(KEY_ADD_APP);
        mAppCategory = findPreference(KEY_APP_CATEGORY);

        mEnabledSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            mEnabled = (Boolean) newValue;
            saveConfig();
            refreshUi();
            return true;
        });
        mAddAppPreference.setOnPreferenceClickListener(preference -> {
            showAddAppDialog();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConfig();
        refreshUi();
    }

    public static boolean isConfigEnabled(@NonNull Context context) {
        try {
            String content = Settings.Secure.getString(
                    context.getContentResolver(), CONFIG_KEY);
            if (content == null || content.isEmpty()) {
                return false;
            }
            return new JSONObject(content).optBoolean("enabled", false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read enabled state", e);
            return false;
        }
    }

    public static int getConfiguredAppCount(@NonNull Context context) {
        try {
            String content = Settings.Secure.getString(
                    context.getContentResolver(), CONFIG_KEY);
            if (content == null || content.isEmpty()) {
                return 0;
            }
            JSONObject root = new JSONObject(content);
            JSONObject apps = root.optJSONObject("apps");
            return apps != null ? apps.length() : 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to count configs", e);
            return 0;
        }
    }

    private void refreshUi() {
        if (mEnabledSwitch == null) {
            return;
        }

        mStatusPreference.setTitle(mEnabled
                ? getString(R.string.app_state_enabled)
                : getString(R.string.app_state_disabled));
        mStatusPreference.setSummary(mEntries.isEmpty()
                ? getString(R.string.app_state_no_apps)
                : getString(R.string.app_state_configured_count, mEntries.size()));

        mEnabledSwitch.setOnPreferenceChangeListener(null);
        mEnabledSwitch.setChecked(mEnabled);
        mEnabledSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            mEnabled = (Boolean) newValue;
            saveConfig();
            refreshUi();
            return true;
        });

        mAddAppPreference.setEnabled(mEnabled);
        bindConfiguredApps();
    }

    private void bindConfiguredApps() {
        mAppCategory.removeAll();
        mAppCategory.setTitle(getString(R.string.app_state_configured_apps));

        if (mEntries.isEmpty()) {
            Preference emptyPreference = new Preference(requireContext());
            emptyPreference.setTitle(R.string.app_state_no_apps);
            emptyPreference.setSelectable(false);
            mAppCategory.addPreference(emptyPreference);
            return;
        }

        List<AppEntry> sorted = new ArrayList<>(mEntries);
        Collections.sort(sorted, (left, right) -> left.appName.compareToIgnoreCase(right.appName));
        PackageManager packageManager = requireContext().getPackageManager();

        for (AppEntry entry : sorted) {
            Preference preference = new Preference(requireContext());
            preference.setTitle(entry.appName);
            StringBuilder flags = new StringBuilder();
            if (entry.hideAccessibility) {
                flags.append(getString(R.string.app_spoof_hide_a11y_title));
            }
            if (entry.isolation) {
                if (flags.length() > 0) flags.append(", ");
                flags.append(getString(R.string.app_spoof_isolation_title));
            }
            if (entry.showRealSettings) {
                if (flags.length() > 0) flags.append(", ");
                flags.append(getString(R.string.app_spoof_real_settings_title));
            }
            preference.setSummary(entry.packageName + "\n" + flags);
            preference.setEnabled(mEnabled);
            preference.setIcon(resolveAppIcon(packageManager, entry.packageName));
            preference.setOnPreferenceClickListener(clicked -> {
                showEditAppDialog(entry);
                return true;
            });
            mAppCategory.addPreference(preference);
        }
    }

    private Drawable resolveAppIcon(PackageManager packageManager, String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return appInfo.loadIcon(packageManager);
        } catch (Exception ignored) {
            return requireContext().getDrawable(android.R.drawable.sym_def_app_icon);
        }
    }

    private void showAddAppDialog() {
        if (!mEnabled) {
            return;
        }
        new Thread(() -> {
            PackageManager packageManager = requireContext().getPackageManager();
            List<ApplicationInfo> installedApps =
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            List<ApplicationInfo> availableApps = new ArrayList<>();

            for (ApplicationInfo appInfo : installedApps) {
                boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (isSystem) {
                    continue;
                }

                boolean alreadyAdded = false;
                for (AppEntry entry : mEntries) {
                    if (entry.packageName.equals(appInfo.packageName)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    availableApps.add(appInfo);
                }
            }

            availableApps.sort((a, b) -> packageManager.getApplicationLabel(a).toString()
                    .compareToIgnoreCase(packageManager.getApplicationLabel(b).toString()));

            String[] labels = new String[availableApps.size()];
            for (int i = 0; i < availableApps.size(); i++) {
                labels[i] = packageManager.getApplicationLabel(availableApps.get(i)).toString();
            }

            requireActivity().runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.app_state_select_app)
                    .setItems(labels,
                            (dialog, which) -> {
                                ApplicationInfo appInfo = availableApps.get(which);
                                String label = packageManager.getApplicationLabel(appInfo)
                                        .toString();
                                showAppOptionsDialog(new AppEntry(appInfo.packageName, label,
                                        false, false, false), true);
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
        }).start();
    }

    private void showEditAppDialog(AppEntry entry) {
        if (!mEnabled) {
            return;
        }
        String[] options = {
                getString(R.string.app_state_options_title),
                getString(R.string.app_state_remove)
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(entry.appName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showAppOptionsDialog(entry, false);
                    } else {
                        mEntries.remove(entry);
                        saveConfig();
                        refreshUi();
                        Toast.makeText(requireContext(), R.string.app_state_removed,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAppOptionsDialog(AppEntry entry, boolean isNew) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(12), dp(24), dp(12));
        android.widget.Switch hideA11y = new android.widget.Switch(requireContext());
        hideA11y.setText(R.string.app_spoof_hide_a11y_title);
        hideA11y.setChecked(entry.hideAccessibility);
        android.widget.Switch isolation = new android.widget.Switch(requireContext());
        isolation.setText(R.string.app_spoof_isolation_title);
        isolation.setChecked(entry.isolation);
        android.widget.Switch realSettings = new android.widget.Switch(requireContext());
        realSettings.setText(R.string.app_spoof_real_settings_title);
        realSettings.setChecked(entry.showRealSettings);
        root.addView(hideA11y);
        root.addView(isolation);
        root.addView(realSettings);
        new AlertDialog.Builder(requireContext())
                .setTitle(entry.appName)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    entry.hideAccessibility = hideA11y.isChecked();
                    entry.isolation = isolation.isChecked();
                    entry.showRealSettings = realSettings.isChecked();
                    if (isNew) {
                        mEntries.add(entry);
                    }
                    saveConfig();
                    refreshUi();
                    Toast.makeText(requireContext(),
                            isNew ? getString(R.string.app_state_app_added, entry.appName)
                                    : getString(R.string.app_state_config_saved),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void loadConfig() {
        mEntries.clear();
        mEnabled = false;

        String content = Settings.Secure.getString(
                requireContext().getContentResolver(), CONFIG_KEY);
        if (content == null || content.isEmpty()) {
            return;
        }

        try {
            JSONObject json = new JSONObject(content);
            mEnabled = json.optBoolean("enabled", false);
            JSONObject apps = json.optJSONObject("apps");
            if (apps == null) {
                return;
            }

            PackageManager packageManager = requireContext().getPackageManager();
            Iterator<String> packages = apps.keys();
            while (packages.hasNext()) {
                String packageName = packages.next();
                JSONObject obj = apps.getJSONObject(packageName);

                String appName;
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                    appName = packageManager.getApplicationLabel(appInfo).toString();
                } catch (Exception e) {
                    appName = packageName;
                }

                mEntries.add(new AppEntry(packageName, appName,
                        obj.optBoolean("hideAccessibility", false),
                        obj.optBoolean("isolation", false),
                        obj.optBoolean("showRealSettings", false)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load app state config", e);
        }
    }

    private void saveConfig() {
        try {
            JSONObject root = new JSONObject();
            JSONObject apps = new JSONObject();
            root.put("enabled", mEnabled);

            for (AppEntry entry : mEntries) {
                JSONObject obj = new JSONObject();
                obj.put("hideAccessibility", entry.hideAccessibility);
                obj.put("isolation", entry.isolation);
                obj.put("showRealSettings", entry.showRealSettings);
                apps.put(entry.packageName, obj);
            }

            root.put("apps", apps);
            Settings.Secure.putString(
                    requireContext().getContentResolver(),
                    CONFIG_KEY,
                    root.toString(2));
        } catch (Exception e) {
            Log.e(TAG, "Failed to save app state config", e);
        }
    }

    private static void migrateFromGamePropsIfNeeded(@NonNull Context context) {
        String current = Settings.Secure.getString(context.getContentResolver(), CONFIG_KEY);
        if (current != null && !current.isEmpty()) {
            return;
        }
        String legacy = Settings.Secure.getString(context.getContentResolver(),
                LEGACY_GAMEPROPS_KEY);
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(legacy);
            JSONObject games = root.optJSONObject("games");
            if (games == null) {
                return;
            }
            JSONObject apps = new JSONObject();
            boolean found = false;
            Iterator<String> keys = games.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                JSONObject entry = games.optJSONObject(pkg);
                if (entry == null || !entry.has("props")) {
                    continue;
                }
                boolean hide = entry.optBoolean("hideAccessibility", false);
                boolean iso = entry.optBoolean("isolation", false);
                boolean real = entry.optBoolean("showRealSettings", false);
                if (hide || iso || real) {
                    JSONObject obj = new JSONObject();
                    obj.put("hideAccessibility", hide);
                    obj.put("isolation", iso);
                    obj.put("showRealSettings", real);
                    apps.put(pkg, obj);
                    found = true;
                }
            }
            if (found) {
                JSONObject out = new JSONObject();
                out.put("enabled", root.optBoolean("enabled", false));
                out.put("apps", apps);
                Settings.Secure.putString(context.getContentResolver(), CONFIG_KEY,
                        out.toString(2));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate app state config", e);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }

    private static final class AppEntry {
        final String packageName;
        final String appName;
        boolean hideAccessibility;
        boolean isolation;
        boolean showRealSettings;

        AppEntry(String packageName, String appName, boolean hideAccessibility,
                boolean isolation, boolean showRealSettings) {
            this.packageName = packageName;
            this.appName = appName;
            this.hideAccessibility = hideAccessibility;
            this.isolation = isolation;
            this.showRealSettings = showRealSettings;
        }
    }
}
