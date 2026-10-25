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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared list for every spoofing app picker. */
public class SpoofAppAdapter extends RecyclerView.Adapter<SpoofAppAdapter.Holder> {

    public static final int MODE_SINGLE = 0;
    public static final int MODE_MULTI = 1;
    public static final int MODE_TARGETS = 2;

    public enum TargetMode {
        AUTO(""),
        LEAF_HACK("?"),
        CERT_GEN("!"),
        SKIP("-");

        public final String symbol;

        TargetMode(String symbol) {
            this.symbol = symbol;
        }

        public static TargetMode fromLine(String line) {
            if (line.endsWith("?")) {
                return LEAF_HACK;
            }
            if (line.endsWith("!")) {
                return CERT_GEN;
            }
            if (line.endsWith("-")) {
                return SKIP;
            }
            return AUTO;
        }

        public static String packageOf(String line) {
            if (line.endsWith("?") || line.endsWith("!") || line.endsWith("-")) {
                return line.substring(0, line.length() - 1);
            }
            return line;
        }
    }

    public interface Listener {
        void onSingleSelected(@NonNull SpoofApps.AppEntry entry);

        void onSelectionChanged();
    }

    private final List<SpoofApps.AppEntry> mItems = new ArrayList<>();
    private final Set<String> mSelected = new LinkedHashSet<>();
    private final Map<String, TargetMode> mTargetModes = new HashMap<>();
    private final int mMode;
    private Listener mListener;

    public SpoofAppAdapter(int mode) {
        mMode = mode;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public int getMode() {
        return mMode;
    }

    public void submit(List<SpoofApps.AppEntry> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void setSelectedPackages(Iterable<String> packages) {
        mSelected.clear();
        if (packages != null) {
            for (String packageName : packages) {
                if (packageName != null && !packageName.isEmpty()) {
                    mSelected.add(packageName);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setTargetLines(List<String> lines) {
        mSelected.clear();
        mTargetModes.clear();
        if (lines != null) {
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                mSelected.add(TargetMode.packageOf(trimmed));
                mTargetModes.put(TargetMode.packageOf(trimmed), TargetMode.fromLine(trimmed));
            }
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedPackages() {
        return new LinkedHashSet<>(mSelected);
    }

    public List<String> getTargetLines() {
        List<String> lines = new ArrayList<>();
        for (String packageName : mSelected) {
            TargetMode mode = mTargetModes.get(packageName);
            lines.add(packageName + (mode == null ? "" : mode.symbol));
        }
        return lines;
    }

    public int getSelectedCount() {
        return mSelected.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_spoof_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SpoofApps.AppEntry entry = mItems.get(position);
        boolean selected = mSelected.contains(entry.packageName);

        holder.icon.setImageDrawable(entry.icon);
        holder.title.setText(entry.label);
        holder.summary.setText(entry.packageName);

        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(selected);
        holder.toggle.setVisibility(mMode == MODE_SINGLE ? View.GONE : View.VISIBLE);
        holder.chevron.setVisibility(mMode == MODE_SINGLE ? View.VISIBLE : View.GONE);
        holder.modes.setVisibility(mMode == MODE_TARGETS && selected
                ? View.VISIBLE : View.GONE);

        if (mMode == MODE_TARGETS) {
            TargetMode mode = mTargetModes.get(entry.packageName);
            if (mode == null) {
                mode = TargetMode.AUTO;
            }
            SpoofUi.styleModePill(holder.modeAuto, mode == TargetMode.AUTO);
            SpoofUi.styleModePill(holder.modeLeaf, mode == TargetMode.LEAF_HACK);
            SpoofUi.styleModePill(holder.modeCert, mode == TargetMode.CERT_GEN);
            SpoofUi.styleModePill(holder.modeSkip, mode == TargetMode.SKIP);
        }

        holder.itemView.setOnClickListener(v -> {
            if (mMode == MODE_SINGLE) {
                if (mListener != null) {
                    mListener.onSingleSelected(entry);
                }
            } else {
                setSelected(entry.packageName, !mSelected.contains(entry.packageName));
            }
        });

        holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                setSelected(entry.packageName, isChecked);
            }
        });

        if (mMode == MODE_TARGETS) {
            holder.modeAuto.setOnClickListener(v -> setTargetMode(entry.packageName,
                    TargetMode.AUTO));
            holder.modeLeaf.setOnClickListener(v -> setTargetMode(entry.packageName,
                    TargetMode.LEAF_HACK));
            holder.modeCert.setOnClickListener(v -> setTargetMode(entry.packageName,
                    TargetMode.CERT_GEN));
            holder.modeSkip.setOnClickListener(v -> setTargetMode(entry.packageName,
                    TargetMode.SKIP));
        }
    }

    private void setSelected(String packageName, boolean selected) {
        if (selected) {
            mSelected.add(packageName);
        } else {
            mSelected.remove(packageName);
            mTargetModes.remove(packageName);
        }
        notifyItemRangeChanged(0, mItems.size());
        if (mListener != null) {
            mListener.onSelectionChanged();
        }
    }

    private void setTargetMode(String packageName, TargetMode mode) {
        mTargetModes.put(packageName, mode);
        notifyItemRangeChanged(0, mItems.size());
        if (mListener != null) {
            mListener.onSelectionChanged();
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public SpoofApps.AppEntry getItem(int position) {
        return mItems.get(position);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView summary;
        final View modes;
        final TextView modeAuto;
        final TextView modeLeaf;
        final TextView modeCert;
        final TextView modeSkip;
        final MaterialSwitch toggle;
        final ImageView chevron;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.row_icon);
            title = itemView.findViewById(R.id.row_title);
            summary = itemView.findViewById(R.id.row_summary);
            modes = itemView.findViewById(R.id.row_modes);
            modeAuto = itemView.findViewById(R.id.mode_auto);
            modeLeaf = itemView.findViewById(R.id.mode_leaf);
            modeCert = itemView.findViewById(R.id.mode_cert);
            modeSkip = itemView.findViewById(R.id.mode_skip);
            toggle = itemView.findViewById(R.id.row_switch);
            chevron = itemView.findViewById(R.id.row_chevron);
        }
    }
}
