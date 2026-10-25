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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.android.settings.R;

/** Hosts the spoofing pages behind a minimal segmented control. */
public class Spoofing extends Fragment {

    private static final String STATE_SEGMENT = "spoof_segment";
    private static final int PAGE_INTEGRITY = 0;
    private static final int PAGE_PER_APP = 1;
    private static final int PAGE_HIDDEN = 2;

    private int mSegment = PAGE_INTEGRITY;
    private TextView[] mSegmentViews;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().setTitle(R.string.spoof_screen_title);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spoofing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSegmentViews = new TextView[] {
                view.findViewById(R.id.spoof_segment_integrity),
                view.findViewById(R.id.spoof_segment_per_app),
                view.findViewById(R.id.spoof_segment_hidden)
        };
        for (int i = 0; i < mSegmentViews.length; i++) {
            final int index = i;
            mSegmentViews[i].setOnClickListener(v -> selectSegment(index));
        }

        if (savedInstanceState != null) {
            mSegment = savedInstanceState.getInt(STATE_SEGMENT, PAGE_INTEGRITY);
        }
        if (savedInstanceState == null
                || getChildFragmentManager().findFragmentById(R.id.spoof_content) == null) {
            showPage(mSegment);
        }
        updateSegments();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SEGMENT, mSegment);
    }

    private void selectSegment(int index) {
        if (index == mSegment) {
            return;
        }
        mSegment = index;
        showPage(index);
        updateSegments();
    }

    private void showPage(int index) {
        Fragment page;
        switch (index) {
            case PAGE_PER_APP:
                page = new AppSpoofFragment();
                break;
            case PAGE_HIDDEN:
                page = new HideAppListSettings();
                break;
            case PAGE_INTEGRITY:
            default:
                page = new IntegritySpoofFragment();
                break;
        }
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        transaction.replace(R.id.spoof_content, page);
        transaction.commit();
    }

    private void updateSegments() {
        for (int i = 0; i < mSegmentViews.length; i++) {
            boolean selected = i == mSegment;
            mSegmentViews[i].setBackgroundResource(selected
                    ? R.drawable.powerhub_spoof_segment_selected
                    : android.R.color.transparent);
            mSegmentViews[i].setTextColor(ContextCompat.getColor(mSegmentViews[i].getContext(),
                    selected ? R.color.powerhub_spoof_accent
                            : R.color.powerhub_spoof_text_secondary));
        }
    }
}
