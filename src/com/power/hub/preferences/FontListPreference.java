package com.power.hub.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;

public class FontListPreference extends ListPreference {
    private final Context mContext;

    public FontListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onClick() {
        if (getEntries() == null || getEntryValues() == null) {
            return;
        }

        int selectedIndex = findIndexOfValue(getValue());

        FontAdapter adapter = new FontAdapter(mContext,
                android.R.layout.select_dialog_singlechoice,
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

    private static class FontAdapter extends ArrayAdapter<CharSequence> {
        private final CharSequence[] mEntryValues;
        private final String mCurrentValue;
        private final LayoutInflater mInflater;

        public FontAdapter(Context context, int resource, CharSequence[] objects,
                           CharSequence[] entryValues, String currentValue) {
            super(context, resource, objects);
            mEntryValues = entryValues;
            mCurrentValue = currentValue;
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(android.R.layout.select_dialog_singlechoice, parent, false);
            }

            CheckedTextView textView = (CheckedTextView) convertView.findViewById(android.R.id.text1);
            textView.setText(getItem(position));

            String pkgName = mEntryValues[position].toString();

            if (pkgName.equals(mCurrentValue)) {
                textView.setChecked(true);
            }

            if (!"android".equals(pkgName)) {
                try {
                    Context overlayContext = getContext().createPackageContext(pkgName, 0);
                    int fontId = overlayContext.getResources().getIdentifier("config_bodyFontFamily", "string", pkgName);
                    if (fontId != 0) {
                        String fontFamily = overlayContext.getResources().getString(fontId);
                        textView.setTypeface(Typeface.create(fontFamily, Typeface.NORMAL));
                    }
                } catch (Exception e) {
                    textView.setTypeface(Typeface.DEFAULT);
                }
            } else {
                textView.setTypeface(Typeface.DEFAULT);
            }

            return convertView;
        }
    }
}
