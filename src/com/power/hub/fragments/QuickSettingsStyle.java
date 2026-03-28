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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.voltage.VoltageUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;

import com.voltage.support.preferences.CustomSeekBarPreference;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class QuickSettingsStyle extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String PREF_DUAL_TONE_SHADE = "qs_dual_tone";
    private static final String PREF_QS_SPLIT_SHADE = Settings.System.QS_SPLIT_SHADE;
    private static final String PREF_SHADE_BLUR_RADIUS = "shade_blur_radius";
    private static final String PREF_VIBRANT_SHADE = "qs_vibrant_shade_elements";

    private SwitchPreferenceCompat mDualToneShadePref;
    private SwitchPreferenceCompat mQsSplitShadePref;
    private SwitchPreferenceCompat mVibrantShadePref;
    private CustomSeekBarPreference mShadeBlurRadiusPref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_monet_qs);

        final ContentResolver resolver = getActivity().getContentResolver();

        mDualToneShadePref = findPreference(PREF_DUAL_TONE_SHADE);
        mQsSplitShadePref = findPreference(PREF_QS_SPLIT_SHADE);
        mVibrantShadePref = findPreference(PREF_VIBRANT_SHADE);
        mShadeBlurRadiusPref = findPreference(PREF_SHADE_BLUR_RADIUS);

        boolean dualToneEnabled = Settings.System.getIntForUser(resolver,
                PREF_DUAL_TONE_SHADE, 1, UserHandle.USER_CURRENT) == 1;
        mDualToneShadePref.setChecked(dualToneEnabled);
        mDualToneShadePref.setOnPreferenceChangeListener(this);

        boolean defaultSplitShadePortrait = false;
        boolean defaultSplitShadeLandscape = false;
        try {
            Context sysUiContext = getContext().createPackageContext("com.android.systemui", 0);
            int resId = sysUiContext.getResources().getIdentifier(
                    "config_use_split_notification_shade", "bool", "com.android.systemui");
            if (resId != 0) {
                Configuration portConfig = new Configuration(sysUiContext.getResources().getConfiguration());
                portConfig.orientation = Configuration.ORIENTATION_PORTRAIT;
                Context portContext = sysUiContext.createConfigurationContext(portConfig);
                defaultSplitShadePortrait = portContext.getResources().getBoolean(resId);
                Configuration landConfig = new Configuration(sysUiContext.getResources().getConfiguration());
                landConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
                Context landContext = sysUiContext.createConfigurationContext(landConfig);
                defaultSplitShadeLandscape = landContext.getResources().getBoolean(resId);
            }
        } catch (Exception ignored) {
        }
        if (defaultSplitShadePortrait) {
            mQsSplitShadePref.setVisible(false);
        } else {
            int userSetting = Settings.System.getIntForUser(resolver, PREF_QS_SPLIT_SHADE, -1, UserHandle.USER_CURRENT);
            boolean isEnabled = (userSetting == -1) ? defaultSplitShadeLandscape : (userSetting == 1);
            mQsSplitShadePref.setChecked(isEnabled);
            mQsSplitShadePref.setOnPreferenceChangeListener(this);
        }

        boolean vibrantShadeEnabled = Settings.System.getIntForUser(resolver,
                PREF_VIBRANT_SHADE, 0, UserHandle.USER_CURRENT) == 1;
        mVibrantShadePref.setChecked(vibrantShadeEnabled);
        mVibrantShadePref.setOnPreferenceChangeListener(this);

        int shadeBlurRadius = Settings.System.getIntForUser(resolver,
                PREF_SHADE_BLUR_RADIUS, 20, UserHandle.USER_CURRENT);
        mShadeBlurRadiusPref.setValue(shadeBlurRadius);
        mShadeBlurRadiusPref.setOnPreferenceChangeListener(this);

        boolean blurEnabled = Settings.Global.getInt(resolver,
                Settings.Global.DISABLE_WINDOW_BLURS, 0) == 0;
        mShadeBlurRadiusPref.setEnabled(blurEnabled);
        if (!blurEnabled) {
            mShadeBlurRadiusPref.setSummary("System blur is disabled");
        } else {
            mShadeBlurRadiusPref.setSummary(R.string.shade_blur_radius_summary);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mDualToneShadePref) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, PREF_DUAL_TONE_SHADE,
                    value ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mQsSplitShadePref) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, PREF_QS_SPLIT_SHADE,
                    value ? 1 : 0, UserHandle.USER_CURRENT);
            VoltageUtils.showSystemUiRestartDialog(getContext());
            return true;
        } else if (preference == mVibrantShadePref) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, PREF_VIBRANT_SHADE,
                    value ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mShadeBlurRadiusPref) {
            int value = (Integer) newValue;
            Settings.System.putIntForUser(resolver, PREF_SHADE_BLUR_RADIUS,
                    value, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
                    ArrayList<SearchIndexableResource> result = new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.powerhub_monet_qs;
                    result.add(sir);
                    return result;
                }
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    return keys;
                }
    };
}
