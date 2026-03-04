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
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.voltage.support.preferences.CustomSeekBarPreference;
import com.android.internal.util.voltage.VoltageUtils;

import java.util.List;

@SearchIndexable
public class Wallpaper extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "Wallpaper";

    private Preference mBlurWpPref;
    private Preference mBlurWpStylePref;
    private Preference mDimPref;
    private Preference mDimLvlPref;

    private Preference mHazePref;
    private Preference mHazeStylePref;
    private Preference mHazeIntensityPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.powerhub_wallpaper);

        mBlurWpPref = findPreference("persist.sys.wallpaper.blur_enabled");
        mBlurWpPref.setOnPreferenceChangeListener(this);

        mBlurWpStylePref = findPreference("persist.sys.wallpaper.blur_type");
        mBlurWpStylePref.setOnPreferenceChangeListener(this);

        mDimPref = findPreference("persist.sys.wallpaper.dim_enabled");
        mDimPref.setOnPreferenceChangeListener(this);

        mDimLvlPref = findPreference("persist.sys.wallpaper.dim_level");
        mDimLvlPref.setOnPreferenceChangeListener(this);

        mHazePref = findPreference("haze_enabled");
        mHazePref.setOnPreferenceChangeListener(this);

        mHazeStylePref = findPreference("haze_style");
        mHazeIntensityPref = findPreference("haze_intensity");

        updatePreferenceStates();
    }

    private void updatePreferenceStates() {
        final Context context = getContext();
        if (context == null) return;

        boolean hazeEnabled = Settings.System.getInt(
                context.getContentResolver(), "haze_enabled", 0) != 0;

        boolean blurEnabled = "1".equals(
                android.os.SystemProperties.get("persist.sys.wallpaper.blur_enabled", "0"));
        boolean dimEnabled = "1".equals(
                android.os.SystemProperties.get("persist.sys.wallpaper.dim_enabled", "0"));
        boolean blurDimActive = blurEnabled || dimEnabled;

        mHazePref.setEnabled(!blurDimActive);
        mHazeStylePref.setEnabled(!blurDimActive && hazeEnabled);
        mHazeIntensityPref.setEnabled(!blurDimActive && hazeEnabled);

        mBlurWpPref.setEnabled(!hazeEnabled);
        mBlurWpStylePref.setEnabled(!hazeEnabled && blurEnabled);
        mDimPref.setEnabled(!hazeEnabled);
        mDimLvlPref.setEnabled(!hazeEnabled && dimEnabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getContext();

        if (preference == mBlurWpPref
                || preference == mBlurWpStylePref
                || preference == mDimPref
                || preference == mDimLvlPref) {

            if (preference == mDimLvlPref) {
                android.os.SystemProperties.set(
                        "persist.sys.wallpaper.dim_level", newValue.toString());
            }

            updatePreferenceStates();

            VoltageUtils.showSystemUiRestartDialog(context);
            return true;
        }

        if (preference == mHazePref) {
            boolean hazeNowEnabled = (Boolean) newValue;

            mHazeStylePref.setEnabled(hazeNowEnabled);
            mHazeIntensityPref.setEnabled(hazeNowEnabled);

            mBlurWpPref.setEnabled(!hazeNowEnabled);
            mDimPref.setEnabled(!hazeNowEnabled);

            boolean blurEnabled = "1".equals(
                    android.os.SystemProperties.get("persist.sys.wallpaper.blur_enabled", "0"));
            boolean dimEnabled = "1".equals(
                    android.os.SystemProperties.get("persist.sys.wallpaper.dim_enabled", "0"));

            mBlurWpStylePref.setEnabled(!hazeNowEnabled && blurEnabled);
            mDimLvlPref.setEnabled(!hazeNowEnabled && dimEnabled);

            return true;
        }

        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.powerhub_wallpaper) {
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    return keys;
                }
            };
}
