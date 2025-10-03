package com.power.hub.fragments;

import com.android.internal.logging.nano.MetricsProto;

import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.Context;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceCategory;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreferenceCompat;
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
import com.voltage.support.preferences.SystemSettingMasterSwitchPreference;
import com.voltage.support.preferences.SystemSettingSeekBarPreference;

import com.power.hub.utils.DeviceUtils;
import com.android.settings.Utils;
import com.android.internal.util.voltage.VoltageUtils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import android.provider.SearchIndexableResource;
import android.util.Log;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class StatusBarSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";

    private SystemSettingListPreference mStatusBarClock;

    private static final String PREF_NEW_STATUS_BAR_ICONS = "new_status_bar_icons";

    private SwitchPreferenceCompat mNewStatusBarIconsPref;

    private static final String KEY_QS_DATA_USAGE = "qs_show_data_usage";
    private static final String KEY_QS_DATA_USAGE_CYCLE_TYPE = "qs_data_usage_cycle_type";

    private Preference mDataUsagePreference;
    private ListPreference mDataUsageCycleTypePreference;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.powerhub_statusbar);
		ContentResolver resolver = getActivity().getContentResolver();

        PreferenceScreen prefSet = getPreferenceScreen();

        mNewStatusBarIconsPref = findPreference(PREF_NEW_STATUS_BAR_ICONS);
        mNewStatusBarIconsPref.setOnPreferenceChangeListener(this);

        boolean newIconsEnabled = Settings.System.getIntForUser(
            resolver, "new_status_bar_icons_enabled", 0, UserHandle.USER_CURRENT) == 1;
        mNewStatusBarIconsPref.setChecked(newIconsEnabled);
        updatePreferenceStates(newIconsEnabled);

        mDataUsagePreference = findPreference(KEY_QS_DATA_USAGE);
        mDataUsageCycleTypePreference = (ListPreference) findPreference(KEY_QS_DATA_USAGE_CYCLE_TYPE);

        if (mDataUsageCycleTypePreference != null) {
            mDataUsageCycleTypePreference.setOnPreferenceChangeListener(this);
        }

        updateDataUsageSummary();

    }

    /**
     * This is the helper method that enables or disables all incompatible preferences.
     * @param newIconsEnabled true if the new UI is on, which means old options should be disabled.
     */
    private void updatePreferenceStates(boolean newIconsEnabled) {
        // We use !newIconsEnabled because if the new UI is ON (true),
        // the old preferences should be DISABLED (false).
        final boolean oldPrefsEnabled = !newIconsEnabled;

        findPreference("systemui_tuner_statusbar").setEnabled(oldPrefsEnabled);
        findPreference("clock").setEnabled(oldPrefsEnabled);
        // findPreference("battery_bar_category").setEnabled(oldPrefsEnabled);
        // findPreference("network_traffic_settings").setEnabled(oldPrefsEnabled);
        // findPreference("ongoing_progress_settings").setEnabled(oldPrefsEnabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        final Context context = getContext();

        if (preference == mNewStatusBarIconsPref) {
            boolean value = (Boolean) objValue;

            updatePreferenceStates(value);

            Settings.System.putIntForUser(resolver, "status_bar_root_modernization_enabled",
                    value ? 1 : 0, UserHandle.USER_CURRENT);
            Settings.System.putIntForUser(resolver, "new_status_bar_icons_enabled",
                    value ? 1 : 0, UserHandle.USER_CURRENT);

            VoltageUtils.showSystemUiRestartDialog(context);
            return true;
        } else if (preference == mDataUsageCycleTypePreference) {
            updateDataUsageSummary(objValue.toString());
            return true;
        }

        return false;

    }

    private void updateDataUsageSummary() {
        updateDataUsageSummary(null);
    }

    private void updateDataUsageSummary(String cycleTypeValue) {
        if (mDataUsagePreference == null) return;

        final ContentResolver resolver = getActivity().getContentResolver();
        int cycleType;

        if (cycleTypeValue != null) {
            try {
                cycleType = Integer.parseInt(cycleTypeValue);
            } catch (NumberFormatException e) {
                cycleType = 0;
            }
        } else {
            cycleType = Settings.Secure.getInt(resolver, KEY_QS_DATA_USAGE_CYCLE_TYPE, 0);
        }

        int summaryResId;
        switch (cycleType) {
            case 0:
                summaryResId = R.string.qs_footer_datausage_summary_daily;
                break;
            case 1:
                summaryResId = R.string.qs_footer_datausage_summary_weekly;
                break;
            default:
                summaryResId = R.string.qs_footer_datausage_summary_daily;
                break;
        }
        mDataUsagePreference.setSummary(getString(summaryResId));
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
