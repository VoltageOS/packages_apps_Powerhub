/*
 * Copyright (C) 2026 AxionOS
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.R;

import java.io.Serializable;
import java.util.ArrayList;

/** Read-only label/value sheet, used for active profile properties. */
public class SpoofDetailsSheet extends SpoofSheetFragment {

    private static final String ARG_ITEMS = "spoof:details";
    private static final String ARG_COPY = "spoof:details_copy";

    public static final class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String label;
        public final String value;

        public Item(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    public static SpoofDetailsSheet newInstance(String title, String subtitle,
            ArrayList<Item> items) {
        return newInstance(title, subtitle, items, null);
    }

    public static SpoofDetailsSheet newInstance(String title, String subtitle,
            ArrayList<Item> items, @Nullable String copyText) {
        SpoofDetailsSheet sheet = new SpoofDetailsSheet();
        Bundle args = baseArgs(null, title, subtitle);
        args.putSerializable(ARG_ITEMS, items);
        args.putString(ARG_COPY, copyText);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.sheet_spoof_details;
    }

    @Override
    protected void onContentReady(@NonNull View content) {
        LinearLayout list = content.findViewById(R.id.details_list);
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        Serializable serialized = args.getSerializable(ARG_ITEMS);
        if (!(serialized instanceof ArrayList)) {
            return;
        }
        //noinspection unchecked
        ArrayList<Item> items = (ArrayList<Item>) serialized;
        for (Item item : items) {
            View row = LayoutInflater.from(list.getContext())
                    .inflate(R.layout.row_spoof_value, list, false);
            TextView label = row.findViewById(R.id.row_label);
            TextView value = row.findViewById(R.id.row_value);
            label.setText(item.label);
            value.setText(item.value);
            if (item.value == null || item.value.isEmpty()) {
                value.setVisibility(View.GONE);
            }
            list.addView(row);
        }

        String copyText = args.getString(ARG_COPY);
        if (copyText != null && !copyText.isEmpty()) {
            addAction(getString(R.string.spoof_copy_to_clipboard), false, () -> {
                ClipboardManager clipboard = (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                            getString(R.string.spoof_diagnostics_title), copyText));
                }
                Toast.makeText(requireContext(), R.string.spoof_copied,
                        Toast.LENGTH_SHORT).show();
                dismiss();
            });
        }
    }
}
