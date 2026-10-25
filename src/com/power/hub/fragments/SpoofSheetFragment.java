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

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.DialogFragment;

import com.android.settings.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

/** Shared bottom-sheet shell for every spoofing picker and editor. */
public abstract class SpoofSheetFragment extends BottomSheetDialogFragment {

    public static final String ARG_REQUEST_KEY = "spoof:request_key";
    public static final String ARG_TITLE = "spoof:title";
    public static final String ARG_SUBTITLE = "spoof:subtitle";

    public static final String RESULT_CLOSED = "spoof:closed";

    private View mRoot;
    private FrameLayout mContent;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Powerhub_Spoof_Sheet);
    }

    @Override
    public int getTheme() {
        return R.style.Theme_Powerhub_Spoof_Sheet;
    }

    protected static Bundle baseArgs(String requestKey, String title, String subtitle) {
        Bundle args = new Bundle();
        args.putString(ARG_REQUEST_KEY, requestKey);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        return args;
    }

    @LayoutRes
    protected abstract int getContentLayout();

    protected abstract void onContentReady(@NonNull View content);

    protected boolean isTallSheet() {
        return false;
    }

    protected String getRequestKey() {
        Bundle args = getArguments();
        return args == null ? null : args.getString(ARG_REQUEST_KEY);
    }

    protected void deliverResult(@NonNull Bundle result) {
        String key = getRequestKey();
        if (key == null || !isAdded()) {
            return;
        }
        getParentFragmentManager().setFragmentResult(key, result);
    }

    protected final View getRootView() {
        return mRoot;
    }

    protected final FrameLayout getContentView() {
        return mContent;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.sheet_spoof_shell, container, false);
        mRoot = root;

        Bundle args = getArguments();
        String title = args == null ? null : args.getString(ARG_TITLE);
        String subtitle = args == null ? null : args.getString(ARG_SUBTITLE);

        TextView titleView = root.findViewById(R.id.sheet_title);
        TextView subtitleView = root.findViewById(R.id.sheet_subtitle);
        titleView.setText(title);
        if (subtitle == null || subtitle.isEmpty()) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }

        mContent = root.findViewById(R.id.sheet_content);
        int layout = getContentLayout();
        if (layout != 0) {
            LayoutInflater themed = inflater.cloneInContext(mContent.getContext());
            View content = themed.inflate(layout, mContent, false);
            mContent.addView(content);
            onContentReady(content);
        }
        if (isTallSheet()) {
            int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.66f);
            mContent.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height));
        }
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            window.setDimAmount(0.45f);
        }
        View sheet = getDialog() == null ? null
                : getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    protected final MaterialButton addAction(CharSequence label, boolean destructive,
            @NonNull Runnable action) {
        View root = mRoot;
        LinearLayout actions = root.findViewById(R.id.sheet_actions);
        actions.setVisibility(View.VISIBLE);

        MaterialButton button = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.view_spoof_button, actions, false);
        button.setText(label);
        if (destructive) {
            button.setTextColor(ContextCompat.getColorStateList(actions.getContext(),
                    R.color.powerhub_spoof_error));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(SpoofUi.dp(requireContext(), 4));
        button.setLayoutParams(params);
        button.setOnClickListener(v -> action.run());
        actions.addView(button);
        return button;
    }

    protected final MaterialButton addPrimaryAction(CharSequence label,
            @NonNull Runnable action) {
        MaterialButton button = addAction(label, false, action);
        button.setBackgroundTintList(ContextCompat.getColorStateList(button.getContext(),
                R.color.powerhub_spoof_accent));
        button.setTextColor(ContextCompat.getColorStateList(button.getContext(),
                R.color.powerhub_spoof_on_accent));
        return button;
    }

    protected final EditText addField(LinearLayout parent, CharSequence label, String value,
            String hint) {
        if (label != null) {
            TextView labelView = new TextView(parent.getContext(), null, 0,
                    R.style.Widget_Powerhub_Spoof_Section);
            labelView.setText(label);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = SpoofUi.dp(parent.getContext(), 12);
            labelParams.bottomMargin = SpoofUi.dp(parent.getContext(), 6);
            parent.addView(labelView, labelParams);
        }

        EditText field = new EditText(parent.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_TextField);
        field.setHint(hint);
        field.setText(value);
        field.setTextSize(15);
        bindIme(field);
        parent.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    protected final void bindIme(@NonNull EditText field) {
        field.setFocusableInTouchMode(true);
        field.setCursorVisible(true);
        field.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                showIme(field);
            }
        });
    }

    protected final void focusField(@NonNull EditText field) {
        bindIme(field);
        field.post(() -> {
            field.requestFocus();
            field.setSelection(field.getText().length());
            showIme(field);
        });
    }

    protected final void showIme(@NonNull View view) {
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window != null) {
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(window, view);
            controller.show(WindowInsetsCompat.Type.ime());
        }
        InputMethodManager manager = view.getContext().getSystemService(
                InputMethodManager.class);
        if (manager != null) {
            manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    protected final TextView addBodyText(LinearLayout parent, CharSequence text,
            boolean monospace) {
        TextView view = new TextView(parent.getContext(), null, 0,
                R.style.Widget_Powerhub_Spoof_RowSummary);
        view.setText(text);
        if (monospace) {
            view.setTypeface(Typeface.MONOSPACE);
        }
        view.setGravity(Gravity.START);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = SpoofUi.dp(parent.getContext(), 8);
        parent.addView(view, params);
        return view;
    }
}
