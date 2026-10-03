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

import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import com.android.internal.util.voltage.nirvana.NirvanaConstants
import com.android.internal.util.voltage.nirvana.NirvanaState
import com.android.internal.util.voltage.nirvana.NirvanaTimeLimits

class NirvanaTimeLimitUtils(private val context: Context) {
    companion object {
        const val EXTRA_LIMIT_PACKAGE = "com.power.hub.extra.NIRVANA_LIMIT_PACKAGE"
        const val EXTRA_LIMIT_USER = "com.power.hub.extra.NIRVANA_LIMIT_USER"
    }

    fun getLimits(): Map<String, Int> {
        return NirvanaTimeLimits.getLimits(context.contentResolver)
    }

    fun getLimitMinutes(packageName: String): Int {
        return NirvanaTimeLimits.getLimitMinutes(context.contentResolver, packageName)
    }

    fun setLimit(
        packageName: String,
        minutes: Int,
    ) {
        val limits = getLimits().toMutableMap()
        if (minutes <= 0) {
            limits.remove(packageName)
        } else {
            limits[packageName] = minutes
        }
        val raw = limits.entries.joinToString(",") { "${it.key}:${it.value}" }
        Settings.Secure.putStringForUser(
            context.contentResolver,
            NirvanaConstants.KEY_LIMITS,
            raw,
            UserHandle.USER_CURRENT,
        )
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun clearAllLimits() {
        Settings.Secure.putStringForUser(
            context.contentResolver,
            NirvanaConstants.KEY_LIMITS,
            "",
            UserHandle.USER_CURRENT,
        )
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun onBoot() {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun onPackagesChanged() {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun onDailyReset() {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun onLimitReached(
        packageName: String,
        userId: Int,
    ) {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun refresh() {
        NirvanaState.sendUpdateBroadcast(context)
    }

    fun scheduleDailyReset() {
        NirvanaState.sendUpdateBroadcast(context)
    }
}
