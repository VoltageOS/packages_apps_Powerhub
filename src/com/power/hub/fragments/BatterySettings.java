/*
 * Copyright (C) 2018 AospExtended ROM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import android.provider.Settings;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.Indexable;
import com.android.settingslib.search.SearchIndexable;

import com.voltage.support.preferences.SystemSettingListPreference;
import com.voltage.support.preferences.SystemSettingSwitchPreference;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable
public class BatterySettings extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    private static final String BATTERY_STYLE = "status_bar_battery_style";
    private static final String SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";
    private static final String SHOW_BATTERY_PERCENT_CHARGING = "status_bar_show_battery_percent_charging";
    private static final String SHOW_BATTERY_PERCENT_INSIDE = "status_bar_show_battery_percent_inside";
    private static final String NEW_STATUS_BAR_ICONS = "new_status_bar_icons_enabled";

    private SystemSettingListPreference mBatteryStyle;
    private SystemSettingSwitchPreference mBatteryPercent;
    private SystemSettingSwitchPreference mBatteryPercentCharging;
    private SystemSettingSwitchPreference mBatteryPercentInside;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.battery_settings);

        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        boolean modernIconsEnabled = Settings.System.getIntForUser(
                resolver, NEW_STATUS_BAR_ICONS, 0, UserHandle.USER_CURRENT) == 1;

        mBatteryStyle = findPreference(BATTERY_STYLE);
        mBatteryPercent = findPreference(SHOW_BATTERY_PERCENT);
        mBatteryPercentCharging = findPreference(SHOW_BATTERY_PERCENT_CHARGING);
        mBatteryPercentInside = findPreference(SHOW_BATTERY_PERCENT_INSIDE);

        if (modernIconsEnabled) {
            // Modern UI is ON: Hide legacy options, show only the master percentage toggle.
            mBatteryStyle.setVisible(false);
            mBatteryPercentInside.setVisible(false);
            mBatteryPercentCharging.setVisible(false);

            mBatteryPercent.setChecked(Settings.System.getIntForUser(resolver,
                    SHOW_BATTERY_PERCENT, 0, UserHandle.USER_CURRENT) == 1);
            mBatteryPercent.setOnPreferenceChangeListener(this);

        } else {
            // Modern UI is OFF (legacy mode): Show all options and set up dependencies.
            final boolean percentEnabled = Settings.System.getIntForUser(resolver,
                    SHOW_BATTERY_PERCENT, 0, UserHandle.USER_CURRENT) == 1;
            mBatteryPercent.setChecked(percentEnabled);
            mBatteryPercent.setOnPreferenceChangeListener(this);

            final boolean percentInside = Settings.System.getIntForUser(resolver,
                    SHOW_BATTERY_PERCENT_INSIDE, 0, UserHandle.USER_CURRENT) == 1;
            mBatteryPercentInside.setChecked(percentInside);
            mBatteryPercentInside.setEnabled(percentEnabled);
            mBatteryPercentInside.setOnPreferenceChangeListener(this);

            int value = Settings.System.getIntForUser(resolver,
                    BATTERY_STYLE, 0, UserHandle.USER_CURRENT);
            mBatteryStyle.setValue(Integer.toString(value));
            mBatteryStyle.setSummary(mBatteryStyle.getEntry());
            mBatteryStyle.setOnPreferenceChangeListener(this);

            updateLegacyPercentEnablement(value != 2);
            updateLegacyPercentChargingEnablement(value, percentEnabled, percentInside);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        boolean modernIconsEnabled = Settings.System.getIntForUser(
                resolver, NEW_STATUS_BAR_ICONS, 0, UserHandle.USER_CURRENT) == 1;

        if (preference == mBatteryPercent) {
            boolean enabled = (boolean) objValue;
            Settings.System.putInt(resolver, SHOW_BATTERY_PERCENT, enabled ? 1 : 0);
            if (!modernIconsEnabled) {
                mBatteryPercentInside.setEnabled(enabled);
                updateLegacyPercentChargingEnablement(null, enabled, null);
            }
            return true;
        }
        else if (preference == mBatteryStyle) {
            int value = Integer.valueOf((String) objValue);
            mBatteryStyle.setSummary(mBatteryStyle.getEntries()[mBatteryStyle.findIndexOfValue((String) objValue)]);
            Settings.System.putIntForUser(resolver, BATTERY_STYLE, value, UserHandle.USER_CURRENT);
            updateLegacyPercentEnablement(value != 2);
            updateLegacyPercentChargingEnablement(value, null, null);
            return true;
        } else if (preference == mBatteryPercentInside) {
            boolean enabled = (boolean) objValue;
            Settings.System.putInt(resolver, SHOW_BATTERY_PERCENT_INSIDE, enabled ? 1 : 0);
            updateLegacyPercentChargingEnablement(null, null, enabled);
            return true;
        }
        return false;
    }

    private void updateLegacyPercentEnablement(boolean enabled) {
        mBatteryPercent.setEnabled(enabled);
        mBatteryPercentInside.setEnabled(enabled && mBatteryPercent.isChecked());
    }

    private void updateLegacyPercentChargingEnablement(Integer style, Boolean percent, Boolean inside) {
        if (style == null) style = Integer.valueOf(mBatteryStyle.getValue());
        if (percent == null) percent = mBatteryPercent.isChecked();
        if (inside == null) inside = mBatteryPercentInside.isChecked();
        mBatteryPercentCharging.setEnabled(style != 2 && (!percent || inside));
    }
    
    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                    boolean enabled) {
                final ArrayList<SearchIndexableResource> result = new ArrayList<>();
                final SearchIndexableResource sir = new SearchIndexableResource(context);
                sir.xmlResId = R.xml.battery_settings;
                result.add(sir);
                return result;
            }

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                final List<String> keys = super.getNonIndexableKeys(context);
                return keys;
            }
    };
}
