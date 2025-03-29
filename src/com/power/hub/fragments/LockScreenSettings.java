/*
 *  Copyright (C) 2015 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.power.hub.fragments;

import com.android.internal.logging.nano.MetricsProto;

import android.util.Log;
import com.android.settings.R;
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
import android.text.TextUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.util.voltage.VoltageUtils;
import com.power.hub.fragments.lockscreen.UdfpsIconPicker;
import com.power.hub.fragments.lockscreen.UdfpsAnimation;
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
    private static final String FINGERPRINT_CATEGORY_KEY = "lockscreen_ui_fingerprint_category";
    private static final String FINGERPRINT_SUCCESS_VIB = "fingerprint_success_vib";
    private static final String FINGERPRINT_ERROR_VIB = "fingerprint_error_vib";
    private static final String UDFPS_CATEGORY = "udfps_category";
    private static final String SCREEN_OFF_UDFPS_ENABLED = "screen_off_udfps_enabled";
    private static final String KEY_UDFPS_ICONS = "udfps_icon_picker";
    private static final String KEY_UDFPS_ANIMATIONS = "udfps_recognizing_animation_preview";
    private static final String KEY_WEATHER = "lockscreen_weather_enabled";

    private FingerprintManager mFingerprintManager;
    private SwitchPreferenceCompat mFingerprintSuccessVib;
    private SwitchPreferenceCompat mFingerprintErrorVib;
    private Preference mScreenOffUdfps;
    private Preference mUdfpsIcons;
    private Preference mUdfpsAnimations;
    private Preference mWeather;
    private OmniJawsClient mWeatherClient;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_lockscreen);

        ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefSet = getPreferenceScreen();
        Resources resources = getResources();

        // Combined fingerprint hardware check
        final boolean hasFingerprintHardware = checkFingerprintHardware();

        // Handle fingerprint category
        PreferenceCategory fingerprintCategory = findPreference(FINGERPRINT_CATEGORY_KEY);
        if (fingerprintCategory != null) {
            if (!hasFingerprintHardware) {
                prefSet.removePreference(fingerprintCategory);
            } else {
                initFingerprintPreferences(fingerprintCategory);
            }
        }

        // Handle UDFPS category
        PreferenceCategory gestCategory = findPreference(UDFPS_CATEGORY);
        if (gestCategory != null) {
            if (!hasFingerprintHardware) {
                prefSet.removePreference(gestCategory);
            } else {
                initUdfpsPreferences(gestCategory, resources);
                // Remove category if empty after initialization
                if (gestCategory.getPreferenceCount() == 0) {
                    prefSet.removePreference(gestCategory);
                    Log.d(TAG, "Removed empty UDFPS category");
                }
            }
        }

        // Weather initialization
        mWeather = findPreference(KEY_WEATHER);
        mWeatherClient = new OmniJawsClient(getContext());
        updateWeatherSettings();
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

    private void initFingerprintPreferences(PreferenceCategory category) {
        mFingerprintSuccessVib = findPreference(FINGERPRINT_SUCCESS_VIB);
        mFingerprintErrorVib = findPreference(FINGERPRINT_ERROR_VIB);

        if (mFingerprintSuccessVib != null) {
            mFingerprintSuccessVib.setChecked(Settings.System.getInt(
                getContentResolver(), Settings.System.FP_SUCCESS_VIBRATE, 1) == 1);
            mFingerprintSuccessVib.setOnPreferenceChangeListener(this);
        }

        if (mFingerprintErrorVib != null) {
            mFingerprintErrorVib.setChecked(Settings.System.getInt(
                getContentResolver(), Settings.System.FP_ERROR_VIBRATE, 1) == 1);
            mFingerprintErrorVib.setOnPreferenceChangeListener(this);
        }
    }

    private void initUdfpsPreferences(PreferenceCategory gestCategory, Resources resources) {
        mUdfpsAnimations = findPreference(KEY_UDFPS_ANIMATIONS);
        mUdfpsIcons = findPreference(KEY_UDFPS_ICONS);
        mScreenOffUdfps = findPreference(SCREEN_OFF_UDFPS_ENABLED);

        // Remove preferences based on package availability
        if (!VoltageUtils.isPackageInstalled(getContext(), "com.power.hub.udfps.animations")) {
            gestCategory.removePreference(mUdfpsAnimations);
        }
        if (!VoltageUtils.isPackageInstalled(getContext(), "com.power.hub.udfps.icons")) {
            gestCategory.removePreference(mUdfpsIcons);
        }

        // Check screen-off UDFPS availability
        boolean screenOffUdfpsAvailable = resources.getBoolean(
                com.android.internal.R.bool.config_supportScreenOffUdfps) ||
                !TextUtils.isEmpty(resources.getString(
                    com.android.internal.R.string.config_dozeUdfpsLongPressSensorType));
        if (!screenOffUdfpsAvailable && mScreenOffUdfps != null) {
            gestCategory.removePreference(mScreenOffUdfps);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mFingerprintSuccessVib) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.FP_SUCCESS_VIBRATE, value ? 1 : 0);
            return true;
        } else if (preference == mFingerprintErrorVib) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.FP_ERROR_VIBRATE, value ? 1 : 0);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.SCREEN_OFF_UDFPS_ENABLED, 0, UserHandle.USER_CURRENT);
        UdfpsIconPicker.reset(mContext);
        UdfpsAnimation.reset(mContext);
    }

    private void updateWeatherSettings() {
        if (mWeatherClient == null || mWeather == null) return;

        boolean weatherEnabled = mWeatherClient.isOmniJawsEnabled();
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
