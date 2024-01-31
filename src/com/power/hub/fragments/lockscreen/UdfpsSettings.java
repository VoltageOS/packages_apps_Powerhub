/*
 * Copyright (C) 2024 VoltageOS
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

package com.power.hub.fragments.lockscreen;

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
import android.text.TextUtils;

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

public class UdfpsSettings extends SettingsPreferenceFragment {

    private static final String KEY_UDFPS_ANIMATIONS = "udfps_recognizing_animation_preview";
    private static final String SCREEN_OFF_UDFPS_ENABLED = "screen_off_udfps_enabled";

    private Preference mUdfpsAnimations;
    private Preference mScreenOffUdfps;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.udfps_settings);

        final PreferenceScreen prefSet = getPreferenceScreen();
        Resources resources = getResources();

        final boolean udfpsResPkgInstalled = VoltageUtils.isPackageInstalled(getContext(),
                "com.power.hub.udfps.animations");
        mUdfpsAnimations = findPreference(KEY_UDFPS_ANIMATIONS);
        if (!udfpsResPkgInstalled) {
            prefSet.removePreference(mUdfpsAnimations);
        }

        mScreenOffUdfps = (Preference) prefSet.findPreference(SCREEN_OFF_UDFPS_ENABLED);
        boolean screenOffUdfpsAvailable = resources.getBoolean(
                com.android.internal.R.bool.config_supportScreenOffUdfps) ||
                !TextUtils.isEmpty(resources.getString(
                    com.android.internal.R.string.config_dozeUdfpsLongPressSensorType));
        if (!screenOffUdfpsAvailable)
            prefSet.removePreference(mScreenOffUdfps);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.SCREEN_OFF_UDFPS_ENABLED, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }
}
