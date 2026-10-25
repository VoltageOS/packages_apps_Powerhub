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

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.settings.R;

import java.io.Serializable;
import java.util.ArrayList;

/** Shared single-choice sheet: actions, profiles, and profile sources. */
public class SpoofChoiceSheet extends SpoofSheetFragment {

    public static final String RESULT_ID = "spoof:choice_id";
    public static final String RESULT_DELETE = "spoof:choice_delete";
    public static final String RESULT_ACTION = "spoof:choice_action";
    public static final String RESULT_CONTEXT = "spoof:choice_context";

    private static final String ARG_ITEMS = "spoof:items";
    private static final String ARG_ACTION_LABEL = "spoof:action_label";
    private static final String ARG_CONTEXT = "spoof:context";

    public static final class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String id;
        public final String title;
        public final String subtitle;
        public final int iconRes;
        public final boolean destructive;
        public final boolean deletable;

        public Item(String id, String title, String subtitle, int iconRes,
                boolean destructive, boolean deletable) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.destructive = destructive;
            this.deletable = deletable;
        }

        public Item(String id, String title, String subtitle) {
            this(id, title, subtitle, 0, false, false);
        }
    }

    public static SpoofChoiceSheet newInstance(String requestKey, String title, String subtitle,
            ArrayList<Item> items, String actionLabel) {
        return newInstance(requestKey, title, subtitle, items, actionLabel, null);
    }

    public static SpoofChoiceSheet newInstance(String requestKey, String title, String subtitle,
            ArrayList<Item> items, String actionLabel, @Nullable String context) {
        SpoofChoiceSheet sheet = new SpoofChoiceSheet();
        Bundle args = baseArgs(requestKey, title, subtitle);
        args.putSerializable(ARG_ITEMS, items);
        args.putString(ARG_ACTION_LABEL, actionLabel);
        args.putString(ARG_CONTEXT, context);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.sheet_spoof_choice;
    }

    @Override
    protected void onContentReady(@NonNull View content) {
        LinearLayout list = content.findViewById(R.id.choice_list);
        Bundle args = getArguments();
        ArrayList<Item> items = null;
        if (args != null) {
            Serializable serialized = args.getSerializable(ARG_ITEMS);
            if (serialized instanceof ArrayList) {
                //noinspection unchecked
                items = (ArrayList<Item>) serialized;
            }
        }
        if (items != null) {
            for (Item item : items) {
                addRow(list, item);
            }
        }

        String actionLabel = args == null ? null : args.getString(ARG_ACTION_LABEL);
        if (actionLabel != null && !actionLabel.isEmpty()) {
            addAction(actionLabel, false, () -> {
                Bundle result = new Bundle();
                result.putBoolean(RESULT_ACTION, true);
                putContext(result, args);
                deliverResult(result);
                dismiss();
            });
        }
    }

    private void putContext(Bundle result, Bundle args) {
        if (args != null) {
            result.putString(RESULT_CONTEXT, args.getString(ARG_CONTEXT));
        }
    }

    private void addRow(LinearLayout list, Item item) {
        View row = LayoutInflater.from(list.getContext())
                .inflate(R.layout.row_spoof_choice, list, false);
        ImageView icon = row.findViewById(R.id.row_icon);
        TextView title = row.findViewById(R.id.row_title);
        TextView summary = row.findViewById(R.id.row_summary);
        ImageView delete = row.findViewById(R.id.row_delete);

        if (item.iconRes != 0) {
            icon.setImageResource(item.iconRes);
            icon.setVisibility(View.VISIBLE);
        }
        title.setText(item.title);
        if (item.subtitle == null || item.subtitle.isEmpty()) {
            summary.setVisibility(View.GONE);
        } else {
            summary.setText(item.subtitle);
        }
        if (item.destructive) {
            ColorStateList error = ContextCompat.getColorStateList(row.getContext(),
                    R.color.powerhub_spoof_error);
            title.setTextColor(error);
            summary.setTextColor(error);
        }
        if (item.deletable) {
            delete.setVisibility(View.VISIBLE);
            delete.setOnClickListener(v -> {
                Bundle result = new Bundle();
                result.putString(RESULT_DELETE, item.id);
                putContext(result, getArguments());
                deliverResult(result);
                dismiss();
            });
        }
        row.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString(RESULT_ID, item.id);
            putContext(result, getArguments());
            deliverResult(result);
            dismiss();
        });
        list.addView(row);
    }
}
