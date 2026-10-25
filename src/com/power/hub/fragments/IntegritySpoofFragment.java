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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.settings.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Play Integrity Fix and TrickyStore in one page. */
public class IntegritySpoofFragment extends Fragment {

    private static final String TAG = "IntegritySpoof";

    private static final String REQ_FETCH_SOURCE = "spoof_integrity_fetch_source";
    private static final String REQ_DEVICE = "spoof_integrity_device";
    private static final String REQ_PIF_PROPS = "spoof_integrity_props";
    private static final String REQ_TARGETS = "spoof_integrity_targets";
    private static final String REQ_PATCH = "spoof_integrity_patch";
    private static final String REQ_KEYBOX_DELETE = "spoof_integrity_keybox_delete";
    private static final String REQ_PROFILE_DELETE = "spoof_integrity_profile_delete";
    private static final String REQ_VERBOSE = "spoof_integrity_verbose";
    private static final String REQ_PIF_PRESETS = "spoof_integrity_pif_presets";
    private static final String REQ_TARGETS_DEFAULTS = "spoof_integrity_targets_defaults";
    private static final String SOURCE_CANARY = "canary";
    private static final String SOURCE_RETAIL = "retail";
    private static final String SOURCE_VOLTAGE = "voltage";
    private static final String[] DIAGNOSTIC_FLAGS = {
            "spoofBuild", "spoofProps", "spoofProvider", "spoofSignature",
            "spoofVendingBuild", "spoofVendingSdk", "DEBUG", "verboseLogs"};

    private PifManager mPifManager;
    private PifRepository mPifRepository;
    private KeyboxManager mKeyboxManager;

    private TextView mHeroSummary;
    private TextView mStatusPill;
    private TextView mProfileName;
    private TextView mProfileModel;
    private TextView mProfileFingerprint;
    private TextView mProfilePatch;
    private TextView mProfileWarning;
    private MaterialButton mFetchButton;
    private ProgressBar mFetchProgress;

    private TextView mKeyboxStatus;
    private TextView mKeyboxPill;
    private TextView mKeyboxDetail;
    private TextView mKeyboxAlgorithms;
    private TextView mKeyboxCertificates;
    private TextView mKeyboxValidity;
    private TextView mKeyboxExpiryWarning;
    private TextView mKeyboxSerial;
    private TextView mKeyboxRevocation;
    private TextView mKeyboxRevocationWarning;
    private View mRowKeyboxCheck;
    private View mRowKeyboxDetails;
    private MaterialButton mKeyboxDelete;
    private KeyboxRevocationChecker mRevocationChecker;
    private boolean mChecking;

    private TextView mTargetStatus;
    private TextView mTargetDistribution;

    private boolean mFetching;
    private List<PifRepository.PifDevice> mDevices = Collections.emptyList();
    private String mDeviceSource = SOURCE_CANARY;
    private String mDeviceApiKey;

