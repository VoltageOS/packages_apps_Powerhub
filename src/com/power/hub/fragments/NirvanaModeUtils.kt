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

import android.app.AlarmManager
import android.app.AppGlobals
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.SuspendDialogInfo
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.android.settings.R
import java.util.Calendar

/**
 *  Helper for Nirvana Mode logic.
 */
class NirvanaModeUtils(private val context: Context) {
    companion object {
        private const val TAG = "NirvanaModeUtils"

        private const val KEY_APPS = "nirvana_mode_apps_list"
        private const val KEY_TRACKED_STATE = "nirvana_mode_tracked_state"
        private const val KEY_MANUAL_ACTIVE = "nirvana_mode_manual_active"
        private const val KEY_SCHEDULE_ENABLED = "nirvana_mode_schedule_enabled"
        private const val KEY_START_TIME = "nirvana_mode_start_time"
        private const val KEY_END_TIME = "nirvana_mode_end_time"

        private const val DEFAULT_START = 540
        private const val DEFAULT_END = 1020
    }

    private val resolver = context.contentResolver
    private val packageManager = context.packageManager
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

    /**
     * Determines if Nirvana Mode *should* be active right now based on Manual Toggle OR Schedule.
     */
    fun shouldNirvanaModeBeActive(): Boolean {
        val manual = Settings.Secure.getInt(resolver, KEY_MANUAL_ACTIVE, 0) == 1
        val schedule = isScheduleEnabled() && shouldScheduleBeActive()
        return manual || schedule
    }

    fun isNirvanaModeActive() = shouldNirvanaModeBeActive()

    /**
     * Toggles manual state and triggers immediate reconciliation.
     */
    fun setNirvanaModeActive(active: Boolean) {
        Settings.Secure.putInt(resolver, KEY_MANUAL_ACTIVE, if (active) 1 else 0)
        reconcileState()
    }

    fun getSelectedApps(): Set<String> {
        val str = Settings.Secure.getString(resolver, KEY_APPS) ?: ""
        return if (str.isBlank()) emptySet() else str.split(",").toSet()
    }

    fun addApp(packageName: String) {
        val set = getSelectedApps().toMutableSet()
        if (set.add(packageName)) {
            saveApps(set)
            if (shouldNirvanaModeBeActive()) reconcileState()
        }
    }

    fun removeApp(packageName: String) {
        val set = getSelectedApps().toMutableSet()
        if (set.remove(packageName)) {
            saveApps(set)
            if (shouldNirvanaModeBeActive()) {
                unsuspendPackageGlobally(packageName)
            }
        }
    }

    private fun saveApps(set: Set<String>) {
        Settings.Secure.putString(resolver, KEY_APPS, set.joinToString(","))
    }

    /**
     * The Brain. Compares "What should be" vs "What is" and fixes it.
     * Handles Multi-User and Ownership logic.
     */
    fun reconcileState() {
        val active = shouldNirvanaModeBeActive()
        val selectedPackages = getSelectedApps()
        val profiles = userManager.userProfiles

        val trackedState = getTrackedState()
        val newTrackedState = mutableSetOf<String>()

        if (active) {
            val dialogInfo = getStrictDialogInfo()

            for (userHandle in profiles) {
                val userId = userHandle.identifier

                val packagesToSuspend = mutableListOf<String>()

                for (pkg in selectedPackages) {
                    val key = "$userId|$pkg"

                    val isSuspended = isPackageSuspendedForUser(pkg, userId)
                    val isOwnedByMe = isSuspendedByMe(pkg, userId)

                    if (isSuspended) {
                        if (isOwnedByMe) {
                            newTrackedState.add(key)
                        } else {
                        }
                    } else {
                        packagesToSuspend.add(pkg)
                        newTrackedState.add(key)
                    }
                }

                if (packagesToSuspend.isNotEmpty()) {
                    applyBatchSuspension(packagesToSuspend.toTypedArray(), true, userId, dialogInfo)
                }
            }

            saveTrackedState(newTrackedState)
        } else {
            val mapUserToPkgs = mutableMapOf<Int, MutableList<String>>()

            for (key in trackedState) {
                val parts = key.split("|")
                if (parts.size != 2) continue
                val userId = parts[0].toIntOrNull() ?: continue
                val pkg = parts[1]

                if (isPackageSuspendedForUser(pkg, userId) && isSuspendedByMe(pkg, userId)) {
                    mapUserToPkgs.getOrPut(userId) { mutableListOf() }.add(pkg)
                }
            }

            for ((userId, pkgs) in mapUserToPkgs) {
                applyBatchSuspension(pkgs.toTypedArray(), false, userId, null)
            }

            saveTrackedState(emptySet())
        }
    }

