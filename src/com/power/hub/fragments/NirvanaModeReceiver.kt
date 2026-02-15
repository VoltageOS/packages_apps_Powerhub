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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives Boot Complete, Alarm intents, and User actions to enforce Nirvana Mode.
 * Acts as a watchdog to prevent unauthorized unsuspension of apps.
 */
class NirvanaModeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_UPDATE_NIRVANA_SCHEDULE = "com.power.hub.action.UPDATE_NIRVANA_SCHEDULE"
        private const val TAG = "NirvanaModeReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        val utils = NirvanaModeUtils(context)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_UPDATE_NIRVANA_SCHEDULE,
            Intent.ACTION_USER_PRESENT,
            -> {
                if (Intent.ACTION_BOOT_COMPLETED == action) {
                    utils.validateTrackedState()
                }

                utils.reconcileState()

                if (utils.isScheduleEnabled()) {
                    utils.scheduleNextAlarm()
                }
            }

            Intent.ACTION_PACKAGES_UNSUSPENDED,
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            -> {
                if (utils.shouldNirvanaModeBeActive()) {
                    val changedPackages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST)

                    val singlePkg = intent.data?.schemeSpecificPart

                    val candidates = mutableListOf<String>()
                    if (!changedPackages.isNullOrEmpty()) {
                        candidates.addAll(changedPackages)
                    }
                    if (singlePkg != null) {
                        candidates.add(singlePkg)
                    }

                    if (candidates.isNotEmpty()) {
                        utils.enforcePackages(candidates)
                    }
                }
            }
        }
    }
}