    private final ActivityResultLauncher<Intent> mPifFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                importPifFile(uri);
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> mKeyboxFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                importKeyboxFile(uri);
                            }
                        }
                    });

    private final ActivityResultLauncher<String> mBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> {
                        if (uri != null) {
                            writeBackup(uri);
                        }
                    });

    private final ActivityResultLauncher<Intent> mRestoreLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                restoreBackup(uri);
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> mTargetFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                importTargetFile(uri);
                            }
                        }
                    });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPifManager = new PifManager(requireContext());
        mPifRepository = new PifRepository();
        mKeyboxManager = new KeyboxManager(requireContext());
        mRevocationChecker = new KeyboxRevocationChecker(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spoof_integrity, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mHeroSummary = view.findViewById(R.id.hero_summary);
        mStatusPill = view.findViewById(R.id.status_pill);
        mProfileName = view.findViewById(R.id.profile_name);
        mProfileModel = view.findViewById(R.id.profile_model);
        mProfileFingerprint = view.findViewById(R.id.profile_fingerprint);
        mProfilePatch = view.findViewById(R.id.profile_patch);
        mProfileWarning = view.findViewById(R.id.profile_warning);
        mFetchButton = view.findViewById(R.id.btn_fetch);
        mFetchProgress = view.findViewById(R.id.fetch_progress);

        mKeyboxStatus = view.findViewById(R.id.keybox_status);
        mKeyboxPill = view.findViewById(R.id.keybox_pill);
        mKeyboxDetail = view.findViewById(R.id.keybox_detail);
        mKeyboxAlgorithms = view.findViewById(R.id.keybox_algorithms);
        mKeyboxCertificates = view.findViewById(R.id.keybox_certificates);
        mKeyboxValidity = view.findViewById(R.id.keybox_validity);
        mKeyboxExpiryWarning = view.findViewById(R.id.keybox_expiry_warning);
        mKeyboxSerial = view.findViewById(R.id.keybox_serial);
        mKeyboxRevocation = view.findViewById(R.id.keybox_revocation);
        mKeyboxRevocationWarning = view.findViewById(R.id.keybox_revocation_warning);
        mRowKeyboxCheck = view.findViewById(R.id.row_keybox_check);
        mRowKeyboxDetails = view.findViewById(R.id.row_keybox_details);
        mKeyboxDelete = view.findViewById(R.id.btn_keybox_delete);

        mTargetStatus = view.findViewById(R.id.target_status);
        mTargetDistribution = view.findViewById(R.id.target_distribution);

        view.findViewById(R.id.btn_fetch).setOnClickListener(v -> showFetchSources());
        view.findViewById(R.id.btn_import).setOnClickListener(v -> openPifFilePicker());
        view.findViewById(R.id.btn_view_props).setOnClickListener(v -> showProperties());
        view.findViewById(R.id.btn_delete_profile).setOnClickListener(v -> confirmDeleteProfile());
        view.findViewById(R.id.btn_keybox_import).setOnClickListener(v -> openKeyboxFilePicker());
        mKeyboxDelete.setOnClickListener(v -> confirmDeleteKeybox());
        mRowKeyboxCheck.setOnClickListener(v -> runRevocationCheck(true));
        mRowKeyboxDetails.setOnClickListener(v -> showKeyboxDetails());
        view.findViewById(R.id.row_patch).setOnClickListener(v -> openPatchEditor());
        view.findViewById(R.id.row_targets_manage).setOnClickListener(v -> openTargetPicker());
        view.findViewById(R.id.row_targets_import).setOnClickListener(v -> openTargetFilePicker());
        view.findViewById(R.id.row_targets_defaults).setOnClickListener(
                v -> confirmRestoreDefaultTargets());
        view.findViewById(R.id.row_verbose).setOnClickListener(v -> showVerbosePicker());
        view.findViewById(R.id.row_pif_presets).setOnClickListener(v -> showPifPresets());
        view.findViewById(R.id.row_diagnostics).setOnClickListener(v -> showDiagnostics());
        view.findViewById(R.id.row_backup).setOnClickListener(v -> exportBackup());
        view.findViewById(R.id.row_restore).setOnClickListener(v -> importBackup());
        view.findViewById(R.id.btn_restart).setOnClickListener(v -> {
            mPifManager.restartPifTargets();
            Toast.makeText(requireContext(), R.string.pif_restart_targets_done,
                    Toast.LENGTH_SHORT).show();
        });

        setupCollapsible(view, R.id.header_options, R.id.options_container,
                R.string.spoof_section_options);
        setupCollapsible(view, R.id.header_targets, R.id.targets_container,
                R.string.spoof_section_targets);
        setupCollapsible(view, R.id.header_logging, R.id.logging_container,
                R.string.spoof_section_logging);
        setupCollapsible(view, R.id.header_apply, R.id.apply_container,
                R.string.spoof_section_apply);

        registerResultListeners();
        refreshUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
    }

    private void registerResultListeners() {
        FragmentManager manager = getChildFragmentManager();

        manager.setFragmentResultListener(REQ_FETCH_SOURCE, getViewLifecycleOwner(),
                (key, bundle) -> {
                    String id = bundle.getString(SpoofChoiceSheet.RESULT_ID);
                    if (SOURCE_CANARY.equals(id)) {
                        fetchProfile(SOURCE_CANARY);
                    } else if (SOURCE_RETAIL.equals(id)) {
                        fetchProfile(SOURCE_RETAIL);
                    } else if (SOURCE_VOLTAGE.equals(id)) {
                        fetchProfile(SOURCE_VOLTAGE);
                    }
                });

        manager.setFragmentResultListener(REQ_DEVICE, getViewLifecycleOwner(),
                (key, bundle) -> {
                    String id = bundle.getString(SpoofChoiceSheet.RESULT_ID);
                    if (id == null) {
                        return;
                    }
                    int index;
                    try {
                        index = Integer.parseInt(id);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    if (index < 0 || index >= mDevices.size()) {
                        return;
                    }
                    buildProfile(mDevices.get(index), mDeviceSource, mDeviceApiKey);
                });

        manager.setFragmentResultListener(REQ_PIF_PROPS, getViewLifecycleOwner(),
                (key, bundle) -> {
                    Serializable serialized =
                            bundle.getSerializable(PropertyEditorSheet.RESULT_PROPERTIES);
                    if (!(serialized instanceof Map)) {
                        return;
                    }
                    //noinspection unchecked
                    Map<String, String> props = (Map<String, String>) serialized;
                    try {
                        mPifManager.saveProperties(props);
                        alignPatchToProfile();
                        refreshUi();
                        Toast.makeText(requireContext(), R.string.pif_properties_saved,
                                Toast.LENGTH_SHORT).show();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(requireContext(), e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        View view = getView();
                        if (view != null) {
                            view.post(() -> {
                                if (isAdded()) {
                                    PropertyEditorSheet.newInstance(REQ_PIF_PROPS,
                                            getString(R.string.pif_current_props_title), props,
                                            null, false, true)
                                            .show(getChildFragmentManager(), "profile_props");
                                }
                            });
                        }
                    }
                });

        manager.setFragmentResultListener(REQ_TARGETS, getViewLifecycleOwner(),
                (key, bundle) -> {
                    if (bundle.getBoolean(SpoofAppPickerSheet.RESULT_CLOSED, false)) {
                        refreshUi();
                        return;
                    }
                    ArrayList<String> lines = bundle.getStringArrayList(
                            SpoofAppPickerSheet.RESULT_TARGETS);
                    if (lines != null) {
                        mKeyboxManager.saveTargetLines(lines);
                        refreshUi();
                    }
                });

        manager.setFragmentResultListener(REQ_PATCH, getViewLifecycleOwner(),
                (key, bundle) -> {
                    boolean auto = bundle.getBoolean(PatchEditorSheet.RESULT_AUTO, false);
                    mKeyboxManager.setPatchAuto(auto);
                    if (auto) {
                        alignPatchToProfile();
                    } else {
                        mKeyboxManager.savePatchConfig(
                                bundle.getString(PatchEditorSheet.RESULT_PATCH, ""));
                    }
                    refreshUi();
                    Toast.makeText(requireContext(),
                            bundle.getBoolean(PatchEditorSheet.RESULT_CLEARED)
                                    ? R.string.spoof_patch_cleared : R.string.spoof_patch_saved,
                            Toast.LENGTH_SHORT).show();
                });

        manager.setFragmentResultListener(REQ_VERBOSE, getViewLifecycleOwner(),
                (key, bundle) -> {
                    String level = bundle.getString(SpoofChoiceSheet.RESULT_ID);
                    if (level == null) {
                        return;
                    }
                    try {
                        mPifManager.setVerboseLogsLevel(Integer.parseInt(level));
                    } catch (NumberFormatException e) {
                        return;
                    }
                    refreshToggles();
                });

        manager.setFragmentResultListener(REQ_PIF_PRESETS, getViewLifecycleOwner(),
                (key, bundle) -> {
                    if (bundle.getBoolean(SpoofChoiceSheet.RESULT_ACTION, false)) {
                        saveCurrentPreset();
                        return;
                    }
                    String deleted = bundle.getString(SpoofChoiceSheet.RESULT_DELETE);
                    if (deleted != null) {
                        mPifManager.deletePreset(deleted);
                        refreshUi();
                        Toast.makeText(requireContext(), R.string.spoof_pif_preset_deleted,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String name = bundle.getString(SpoofChoiceSheet.RESULT_ID);
                    if (name == null) {
                        return;
                    }
                    if (mPifManager.applyPreset(name)) {
                        alignPatchToProfile();
                        refreshUi();
                        Toast.makeText(requireContext(),
                                getString(R.string.spoof_pif_preset_applied, name),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        manager.setFragmentResultListener(REQ_TARGETS_DEFAULTS, getViewLifecycleOwner(),
                (key, bundle) -> {
                    if (!bundle.getBoolean(SpoofConfirmSheet.RESULT_CONFIRMED)) {
                        return;
                    }
                    mKeyboxManager.restoreDefaultTargets();
                    refreshUi();
                    Toast.makeText(requireContext(),
                            R.string.spoof_restore_default_targets_done,
                            Toast.LENGTH_SHORT).show();
                });

        manager.setFragmentResultListener(REQ_KEYBOX_DELETE, getViewLifecycleOwner(),
                (key, bundle) -> {
                    if (bundle.getBoolean(SpoofConfirmSheet.RESULT_CONFIRMED)) {
                        mKeyboxManager.deleteKeybox();
                        mRevocationChecker.clear();
                        refreshUi();
                        Toast.makeText(requireContext(), R.string.keybox_deleted,
                                Toast.LENGTH_SHORT).show();
                    }
                });

        manager.setFragmentResultListener(REQ_PROFILE_DELETE, getViewLifecycleOwner(),
                (key, bundle) -> {
                    if (bundle.getBoolean(SpoofConfirmSheet.RESULT_CONFIRMED)) {
                        String fileName = activeFileName();
                        if (!fileName.isEmpty()) {
                            mPifManager.deleteConfig(fileName);
                            refreshUi();
                            Toast.makeText(requireContext(),
                                    getString(R.string.pif_deleted_named, fileName),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void refreshUi() {
        if (!isAdded()) {
            return;
        }
        alignPatchToProfile();
        refreshHero();
        refreshProfile();
        refreshToggles();
        refreshKeybox();
        refreshTargets();
        refreshPatchRow();
        maybeAutoCheckRevocation();
    }

    private void refreshHero() {
        String activeConfig = mPifManager.getActiveConfigName();
        Map<String, String> props = mPifManager.getCurrentProperties();
        String model = props.get("MODEL");
        if (model == null || model.isEmpty()) {
            model = getString(R.string.pif_no_props);
        }

        boolean keyboxExists = mKeyboxManager.keyboxExists();
        KeyboxManager.KeyboxInfo info = mKeyboxManager.getKeyboxInfo();
        String keyboxState;
        if (!keyboxExists) {
            keyboxState = getString(R.string.keybox_not_found);
        } else if (!info.valid) {
            keyboxState = getString(R.string.keybox_status_invalid);
        } else if (info.expired) {
            keyboxState = getString(R.string.keybox_status_expired);
        } else {
            keyboxState = getString(R.string.keybox_status_active);
        }
        int targets = mKeyboxManager.getTargetAppCount();
        mHeroSummary.setText(getString(R.string.spoof_hero_summary, model, keyboxState, targets));

        List<String> warnings = mPifManager.getConfigWarnings();
        KeyboxRevocationChecker.Result revocation = mRevocationChecker.getLastResult();
        if (activeConfig.isEmpty()) {
            SpoofUi.bindStatusPill(mStatusPill, getString(R.string.spoof_status_no_profile),
                    SpoofUi.TONE_MUTED);
        } else if (keyboxExists && (!info.valid || info.expired)) {
            SpoofUi.bindStatusPill(mStatusPill, getString(R.string.spoof_status_keybox_issue),
                    SpoofUi.TONE_ERROR);
        } else if (keyboxExists && revocation.isProblem()) {
            SpoofUi.bindStatusPill(mStatusPill, getString(R.string.spoof_status_keybox_issue),
                    SpoofUi.TONE_ERROR);
        } else if (keyboxExists
                && revocation.status == KeyboxRevocationChecker.Status.EXPIRING_SOON) {
            SpoofUi.bindStatusPill(mStatusPill, getString(R.string.keybox_revocation_expiring),
                    SpoofUi.TONE_MUTED);
        } else if (!warnings.isEmpty()) {
            SpoofUi.bindStatusPill(mStatusPill, getString(R.string.spoof_status_setup_required),
                    SpoofUi.TONE_ERROR);
        } else {
            SpoofUi.bindStatusPill(mStatusPill, getString(R.string.spoof_status_ready),
                    SpoofUi.TONE_ACCENT);
        }
    }

    private void refreshProfile() {
        String activeConfig = mPifManager.getActiveConfigName();
        Map<String, String> props = mPifManager.getCurrentProperties();
        boolean hasProfile = !activeConfig.isEmpty();

        mProfileName.setText(hasProfile
                ? getString(R.string.pif_active_config, activeConfig)
                : getString(R.string.pif_no_config_loaded));
        mProfileModel.setVisibility(hasProfile ? View.VISIBLE : View.GONE);
        mProfileFingerprint.setVisibility(hasProfile ? View.VISIBLE : View.GONE);
        mProfilePatch.setVisibility(hasProfile ? View.VISIBLE : View.GONE);
        View viewProps = getView().findViewById(R.id.btn_view_props);
        View deleteProfile = getView().findViewById(R.id.btn_delete_profile);
        viewProps.setVisibility(hasProfile ? View.VISIBLE : View.GONE);
        deleteProfile.setVisibility(hasProfile ? View.VISIBLE : View.GONE);

        if (!hasProfile) {
            mProfileWarning.setVisibility(View.GONE);
            mProfileModel.setText("");
            mProfileFingerprint.setText("");
            mProfilePatch.setText("");
            return;
        }

        String model = props.getOrDefault("MODEL", getString(R.string.pif_no_props));
        String fingerprint = props.getOrDefault("FINGERPRINT",
                getString(R.string.pif_not_available));
        String patch = props.getOrDefault("SECURITY_PATCH",
                getString(R.string.pif_not_available));
        mProfileModel.setText(getString(R.string.pif_model_label, model));
        mProfileFingerprint.setText(getString(R.string.pif_fingerprint_label, fingerprint));
        mProfilePatch.setText(patchLabel(patch));

        List<String> warnings = mPifManager.getConfigWarnings();
        if (warnings.isEmpty()) {
            mProfileWarning.setVisibility(View.GONE);
        } else {
            mProfileWarning.setText(getString(R.string.pif_config_warnings,
                    TextUtils.join(", ", warnings)));
            mProfileWarning.setVisibility(View.VISIBLE);
        }
    }

    private CharSequence patchLabel(String patch) {
        if (patch == null || patch.isEmpty() || patch.equals(getString(R.string.pif_not_available))) {
            return getString(R.string.pif_security_patch_label, patch);
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            Date date = format.parse(patch);
            long days = (System.currentTimeMillis() - date.getTime()) / 86400000L;
            if (days >= 0) {
                return getString(R.string.pif_security_patch_age, patch, days);
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.pif_security_patch_label, patch);
    }

    private void refreshToggles() {
        View view = getView();
        if (view == null) {
            return;
        }
        boolean hasProfile = !mPifManager.getActiveConfigName().isEmpty();
        bindSwitch(view.findViewById(R.id.row_photos), R.string.pif_spoof_photos_title,
                R.string.pif_spoof_photos_summary, mPifManager.isSpoofPhotosEnabled(), true,
                mPifManager::setSpoofPhotos);
        bindSwitch(view.findViewById(R.id.row_props), R.string.pif_spoof_props_title,
                R.string.pif_spoof_props_summary, mPifManager.isSpoofPropsEnabled(), hasProfile,
                mPifManager::setSpoofProps);
        bindSwitch(view.findViewById(R.id.row_build), R.string.pif_spoof_build_title,
                R.string.pif_spoof_build_summary, mPifManager.isSpoofBuildEnabled(), hasProfile,
                mPifManager::setSpoofBuild);
        bindSwitch(view.findViewById(R.id.row_signature), R.string.pif_spoof_signature_title,
                R.string.pif_spoof_signature_summary, mPifManager.isSpoofSignatureEnabled(),
                hasProfile, mPifManager::setSpoofSignature);
        bindSwitch(view.findViewById(R.id.row_vending), R.string.pif_spoof_vending_build_title,
                R.string.pif_spoof_vending_build_summary,
                mPifManager.isSpoofVendingBuildEnabled(), hasProfile,
                mPifManager::setSpoofVendingBuild);
        bindSwitch(view.findViewById(R.id.row_provider), R.string.pif_spoof_provider_title,
                R.string.pif_spoof_provider_summary,
                mPifManager.isSpoofProviderEnabled(), hasProfile,
                mPifManager::setSpoofProvider);
        bindSwitch(view.findViewById(R.id.row_debug), R.string.pif_debug_title,
                R.string.pif_debug_summary, mPifManager.isDebugEnabled(), hasProfile,
                mPifManager::setDebugEnabled);

        View verboseRow = view.findViewById(R.id.row_verbose);
        SpoofUi.bindActionRow(verboseRow, 0,
                getString(R.string.pif_verbose_title), verboseSummary());
        verboseRow.setAlpha(hasProfile ? 1f : 0.45f);
        verboseRow.setEnabled(hasProfile);

        SpoofUi.bindActionRow(view.findViewById(R.id.row_pif_presets), R.drawable.ic_shortcuts,
                getString(R.string.spoof_pif_presets_title),
                getString(R.string.spoof_pif_presets_summary));
        SpoofUi.bindActionRow(view.findViewById(R.id.row_diagnostics), 0,
                getString(R.string.spoof_diagnostics_title),
                getString(R.string.spoof_diagnostics_summary));
        SpoofUi.bindActionRow(view.findViewById(R.id.row_backup),
                R.drawable.ic_wakelockblocker_save, getString(R.string.spoof_backup_title),
                getString(R.string.spoof_backup_summary));
        SpoofUi.bindActionRow(view.findViewById(R.id.row_restore), R.drawable.ic_recovery,
                getString(R.string.spoof_restore_title),
                getString(R.string.spoof_restore_summary));
    }

    private void setupCollapsible(View view, int headerId, int contentId, int titleRes) {
        View header = view.findViewById(headerId);
        View content = view.findViewById(contentId);
        TextView title = header.findViewById(R.id.row_section_title);
        View chevron = header.findViewById(R.id.row_section_chevron);
        title.setText(titleRes);
        chevron.setRotation(content.getVisibility() == View.VISIBLE ? 90f : 0f);
        header.setOnClickListener(v -> {
            boolean expand = content.getVisibility() != View.VISIBLE;
            content.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(expand ? 90f : 0f).setDuration(150).start();
        });
    }

    private CharSequence verboseSummary() {
        switch (mPifManager.getVerboseLogsLevel()) {
            case 1:
                return getString(R.string.pif_verbose_build);
            case 2:
                return getString(R.string.pif_verbose_props);
            case 100:
                return getString(R.string.pif_verbose_all);
            default:
                return getString(R.string.pif_verbose_off);
        }
    }

    private void bindSwitch(View row, int titleRes, int summaryRes, boolean checked,
            boolean enabled, ToggleSetter setter) {
        SpoofUi.bindSwitchRow(row, getString(titleRes), getString(summaryRes), checked, enabled);
        MaterialSwitch toggle = row.findViewById(R.id.row_switch);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> setter.set(isChecked));
    }

    private interface ToggleSetter {
        void set(boolean enabled);
    }

    private void refreshKeybox() {
        boolean exists = mKeyboxManager.keyboxExists();
        KeyboxManager.KeyboxInfo info = mKeyboxManager.getKeyboxInfo();
        KeyboxRevocationChecker.Result revocation = mRevocationChecker.getLastResult();
        mKeyboxDelete.setVisibility(exists ? View.VISIBLE : View.GONE);

        if (!exists) {
            mKeyboxStatus.setText(R.string.keybox_not_found);
            SpoofUi.bindStatusPill(mKeyboxPill, getString(R.string.spoof_status_none),
                    SpoofUi.TONE_MUTED);
            mKeyboxDetail.setText(R.string.keybox_absent_detail);
            setKeyboxDetailsVisible(false);
            mKeyboxExpiryWarning.setVisibility(View.GONE);
            mKeyboxSerial.setText("");
            mKeyboxRevocation.setText("");
            mKeyboxRevocationWarning.setVisibility(View.GONE);
            mRowKeyboxCheck.setVisibility(View.GONE);
            mRowKeyboxDetails.setVisibility(View.GONE);
            return;
        }
        if (!info.valid) {
            mKeyboxStatus.setText(R.string.keybox_status_invalid);
            SpoofUi.bindStatusPill(mKeyboxPill, getString(R.string.spoof_status_keybox_issue),
                    SpoofUi.TONE_ERROR);
            mKeyboxDetail.setText(info.error != null ? info.error
                    : getString(R.string.keybox_error_invalid));
            setKeyboxDetailsVisible(false);
            mKeyboxExpiryWarning.setVisibility(View.GONE);
            mKeyboxSerial.setText("");
            mKeyboxRevocation.setText("");
            mKeyboxRevocationWarning.setVisibility(View.GONE);
            mRowKeyboxCheck.setVisibility(View.GONE);
            mRowKeyboxDetails.setVisibility(View.GONE);
            return;
        }

        mKeyboxStatus.setText(info.expired
                ? R.string.keybox_status_expired : R.string.keybox_status_active);
        if (revocation.isProblem()) {
            SpoofUi.bindStatusPill(mKeyboxPill, getString(R.string.spoof_status_keybox_issue),
                    SpoofUi.TONE_ERROR);
        } else if (revocation.status == KeyboxRevocationChecker.Status.EXPIRING_SOON) {
            SpoofUi.bindStatusPill(mKeyboxPill, getString(R.string.keybox_revocation_expiring),
                    SpoofUi.TONE_MUTED);
        } else {
            SpoofUi.bindStatusPill(mKeyboxPill,
                    info.expired ? getString(R.string.spoof_status_keybox_issue)
                            : getString(R.string.keybox_status_active),
                    info.expired ? SpoofUi.TONE_ERROR : SpoofUi.TONE_ACCENT);
        }
        mKeyboxDetail.setText(R.string.keybox_installed);
        setKeyboxDetailsVisible(true);
        mKeyboxAlgorithms.setText(getString(R.string.keybox_detail_algorithms,
                info.algorithms.isEmpty() ? "-" : TextUtils.join(", ", info.algorithms)));
        mKeyboxCertificates.setText(getString(R.string.keybox_detail_certificates,
                info.certificateCount));
        if (info.leaves.isEmpty()) {
            mKeyboxSerial.setText("");
            mKeyboxSerial.setVisibility(View.GONE);
        } else {
            List<String> serials = new ArrayList<>();
            for (KeyboxManager.KeyboxCertificate leaf : info.leaves) {
                serials.add(leaf.serialHex);
            }
            mKeyboxSerial.setText(getString(R.string.keybox_detail_serial,
                    TextUtils.join(", ", serials)));
            mKeyboxSerial.setVisibility(View.VISIBLE);
        }
        if (info.notAfter != null) {
            String date = SpoofUi.formatDate(info.notAfter);
            mKeyboxValidity.setText(info.expired
                    ? getString(R.string.keybox_detail_expired, date)
                    : getString(R.string.keybox_detail_valid_until, date));
            long days = (info.notAfter.getTime() - System.currentTimeMillis()) / 86400000L;
            if (!info.expired && days <= 14) {
                mKeyboxExpiryWarning.setText(getString(R.string.spoof_keybox_expiring_soon,
                        Math.max(days, 0)));
                mKeyboxExpiryWarning.setVisibility(View.VISIBLE);
            } else {
                mKeyboxExpiryWarning.setVisibility(View.GONE);
            }
        } else {
            mKeyboxValidity.setText("");
            mKeyboxExpiryWarning.setVisibility(View.GONE);
        }
        mRowKeyboxCheck.setVisibility(View.VISIBLE);
        mRowKeyboxDetails.setVisibility(info.certificates.isEmpty() ? View.GONE : View.VISIBLE);
        bindRevocation(revocation);
    }

    private void bindRevocation(KeyboxRevocationChecker.Result result) {
        String statusText = revocationStatusText(result.status);
        if (result.checkedAt > 0 && result.status != KeyboxRevocationChecker.Status.UNKNOWN) {
            mKeyboxRevocation.setText(getString(R.string.keybox_revocation_summary, statusText,
                    SpoofUi.formatDate(new Date(result.checkedAt))));
        } else {
            mKeyboxRevocation.setText(getString(R.string.keybox_revocation_none, statusText));
        }
        mKeyboxRevocation.setTextColor(ContextCompat.getColor(requireContext(),
                result.isProblem() ? R.color.powerhub_spoof_error
                        : R.color.powerhub_spoof_text_secondary));
        if (result.isProblem()) {
            StringBuilder warning = new StringBuilder(statusText);
            if (!TextUtils.isEmpty(result.reason)) {
                warning.append(" (").append(result.reason).append(")");
            }
            if (result.offline) {
                warning.append(" · ").append(getString(R.string.keybox_revocation_offline));
            }
            mKeyboxRevocationWarning.setText(warning);
            mKeyboxRevocationWarning.setVisibility(View.VISIBLE);
        } else if (result.offline) {
            mKeyboxRevocationWarning.setText(R.string.keybox_revocation_offline);
            mKeyboxRevocationWarning.setVisibility(View.VISIBLE);
        } else {
            mKeyboxRevocationWarning.setVisibility(View.GONE);
        }

        SpoofUi.bindActionRow(mRowKeyboxCheck, R.drawable.ic_run,
                getString(R.string.spoof_keybox_check_now),
                getString(mChecking ? R.string.spoof_keybox_checking
                        : R.string.spoof_keybox_check_summary));
        SpoofUi.bindActionRow(mRowKeyboxDetails, R.drawable.ic_signature,
                getString(R.string.spoof_keybox_view_details), null);
    }

    private String revocationStatusText(KeyboxRevocationChecker.Status status) {
        switch (status) {
            case VALID:
                return getString(R.string.keybox_revocation_valid);
            case REVOKED:
                return getString(R.string.keybox_revocation_revoked);
            case SUSPENDED:
                return getString(R.string.keybox_revocation_suspended);
            case EXPIRING_SOON:
                return getString(R.string.keybox_revocation_expiring);
            case EXPIRED:
                return getString(R.string.keybox_revocation_expired);
            case CHAIN_INVALID:
                return getString(R.string.keybox_revocation_chain_invalid);
            case UNTRUSTED_ROOT:
                return getString(R.string.keybox_revocation_untrusted);
            case UNVERIFIED:
                return getString(R.string.keybox_revocation_unverified);
            case UNKNOWN:
            default:
                return getString(R.string.keybox_revocation_not_checked);
        }
    }

    private void maybeAutoCheckRevocation() {
        if (mChecking || !mKeyboxManager.keyboxExists()) {
            return;
        }
        if (!mRevocationChecker.shouldAutoCheck()) {
            return;
        }
        runRevocationCheck(false);
    }

    private void runRevocationCheck(boolean manual) {
        if (mChecking) {
            return;
        }
        byte[] keybox = mKeyboxManager.getKeyboxBytes();
        if (keybox == null) {
            return;
        }
        mChecking = true;
        refreshKeybox();
        new Thread(() -> {
            mRevocationChecker.check(keybox);
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                mChecking = false;
                if (!isAdded() || getView() == null) {
                    return;
                }
                refreshKeybox();
                refreshHero();
                if (manual) {
                    Toast.makeText(requireContext(), R.string.spoof_keybox_checked,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }, "SpoofKeyboxCheck").start();
    }

    private void showKeyboxDetails() {
        KeyboxManager.KeyboxInfo info = mKeyboxManager.getKeyboxInfo();
        if (!info.valid || info.certificates.isEmpty()) {
            return;
        }
        ArrayList<SpoofDetailsSheet.Item> items = new ArrayList<>();
        int index = 0;
        for (KeyboxManager.KeyboxCertificate cert : info.certificates) {
            index++;
            String label = "#" + index + " · "
                    + (cert.algorithm == null ? "-" : cert.algorithm) + " · "
                    + shortSha(cert.sha256);
            String value = getString(R.string.keybox_cert_detail_subject, cert.subject)
                    + "\n" + getString(R.string.keybox_cert_detail_issuer, cert.issuer)
                    + "\n" + getString(R.string.keybox_cert_detail_serial, cert.serialHex)
                    + "\n" + getString(R.string.keybox_cert_detail_validity,
                            SpoofUi.formatDate(cert.notBefore), SpoofUi.formatDate(cert.notAfter));
            items.add(new SpoofDetailsSheet.Item(label, value));
        }
        SpoofDetailsSheet.newInstance(getString(R.string.keybox_cert_details_title), null, items)
                .show(getChildFragmentManager(), "keybox_details");
    }

    private static String shortSha(String sha) {
        if (sha == null || sha.isEmpty()) {
            return "-";
        }
        return sha.length() > 16 ? sha.substring(0, 16) + "…" : sha;
    }

    private void setKeyboxDetailsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        mKeyboxAlgorithms.setVisibility(visibility);
        mKeyboxCertificates.setVisibility(visibility);
        mKeyboxValidity.setVisibility(visibility);
    }

    private void refreshTargets() {
        List<String> targets = mKeyboxManager.readTargetLines();
        int auto = 0;
        int leaf = 0;
        int gen = 0;
        int skip = 0;
        for (String line : targets) {
            char last = line.isEmpty() ? ' ' : line.charAt(line.length() - 1);
            if (last == '?') {
                leaf++;
            } else if (last == '!') {
                gen++;
            } else if (last == '-') {
                skip++;
            } else {
                auto++;
            }
        }
        mTargetStatus.setText(targets.isEmpty()
                ? getString(R.string.target_no_apps)
                : getString(R.string.target_apps_count, targets.size()));
        if (targets.isEmpty()) {
            mTargetDistribution.setVisibility(View.GONE);
        } else {
            mTargetDistribution.setText(getString(R.string.target_mode_distribution,
                    auto, leaf, gen, skip));
            mTargetDistribution.setVisibility(View.VISIBLE);
        }

        View view = getView();
        if (view == null) {
            return;
        }
        SpoofUi.bindActionRow(view.findViewById(R.id.row_targets_manage),
                R.drawable.ic_apps, getString(R.string.target_manage_title),
                getString(R.string.spoof_targets_manage_summary));
        SpoofUi.bindActionRow(view.findViewById(R.id.row_targets_import),
                R.drawable.ic_upload, getString(R.string.target_import_title),
                getString(R.string.target_import_summary));
        SpoofUi.bindActionRow(view.findViewById(R.id.row_targets_defaults),
                R.drawable.ic_menu_reset,
                getString(R.string.spoof_restore_default_targets_title),
                getString(R.string.spoof_restore_default_targets_summary));
    }

    private void refreshPatchRow() {
        View view = getView();
        if (view == null) {
            return;
        }
        CharSequence summary;
        if (mKeyboxManager.isPatchAuto()) {
            String patch = mPifManager.getCurrentProperties().get("SECURITY_PATCH");
            summary = getString(R.string.spoof_patch_auto_row,
                    patch == null || patch.trim().isEmpty()
                            ? getString(R.string.pif_not_available) : patch.trim());
        } else {
            summary = SpoofUi.patchSummary(requireContext(), mKeyboxManager.getPatchConfig());
        }
        SpoofUi.bindActionRow(view.findViewById(R.id.row_patch), R.drawable.ic_settings_device,
                getString(R.string.spoof_patch_title), summary);
    }

    private String activeFileName() {
        List<PifManager.ConfigState> states = mPifManager.getConfigStates();
        if (states.isEmpty()) {
            return "";
        }
        PifManager.ConfigState state = states.get(0);
        return state.exists ? state.fileName : "";
    }

    private void showFetchSources() {
        ArrayList<SpoofChoiceSheet.Item> items = new ArrayList<>();
        items.add(new SpoofChoiceSheet.Item(SOURCE_CANARY,
                getString(R.string.spoof_fetch_source_canary),
                getString(R.string.spoof_fetch_source_canary_detail)));
        items.add(new SpoofChoiceSheet.Item(SOURCE_RETAIL,
                getString(R.string.spoof_fetch_source_retail),
                getString(R.string.spoof_fetch_source_retail_detail)));
        items.add(new SpoofChoiceSheet.Item(SOURCE_VOLTAGE,
                getString(R.string.spoof_fetch_source_voltage),
                getString(R.string.spoof_fetch_source_voltage_detail)));
        SpoofChoiceSheet.newInstance(REQ_FETCH_SOURCE,
                getString(R.string.spoof_fetch_source_title), null, items, null)
                .show(getChildFragmentManager(), "fetch_source");
    }

    private void fetchProfile(String source) {
        if (mFetching) {
            return;
        }
        mFetching = true;
        if (SOURCE_VOLTAGE.equals(source)) {
            setFetchBusy(true, R.string.pif_fetching_voltage);
            new Thread(() -> {
                PifRepository.PifResult result = mPifRepository.fetchVoltagePif();
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(() -> finishFetch(result));
            }, "SpoofPifFetch").start();
            return;
        }

        setFetchBusy(true, SOURCE_RETAIL.equals(source)
                ? R.string.pif_fetching_retail : R.string.pif_fetching);
        new Thread(() -> {
            PifRepository.DeviceList list = mPifRepository.fetchDevices(source);
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                mFetching = false;
                setFetchBusy(false, 0);
                if (!isAdded() || getView() == null) {
                    return;
                }
                if (list.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.pif_no_devices_found,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (SOURCE_CANARY.equals(source)
                        && (list.apiKey == null || list.apiKey.isEmpty())) {
                    Toast.makeText(requireContext(), R.string.pif_no_flash_key,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                mDevices = list.devices;
                mDeviceSource = source;
                mDeviceApiKey = list.apiKey;
                showDevicePicker(list.devices);
            });
        }, "SpoofPifDevices").start();
    }

    private void showDevicePicker(List<PifRepository.PifDevice> devices) {
        String current = mPifManager.getCurrentProperties().get("DEVICE");
        ArrayList<SpoofChoiceSheet.Item> items = new ArrayList<>();
        for (int i = 0; i < devices.size(); i++) {
            PifRepository.PifDevice device = devices.get(i);
            String subtitle = device.device;
            if (subtitle.equals(current)) {
                subtitle = getString(R.string.pif_device_current, subtitle);
            }
            items.add(new SpoofChoiceSheet.Item(String.valueOf(i), device.model, subtitle));
        }
        SpoofChoiceSheet.newInstance(REQ_DEVICE, getString(R.string.pif_fetch_devices_title),
                null, items, null).show(getChildFragmentManager(), "device_picker");
    }

    private void buildProfile(PifRepository.PifDevice device, String source, String apiKey) {
        if (mFetching) {
            return;
        }
        mFetching = true;
        setFetchBusy(true, R.string.pif_fetching);
        new Thread(() -> {
            PifRepository.PifResult result = mPifRepository.buildProfile(source, device, apiKey);
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> finishFetch(result));
        }, "SpoofPifFetch").start();
    }

    private void finishFetch(PifRepository.PifResult result) {
        mFetching = false;
        if (!isAdded() || getView() == null) {
            return;
        }
        setFetchBusy(false, 0);
        if (result instanceof PifRepository.PifResult.Success) {
            PifRepository.PifResult.Success success = (PifRepository.PifResult.Success) result;
            try {
                String targetFile = mPifManager.writeAutoSelectedJsonConfig(success.pifData);
                alignPatchToProfile();
                refreshUi();
                Toast.makeText(requireContext(),
                        getString(R.string.pif_fetch_success_named, success.model, targetFile),
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "write fetched profile failed", e);
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
    }

    private void setFetchBusy(boolean busy, int labelRes) {
        mFetchButton.setEnabled(!busy);
        mFetchButton.setText(busy ? labelRes : R.string.spoof_fetch_profile);
        mFetchProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void alignPatchToProfile() {
        if (!mKeyboxManager.isPatchAuto()) {
            return;
        }
        String patch = mPifManager.getCurrentProperties().get("SECURITY_PATCH");
        String desired = patch == null || patch.trim().isEmpty() ? "" : "all=" + patch.trim();
        if (desired.equals(mKeyboxManager.getPatchConfig().trim())) {
            return;
        }
        mKeyboxManager.setPatchAuto(true);
        mKeyboxManager.savePatchConfig(desired.isEmpty() ? null : desired);
    }

    private void openPifFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
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
            String imported = mPifManager.importPifConfig(null, fileName, content);
            alignPatchToProfile();
            refreshUi();
            Toast.makeText(requireContext(),
                    getString(R.string.pif_import_success_named, imported),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "importPifFile error", e);
            Toast.makeText(requireContext(),
                    getString(R.string.pif_import_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showProperties() {
        Map<String, String> props = mPifManager.getCurrentProperties();
        if (props.isEmpty()) {
            Toast.makeText(requireContext(), R.string.pif_no_props, Toast.LENGTH_SHORT).show();
            return;
        }
        PropertyEditorSheet.newInstance(REQ_PIF_PROPS,
                getString(R.string.pif_current_props_title), props, null, false, true)
                .show(getChildFragmentManager(), "profile_props");
    }

    private void showVerbosePicker() {
        ArrayList<SpoofChoiceSheet.Item> items = new ArrayList<>();
        items.add(new SpoofChoiceSheet.Item("0", getString(R.string.pif_verbose_off),
                getString(R.string.pif_verbose_off_summary)));
        items.add(new SpoofChoiceSheet.Item("1", getString(R.string.pif_verbose_build),
                getString(R.string.pif_verbose_build_summary)));
        items.add(new SpoofChoiceSheet.Item("2", getString(R.string.pif_verbose_props),
                getString(R.string.pif_verbose_props_summary)));
        items.add(new SpoofChoiceSheet.Item("100", getString(R.string.pif_verbose_all),
                getString(R.string.pif_verbose_all_summary)));
        SpoofChoiceSheet.newInstance(REQ_VERBOSE, getString(R.string.pif_verbose_title),
                null, items, null).show(getChildFragmentManager(), "verbose");
    }

    private void showPifPresets() {
        List<PifManager.Preset> presets = mPifManager.getPresets();
        ArrayList<SpoofChoiceSheet.Item> items = new ArrayList<>();
        for (PifManager.Preset preset : presets) {
            String model = preset.props.getOrDefault("MODEL", "");
            items.add(new SpoofChoiceSheet.Item(preset.name, preset.name,
                    model.isEmpty() ? null : model, 0, false, true));
        }
        String action = mPifManager.getActiveConfigName().isEmpty()
                ? null : getString(R.string.spoof_pif_preset_save);
        SpoofChoiceSheet.newInstance(REQ_PIF_PRESETS,
                getString(R.string.spoof_pif_presets_title),
                presets.isEmpty() ? getString(R.string.spoof_pif_presets_empty) : null,
                items, action).show(getChildFragmentManager(), "pif_presets");
    }

    private void saveCurrentPreset() {
        Map<String, String> props = mPifManager.getCurrentProperties();
        if (props.isEmpty()) {
            return;
        }
        String name = props.getOrDefault("MODEL", "").trim();
        if (name.isEmpty()) {
            name = activeFileName();
        }
        if (name.isEmpty()) {
            return;
        }
        mPifManager.savePreset(name, props);
        refreshUi();
        Toast.makeText(requireContext(),
                getString(R.string.spoof_pif_preset_saved, name), Toast.LENGTH_SHORT).show();
    }

    private void confirmRestoreDefaultTargets() {
        SpoofConfirmSheet.newInstance(REQ_TARGETS_DEFAULTS,
                getString(R.string.spoof_restore_defaults_title),
                getString(R.string.spoof_restore_defaults_message),
                getString(R.string.spoof_restore_defaults_action))
                .show(getChildFragmentManager(), "targets_defaults");
    }

    private void showDiagnostics() {
        Map<String, String> props = mPifManager.getCurrentProperties();
        String activeConfig = mPifManager.getActiveConfigName();
        ArrayList<SpoofDetailsSheet.Item> items = new ArrayList<>();
        StringBuilder copy = new StringBuilder();

        String profile = activeConfig.isEmpty()
                ? getString(R.string.pif_no_config_loaded)
                : getString(R.string.pif_active_config, activeConfig);
        addDiagnostic(items, copy, getString(R.string.spoof_diagnostics_profile, profile));

        StringBuilder flags = new StringBuilder();
        for (String key : DIAGNOSTIC_FLAGS) {
            if (flags.length() > 0) {
                flags.append(" · ");
            }
            flags.append(key).append('=').append(props.getOrDefault(key, "-"));
        }
        addDiagnostic(items, copy, getString(R.string.spoof_diagnostics_flags, flags));

        String fingerprint = props.get("FINGERPRINT");
        if (PifManager.isValidFingerprint(fingerprint)) {
            for (Map.Entry<String, String> entry
                    : PifManager.fingerprintFields(fingerprint).entrySet()) {
                addDiagnostic(items, copy, getString(R.string.spoof_diagnostics_expanded,
                        entry.getKey(), entry.getValue()));
            }
        }

        addDiagnostic(items, copy, getString(R.string.spoof_diagnostics_sdk,
                props.getOrDefault("DEVICE_INITIAL_SDK_INT",
                        getString(R.string.pif_not_available))));

        KeyboxManager.KeyboxInfo info = mKeyboxManager.getKeyboxInfo();
        String keybox;
        if (!mKeyboxManager.keyboxExists()) {
            keybox = getString(R.string.keybox_not_found);
        } else if (!info.valid) {
            keybox = getString(R.string.keybox_status_invalid);
        } else if (info.expired) {
            keybox = getString(R.string.keybox_status_expired);
        } else {
            keybox = getString(R.string.keybox_status_active);
        }
        addDiagnostic(items, copy, getString(R.string.spoof_diagnostics_keybox, keybox));

        int targets = mKeyboxManager.getTargetAppCount();
        addDiagnostic(items, copy, getString(R.string.spoof_diagnostics_targets,
                targets == 0 ? getString(R.string.target_no_apps)
                        : getString(R.string.target_apps_count, targets)));

        CharSequence patch = mKeyboxManager.isPatchAuto()
                ? getString(R.string.spoof_patch_auto_row,
                        props.getOrDefault("SECURITY_PATCH",
                                getString(R.string.pif_not_available)))
                : SpoofUi.patchSummary(requireContext(), mKeyboxManager.getPatchConfig());
        addDiagnostic(items, copy, getString(R.string.spoof_diagnostics_patch, patch));

        SpoofDetailsSheet.newInstance(getString(R.string.spoof_diagnostics_title), null, items,
                copy.toString()).show(getChildFragmentManager(), "diagnostics");
    }

    private static void addDiagnostic(ArrayList<SpoofDetailsSheet.Item> items,
            StringBuilder copy, String line) {
        items.add(new SpoofDetailsSheet.Item(line, ""));
        if (copy.length() > 0) {
            copy.append('\n');
        }
        copy.append(line);
    }

    private void exportBackup() {
        String name = "powerhub-spoof-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                + ".json";
        mBackupLauncher.launch(name);
    }

    private void writeBackup(@NonNull Uri uri) {
        try (OutputStream stream = requireContext().getContentResolver().openOutputStream(uri)) {
            if (stream == null) {
                throw new IllegalStateException("Unable to open file");
            }
            stream.write(SpoofBackup.export(requireContext())
                    .getBytes(StandardCharsets.UTF_8));
            Toast.makeText(requireContext(), R.string.spoof_backup_saved,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "writeBackup error", e);
            Toast.makeText(requireContext(),
                    getString(R.string.spoof_backup_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"application/json", "text/plain"});
        mRestoreLauncher.launch(intent);
    }

    private void restoreBackup(@NonNull Uri uri) {
        try (InputStream stream = requireContext().getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IllegalStateException("Unable to open file");
            }
            int count = SpoofBackup.restore(requireContext(),
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            mRevocationChecker.clear();
            refreshUi();
            Toast.makeText(requireContext(),
                    getString(R.string.spoof_restore_success, count),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "restoreBackup error", e);
            Toast.makeText(requireContext(),
                    getString(R.string.spoof_restore_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDeleteProfile() {
        SpoofConfirmSheet.newInstance(REQ_PROFILE_DELETE,
                getString(R.string.pif_delete_confirm_title),
                getString(R.string.pif_delete_confirm_message),
                getString(R.string.action_delete))
                .show(getChildFragmentManager(), "delete_profile");
    }

    private void openKeyboxFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"text/xml", "application/xml"});
        mKeyboxFileLauncher.launch(intent);
    }

    private void importKeyboxFile(@NonNull Uri uri) {
        new Thread(() -> {
            byte[] bytes;
            KeyboxRevocationChecker.Result result;
            try {
                bytes = mKeyboxManager.readKeybox(uri);
                result = mRevocationChecker.check(bytes);
            } catch (Exception e) {
                Log.e(TAG, "importKeyboxFile error", e);
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    getString(R.string.keybox_import_failed, e.getMessage()),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
                return;
            }
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                if (result.isProblem()) {
                    String reason = TextUtils.isEmpty(result.reason)
                            ? revocationStatusText(result.status) : result.reason;
                    Toast.makeText(requireContext(),
                            getString(R.string.keybox_import_blocked, reason),
                            Toast.LENGTH_LONG).show();
                    mRevocationChecker.clear();
                    refreshKeybox();
                    refreshHero();
                    maybeAutoCheckRevocation();
                    return;
                }
                mKeyboxManager.saveKeybox(bytes);
                refreshUi();
                Toast.makeText(requireContext(), R.string.keybox_import_success,
                        Toast.LENGTH_SHORT).show();
            });
        }, "SpoofKeyboxImport").start();
    }

    private void confirmDeleteKeybox() {
        SpoofConfirmSheet.newInstance(REQ_KEYBOX_DELETE,
                getString(R.string.keybox_delete_title),
                getString(R.string.keybox_delete_message),
                getString(R.string.action_delete))
                .show(getChildFragmentManager(), "delete_keybox");
    }

    private void openTargetPicker() {
        ArrayList<String> selected = new ArrayList<>(mKeyboxManager.readTargetLines());
        SpoofAppPickerSheet.newInstance(REQ_TARGETS,
                getString(R.string.target_manage_title), SpoofAppAdapter.MODE_TARGETS,
                selected, null, false, false, false)
                .show(getChildFragmentManager(), "targets");
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
            Toast.makeText(requireContext(), R.string.target_import_success,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "importTargetFile error", e);
            Toast.makeText(requireContext(),
                    getString(R.string.target_import_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openPatchEditor() {
        String patch = mPifManager.getCurrentProperties().get("SECURITY_PATCH");
        PatchEditorSheet.newInstance(REQ_PATCH, getString(R.string.spoof_patch_title),
                mKeyboxManager.getPatchConfig(), mKeyboxManager.isPatchAuto(),
                patch == null ? "" : patch)
                .show(getChildFragmentManager(), "patch_editor");
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
}
