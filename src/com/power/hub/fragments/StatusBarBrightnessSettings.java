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
import android.content.ContentResolver;
import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import com.android.settings.R;

import com.android.settings.SettingsPreferenceFragment;
import com.voltage.support.preferences.SecureSettingListPreference;

import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;

import java.util.List;
import java.util.ArrayList;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class StatusBarBrightnessSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String KEY_QS_SHOW_BRIGHTNESS_SLIDER = "qs_show_brightness_slider";
    private static final String KEY_QS_BRIGHTNESS_SLIDER_POSITION = "qs_brightness_slider_position";

    private SecureSettingListPreference mQsShowBrightnessSlider;
    private SecureSettingListPreference mQsBrightnessSliderPosition;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.powerhub_statusbar_brightness);

        ContentResolver resolver = getActivity().getContentResolver();

        PreferenceScreen prefSet = getPreferenceScreen();

        mQsShowBrightnessSlider = (SecureSettingListPreference) findPreference(KEY_QS_SHOW_BRIGHTNESS_SLIDER);
        mQsBrightnessSliderPosition = (SecureSettingListPreference) findPreference(KEY_QS_BRIGHTNESS_SLIDER_POSITION);

        if (mQsShowBrightnessSlider != null) {
            int brightnessSliderShow = Settings.Secure.getInt(resolver,
                    KEY_QS_SHOW_BRIGHTNESS_SLIDER, 1);
            mQsShowBrightnessSlider.setValue(String.valueOf(brightnessSliderShow));
            mQsShowBrightnessSlider.setSummary(mQsShowBrightnessSlider.getEntry());
            mQsShowBrightnessSlider.setOnPreferenceChangeListener(this);
            updateBrightnessPositionState(brightnessSliderShow);
        }

        if (mQsBrightnessSliderPosition != null) {
            int brightnessSliderPosition = Settings.Secure.getInt(resolver,
                    KEY_QS_BRIGHTNESS_SLIDER_POSITION, 0);
            mQsBrightnessSliderPosition.setValue(String.valueOf(brightnessSliderPosition));
            mQsBrightnessSliderPosition.setSummary(mQsBrightnessSliderPosition.getEntry());
            mQsBrightnessSliderPosition.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ContentResolver resolver = getActivity().getContentResolver();

        if (preference == mQsShowBrightnessSlider) {
            int value = Integer.parseInt((String) objValue);
            int index = mQsShowBrightnessSlider.findIndexOfValue((String) objValue);
            mQsShowBrightnessSlider.setSummary(mQsShowBrightnessSlider.getEntries()[index]);
            Settings.Secure.putInt(resolver,
                    KEY_QS_SHOW_BRIGHTNESS_SLIDER, value);
            updateBrightnessPositionState(value);
            return true;
        } else if (preference == mQsBrightnessSliderPosition) {
            int value = Integer.parseInt((String) objValue);
            int index = mQsBrightnessSliderPosition.findIndexOfValue((String) objValue);
            mQsBrightnessSliderPosition.setSummary(mQsBrightnessSliderPosition.getEntries()[index]);
            Settings.Secure.putInt(resolver,
                    KEY_QS_BRIGHTNESS_SLIDER_POSITION, value);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }

    private void updateBrightnessPositionState(int brightnessShowValue) {
        if (mQsBrightnessSliderPosition != null) {
            boolean enabled = brightnessShowValue != 0;
            mQsBrightnessSliderPosition.setEnabled(enabled);
            if (!enabled) {
                mQsBrightnessSliderPosition.setSummary(R.string.qs_brightness_slider_show_never);
            }
        }
    }

     /**
     * For Search.
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.powerhub_statusbar_brightness;
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