    /**
     * Targeted enforcement for Watchdog (Package Added/Unsuspended events).
     */
    fun enforcePackages(packages: List<String>) {
        val selected = getSelectedApps()
        val targets = packages.filter { selected.contains(it) }
        if (targets.isEmpty()) return

        val profiles = userManager.userProfiles
        val dialogInfo = getStrictDialogInfo()

        for (userHandle in profiles) {
            val userId = userHandle.identifier
            val toSuspend = targets.filter { !isPackageSuspendedForUser(it, userId) }

            if (toSuspend.isNotEmpty()) {
                Log.i(TAG, "Watchdog enforcing suspension on user $userId: $toSuspend")
                applyBatchSuspension(toSuspend.toTypedArray(), true, userId, dialogInfo)

                val currentTracked = getTrackedState().toMutableSet()
                toSuspend.forEach { currentTracked.add("$userId|$it") }
                saveTrackedState(currentTracked)
            }
        }
    }

    /**
     * Boot Integrity Check.
     * If the system cleared suspension state during reboot (rare but possible),
     * or if we have stale entries in our tracker that we no longer own,
     * we must prune them so we don't think we are managing them.
     */
    fun validateTrackedState() {
        val profiles = userManager.userProfiles
        val currentTracked = getTrackedState()
        val validTracked = mutableSetOf<String>()

        for (key in currentTracked) {
            val parts = key.split("|")
            if (parts.size != 2) continue
            val userId = parts[0].toIntOrNull() ?: continue
            val pkg = parts[1]

            val userExists = profiles.any { it.identifier == userId }
            if (!userExists) continue

            if (isPackageSuspendedForUser(pkg, userId) && isSuspendedByMe(pkg, userId)) {
                validTracked.add(key)
            } else {
                Log.w(TAG, "Pruning stale Nirvana tracking for $pkg on user $userId")
            }
        }

        if (validTracked.size != currentTracked.size) {
            saveTrackedState(validTracked)
        }
    }

    private fun unsuspendPackageGlobally(pkg: String) {
        val profiles = userManager.userProfiles
        for (userHandle in profiles) {
            val userId = userHandle.identifier
            if (isPackageSuspendedForUser(pkg, userId) && isSuspendedByMe(pkg, userId)) {
                applyBatchSuspension(arrayOf(pkg), false, userId, null)
            }
        }
        val current = getTrackedState().filter { !it.endsWith("|$pkg") }.toSet()
        saveTrackedState(current)
    }

    fun forceUnsuspendAll(): Int {
        val profiles = userManager.userProfiles
        val allPackages = packageManager.getInstalledPackages(android.content.pm.PackageManager.MATCH_ANY_USER)
        var count = 0

        for (userHandle in profiles) {
            val userId = userHandle.identifier
            val suspended = allPackages
                .map { it.packageName }
                .filter { isPackageSuspendedForUser(it, userId) }

            if (suspended.isNotEmpty()) {
                try {
                    AppGlobals.getPackageManager().setPackagesSuspendedAsUser(
                        suspended.toTypedArray(),
                        false,
                        null, null, null, 0,
                        context.opPackageName,
                        userId,
                        userId,
                    )
                    count += suspended.size
                    Log.i(TAG, "Force-unsuspended ${suspended.size} package(s) for user $userId: $suspended")
                } catch (e: Exception) {
                    Log.e(TAG, "Force-unsuspend failed for user $userId", e)
                }
            }
        }

        saveTrackedState(emptySet())
        return count
    }

