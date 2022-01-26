/*
 * Copyright (C) 2022 VoltageOS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.power.hub.fragments;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.voltage.VoltageUtils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.power.hub.preferences.SystemSettingListPreference;
import com.power.hub.preferences.SystemSettingSwitchPreference;

public class UdfpsSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private boolean mShowUdfpsPressedColor;
    private boolean mShowUdfpsScreenOff;

    private static final String UDFPS_CUSTOMIZATION = "udfps_customization";
    private static final String UDFPS_PRESSED_COLOR = "fod_color";
    private static final String UDFPS_SCREEN_OFF = "screen_off_fod";

    private static final int REQUEST_PICK_IMAGE = 0;

    private PreferenceCategory mUdfpsCustomization;
    private SystemSettingListPreference mUdfpsPressedColor;
    private SystemSettingSwitchPreference mUdfpsScreenOff;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_udfps);

        final PreferenceScreen prefSet = getPreferenceScreen();
        Resources resources = getResources();

        final boolean udfpsResPkgInstalled = VoltageUtils.isPackageInstalled(getContext(),
                "com.power.hub.udfps.resources");
	mUdfpsCustomization = (PreferenceCategory) findPreference(UDFPS_CUSTOMIZATION);
        if (!udfpsResPkgInstalled) {
            prefSet.removePreference(mUdfpsCustomization);
        }

        mShowUdfpsPressedColor = getContext().getResources().getBoolean(R.bool.config_show_fod_pressed_color_settings);
        mUdfpsPressedColor = (SystemSettingListPreference) findPreference(UDFPS_PRESSED_COLOR);
        if (!mShowUdfpsPressedColor) {
            prefSet.removePreference(mUdfpsPressedColor);
        }

        mShowUdfpsScreenOff = getContext().getResources().getBoolean(R.bool.config_supportScreenOffFod);
        mUdfpsScreenOff = findPreference(UDFPS_SCREEN_OFF);
        if (!mShowUdfpsScreenOff) {
            prefSet.removePreference(mUdfpsScreenOff);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }
}
