package com.power.hub.fragments;

import com.android.internal.logging.nano.MetricsProto;

import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceCategory;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;
import android.provider.Settings;
import com.android.settings.R;

import java.util.Locale;
import android.text.TextUtils;
import android.view.View;

import com.android.settings.SettingsPreferenceFragment;
import com.voltage.support.preferences.CustomSeekBarPreference;
import com.voltage.support.preferences.SystemSettingSeekBarPreference;
import com.voltage.support.preferences.SystemSettingListPreference;
import com.voltage.support.preferences.SystemSettingSwitchPreference;
import com.voltage.support.preferences.SecureSettingListPreference;
import com.voltage.support.preferences.SystemSettingMasterSwitchPreference;
import com.android.settings.Utils;
import com.android.internal.util.voltage.VoltageUtils;
import android.util.Log;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class StatusBarSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String KEY_QS_SHOW_BRIGHTNESS_SLIDER = "qs_show_brightness_slider";
    private static final String KEY_QS_BRIGHTNESS_SLIDER_POSITION = "qs_brightness_slider_position";

    private SecureSettingListPreference mQsShowBrightnessSlider;
    private SecureSettingListPreference mQsBrightnessSliderPosition;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.powerhub_statusbar);

        ContentResolver resolver = getActivity().getContentResolver();

        PreferenceScreen prefSet = getPreferenceScreen();

        mQsShowBrightnessSlider = (SecureSettingListPreference) findPreference(KEY_QS_SHOW_BRIGHTNESS_SLIDER);
        mQsBrightnessSliderPosition = (SecureSettingListPreference) findPreference(KEY_QS_BRIGHTNESS_SLIDER_POSITION);

        if (mQsShowBrightnessSlider != null) {
            int brightnessSliderShow = Settings.Secure.getInt(resolver,
                    KEY_QS_SHOW_BRIGHTNESS_SLIDER, 1);
            mQsShowBrightnessSlider.setValue(String.valueOf(brightnessSliderShow));
            mQsShowBrightnessSlider.setSummary(mQsShowBrightnessSlider.getEntry());
            mQsShowBrightnessSlider.setOnPreferenceChangeListener(this);
            updateBrightnessPositionState(brightnessSliderShow);
        }

        if (mQsBrightnessSliderPosition != null) {
            int brightnessSliderPosition = Settings.Secure.getInt(resolver,
                    KEY_QS_BRIGHTNESS_SLIDER_POSITION, 0);
            mQsBrightnessSliderPosition.setValue(String.valueOf(brightnessSliderPosition));
            mQsBrightnessSliderPosition.setSummary(mQsBrightnessSliderPosition.getEntry());
            mQsBrightnessSliderPosition.setOnPreferenceChangeListener(this);
        }

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ContentResolver resolver = getActivity().getContentResolver();

        if (preference == mQsShowBrightnessSlider) {
            int value = Integer.parseInt((String) objValue);
            int index = mQsShowBrightnessSlider.findIndexOfValue((String) objValue);
            mQsShowBrightnessSlider.setSummary(mQsShowBrightnessSlider.getEntries()[index]);
            Settings.Secure.putInt(resolver,
                    KEY_QS_SHOW_BRIGHTNESS_SLIDER, value);
            updateBrightnessPositionState(value);
            return true;
        } else if (preference == mQsBrightnessSliderPosition) {
            int value = Integer.parseInt((String) objValue);
            int index = mQsBrightnessSliderPosition.findIndexOfValue((String) objValue);
            mQsBrightnessSliderPosition.setSummary(mQsBrightnessSliderPosition.getEntries()[index]);
            Settings.Secure.putInt(resolver,
                    KEY_QS_BRIGHTNESS_SLIDER_POSITION, value);
            return true;
        }
        return false;
    }
	
    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }

    private void updateBrightnessPositionState(int brightnessShowValue) {
        if (mQsBrightnessSliderPosition != null) {
            boolean enabled = brightnessShowValue != 0;
            mQsBrightnessSliderPosition.setEnabled(enabled);
            if (!enabled) {
                mQsBrightnessSliderPosition.setSummary(R.string.qs_brightness_slider_show_never);
            }
        }
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
                    sir.xmlResId = R.xml.powerhub_statusbar;
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
