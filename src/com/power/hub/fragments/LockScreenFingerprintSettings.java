/*
 * Copyright (C) 2026 VoltageOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.power.hub.fragments;

import com.android.internal.logging.nano.MetricsProto;
import android.os.Bundle;
import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import android.provider.Settings;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;
import java.util.List;
import java.util.ArrayList;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class LockScreenFingerprintSettings extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String FINGERPRINT_SUCCESS_VIB = "fingerprint_success_vib";
    private static final String FINGERPRINT_ERROR_VIB = "fingerprint_error_vib";

    private SwitchPreferenceCompat mFingerprintSuccessVib;
    private SwitchPreferenceCompat mFingerprintErrorVib;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_lockscreen_fingerprint);

        mFingerprintSuccessVib = findPreference(FINGERPRINT_SUCCESS_VIB);
        if (mFingerprintSuccessVib != null) {
            mFingerprintSuccessVib.setChecked(Settings.System.getInt(
                getContentResolver(), Settings.System.FP_SUCCESS_VIBRATE, 1) == 1);
            mFingerprintSuccessVib.setOnPreferenceChangeListener(this);
        }

        mFingerprintErrorVib = findPreference(FINGERPRINT_ERROR_VIB);
        if (mFingerprintErrorVib != null) {
            mFingerprintErrorVib.setChecked(Settings.System.getInt(
                getContentResolver(), Settings.System.FP_ERROR_VIBRATE, 1) == 1);
            mFingerprintErrorVib.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
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

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
                    ArrayList<SearchIndexableResource> result = new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.powerhub_lockscreen_fingerprint;
                    result.add(sir);
                    return result;
                }
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    return super.getNonIndexableKeys(context);
                }
    };
}
