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

import com.android.internal.logging.nano.MetricsProto;
import android.os.Bundle;
import android.content.Context;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.internal.util.voltage.VoltageUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;
import java.util.List;
import java.util.ArrayList;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class LockScreenWeatherSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_WEATHER_STYLE    = "lockscreen_weather_style";
    private static final String KEY_WEATHER_LOCATION = "lockscreen_weather_location";
    private static final String KEY_WEATHER_WIND     = "lockscreen_weather_wind_info";
    private static final String KEY_WEATHER_HUMIDITY = "lockscreen_weather_humidity_info";

    private ListPreference mWeatherStyle;
    private Preference[] mClassicOnlyPrefs;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_lockscreen_weather);

        mWeatherStyle = (ListPreference) findPreference(KEY_WEATHER_STYLE);
        if (mWeatherStyle != null) {
            mWeatherStyle.setOnPreferenceChangeListener(this);
        }

        mClassicOnlyPrefs = new Preference[] {
            findPreference(KEY_WEATHER_LOCATION),
            findPreference(KEY_WEATHER_WIND),
            findPreference(KEY_WEATHER_HUMIDITY),
        };

        updateClassicPrefsState(mWeatherStyle != null ? mWeatherStyle.getValue() : "0");
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mWeatherStyle) {
            updateClassicPrefsState((String) newValue);
            VoltageUtils.showSystemUiRestartDialog(getContext());
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateClassicPrefsState(mWeatherStyle != null ? mWeatherStyle.getValue() : "0");
    }

    private void updateClassicPrefsState(String value) {
        boolean isModern = "1".equals(value);
        if (mClassicOnlyPrefs == null) return;
        for (Preference p : mClassicOnlyPrefs) {
            if (p != null) p.setEnabled(!isModern);
        }
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
                    sir.xmlResId = R.xml.powerhub_lockscreen_weather;
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