    private fun applyBatchSuspension(
        pkgs: Array<String>,
        suspend: Boolean,
        userId: Int,
        dialogInfo: SuspendDialogInfo?,
    ) {
        try {
            val finalPkgs =
                if (suspend) {
                    pkgs.filter { !isPackageSuspendedForUser(it, userId) }.toTypedArray()
                } else {
                    pkgs
                }

            if (finalPkgs.isNotEmpty()) {
                AppGlobals.getPackageManager().setPackagesSuspendedAsUser(
                    finalPkgs,
                    suspend,
                    null, null, dialogInfo, 0,
                    context.opPackageName,
                    userId,
                    userId,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change suspension state for user $userId", e)
        }
    }

    private fun isPackageSuspendedForUser(
        pkg: String,
        userId: Int,
    ): Boolean {
        return try {
            AppGlobals.getPackageManager().isPackageSuspendedForUser(pkg, userId)
        } catch (e: Exception) {
            false
        }
    }

    private fun isSuspendedByMe(
        pkg: String,
        userId: Int,
    ): Boolean {
        return try {
            val owner = AppGlobals.getPackageManager().getSuspendingPackage(pkg, userId)
            owner == context.opPackageName
        } catch (e: Exception) {
            false
        }
    }

    private fun getTrackedState(): Set<String> {
        val str = Settings.Secure.getString(resolver, KEY_TRACKED_STATE) ?: ""
        return if (str.isBlank()) emptySet() else str.split(",").toSet()
    }

    private fun saveTrackedState(set: Set<String>) {
        Settings.Secure.putString(resolver, KEY_TRACKED_STATE, set.joinToString(","))
    }

    private fun getStrictDialogInfo(): SuspendDialogInfo {
        val builder =
            SuspendDialogInfo.Builder()
                .setTitle(R.string.nirvana_mode_dialog_title)
                .setMessage(R.string.nirvana_mode_dialog_message)

        try {
            val resId = context.resources.getIdentifier("ic_nirvana_mode", "drawable", context.packageName)
            if (resId != 0) builder.setIcon(resId)
        } catch (e: Exception) {
        }

        return builder.build()
    }

    fun isScheduleEnabled() = Settings.Secure.getInt(resolver, KEY_SCHEDULE_ENABLED, 0) == 1

    fun setScheduleEnabled(enable: Boolean) = Settings.Secure.putInt(resolver, KEY_SCHEDULE_ENABLED, if (enable) 1 else 0)

    fun getStartTime() = Settings.Secure.getInt(resolver, KEY_START_TIME, DEFAULT_START)

    fun getEndTime() = Settings.Secure.getInt(resolver, KEY_END_TIME, DEFAULT_END)

    fun saveSchedule(
        start: Int,
        end: Int,
    ) {
        Settings.Secure.putInt(resolver, KEY_START_TIME, start)
        Settings.Secure.putInt(resolver, KEY_END_TIME, end)
    }

    fun shouldScheduleBeActive(): Boolean {
        val now = Calendar.getInstance()
        val currentMinutes = (now.get(Calendar.HOUR_OF_DAY) * 60) + now.get(Calendar.MINUTE)
        val start = getStartTime()
        val end = getEndTime()
        return if (end < start) currentMinutes >= start || currentMinutes < end else currentMinutes in start until end
    }

    fun scheduleNextAlarm() {
        if (!isScheduleEnabled()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NirvanaModeReceiver::class.java).apply { action = NirvanaModeReceiver.ACTION_UPDATE_NIRVANA_SCHEDULE }
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val now = Calendar.getInstance()
        val currentMinutes = (now.get(Calendar.HOUR_OF_DAY) * 60) + now.get(Calendar.MINUTE)

        val nextStart = getNextOccurrence(now, getStartTime(), currentMinutes)
        val nextEnd = getNextOccurrence(now, getEndTime(), currentMinutes)
        val triggerTime = if (nextStart < nextEnd) nextStart else nextEnd

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
    }

    private fun getNextOccurrence(
        now: Calendar,
        targetMinutes: Int,
        currentMinutes: Int,
    ): Long {
        val c = now.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, targetMinutes / 60)
        c.set(Calendar.MINUTE, targetMinutes % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (targetMinutes <= currentMinutes) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun cancelAlarms() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NirvanaModeReceiver::class.java).apply { action = NirvanaModeReceiver.ACTION_UPDATE_NIRVANA_SCHEDULE }
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
    }
}
