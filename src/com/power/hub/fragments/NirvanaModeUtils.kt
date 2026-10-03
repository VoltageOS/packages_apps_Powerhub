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

import android.app.AppGlobals
import android.content.Context
import android.content.Intent
import android.content.pm.SuspendDialogInfo
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.android.internal.util.voltage.nirvana.NirvanaConstants
import com.android.internal.util.voltage.nirvana.NirvanaState
import com.android.internal.util.voltage.nirvana.NirvanaSuspension

class NirvanaModeUtils(private val context: Context) {
    companion object {
        private const val TAG = "NirvanaModeUtils"
    }

    private val resolver = context.contentResolver
    private val packageManager = context.packageManager
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

    fun shouldNirvanaModeBeActive(): Boolean {
        return NirvanaState.shouldBeActive(resolver)
    }

    fun isNirvanaModeActive() = shouldNirvanaModeBeActive()

    fun setNirvanaModeActive(active: Boolean) {
        NirvanaState.setManualActive(resolver, active)
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun getSelectedApps(): Set<String> {
        return NirvanaState.getSelectedApps(resolver)
    }

    fun addApp(packageName: String) {
        val set = getSelectedApps().toMutableSet()
        if (set.add(packageName)) {
            NirvanaState.saveSelectedApps(resolver, set)
            NirvanaState.sendUpdateBroadcast(context)
        }
    }

    fun removeApp(packageName: String) {
        val set = getSelectedApps().toMutableSet()
        if (set.remove(packageName)) {
            NirvanaState.saveSelectedApps(resolver, set)
            NirvanaState.sendUpdateBroadcast(context)
        }
    }

    fun reconcileState() {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun enforcePackages(packages: List<String>) {
        if (packages.isEmpty()) {
            return
        }
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun validateTrackedState() {
        NirvanaState.sendUpdateBroadcast(context)
    }

    private fun unsuspendPackageGlobally(pkg: String) {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun forceUnsuspendAll(): Int {
        var count = 0
        try {
            val profiles = userManager.userProfiles
            val allPackages = packageManager.getInstalledPackages(
                android.content.pm.PackageManager.MATCH_ANY_USER)
            for (userHandle in profiles) {
                val userId = userHandle.identifier
                val suspended = allPackages
                    .map { it.packageName }
                    .filter {
                        try {
                            AppGlobals.getPackageManager().isPackageSuspendedForUser(it, userId)
                        } catch (e: Exception) {
                            false
                        }
                    }
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Force-unsuspend failed for user $userId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "forceUnsuspendAll failed", e)
        }
        context.sendBroadcastAsUser(
            Intent(NirvanaConstants.ACTION_FORCE_UNSUSPEND).apply {
                setPackage(NirvanaConstants.TARGET_PACKAGE)
            },
            UserHandle.CURRENT,
        )
        NirvanaState.sendUpdateBroadcast(context)
        return count
    }

    fun applyBatchSuspension(
        pkgs: Array<String>,
        suspend: Boolean,
        userId: Int,
        dialogInfo: SuspendDialogInfo?,
    ) {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun isPackageSuspendedForUser(
        pkg: String,
        userId: Int,
    ): Boolean {
        return try {
            AppGlobals.getPackageManager().isPackageSuspendedForUser(pkg, userId)
        } catch (e: Exception) {
            false
        }
    }

    fun isSuspendedByMe(
        pkg: String,
        userId: Int,
    ): Boolean {
        return try {
            val owner = AppGlobals.getPackageManager().getSuspendingPackage(pkg, userId)
            owner == context.opPackageName
                || owner == NirvanaConstants.SYSTEMUI_SUSPENDER
        } catch (e: Exception) {
            false
        }
    }

    fun isScheduleEnabled() = NirvanaState.isScheduleEnabled(resolver)

    fun setScheduleEnabled(enable: Boolean) {
        NirvanaState.setScheduleEnabled(resolver, enable)
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun getStartTime() = NirvanaState.getStartTime(resolver)

    fun getEndTime() = NirvanaState.getEndTime(resolver)

    fun saveSchedule(
        start: Int,
        end: Int,
    ) {
        NirvanaState.saveSchedule(resolver, start, end)
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun shouldScheduleBeActive(): Boolean {
        return NirvanaState.shouldScheduleBeActive(resolver)
    }

    fun scheduleNextAlarm() {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun cancelAlarms() {
        NirvanaSuspension.cancelAlarms(context)
    }
}
