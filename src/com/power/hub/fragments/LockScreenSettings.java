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
import androidx.preference.SwitchPreference;
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
import com.power.hub.preferences.SystemSettingListPreference;
import com.power.hub.preferences.CustomSeekBarPreference;
import com.power.hub.preferences.SecureSettingListPreference;
import com.power.hub.preferences.SystemSettingSwitchPreference;
import com.power.hub.preferences.SystemSettingListPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class LockScreenSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {
			
    private static final String FINGERPRINT_SUCCESS_VIB = "fingerprint_success_vib";
    private static final String FINGERPRINT_ERROR_VIB = "fingerprint_error_vib";
    private static final String AOD_SCHEDULE_KEY = "always_on_display_schedule";

    private FingerprintManager mFingerprintManager;
    private SwitchPreference mFingerprintSuccessVib;
    private SwitchPreference mFingerprintErrorVib;
			

    static final int MODE_DISABLED = 0;
    static final int MODE_NIGHT = 1;
    static final int MODE_TIME = 2;
    static final int MODE_MIXED_SUNSET = 3;
    static final int MODE_MIXED_SUNRISE = 4;
    Preference mAODPref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_lockscreen);

        ContentResolver resolver = getActivity().getContentResolver();
        PreferenceScreen prefScreen = getPreferenceScreen();
        Resources resources = getResources();
        final PackageManager mPm = getActivity().getPackageManager();
	
     mFingerprintManager = (FingerprintManager) getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mFingerprintSuccessVib = (SwitchPreference) findPreference(FINGERPRINT_SUCCESS_VIB);
        mFingerprintErrorVib = (SwitchPreference) findPreference(FINGERPRINT_ERROR_VIB);
        if (mPm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
                 mFingerprintManager != null) {
            if (!mFingerprintManager.isHardwareDetected()){
                prefSet.removePreference(mFingerprintSuccessVib);
                prefSet.removePreference(mFingerprintErrorVib);
            } else {
                mFingerprintSuccessVib.setChecked((Settings.System.getInt(getContentResolver(),
                        Settings.System.FP_SUCCESS_VIBRATE, 1) == 1));
                mFingerprintSuccessVib.setOnPreferenceChangeListener(this);
                mFingerprintErrorVib.setChecked((Settings.System.getInt(getContentResolver(),
                        Settings.System.FP_ERROR_VIBRATE, 1) == 1));
                mFingerprintErrorVib.setOnPreferenceChangeListener(this);
            }
        } else {
            prefSet.removePreference(mFingerprintSuccessVib);
            prefSet.removePreference(mFingerprintErrorVib);
        }
		
	mAODPref = findPreference(AOD_SCHEDULE_KEY);
        updateAlwaysOnSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAlwaysOnSummary();
    }

    private void updateAlwaysOnSummary() {
        if (mAODPref == null) return;
        int mode = Settings.Secure.getIntForUser(getActivity().getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON_AUTO_MODE, 0, UserHandle.USER_CURRENT);
        switch (mode) {
            default:
            case MODE_DISABLED:
                mAODPref.setSummary(R.string.disabled);
                break;
            case MODE_NIGHT:
                mAODPref.setSummary(R.string.night_display_auto_mode_twilight);
                break;
            case MODE_TIME:
                mAODPref.setSummary(R.string.night_display_auto_mode_custom);
                break;
            case MODE_MIXED_SUNSET:
                mAODPref.setSummary(R.string.always_on_display_schedule_mixed_sunset);
                break;
            case MODE_MIXED_SUNRISE:
                mAODPref.setSummary(R.string.always_on_display_schedule_mixed_sunrise);
                break;
        }
    }

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

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
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
