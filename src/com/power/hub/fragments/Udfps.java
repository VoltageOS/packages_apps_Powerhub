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
import android.provider.SearchIndexableResource;
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
import com.android.internal.util.voltage.udfps.UdfpsUtils;
import com.power.hub.fragments.UdfpsIconPicker;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class Udfps extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String UDFPS_CUSTOMIZATION = "udfps_customization";
    private static final String KEY_UDFPS_ICONS = "udfps_icon_picker";
    private static final String KEY_UDFPS_ANIMATION = "udfps_recognizing_animation_preview";

    private static final String PKG_UDFPS_ANIMATIONS = "com.power.hub.udfps.animations";
    private static final String PKG_UDFPS_ICONS = "com.power.hub.udfps.icons";

    private PreferenceCategory mUdfpsCustomization;
    private Preference mUdfpsIcons;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_udfps);

        final PreferenceScreen prefSet = getPreferenceScreen();
        if (prefSet == null) {
            return;
        }

        if (!UdfpsUtils.hasUdfpsSupport(getContext())) {
            prefSet.removeAll();
            return;
        }

        final boolean udfpsAnimPkgInstalled = VoltageUtils.isPackageInstalled(getContext(),
                PKG_UDFPS_ANIMATIONS);
        final boolean udfpsIconPkgInstalled = VoltageUtils.isPackageInstalled(getContext(),
                PKG_UDFPS_ICONS);

        mUdfpsCustomization = (PreferenceCategory) findPreference(UDFPS_CUSTOMIZATION);
        mUdfpsIcons = (Preference) findPreference(KEY_UDFPS_ICONS);
        Preference udfpsAnimation = (Preference) findPreference(KEY_UDFPS_ANIMATION);

        if (mUdfpsCustomization != null) {
            if (!udfpsIconPkgInstalled && mUdfpsIcons != null) {
                mUdfpsCustomization.removePreference(mUdfpsIcons);
            }
            if (!udfpsAnimPkgInstalled && udfpsAnimation != null) {
                mUdfpsCustomization.removePreference(udfpsAnimation);
            }
            if (mUdfpsCustomization.getPreferenceCount() == 0) {
                prefSet.removePreference(mUdfpsCustomization);
            }
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
                    if (!UdfpsUtils.hasUdfpsSupport(context)) {
                        return result;
                    }
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.powerhub_udfps;
                    result.add(sir);
                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    if (!UdfpsUtils.hasUdfpsSupport(context)) {
                        keys.add(UDFPS_CUSTOMIZATION);
                        keys.add(KEY_UDFPS_ICONS);
                        keys.add(KEY_UDFPS_ANIMATION);
                        return keys;
                    }
                    final boolean animPkgInstalled = VoltageUtils.isPackageInstalled(
                            context, PKG_UDFPS_ANIMATIONS);
                    final boolean iconPkgInstalled = VoltageUtils.isPackageInstalled(
                            context, PKG_UDFPS_ICONS);
                    if (!animPkgInstalled) {
                        keys.add(KEY_UDFPS_ANIMATION);
                    }
                    if (!iconPkgInstalled) {
                        keys.add(KEY_UDFPS_ICONS);
                    }
                    if (!animPkgInstalled && !iconPkgInstalled) {
                        keys.add(UDFPS_CUSTOMIZATION);
                    }
                    return keys;
                }
            };
}
