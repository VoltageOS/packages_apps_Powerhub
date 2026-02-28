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
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
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
    private static final String PREF_SHADE_BLUR_RADIUS = "shade_blur_radius";
    private static final String PREF_VIBRANT_SHADE = "qs_vibrant_shade_elements";

    private SwitchPreferenceCompat mDualToneShadePref;
    private SwitchPreferenceCompat mVibrantShadePref;
    private CustomSeekBarPreference mShadeBlurRadiusPref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_monet_qs);

        final ContentResolver resolver = getActivity().getContentResolver();

        mDualToneShadePref = findPreference(PREF_DUAL_TONE_SHADE);
        mVibrantShadePref = findPreference(PREF_VIBRANT_SHADE);
        mShadeBlurRadiusPref = findPreference(PREF_SHADE_BLUR_RADIUS);

        boolean dualToneEnabled = Settings.System.getIntForUser(resolver,
                PREF_DUAL_TONE_SHADE, 1, UserHandle.USER_CURRENT) == 1;
        mDualToneShadePref.setChecked(dualToneEnabled);
        mDualToneShadePref.setOnPreferenceChangeListener(this);

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
