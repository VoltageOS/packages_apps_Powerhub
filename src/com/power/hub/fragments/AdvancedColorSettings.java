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

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;

import com.voltage.support.preferences.CustomSeekBarPreference;

import java.util.List;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class AdvancedColorSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String OVERLAY_COLOR_SOURCE = "android.theme.customization.color_source";
    private static final String OVERLAY_CATEGORY_BG_COLOR = "android.theme.customization.bg_color";
    private static final String OVERLAY_LUMINANCE_FACTOR = "android.theme.customization.luminance_factor";
    private static final String OVERLAY_CHROMA_FACTOR = "android.theme.customization.chroma_factor";
    private static final String OVERLAY_TINT_BACKGROUND = "android.theme.customization.tint_background";
    private static final String COLOR_SOURCE_PRESET = "preset";

    private static final String PREF_ACCENT_BACKGROUND = "accent_background";
    private static final String PREF_LUMINANCE_FACTOR = "luminance_factor";
    private static final String PREF_CHROMA_FACTOR = "chroma_factor";
    private static final String PREF_TINT_BACKGROUND = "tint_background";

    private SwitchPreferenceCompat mAccentBackgroundPref;
    private CustomSeekBarPreference mLuminancePref;
    private CustomSeekBarPreference mChromaPref;
    private SwitchPreferenceCompat mTintBackgroundPref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_monet_advanced);

        mAccentBackgroundPref = findPreference(PREF_ACCENT_BACKGROUND);
        mLuminancePref = findPreference(PREF_LUMINANCE_FACTOR);
        mChromaPref = findPreference(PREF_CHROMA_FACTOR);
        mTintBackgroundPref = findPreference(PREF_TINT_BACKGROUND);

        mAccentBackgroundPref.setOnPreferenceChangeListener(this);
        mLuminancePref.setOnPreferenceChangeListener(this);
        mChromaPref.setOnPreferenceChangeListener(this);
        mTintBackgroundPref.setOnPreferenceChangeListener(this);
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
                final int bgColor = object.optInt(OVERLAY_CATEGORY_BG_COLOR);
                final boolean tintBG = object.optInt(OVERLAY_TINT_BACKGROUND, 0) == 1;
                final float lumin = (float) object.optDouble(OVERLAY_LUMINANCE_FACTOR, 1d);
                final float chroma = (float) object.optDouble(OVERLAY_CHROMA_FACTOR, 1d);

                final boolean isPreset = source != null && source.equals(COLOR_SOURCE_PRESET);
                
                final boolean bgEnabled = isPreset && bgColor != 0;
                
                mAccentBackgroundPref.setEnabled(isPreset);
                mAccentBackgroundPref.setChecked(bgEnabled);

                int luminV = 0;
                if (lumin > 1d) luminV = Math.round((lumin - 1f) * 100f);
                else if (lumin < 1d) luminV = -1 * Math.round((1f - lumin) * 100f);
                mLuminancePref.setValue(luminV);

                int chromaV = 0;
                if (chroma > 1d) chromaV = Math.round((chroma - 1f) * 100f);
                else if (chroma < 1d) chromaV = -1 * Math.round((1f - chroma) * 100f);
                mChromaPref.setValue(chromaV);

                mTintBackgroundPref.setChecked(tintBG);
            } catch (JSONException | IllegalArgumentException ignored) {}
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAccentBackgroundPref) {
            boolean value = (Boolean) newValue;
            if (!value) setBgColorValue(0);
            return true;
        } else if (preference == mLuminancePref) {
            int value = (Integer) newValue;
            setLuminanceValue(value);
            return true;
        } else if (preference == mChromaPref) {
            int value = (Integer) newValue;
            setChromaValue(value);
            return true;
        } else if (preference == mTintBackgroundPref) {
            boolean value = (Boolean) newValue;
            setTintBackgroundValue(value);
            return true;
        }
        return false;
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
    
    private void setBgColorValue(int color) {
        try {
            JSONObject object = getSettingsJson();
            if (color != 0) object.putOpt(OVERLAY_CATEGORY_BG_COLOR, color);
            else object.remove(OVERLAY_CATEGORY_BG_COLOR);
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setLuminanceValue(int lumin) {
        try {
            JSONObject object = getSettingsJson();
            if (lumin == 0)
                object.remove(OVERLAY_LUMINANCE_FACTOR);
            else
                object.putOpt(OVERLAY_LUMINANCE_FACTOR, 1d + ((double) lumin / 100d));
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setChromaValue(int chroma) {
        try {
            JSONObject object = getSettingsJson();
            if (chroma == 0)
                object.remove(OVERLAY_CHROMA_FACTOR);
            else
                object.putOpt(OVERLAY_CHROMA_FACTOR, 1d + ((double) chroma / 100d));
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setTintBackgroundValue(boolean tint) {
        try {
            JSONObject object = getSettingsJson();
            if (!tint) object.remove(OVERLAY_TINT_BACKGROUND);
            else object.putOpt(OVERLAY_TINT_BACKGROUND, 1);
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
                    sir.xmlResId = R.xml.powerhub_monet_advanced;
                    result.add(sir);
                    return result;
                }
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    return super.getNonIndexableKeys(context);
                }
    };
}
