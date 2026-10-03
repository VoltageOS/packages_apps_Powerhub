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
import android.os.UserHandle
import com.android.internal.util.voltage.nirvana.NirvanaConstants
import com.android.internal.util.voltage.nirvana.NirvanaState

class NirvanaModeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_UPDATE_NIRVANA_SCHEDULE = "com.power.hub.action.UPDATE_NIRVANA_SCHEDULE"
        const val ACTION_NIRVANA_TIME_LIMIT_REACHED = "com.power.hub.action.NIRVANA_TIME_LIMIT_REACHED"
        const val ACTION_NIRVANA_DAILY_RESET = "com.power.hub.action.NIRVANA_DAILY_RESET"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_UPDATE_NIRVANA_SCHEDULE,
            Intent.ACTION_USER_PRESENT,
            NirvanaConstants.ACTION_UPDATE,
            -> {
                NirvanaState.sendUpdateBroadcast(context)
            }
            ACTION_NIRVANA_TIME_LIMIT_REACHED,
            NirvanaConstants.ACTION_LIMIT_REACHED,
            -> {
                val packageName = intent.getStringExtra(NirvanaTimeLimitUtils.EXTRA_LIMIT_PACKAGE)
                    ?: intent.getStringExtra(NirvanaConstants.EXTRA_LIMIT_PACKAGE)
                val userId = if (intent.hasExtra(NirvanaTimeLimitUtils.EXTRA_LIMIT_USER)) {
                    intent.getIntExtra(NirvanaTimeLimitUtils.EXTRA_LIMIT_USER, UserHandle.myUserId())
                } else {
                    intent.getIntExtra(NirvanaConstants.EXTRA_LIMIT_USER, UserHandle.myUserId())
                }
                if (!packageName.isNullOrEmpty()) {
                    val forward = Intent(NirvanaConstants.ACTION_LIMIT_REACHED).apply {
                        setPackage(NirvanaConstants.TARGET_PACKAGE)
                        putExtra(NirvanaConstants.EXTRA_LIMIT_PACKAGE, packageName)
                        putExtra(NirvanaConstants.EXTRA_LIMIT_USER, userId)
                    }
                    context.sendBroadcastAsUser(forward, UserHandle.CURRENT)
                }
                NirvanaState.sendUpdateBroadcast(context)
            }
            ACTION_NIRVANA_DAILY_RESET,
            NirvanaConstants.ACTION_DAILY_RESET,
            -> {
                val forward = Intent(NirvanaConstants.ACTION_DAILY_RESET).apply {
                    setPackage(NirvanaConstants.TARGET_PACKAGE)
                }
                context.sendBroadcastAsUser(forward, UserHandle.CURRENT)
            }
            Intent.ACTION_PACKAGE_FULLY_REMOVED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGES_UNSUSPENDED,
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            -> {
                NirvanaState.sendUpdateBroadcast(context)
            }
        }
    }
}
