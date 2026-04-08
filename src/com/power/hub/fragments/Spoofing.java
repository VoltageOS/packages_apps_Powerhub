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

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SubSettings;

import java.util.Map;

public class Spoofing extends SettingsPreferenceFragment {

    private static final String KEY_PIF = "spoofing_pif";
    private static final String KEY_TRICKYSTORE = "spoofing_trickystore";
    private static final String KEY_APP_SPOOF = "spoofing_app_spoof";
    private static final String KEY_HIDE_APPLIST = "spoofing_hide_applist";

    private PifManager mPifManager;
    private KeyboxManager mKeyboxManager;

    private Preference mPifPreference;
    private Preference mTrickyStorePreference;
    private Preference mAppSpoofPreference;
    private Preference mHideAppListPreference;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requireActivity().setTitle(R.string.spoof_screen_title);
        mPifManager = new PifManager(requireContext());
        mKeyboxManager = new KeyboxManager(requireContext());

        addPreferencesFromResource(R.xml.spoofing);

        mPifPreference = findPreference(KEY_PIF);
        mTrickyStorePreference = findPreference(KEY_TRICKYSTORE);
        mAppSpoofPreference = findPreference(KEY_APP_SPOOF);
        mHideAppListPreference = findPreference(KEY_HIDE_APPLIST);

        mPifPreference.setOnPreferenceClickListener(preference -> {
            openFragment(PifFragment.class, getString(R.string.pif_category_title));
            return true;
        });
        mTrickyStorePreference.setOnPreferenceClickListener(preference -> {
            openFragment(TrickyStoreFragment.class, getString(R.string.trickystore_screen_title));
            return true;
        });
        mAppSpoofPreference.setOnPreferenceClickListener(preference -> {
            openFragment(AppSpoofFragment.class, getString(R.string.game_spoofing_title));
            return true;
        });
        mHideAppListPreference.setOnPreferenceClickListener(preference -> {
            openFragment(HideAppListSettings.class, getString(R.string.hide_applist_title));
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDashboard();
    }

    private void refreshDashboard() {
        bindPifSummary();
        bindTrickyStoreSummary();
        bindAppSpoofSummary();
        bindHideAppListSummary();
    }

    private void bindPifSummary() {
        String activeConfig = mPifManager.getActiveConfigName();
        Map<String, String> props = mPifManager.getCurrentProperties();
        String model = props.get("MODEL");
        String patch = props.get("SECURITY_PATCH");

        if (activeConfig.isEmpty()) {
            mPifPreference.setSummary(getString(R.string.pif_no_config_loaded) + "\n"
                    + getString(R.string.spoof_dashboard_pif_empty_detail));
            return;
        }

        if (model == null || model.isEmpty()) {
            model = getString(R.string.pif_no_props);
        }
        if (patch == null || patch.isEmpty()) {
            mPifPreference.setSummary(getString(R.string.pif_active_config, activeConfig) + "\n"
                    + model);
        } else {
            mPifPreference.setSummary(getString(R.string.pif_active_config, activeConfig) + "\n"
                    + getString(R.string.spoof_dashboard_pif_detail, model, patch));
        }
    }

    private void bindTrickyStoreSummary() {
        boolean keyboxExists = mKeyboxManager.keyboxExists();
        int targetCount = mKeyboxManager.getTargetAppCount();

        String status = keyboxExists
                ? getString(R.string.keybox_installed)
                : getString(R.string.keybox_not_found);
        String detail = targetCount > 0
                ? getString(R.string.target_apps_count, targetCount)
                : getString(R.string.spoof_dashboard_target_empty_detail);
        mTrickyStorePreference.setSummary(status + "\n" + detail);
    }

    private void bindAppSpoofSummary() {
        boolean enabled = AppSpoofFragment.isConfigEnabled(requireContext());
        int appCount = AppSpoofFragment.getConfiguredAppCount(requireContext());

        String status = enabled
                ? getString(R.string.game_spoofing_enabled)
                : getString(R.string.game_spoofing_disabled);
        String detail = appCount > 0
                ? getString(R.string.game_spoofing_configured_count, appCount)
                : getString(R.string.game_spoof_no_games);
        mAppSpoofPreference.setSummary(status + "\n" + detail);
    }

    private void bindHideAppListSummary() {
        String hiddenApps = Settings.Secure.getString(requireContext().getContentResolver(),
                Settings.Secure.HIDE_APPLIST);
        int appCount = 0;

        if (hiddenApps != null && !hiddenApps.trim().isEmpty()) {
            for (String packageName : hiddenApps.split(",")) {
                if (!packageName.trim().isEmpty()) {
                    appCount++;
                }
            }
        }

        String status = appCount > 0
                ? getString(R.string.hide_applist_selected_count, appCount)
                : getString(R.string.hide_applist_none_selected);
        String detail = appCount > 0
                ? getString(R.string.hide_applist_summary)
                : getString(R.string.spoof_dashboard_hide_applist_empty_detail);
        mHideAppListPreference.setSummary(status + "\n" + detail);
    }

    private void openFragment(@NonNull Class<? extends Fragment> fragmentClass,
            @NonNull String title) {
        Intent intent = new Intent(requireActivity(), SubSettings.class);
        intent.putExtra(":settings:show_fragment", fragmentClass.getName());
        intent.putExtra(":settings:show_fragment_title", title);
        startActivity(intent);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }
}
