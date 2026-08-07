/*
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

package com.power.hub.fragments

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import java.util.Locale

/**
 * UI Controller for Nirvana Mode.
 * Allows selecting apps, setting schedules, and manual toggling.
 */
class NirvanaModeSettings : Fragment(R.layout.nirvana_mode_fragment) {
    private lateinit var packageManager: PackageManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var packageList: List<PackageInfo>
    private lateinit var nirvanaUtils: NirvanaModeUtils
    private lateinit var timeLimitUtils: NirvanaTimeLimitUtils
    private var limitSnapshot: Map<String, Int> = emptyMap()

    private lateinit var scheduleSwitch: CompoundButton
    private lateinit var scheduleCard: LinearLayout
    private lateinit var toggleButton: Button
    private lateinit var heroSection: LinearLayout
    private lateinit var heroDescription: TextView
    private var defaultDescriptionColor: Int = 0
    private val statusActiveColor: Int = Color.parseColor("#34A853")

    private var searchText = ""
    private var showSystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        packageManager = requireContext().packageManager
        packageList = packageManager.getInstalledPackages(PackageManager.MATCH_ANY_USER)
        nirvanaUtils = NirvanaModeUtils(requireContext())
        timeLimitUtils = NirvanaTimeLimitUtils(requireContext())
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.nirvana_mode_title)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        scheduleSwitch = view.findViewById(R.id.switch_schedule)
        scheduleCard = view.findViewById(R.id.schedule_card)
        toggleButton = view.findViewById(R.id.btn_toggle_nirvana)
        recyclerView = view.findViewById(R.id.rv_apps)
        heroSection = view.findViewById(R.id.hero_section)
        heroDescription = view.findViewById(R.id.tv_hero_description)
        defaultDescriptionColor = heroDescription.currentTextColor

        setupEdgeToEdge(view)

        adapter = AppListAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        initScheduleUI()
        initManualToggleUI()
        refreshAppList()
        updateHeroStatus()
        refreshTimeLimits()
    }

    private fun refreshTimeLimits() {
        Thread {
            timeLimitUtils.refresh()
        }.start()
    }

    private fun showTimeLimitDialog(info: AppInfo) {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.nirvana_time_limit_dialog, null)
        val hourPicker = dialogView.findViewById<NumberPicker>(R.id.picker_hours)
        val minutePicker = dialogView.findViewById<NumberPicker>(R.id.picker_minutes)

        val current = limitSnapshot[info.packageName] ?: 0

        hourPicker.minValue = 0
        hourPicker.maxValue = 23
        hourPicker.value = current / 60
        hourPicker.wrapSelectorWheel = false

        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.value = current % 60
        minutePicker.wrapSelectorWheel = false

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.nirvana_time_limit_picker_title, info.label))
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                applyTimeLimit(info.packageName, hourPicker.value * 60 + minutePicker.value)
            }
            .setNeutralButton(R.string.nirvana_time_limit_remove) { _, _ ->
                applyTimeLimit(info.packageName, 0)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyTimeLimit(
        packageName: String,
        minutes: Int,
    ) {
        Thread {
            timeLimitUtils.setLimit(packageName, minutes)
            Handler(Looper.getMainLooper()).post {
                if (!isAdded) return@post
                packageList = requireContext().packageManager
                    .getInstalledPackages(PackageManager.MATCH_ANY_USER)
                refreshAppList()
            }
        }.start()
    }

    /**
     * Handle edge-to-edge window insets properly for Android 15+
     */
    private fun setupEdgeToEdge(view: View) {
        val basePadding = (16 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val typeMask =
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val insets = windowInsets.getInsets(typeMask)

            val rootBottom =
                ViewCompat.getRootWindowInsets(view)?.getInsets(typeMask)?.bottom ?: 0
            val bottomInset = maxOf(insets.bottom, rootBottom)

            heroSection.updatePadding(top = insets.top + basePadding)
            recyclerView.updatePadding(bottom = bottomInset + basePadding)

            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(view)
    }

    private fun initScheduleUI() {
        scheduleSwitch.isChecked = nirvanaUtils.isScheduleEnabled()

        scheduleSwitch.setOnCheckedChangeListener { _, isChecked ->
            nirvanaUtils.setScheduleEnabled(isChecked)
            if (isChecked) {
                nirvanaUtils.scheduleNextAlarm()
                nirvanaUtils.setNirvanaModeActive(nirvanaUtils.shouldScheduleBeActive())
            } else {
                nirvanaUtils.cancelAlarms()
            }
            updateManualButtonState()
            updateHeroStatus()
        }

        scheduleCard.setOnClickListener {
            showTimePickerSequence()
        }
    }

    private fun initManualToggleUI() {
        updateManualButtonState()
        toggleButton.setOnClickListener {
            val newState = !nirvanaUtils.isNirvanaModeActive()
            nirvanaUtils.setNirvanaModeActive(newState)
            updateManualButtonState()
            updateHeroStatus()
        }
    }

    private fun updateManualButtonState() {
        val isActive = nirvanaUtils.isNirvanaModeActive()
        if (isActive) {
            toggleButton.text = getString(R.string.nirvana_mode_turn_off_now)
        } else {
            toggleButton.text = getString(R.string.nirvana_mode_turn_on_now)
        }
    }

    private fun updateHeroStatus() {
        val isActive = nirvanaUtils.isNirvanaModeActive()
        val isScheduleEnabled = nirvanaUtils.isScheduleEnabled()
        val isScheduleActive = isScheduleEnabled && nirvanaUtils.shouldScheduleBeActive()

        val startStr = formatTime(nirvanaUtils.getStartTime())
        val endStr = formatTime(nirvanaUtils.getEndTime())

        if (isActive) {
            heroDescription.setTextColor(statusActiveColor)

            if (isScheduleActive) {
                heroDescription.text = getString(R.string.nirvana_status_active_until, endStr)
            } else {
                if (isScheduleEnabled) {
                    heroDescription.text = getString(R.string.nirvana_status_active_manual_scheduled, startStr, endStr)
                } else {
                    heroDescription.text = getString(R.string.nirvana_status_active)
                }
            }
        } else {
            heroDescription.setTextColor(defaultDescriptionColor)

            if (isScheduleEnabled) {
                heroDescription.text = getString(R.string.nirvana_status_scheduled, startStr, endStr)
            } else {
                heroDescription.text = getString(R.string.nirvana_mode_description)
            }
        }
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    }

    private fun showTimePickerSequence() {
        val context = requireContext()
        val currentStart = nirvanaUtils.getStartTime()
        val currentEnd = nirvanaUtils.getEndTime()

        TimePickerDialog(context, { _, h, m ->
            val newStart = h * 60 + m
            TimePickerDialog(context, { _, h2, m2 ->
                val newEnd = h2 * 60 + m2
                nirvanaUtils.saveSchedule(newStart, newEnd)

                if (!nirvanaUtils.isScheduleEnabled()) {
                    nirvanaUtils.setScheduleEnabled(true)
                    scheduleSwitch.isChecked = true
                }

                nirvanaUtils.scheduleNextAlarm()
                nirvanaUtils.setNirvanaModeActive(nirvanaUtils.shouldScheduleBeActive())
                updateManualButtonState()
                updateHeroStatus()
            }, currentEnd / 60, currentEnd % 60, true).apply {
                setTitle(getString(R.string.nirvana_mode_schedule_end))
                show()
            }
        }, currentStart / 60, currentStart % 60, true).apply {
            setTitle(getString(R.string.nirvana_mode_schedule_start))
            show()
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.hide_applist_menu, menu)

        val statsItem = menu.findItem(R.id.show_overlay)
        if (statsItem != null) {
            statsItem.isVisible = true
            statsItem.title = getString(R.string.nirvana_stats_title)
            statsItem.setOnMenuItemClickListener {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(id, NirvanaStatsFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }
        }

        menu.findItem(R.id.hide_overlay)?.isVisible = false
        menu.findItem(R.id.force_unsuspend_all)?.isVisible = true

        val searchItem = menu.findItem(R.id.search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.apply {
            queryHint = getString(R.string.search_apps)
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String) = false

                    override fun onQueryTextChange(newText: String): Boolean {
                        searchText = newText
                        refreshAppList()
                        return true
                    }
                },
            )
        }

        searchItem?.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    return true
                }
            },
        )

        updateMenuState(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_system, R.id.hide_system -> {
                showSystem = !showSystem
                refreshAppList()
                activity?.invalidateOptionsMenu()
                return true
            }
            R.id.force_unsuspend_all -> {
                showForceUnsuspendDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showForceUnsuspendDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.nirvana_force_unsuspend_dialog_title))
            .setMessage(getString(R.string.nirvana_force_unsuspend_dialog_message))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runForceUnsuspend()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runForceUnsuspend() {
        Thread {
            val count = nirvanaUtils.forceUnsuspendAll()
            Handler(Looper.getMainLooper()).post {
                if (!isAdded) return@post
                val msg = getString(R.string.nirvana_force_unsuspend_done, count)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                packageList = requireContext().packageManager
                    .getInstalledPackages(PackageManager.MATCH_ANY_USER)
                refreshAppList()
            }
        }.start()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        updateMenuState(menu)
    }

    private fun updateMenuState(menu: Menu) {
        menu.findItem(R.id.show_system)?.isVisible = !showSystem
        menu.findItem(R.id.hide_system)?.isVisible = showSystem
    }

    private fun refreshAppList() {
        limitSnapshot = timeLimitUtils.getLimits()

        val filtered =
            packageList.filter {
                val appInfo = it.applicationInfo!!
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val pkg = it.packageName
                val label = getLabel(it)

                val isVisibleType = showSystem || (!isSystem || isUpdatedSystem)
                val searchFilter = label.contains(searchText, true)

                isVisibleType && searchFilter && pkg != "com.android.settings"
            }.sortedWith { a, b -> getLabel(a).compareTo(getLabel(b), true) }

        adapter.submitList(filtered.map { appInfoFromPackageInfo(it) })
    }

    private fun getLabel(packageInfo: PackageInfo): String {
        val label = packageInfo.applicationInfo!!.loadLabel(packageManager).toString()
        return if (isPackageSuspended(packageInfo.packageName)) {
            "$label (Suspended)"
        } else {
            label
        }
    }

    private fun isPackageSuspended(packageName: String): Boolean {
        return try {
            packageManager.isPackageSuspended(packageName)
        } catch (e: Exception) {
            false
        }
    }

    private fun appInfoFromPackageInfo(packageInfo: PackageInfo) =
        AppInfo(
            packageInfo.packageName,
            getLabel(packageInfo),
            packageInfo.applicationInfo!!.loadIcon(packageManager),
            limitSnapshot[packageInfo.packageName] ?: 0,
        )

    private fun onListUpdate(
        packageName: String,
        isChecked: Boolean,
    ) {
        if (isChecked) {
            nirvanaUtils.addApp(packageName)
        } else {
            nirvanaUtils.removeApp(packageName)
        }
    }

    private inner class AppListAdapter : ListAdapter<AppInfo, AppListViewHolder>(DiffCallback) {
        private var selectedList = nirvanaUtils.getSelectedApps().toMutableSet()

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): AppListViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.hide_applist_list_item, parent, false)
            return AppListViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: AppListViewHolder,
            position: Int,
        ) {
            val item = getItem(position)
            holder.bind(item, selectedList.contains(item.packageName))

            holder.itemView.setOnClickListener {
                val isSelected = selectedList.contains(item.packageName)
                if (isSelected) {
                    selectedList.remove(item.packageName)
                } else {
                    selectedList.add(item.packageName)
                }
                onListUpdate(item.packageName, !isSelected)

                holder.checkBox.isChecked = !isSelected
            }

            holder.itemView.setOnLongClickListener {
                showTimeLimitDialog(item)
                true
            }
        }

        override fun submitList(list: List<AppInfo>?) {
            selectedList = nirvanaUtils.getSelectedApps().toMutableSet()
            super.submitList(list)
        }
    }

    private class AppListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.app_icon)
        val label: TextView = itemView.findViewById(R.id.app_name)
        val pkg: TextView = itemView.findViewById(R.id.package_name)
        val checkBox: CheckBox = itemView.findViewById(R.id.check_box)

        fun bind(
            info: AppInfo,
            isChecked: Boolean,
        ) {
            label.text = info.label
            icon.setImageDrawable(info.icon)
            checkBox.isChecked = isChecked

            pkg.text =
                if (info.limitMinutes > 0) {
                    val hours = info.limitMinutes / 60
                    val minutes = info.limitMinutes % 60
                    val limitText =
                        if (hours > 0) {
                            itemView.context.getString(R.string.nirvana_time_limit_row_hm, hours, minutes)
                        } else {
                            itemView.context.getString(R.string.nirvana_time_limit_row_m, minutes)
                        }
                    "${info.packageName} • $limitText"
                } else {
                    info.packageName
                }
        }
    }

    data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable,
        val limitMinutes: Int = 0,
    )

    object DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(
            oldItem: AppInfo,
            newItem: AppInfo,
        ) = oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(
            oldItem: AppInfo,
            newItem: AppInfo,
        ) = oldItem == newItem
    }
}
