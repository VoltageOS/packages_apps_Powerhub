package com.power.hub.fragments;

import com.android.internal.logging.nano.MetricsProto;

import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;
import com.voltage.support.preferences.CustomSeekBarPreference;
import android.provider.Settings;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import java.util.Locale;
import android.text.TextUtils;
import android.view.View;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import android.content.pm.PackageManager.NameNotFoundException;
import com.android.settings.SettingsPreferenceFragment;
import com.voltage.support.preferences.SystemSettingMasterSwitchPreference;
import com.voltage.support.preferences.SystemSettingListPreference;
import com.voltage.support.preferences.SecureSettingSwitchPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class MiscSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String HOMEPAGE_TOAST_TOGGLE = "homepage_toast_messages";
    private static final String HOMEPAGE_TOAST_TEXT = "homepage_toast_custom_text";
    private SwitchPreferenceCompat mToastToggle;
    private EditTextPreference mToastText;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.powerhub_misc);
		Resources res = null;
        Context ctx = getContext();
        float density = Resources.getSystem().getDisplayMetrics().density;

        final ContentResolver resolver = getContentResolver();
        final PreferenceScreen prefSet = getPreferenceScreen();

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
                mToastText.setSummary(R.string.homepage_toast_text_summary);
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
                mToastText.setSummary(R.string.homepage_toast_text_summary);
            }
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
                    sir.xmlResId = R.xml.powerhub_misc;
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
