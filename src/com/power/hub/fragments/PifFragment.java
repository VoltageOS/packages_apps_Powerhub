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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class PifFragment extends SettingsPreferenceFragment {

    private static final String TAG = "PifFragment";

    private static final String KEY_ACTIVE_CONFIG = "pif_active_config";
    private static final String KEY_FETCH_BETA = "pif_fetch_beta";
    private static final String KEY_FETCH_VOLTAGE = "pif_fetch_voltage";
    private static final String KEY_IMPORT_FILE = "pif_import_file";
    private static final String KEY_SHOW_PROPS = "pif_show_props";
    private static final String KEY_SPOOF_PHOTOS = "pif_spoof_photos";
    private static final String KEY_OPTIONS_CATEGORY = "pif_options_category";
    private static final String KEY_FILES_CATEGORY = "pif_files_category";

    private PifManager mPifManager;
    private PifRepository mPifRepository;

    private Preference mStatusPreference;
    private Preference mFetchBetaPreference;
    private Preference mFetchVoltagePreference;
    private Preference mImportPreference;
    private Preference mShowPropsPreference;
    private SwitchPreferenceCompat mSpoofPhotosPreference;
    private PreferenceCategory mOptionsCategory;
    private PreferenceCategory mConfigCategory;
    private String mImportTargetFileName;

    private final ActivityResultLauncher<android.content.Intent> mPifFileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                importPifFile(uri);
                            }
                        } else {
                            mImportTargetFileName = null;
                        }
                    });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().setTitle(R.string.pif_category_title);
        mPifManager = new PifManager(requireContext());
        mPifRepository = new PifRepository();

        addPreferencesFromResource(R.xml.pif_settings);

        mStatusPreference = findPreference(KEY_ACTIVE_CONFIG);
        mFetchBetaPreference = findPreference(KEY_FETCH_BETA);
        mFetchVoltagePreference = findPreference(KEY_FETCH_VOLTAGE);
        mImportPreference = findPreference(KEY_IMPORT_FILE);
        mShowPropsPreference = findPreference(KEY_SHOW_PROPS);
        mSpoofPhotosPreference = findPreference(KEY_SPOOF_PHOTOS);
        mOptionsCategory = findPreference(KEY_OPTIONS_CATEGORY);
        mConfigCategory = findPreference(KEY_FILES_CATEGORY);

        mFetchBetaPreference.setOnPreferenceClickListener(preference -> {
            fetchBetaPif();
            return true;
        });
        mFetchVoltagePreference.setOnPreferenceClickListener(preference -> {
            fetchVoltagePif();
            return true;
        });
        mImportPreference.setOnPreferenceClickListener(preference -> {
            mImportTargetFileName = null;
            openPifFilePicker();
            return true;
        });
        mShowPropsPreference.setOnPreferenceClickListener(preference -> {
            showCurrentProps();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
    }

    private void refreshUi() {
        String activeConfig = mPifManager.getActiveConfigName();
        Map<String, String> props = mPifManager.getCurrentProperties();

        mStatusPreference.setTitle(activeConfig.isEmpty()
                ? getString(R.string.pif_no_config_loaded)
                : getString(R.string.pif_active_config, activeConfig));
        mStatusPreference.setSummary(buildActiveConfigSummary(props));
        mShowPropsPreference.setSummary(props.isEmpty()
                ? getString(R.string.pif_no_props)
                : buildPropsPreview(props));

        boolean hasActiveConfig = !activeConfig.isEmpty();
        if (mOptionsCategory != null) {
            mOptionsCategory.setVisible(true);
        }
        if (mSpoofPhotosPreference != null) {
            mSpoofPhotosPreference.setOnPreferenceChangeListener(null);
            mSpoofPhotosPreference.setChecked(mPifManager.isSpoofPhotosEnabled());
            mSpoofPhotosPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                mPifManager.setSpoofPhotos((Boolean) newValue);
                refreshUi();
                return true;
            });
        }

        bindConfigPreferences();
    }

    private String buildActiveConfigSummary(@NonNull Map<String, String> props) {
        String model = props.getOrDefault("MODEL", getString(R.string.pif_no_props));
        String fingerprint = props.getOrDefault("FINGERPRINT", getString(R.string.pif_not_available));
        String patch = props.getOrDefault("SECURITY_PATCH", getString(R.string.pif_not_available));
        return getString(R.string.pif_model_label, model) + "\n"
                + getString(R.string.pif_fingerprint_label, fingerprint) + "\n"
                + getString(R.string.pif_security_patch_label, patch);
    }

    private String buildPropsPreview(@NonNull Map<String, String> props) {
        String model = props.get("MODEL");
        String patch = props.get("SECURITY_PATCH");
        if (model != null && patch != null && !patch.isEmpty()) {
            return getString(R.string.spoof_dashboard_pif_detail, model, patch);
        }
        if (model != null && !model.isEmpty()) {
            return model;
        }
        return getString(R.string.pif_current_props_title);
    }

    private void fetchBetaPif() {
        Toast.makeText(requireContext(), R.string.pif_fetching, Toast.LENGTH_SHORT).show();
        mFetchBetaPreference.setEnabled(false);

        new Thread(() -> {
            PifRepository.PifResult result = mPifRepository.fetchBetaPif();
            requireActivity().runOnUiThread(() -> {
                mFetchBetaPreference.setEnabled(true);
                if (result instanceof PifRepository.PifResult.Success) {
                    PifRepository.PifResult.Success success =
                            (PifRepository.PifResult.Success) result;
                    try {
                        String targetFile = mPifManager.writeAutoSelectedJsonConfig(success.pifData);
                        refreshUi();
                        Toast.makeText(requireContext(),
                                getString(R.string.pif_fetch_success_named, success.model, targetFile),
                                Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "applyPif error", e);
                        Toast.makeText(requireContext(),
                                getString(R.string.pif_fetch_failed, e.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                } else if (result instanceof PifRepository.PifResult.Error) {
                    PifRepository.PifResult.Error error = (PifRepository.PifResult.Error) result;
                    Toast.makeText(requireContext(),
                            getString(R.string.pif_fetch_failed, error.message),
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void fetchVoltagePif() {
        Toast.makeText(requireContext(), R.string.pif_fetching_voltage, Toast.LENGTH_SHORT).show();
        mFetchVoltagePreference.setEnabled(false);

        new Thread(() -> {
            PifRepository.PifResult result = mPifRepository.fetchVoltagePif();
            requireActivity().runOnUiThread(() -> {
                mFetchVoltagePreference.setEnabled(true);
                if (result instanceof PifRepository.PifResult.Success) {
                    PifRepository.PifResult.Success success =
                            (PifRepository.PifResult.Success) result;
                    try {
                        String targetFile = mPifManager.writeAutoSelectedJsonConfig(success.pifData);
                        refreshUi();
                        Toast.makeText(requireContext(),
                                getString(R.string.pif_fetch_success_named, success.model, targetFile),
                                Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "writeVoltagePif error", e);
                        Toast.makeText(requireContext(),
                                getString(R.string.pif_fetch_failed, e.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                } else if (result instanceof PifRepository.PifResult.Error) {
                    PifRepository.PifResult.Error error = (PifRepository.PifResult.Error) result;
                    Toast.makeText(requireContext(),
                            getString(R.string.pif_fetch_failed, error.message),
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void openPifFilePicker() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                new String[] {"application/json", "text/plain", "text/x-java-properties"});
        mPifFileLauncher.launch(intent);
    }

    private void importPifFile(@NonNull Uri uri) {
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IllegalStateException("Unable to open config");
            }

            String fileName = resolveDisplayName(uri);
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String importedFile = mPifManager.importPifConfig(mImportTargetFileName, fileName, content);
            mImportTargetFileName = null;
            refreshUi();
            Toast.makeText(requireContext(),
                    getString(R.string.pif_import_success_named, importedFile),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            mImportTargetFileName = null;
            Log.e(TAG, "importPifFile error", e);
            Toast.makeText(requireContext(),
                    getString(R.string.pif_import_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showCurrentProps() {
        Map<String, String> props = mPifManager.getCurrentProperties();
        if (props.isEmpty()) {
            Toast.makeText(requireContext(), R.string.pif_no_props, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject(props);
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.pif_current_props_title)
                    .setMessage(json.toString(4).replace("\\/", "/"))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "showCurrentProps error", e);
        }
    }

    private String resolveDisplayName(@NonNull Uri uri) {
        Cursor cursor = null;
        try {
            cursor = requireContext().getContentResolver().query(
                    uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "resolveDisplayName failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String lastSegment = uri.getLastPathSegment();
        return lastSegment != null ? lastSegment : "config";
    }

    private void bindConfigPreferences() {
        if (mConfigCategory == null) {
            return;
        }

        mConfigCategory.removeAll();
        List<PifManager.ConfigState> states = mPifManager.getConfigStates();
        for (PifManager.ConfigState state : states) {
            Preference preference = new Preference(requireContext());
            preference.setTitle(state.fileName);
            preference.setSummary(buildConfigPreferenceSummary(state));
            preference.setOnPreferenceClickListener(clicked -> {
                handleConfigPreferenceClick(state);
                return true;
            });
            mConfigCategory.addPreference(preference);
        }
    }

    private String buildConfigPreferenceSummary(PifManager.ConfigState state) {
        String status = state.isActive
                ? getString(R.string.pif_file_status_active)
                : state.exists
                        ? getString(R.string.pif_file_status_available)
                        : getString(R.string.pif_file_status_empty);
        return status + "\n" + buildConfigSummary(state);
    }

    private void handleConfigPreferenceClick(@NonNull PifManager.ConfigState state) {
        if (!state.exists) {
            mImportTargetFileName = state.fileName;
            openPifFilePicker();
            return;
        }

        String[] options = {
                getString(R.string.pif_replace_title),
                getString(R.string.action_delete)
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(state.fileName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        mImportTargetFileName = state.fileName;
                        openPifFilePicker();
                    } else {
                        mPifManager.deleteConfig(state.fileName);
                        refreshUi();
                        Toast.makeText(requireContext(),
                                getString(R.string.pif_deleted_named, state.fileName),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String buildConfigSummary(PifManager.ConfigState state) {
        if (!state.exists) {
            return getString(R.string.pif_file_empty_summary);
        }

        String model = state.data.get("MODEL");
        String patch = state.data.get("SECURITY_PATCH");
        if (model != null && patch != null && !patch.isEmpty()) {
            return getString(R.string.spoof_dashboard_pif_detail, model, patch);
        }
        if (model != null && !model.isEmpty()) {
            return model;
        }
        return getString(R.string.pif_file_present_summary);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VOLTAGE;
    }
}
