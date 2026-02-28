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

import android.util.Log;
import android.app.Activity;
import android.content.Context;
import android.content.ContentResolver;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import android.os.SystemProperties;
import android.provider.Settings;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.util.voltage.VoltageUtils;
import com.android.internal.util.voltage.udfps.UdfpsUtils;
import com.voltage.support.preferences.SystemSettingListPreference;
import com.voltage.support.preferences.CustomSeekBarPreference;
import com.voltage.support.preferences.SecureSettingListPreference;
import com.voltage.support.preferences.SystemSettingSwitchPreference;
import com.voltage.support.preferences.SystemSettingListPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;
import com.android.internal.util.crdroid.OmniJawsClient;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class LockScreenSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "LockScreenSettings";

    private FingerprintManager mFingerprintManager;

    private static final String KEY_WEATHER = "lockscreen_weather_enabled";

    private Preference mWeather;

    @Override
    public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        android.view.ViewGroup listCollection = view.findViewById(android.R.id.list_container);
        if (listCollection != null) {
            for (int i = 0; i < listCollection.getChildCount(); i++) {
                android.view.View child = listCollection.getChildAt(i);
                if (child instanceof androidx.recyclerview.widget.RecyclerView) {
                    child.setPadding(child.getPaddingLeft(), child.getPaddingTop(), child.getPaddingRight(), 
                                     (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 130, getResources().getDisplayMetrics()));
                    break;
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_lockscreen);

        ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefSet = getPreferenceScreen();
        Resources resources = getResources();

    final boolean hasFingerprintHardware = checkFingerprintHardware();

        Preference fingerprintCategory = findPreference("lockscreen_fingerprint_settings");
        if (fingerprintCategory != null && !hasFingerprintHardware) {
            prefSet.removePreference(fingerprintCategory);
        }

       mWeather = (Preference) findPreference(KEY_WEATHER);
       updateWeatherSettings();

        Preference mUdfpsSettings = findPreference("udfps_settings");
        if (!UdfpsUtils.hasUdfpsSupport(getContext()) || !hasFingerprintHardware) {
            if (mUdfpsSettings != null) {
                prefSet.removePreference(mUdfpsSettings);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    private boolean checkFingerprintHardware() {
        final PackageManager pm = getActivity().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return false;

        try {
            mFingerprintManager = (FingerprintManager) getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
            return mFingerprintManager != null && mFingerprintManager.isHardwareDetected();
        } catch (Exception e) {
            Log.e(TAG, "Fingerprint service error", e);
            return false;
        }
    }

    private void updateWeatherSettings() {
        if (mWeather == null) return;

        boolean weatherEnabled = OmniJawsClient.get().isOmniJawsEnabled(getContext());
        mWeather.setEnabled(weatherEnabled);
        mWeather.setSummary(weatherEnabled ? R.string.lockscreen_weather_summary :
            R.string.lockscreen_weather_enabled_info);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWeatherSettings();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }

    /**
      * For Search.
      */

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.powerhub_lockscreen;
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
