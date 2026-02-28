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

import android.content.ContentResolver;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.content.Context;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;

import com.voltage.support.colorpicker.ColorPickerPreference;

import java.util.List;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class ColorEngineSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String OVERLAY_CATEGORY_ACCENT_COLOR = "android.theme.customization.accent_color";
    private static final String OVERLAY_CATEGORY_SYSTEM_PALETTE = "android.theme.customization.system_palette";
    private static final String OVERLAY_CATEGORY_BG_COLOR = "android.theme.customization.bg_color";
    private static final String OVERLAY_COLOR_SOURCE = "android.theme.customization.color_source";
    private static final String OVERLAY_COLOR_BOTH = "android.theme.customization.color_both";
    private static final String COLOR_SOURCE_PRESET = "preset";
    private static final String COLOR_SOURCE_HOME = "home_wallpaper";

    private static final String PREF_COLOR_SOURCE = "color_source";
    private static final String PREF_ACCENT_COLOR = "accent_color";
    private static final String PREF_BG_COLOR = "bg_color";

    private ListPreference mColorSourcePref;
    private ColorPickerPreference mAccentColorPref;
    private ColorPickerPreference mBgColorPref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_monet_engine);

        mColorSourcePref = findPreference(PREF_COLOR_SOURCE);
        mAccentColorPref = findPreference(PREF_ACCENT_COLOR);
        mBgColorPref = findPreference(PREF_BG_COLOR);

        mColorSourcePref.setOnPreferenceChangeListener(this);
        mAccentColorPref.setOnPreferenceChangeListener(this);
        mBgColorPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    private void updatePreferences() {
        final ContentResolver resolver = getActivity().getContentResolver();
        final String overlayPackageJson = Settings.Secure.getStringForUser(
                resolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        if (overlayPackageJson != null && !overlayPackageJson.isEmpty()) {
            try {
                final JSONObject object = new JSONObject(overlayPackageJson);
                final String source = object.optString(OVERLAY_COLOR_SOURCE, null);
                final String color = object.optString(OVERLAY_CATEGORY_SYSTEM_PALETTE, null);
                final int bgColor = object.optInt(OVERLAY_CATEGORY_BG_COLOR);
                final boolean both = object.optInt(OVERLAY_COLOR_BOTH, 0) == 1;

                final String sourceVal = (source == null || source.isEmpty() ||
                        (source.equals(COLOR_SOURCE_HOME) && both)) ? "both" : source;
                
                updateListByValue(mColorSourcePref, sourceVal);
                
                final boolean enabled = sourceVal != null && sourceVal.equals(COLOR_SOURCE_PRESET);
                mAccentColorPref.setEnabled(enabled);

                if (enabled && color != null && !color.isEmpty()) {
                    mAccentColorPref.setNewPreviewColor(ColorPickerPreference.convertToColorInt(color));
                }
                
                final boolean bgEnabled = enabled && bgColor != 0;
                if (bgEnabled) {
                    mBgColorPref.setNewPreviewColor(bgColor);
                }
                
                mBgColorPref.setEnabled(enabled);
                
            } catch (JSONException | IllegalArgumentException ignored) {}
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mColorSourcePref) {
            String value = (String) newValue;
            setSourceValue(value);
            updateListByValue(mColorSourcePref, value, false);
            final boolean enabled = value != null && value.equals(COLOR_SOURCE_PRESET);
            mAccentColorPref.setEnabled(enabled);
            mBgColorPref.setEnabled(enabled);
            return true;
        } else if (preference == mAccentColorPref) {
            int value = (Integer) newValue;
            setColorValue(value);
            return true;
        } else if (preference == mBgColorPref) {
            int value = (Integer) newValue;
            setBgColorValue(value);
            return true;
        }
        return false;
    }

    private void updateListByValue(ListPreference pref, String value) {
        updateListByValue(pref, value, true);
    }

    private void updateListByValue(ListPreference pref, String value, boolean set) {
        if (set) pref.setValue(value);
        final int index = pref.findIndexOfValue(value);
        if (index >= 0) pref.setSummary(pref.getEntries()[index]);
    }

    private JSONObject getSettingsJson() throws JSONException {
        final String overlayPackageJson = Settings.Secure.getStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        if (overlayPackageJson == null || overlayPackageJson.isEmpty())
            return new JSONObject();
        return new JSONObject(overlayPackageJson);
    }

    private void putSettingsJson(JSONObject object) {
        Settings.Secure.putStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                object.toString(), UserHandle.USER_CURRENT);
    }

    private void setSourceValue(String source) {
        try {
            JSONObject object = getSettingsJson();
            if (source.equals("both")) {
                object.putOpt(OVERLAY_COLOR_BOTH, 1);
                object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_HOME);
            } else {
                object.remove(OVERLAY_COLOR_BOTH);
                object.putOpt(OVERLAY_COLOR_SOURCE, source);
            }
            if (!source.equals(COLOR_SOURCE_PRESET)) {
                object.remove(OVERLAY_CATEGORY_ACCENT_COLOR);
                object.remove(OVERLAY_CATEGORY_SYSTEM_PALETTE);
            }
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setColorValue(int color) {
        try {
            JSONObject object = getSettingsJson();
            final String rgbColor = ColorPickerPreference.convertToARGB(color).replace("#", "");
            object.putOpt(OVERLAY_CATEGORY_ACCENT_COLOR, rgbColor);
            object.putOpt(OVERLAY_CATEGORY_SYSTEM_PALETTE, rgbColor);
            object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_PRESET);
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setBgColorValue(int color) {
        try {
            JSONObject object = getSettingsJson();
            if (color != 0) object.putOpt(OVERLAY_CATEGORY_BG_COLOR, color);
            else object.remove(OVERLAY_CATEGORY_BG_COLOR);
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
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
                    sir.xmlResId = R.xml.powerhub_monet_engine;
                    result.add(sir);
                    return result;
                }
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    return super.getNonIndexableKeys(context);
                }
    };
}
