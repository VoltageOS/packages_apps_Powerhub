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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SubSettings;

public class TrickyStoreFragment extends SettingsPreferenceFragment {

    private static final String TAG = "TrickyStoreFragment";

    private static final String KEY_KEYBOX_IMPORT = "keybox_import";
    private static final String KEY_KEYBOX_DELETE = "keybox_delete";
    private static final String KEY_TARGET_MANAGE = "target_manage_apps";
    private static final String KEY_TARGET_IMPORT = "target_import_file";

    private KeyboxManager mKeyboxManager;

    private Preference mKeyboxImportPreference;
    private Preference mDeleteKeyboxPreference;
    private Preference mTargetManagePreference;
    private Preference mTargetImportPreference;

    private final ActivityResultLauncher<Intent> mKeyboxFileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                importKeyboxFile(uri);
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> mTargetFileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                importTargetFile(uri);
                            }
                        }
                    });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().setTitle(R.string.trickystore_screen_title);
        mKeyboxManager = new KeyboxManager(requireContext());

        addPreferencesFromResource(R.xml.trickystore_settings);

        mKeyboxImportPreference = findPreference(KEY_KEYBOX_IMPORT);
        mDeleteKeyboxPreference = findPreference(KEY_KEYBOX_DELETE);
        mTargetManagePreference = findPreference(KEY_TARGET_MANAGE);
        mTargetImportPreference = findPreference(KEY_TARGET_IMPORT);

        mKeyboxImportPreference.setOnPreferenceClickListener(preference -> {
            openKeyboxFilePicker();
            return true;
        });
        mDeleteKeyboxPreference.setOnPreferenceClickListener(preference -> {
            confirmDeleteKeybox();
            return true;
        });
        mTargetManagePreference.setOnPreferenceClickListener(preference -> {
            openFragment(TargetAppsFragment.class, getString(R.string.target_screen_title));
            return true;
        });
        mTargetImportPreference.setOnPreferenceClickListener(preference -> {
            openTargetFilePicker();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
    }

    private void refreshUi() {
        boolean keyboxExists = mKeyboxManager.keyboxExists();
        int targetCount = mKeyboxManager.getTargetAppCount();

        String keyboxStatus = keyboxExists
                ? getString(R.string.keybox_installed)
                : getString(R.string.keybox_not_found);
        String keyboxDetail = keyboxExists
                ? getString(R.string.spoof_dashboard_keybox_present_detail)
                : getString(R.string.keybox_delete_summary);
        mKeyboxImportPreference.setSummary(keyboxStatus + "\n" + keyboxDetail);
        mDeleteKeyboxPreference.setEnabled(keyboxExists);

        String targetStatus = targetCount > 0
                ? getString(R.string.target_apps_count, targetCount)
                : getString(R.string.target_no_apps);
        String targetDetail = targetCount > 0
                ? getString(R.string.spoof_dashboard_target_present_detail)
                : getString(R.string.target_import_summary);
        mTargetManagePreference.setSummary(targetStatus + "\n" + targetDetail);
        mTargetImportPreference.setSummary(getString(R.string.target_import_summary));
    }

    private void openKeyboxFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"text/xml", "application/xml"});
        mKeyboxFileLauncher.launch(intent);
    }

    private void importKeyboxFile(@NonNull Uri uri) {
        try {
            mKeyboxManager.importKeybox(uri);
            refreshUi();
            Toast.makeText(requireContext(), R.string.keybox_import_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "importKeyboxFile error", e);
            Toast.makeText(requireContext(),
                    getString(R.string.keybox_import_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDeleteKeybox() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.keybox_delete_title)
                .setMessage(R.string.keybox_delete_message)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    mKeyboxManager.deleteKeybox();
                    refreshUi();
                    Toast.makeText(requireContext(), R.string.keybox_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openTargetFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        mTargetFileLauncher.launch(intent);
    }

    private void importTargetFile(@NonNull Uri uri) {
        try {
            mKeyboxManager.importTargetFile(uri);
            refreshUi();
            Toast.makeText(requireContext(), R.string.target_import_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "importTargetFile error", e);
            Toast.makeText(requireContext(),
                    getString(R.string.target_import_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openFragment(@NonNull Class<? extends Fragment> fragmentClass,
            @NonNull String title) {
        Intent intent = new Intent(requireActivity(), SubSettings.class);
        intent.putExtra(":settings:show_fragment", fragmentClass.getName());
        intent.putExtra(":settings:show_fragment_title", title);
        startActivity(intent);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }
}
