package com.power.hub.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;

import com.android.settings.R;

public class IconPackListPreference extends ListPreference {
    private final Context mContext;

    public IconPackListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onClick() {
        if (getEntries() == null || getEntryValues() == null) {
            return;
        }

        int selectedIndex = findIndexOfValue(getValue());

        IconAdapter adapter = new IconAdapter(mContext,
                R.layout.icon_pack_dialog_item,
                getEntries(), getEntryValues(), getValue());

        new AlertDialog.Builder(mContext)
                .setTitle(getTitle())
                .setSingleChoiceItems(adapter, selectedIndex, (dialog, which) -> {
                    String value = getEntryValues()[which].toString();
                    if (callChangeListener(value)) {
                        setValue(value);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private static class IconAdapter extends ArrayAdapter<CharSequence> {
        private final CharSequence[] mEntryValues;
        private final String mCurrentValue;
        private final PackageManager mPm;
        private final LayoutInflater mInflater;

        public IconAdapter(Context context, int resource, CharSequence[] objects,
                           CharSequence[] entryValues, String currentValue) {
            super(context, resource, objects);
            mEntryValues = entryValues;
            mCurrentValue = currentValue;
            mPm = context.getPackageManager();
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.icon_pack_dialog_item, parent, false);
            }

            TextView title = convertView.findViewById(android.R.id.title);
            ImageView icon = convertView.findViewById(android.R.id.icon);
            RadioButton radio = convertView.findViewById(R.id.radio_button);

            title.setText(getItem(position));

            String pkgName = mEntryValues[position].toString();
            
            radio.setChecked(pkgName.equals(mCurrentValue));

            try {
                Resources res;
                if ("android".equals(pkgName)) {
                    res = Resources.getSystem();
                } else {
                    res = mPm.getResourcesForApplication(pkgName);
                }

                int iconId = res.getIdentifier("ic_wifi_signal_4", "drawable", pkgName);
                if (iconId == 0) {
                    iconId = res.getIdentifier("ic_wifi_signal_4", "drawable", "com.android.systemui");
                }
                if (iconId == 0) {
                    iconId = Resources.getSystem().getIdentifier("ic_wifi_signal_4", "drawable", "android");
                }

                if (iconId != 0) {
                    Drawable d = res.getDrawable(iconId, null);
                    icon.setImageDrawable(d);
                } else {
                    icon.setImageDrawable(getContext().getDrawable(com.android.internal.R.drawable.ic_wifi_signal_4));
                }
            } catch (Exception e) {
                icon.setImageDrawable(getContext().getDrawable(com.android.internal.R.drawable.ic_wifi_signal_4));
            }

            return convertView;
        }
    }
}
