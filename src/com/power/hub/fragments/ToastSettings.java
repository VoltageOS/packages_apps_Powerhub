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
import android.os.Bundle;
import android.content.Context;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.EditTextPreference;
import android.content.ContentResolver;
import android.provider.Settings;
import android.text.TextUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;
import java.util.List;
import java.util.ArrayList;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class ToastSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String HOMEPAGE_TOAST_TOGGLE = "homepage_toast_messages";
    private static final String HOMEPAGE_TOAST_TEXT = "homepage_toast_custom_text";
    private SwitchPreferenceCompat mToastToggle;
    private EditTextPreference mToastText;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.powerhub_misc_toast);

        final ContentResolver resolver = getContentResolver();

        mToastToggle = (SwitchPreferenceCompat) findPreference(HOMEPAGE_TOAST_TOGGLE);
        mToastText = (EditTextPreference) findPreference(HOMEPAGE_TOAST_TEXT);

        boolean isToastEnabled = Settings.System.getInt(resolver, "homepage_toast_messages_enabled", 0) == 1;
        mToastToggle.setChecked(isToastEnabled);

        mToastToggle.setOnPreferenceChangeListener(this);
        mToastText.setOnPreferenceChangeListener(this);

        updateToastTextPreference(isToastEnabled);
    }

    private void updateToastTextPreference(boolean enabled) {
        if (mToastText != null) {
            mToastText.setVisible(enabled);
            String currentText = Settings.System.getString(getContentResolver(), "homepage_toast_custom_text");
            if (!TextUtils.isEmpty(currentText)) {
                mToastText.setSummary(currentText);
            } else {
                // Notice: fallback to empty string or default depending on string resources
                mToastText.setSummary(currentText);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getContentResolver();

        if (preference == mToastToggle) {
            boolean isChecked = (Boolean) newValue;
            Settings.System.putInt(resolver, "homepage_toast_messages_enabled", isChecked ? 1 : 0);
            updateToastTextPreference(isChecked);
            return true;
        }

        if (preference == mToastText) {
            String text = ((String) newValue).trim();
            Settings.System.putString(resolver, "homepage_toast_custom_text", text);
            if (!TextUtils.isEmpty(text)) {
                mToastText.setSummary(text);
            } else {
                mToastText.setSummary("");
            }
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
                    sir.xmlResId = R.xml.powerhub_misc_toast;
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
