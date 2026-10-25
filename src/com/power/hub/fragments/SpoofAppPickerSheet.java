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

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The single app picker used by every spoofing page. */
public class SpoofAppPickerSheet extends SpoofSheetFragment
        implements SpoofAppAdapter.Listener {

    public static final String RESULT_PACKAGE = "spoof:package";
    public static final String RESULT_SELECTION = "spoof:selection";
    public static final String RESULT_TARGETS = "spoof:targets";
    public static final String RESULT_CLOSED = "spoof:closed";

    private static final String ARG_MODE = "spoof:mode";
    private static final String ARG_PRESELECTED = "spoof:preselected";
    private static final String ARG_EXCLUDED = "spoof:excluded";
    private static final String ARG_SHOW_SYSTEM = "spoof:show_system";
    private static final String ARG_ALLOW_OVERLAYS = "spoof:allow_overlays";
    private static final String ARG_SHOW_OVERLAYS = "spoof:show_overlays";
    private static final String STATE_SELECTION = "spoof:state_selection";

    private RecyclerView mList;
    private TextView mCount;
    private TextView mEmpty;
    private TextView mFilterSystem;
    private TextView mFilterOverlays;
    private ProgressBar mProgress;
    private EditText mSearch;

    private SpoofAppAdapter mAdapter;
    private List<SpoofApps.AppEntry> mAllApps = new ArrayList<>();
    private boolean mShowSystem;
    private boolean mShowOverlays;
    private boolean mAllowOverlays;
    private boolean mLoaded;
    private String mQuery = "";
    private Bundle mRestoredState;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRestoredState = savedInstanceState;
    }

    public static SpoofAppPickerSheet newInstance(String requestKey, String title, int mode,
            @Nullable ArrayList<String> preselected, @Nullable ArrayList<String> excluded,
            boolean showSystem, boolean allowOverlays, boolean showOverlays) {
        SpoofAppPickerSheet sheet = new SpoofAppPickerSheet();
        Bundle args = baseArgs(requestKey, title, null);
        args.putInt(ARG_MODE, mode);
        args.putStringArrayList(ARG_PRESELECTED, preselected);
        args.putStringArrayList(ARG_EXCLUDED, excluded);
        args.putBoolean(ARG_SHOW_SYSTEM, showSystem);
        args.putBoolean(ARG_ALLOW_OVERLAYS, allowOverlays);
        args.putBoolean(ARG_SHOW_OVERLAYS, showOverlays);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.sheet_spoof_picker;
    }

    @Override
    protected boolean isTallSheet() {
        return true;
    }

    @Override
    protected void onContentReady(@NonNull View content) {
        Bundle args = requireArguments();
        int mode = args.getInt(ARG_MODE, SpoofAppAdapter.MODE_MULTI);
        mShowSystem = args.getBoolean(ARG_SHOW_SYSTEM, false);
        mAllowOverlays = args.getBoolean(ARG_ALLOW_OVERLAYS, false);
        mShowOverlays = args.getBoolean(ARG_SHOW_OVERLAYS, false);

        mList = content.findViewById(R.id.picker_list);
        mCount = content.findViewById(R.id.picker_count);
        mEmpty = content.findViewById(R.id.picker_empty);
        mFilterSystem = content.findViewById(R.id.picker_filter_system);
        mFilterOverlays = content.findViewById(R.id.picker_filter_overlays);
        mProgress = content.findViewById(R.id.picker_progress);
        mSearch = content.findViewById(R.id.picker_search);
        bindIme(mSearch);

        mAdapter = new SpoofAppAdapter(mode);
        mAdapter.setListener(this);
        ArrayList<String> preselected = args.getStringArrayList(ARG_PRESELECTED);
        if (mRestoredState != null) {
            ArrayList<String> restored = mRestoredState.getStringArrayList(STATE_SELECTION);
            if (restored != null) {
                preselected = restored;
            }
        }
        if (mode == SpoofAppAdapter.MODE_TARGETS) {
            mAdapter.setTargetLines(preselected);
        } else if (mode != SpoofAppAdapter.MODE_SINGLE) {
            mAdapter.setSelectedPackages(preselected);
        }
        mList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mList.setAdapter(mAdapter);

        mCount.setVisibility(mode == SpoofAppAdapter.MODE_SINGLE ? View.GONE : View.VISIBLE);
        updateCount();

        mFilterOverlays.setVisibility(mAllowOverlays ? View.VISIBLE : View.GONE);
        styleFilters();
        mFilterSystem.setOnClickListener(v -> {
            mShowSystem = !mShowSystem;
            styleFilters();
            applyFilter();
        });
        mFilterOverlays.setOnClickListener(v -> {
            mShowOverlays = !mShowOverlays;
            styleFilters();
            applyFilter();
        });

        mSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mQuery = s == null ? "" : s.toString().trim();
                applyFilter();
            }
        });

        Set<String> excluded = new HashSet<>();
        ArrayList<String> excludedArgs = args.getStringArrayList(ARG_EXCLUDED);
        if (excludedArgs != null) {
            excluded.addAll(excludedArgs);
        }
        mList.setVisibility(View.INVISIBLE);
        SpoofApps.load(requireContext(), excluded, this::onAppsLoaded);
    }

    private void onAppsLoaded(List<SpoofApps.AppEntry> apps) {
        if (!isAdded() || mAdapter == null) {
            return;
        }
        mAllApps = apps;
        mLoaded = true;
        mProgress.setVisibility(View.GONE);
        applyFilter();
    }

    private void styleFilters() {
        SpoofUi.styleFilterPill(mFilterSystem, mShowSystem);
        SpoofUi.styleFilterPill(mFilterOverlays, mShowOverlays);
    }

    private void updateCount() {
        if (mAdapter == null) {
            return;
        }
        mCount.setText(getString(R.string.target_selected_count, mAdapter.getSelectedCount()));
    }

    private void applyFilter() {
        if (mAdapter == null || !mLoaded) {
            return;
        }
        Set<String> selected = mAdapter.getSelectedPackages();
        String query = mQuery.toLowerCase();
        List<SpoofApps.AppEntry> filtered = new ArrayList<>();
        for (SpoofApps.AppEntry entry : mAllApps) {
            boolean isSelected = selected.contains(entry.packageName);
            if (!mShowSystem && entry.isSystem && !isSelected) {
                continue;
            }
            if (!mShowOverlays && entry.isOverlay && !isSelected) {
                continue;
            }
            if (!query.isEmpty()) {
                String label = entry.label.toLowerCase();
                String packageName = entry.packageName.toLowerCase();
                if (!label.contains(query) && !packageName.contains(query)) {
                    continue;
                }
            }
            filtered.add(entry);
        }
        if (mAdapter.getMode() == SpoofAppAdapter.MODE_TARGETS) {
            filtered.sort((a, b) -> {
                boolean aSelected = selected.contains(a.packageName);
                boolean bSelected = selected.contains(b.packageName);
                if (aSelected != bSelected) {
                    return aSelected ? -1 : 1;
                }
                return a.label.compareToIgnoreCase(b.label);
            });
        }
        mAdapter.submit(filtered);
        mList.setVisibility(View.VISIBLE);
        mEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        mCount.setVisibility(mAdapter.getMode() == SpoofAppAdapter.MODE_SINGLE
                ? View.GONE : View.VISIBLE);
        updateCount();
    }

    private void emitSelection() {
        if (mAdapter == null) {
            return;
        }
        Bundle result = new Bundle();
        result.putStringArrayList(RESULT_SELECTION,
                new ArrayList<>(mAdapter.getSelectedPackages()));
        result.putStringArrayList(RESULT_TARGETS,
                new ArrayList<>(mAdapter.getTargetLines()));
        deliverResult(result);
    }

    @Override
    public void onSingleSelected(@NonNull SpoofApps.AppEntry entry) {
        Bundle result = new Bundle();
        result.putString(RESULT_PACKAGE, entry.packageName);
        deliverResult(result);
        dismiss();
    }

    @Override
    public void onSelectionChanged() {
        updateCount();
        if (mAdapter != null && mAdapter.getMode() == SpoofAppAdapter.MODE_TARGETS) {
            applyFilter();
        }
        emitSelection();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAdapter == null) {
            return;
        }
        outState.putStringArrayList(STATE_SELECTION, new ArrayList<>(
                mAdapter.getMode() == SpoofAppAdapter.MODE_TARGETS
                        ? mAdapter.getTargetLines() : mAdapter.getSelectedPackages()));
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        String key = getRequestKey();
        if (key == null) {
            return;
        }
        try {
            Bundle result = new Bundle();
            result.putBoolean(RESULT_CLOSED, true);
            getParentFragmentManager().setFragmentResult(key, result);
        } catch (IllegalStateException ignored) {
        }
    }
}
