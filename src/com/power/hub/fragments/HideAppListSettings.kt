/*
 * SPDX-FileCopyrightText: 2018-2024 The LineageOS Project
 * SPDX-FileCopyrightText: 2021 AOSP-Krypton Project
 * SPDX-FileCopyrightText: 2022 Nameless-AOSP Project
 * SPDX-FileCopyrightText: 2022 Paranoid Android
 * SPDX-FileCopyrightText: 2026 AxionOS
 * SPDX-FileCopyrightText: 2026 VoltageOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.power.hub.fragments

import android.app.ActivityManager
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.fragment.app.Fragment
import com.android.internal.util.voltage.HideAppListUtils
import com.android.settings.R

class HideAppListSettings : Fragment() {

    private lateinit var activityManager: ActivityManager
    private lateinit var userInfos: List<android.content.pm.UserInfo>
    private val hideAppListUtils = HideAppListUtils()

    private lateinit var heroSummary: TextView
    private lateinit var statusPill: TextView
    private lateinit var appsContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var chooseAppsRow: View

    @SuppressLint("QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityManager = requireContext().getSystemService(ActivityManager::class.java)
            as ActivityManager
        userInfos = UserManager.get(requireContext()).users
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_spoof_hidden, container, false)

    override fun onViewCreated(@NonNull view: View, @Nullable savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        heroSummary = view.findViewById(R.id.hero_summary)
        statusPill = view.findViewById(R.id.app_status_pill)
        appsContainer = view.findViewById(R.id.apps_container)
        emptyView = view.findViewById(R.id.apps_empty)
        chooseAppsRow = view.findViewById(R.id.row_add)

        chooseAppsRow.setOnClickListener { showAppPicker() }

        childFragmentManager.setFragmentResultListener(
            REQ_PICK_APPS,
            viewLifecycleOwner,
        ) { _, result ->
            val selection = result.getStringArrayList(SpoofAppPickerSheet.RESULT_SELECTION)
            if (selection != null) {
                applySelection(selection)
            }
        }

        childFragmentManager.setFragmentResultListener(
            REQ_REMOVE,
            viewLifecycleOwner,
        ) { _, result ->
            val packageName = result.getString(SpoofConfirmSheet.RESULT_CONTEXT)
            if (packageName != null && result.getBoolean(SpoofConfirmSheet.RESULT_CONFIRMED)) {
                setHidden(packageName, false)
                refresh()
                toast(R.string.spoof_hidden_removed)
            }
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun getHiddenPackages(): List<String> {
        val raw = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.HIDE_APPLIST,
        )
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun refresh() {
        if (!isAdded) {
            return
        }
        val hidden = getHiddenPackages()
        heroSummary.setText(R.string.spoof_hidden_summary)
        SpoofUi.bindStatusPill(
            statusPill,
            if (hidden.isEmpty()) {
                getString(R.string.spoof_status_none)
            } else {
                getString(R.string.spoof_status_hidden, hidden.size)
            },
            if (hidden.isEmpty()) SpoofUi.TONE_MUTED else SpoofUi.TONE_ACCENT,
        )

        SpoofUi.bindActionRow(
            chooseAppsRow,
            R.drawable.ic_blacklist,
            getString(R.string.spoof_choose_apps),
            getString(R.string.spoof_hidden_summary),
        )

        appsContainer.removeAllViews()
        emptyView.visibility = if (hidden.isEmpty()) View.VISIBLE else View.GONE

        for (packageName in hidden) {
            val row = LayoutInflater.from(appsContainer.context)
                .inflate(R.layout.row_spoof_choice, appsContainer, false)
            val icon = row.findViewById<ImageView>(R.id.row_icon)
            val title = row.findViewById<TextView>(R.id.row_title)
            val summary = row.findViewById<TextView>(R.id.row_summary)
            val delete = row.findViewById<ImageView>(R.id.row_delete)
            row.findViewById<ImageView>(R.id.row_chevron).visibility = View.GONE

            icon.setImageDrawable(SpoofUi.loadAppIcon(requireContext(), packageName))
            icon.imageTintList = null
            icon.visibility = View.VISIBLE
            title.text = SpoofUi.appLabel(requireContext(), packageName)
            summary.text = packageName
            delete.visibility = View.VISIBLE
            delete.setOnClickListener { showRemoveConfirm(packageName) }
            appsContainer.addView(row)
        }
    }

    private fun showAppPicker() {
        val protectedPackages = requireContext().resources
            .getStringArray(R.array.hide_applist_hidden_apps)
            .toMutableList()
        protectedPackages.add(requireContext().packageName)

        SpoofAppPickerSheet.newInstance(
            REQ_PICK_APPS,
            getString(R.string.spoof_app_picker_title),
            SpoofAppAdapter.MODE_MULTI,
            ArrayList(getHiddenPackages()),
            ArrayList(protectedPackages),
            false,
            true,
            false,
        ).show(childFragmentManager, "hidden_picker")
    }

    private fun applySelection(selection: List<String>) {
        val selected = selection.toSet()
        val current = getHiddenPackages()
        for (packageName in selected) {
            if (!current.contains(packageName)) {
                setHidden(packageName, true)
            }
        }
        for (packageName in current) {
            if (!selected.contains(packageName)) {
                setHidden(packageName, false)
            }
        }
        refresh()
    }

    private fun setHidden(packageName: String, hidden: Boolean) {
        if (packageName.isBlank()) {
            return
        }
        for (info in userInfos) {
            if (hidden) {
                hideAppListUtils.addApp(requireContext(), packageName, info.id)
            } else {
                hideAppListUtils.removeApp(requireContext(), packageName, info.id)
            }
        }
        try {
            activityManager.forceStopPackage(packageName)
        } catch (_: Exception) {
        }
    }

    private fun showRemoveConfirm(packageName: String) {
        SpoofConfirmSheet.newInstance(
            REQ_REMOVE,
            getString(R.string.spoof_unhide_app_title),
            getString(
                R.string.spoof_unhide_app_message,
                SpoofUi.appLabel(requireContext(), packageName),
            ),
            getString(R.string.action_delete),
            packageName,
        ).show(childFragmentManager, "hidden_remove")
    }

    private fun toast(resId: Int) {
        if (isAdded) {
            android.widget.Toast.makeText(requireContext(), resId, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    companion object {
        private const val REQ_PICK_APPS = "spoof:hidden_pick"
        private const val REQ_REMOVE = "spoof:hidden_remove"
    }
}
