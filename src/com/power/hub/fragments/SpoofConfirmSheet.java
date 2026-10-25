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
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.settings.R;

/** Shared destructive confirmation sheet. */
public class SpoofConfirmSheet extends SpoofSheetFragment {

    public static final String RESULT_CONFIRMED = "spoof:confirmed";
    public static final String RESULT_CONTEXT = "spoof:confirm_context";

    private static final String ARG_MESSAGE = "spoof:message";
    private static final String ARG_CONFIRM_LABEL = "spoof:confirm_label";
    private static final String ARG_CONTEXT = "spoof:confirm_arg_context";

    public static SpoofConfirmSheet newInstance(String requestKey, String title, String message,
            String confirmLabel) {
        return newInstance(requestKey, title, message, confirmLabel, null);
    }

    public static SpoofConfirmSheet newInstance(String requestKey, String title, String message,
            String confirmLabel, String context) {
        SpoofConfirmSheet sheet = new SpoofConfirmSheet();
        Bundle args = baseArgs(requestKey, title, null);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_CONFIRM_LABEL, confirmLabel);
        args.putString(ARG_CONTEXT, context);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.sheet_spoof_confirm;
    }

    @Override
    protected void onContentReady(@NonNull View content) {
        Bundle args = getArguments();
        TextView message = content.findViewById(R.id.confirm_message);
        if (args != null) {
            message.setText(args.getString(ARG_MESSAGE));
        }

        String label = args == null ? null : args.getString(ARG_CONFIRM_LABEL);
        if (label == null || label.isEmpty()) {
            label = getString(R.string.action_delete);
        }
        addAction(getString(R.string.cancel), false, this::dismiss);
        addAction(label, true, () -> {
            Bundle result = new Bundle();
            result.putBoolean(RESULT_CONFIRMED, true);
            if (args != null) {
                result.putString(RESULT_CONTEXT, args.getString(ARG_CONTEXT));
            }
            deliverResult(result);
            dismiss();
        });
    }
}
