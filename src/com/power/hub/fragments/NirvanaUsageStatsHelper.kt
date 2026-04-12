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

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.util.Calendar

object NirvanaUsageStatsHelper {
    private const val LOOKBACK_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

    data class DailySummary(
        val usageByPackage: Map<String, Long>,
        val notificationCountByPackage: Map<String, Int>,
        val unlockCount: Int,
    )

    fun getStartOfTodayMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun queryTodaySummary(usageStatsManager: UsageStatsManager): DailySummary {
        return querySummary(
            usageStatsManager = usageStatsManager,
            start = getStartOfTodayMillis(),
            end = System.currentTimeMillis(),
        )
    }

    fun querySummary(
        usageStatsManager: UsageStatsManager,
        start: Long,
        end: Long,
    ): DailySummary {
        if (end <= start) {
            return DailySummary(emptyMap(), emptyMap(), 0)
        }

        val usageByPackage = HashMap<String, Long>()
        val notificationCountByPackage = HashMap<String, Int>()
        val activePackages = HashMap<String, Long>()
        var unlockCount = 0

        val queryStart = (start - LOOKBACK_WINDOW_MILLIS).coerceAtLeast(0L)
        val events = usageStatsManager.queryEvents(queryStart, end)
        val event = UsageEvents.Event()

        fun addUsage(packageName: String, sessionEnd: Long) {
            val sessionStart = activePackages.remove(packageName) ?: return
            val boundedEnd = sessionEnd.coerceAtMost(end)
            if (boundedEnd <= sessionStart) return

            usageByPackage[packageName] = (usageByPackage[packageName] ?: 0L) + (boundedEnd - sessionStart)
        }

        fun closeAllActiveSessions(sessionEnd: Long) {
            if (activePackages.isEmpty()) return
            activePackages.keys.toList().forEach { packageName ->
                addUsage(packageName, sessionEnd)
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED,
                -> {
                    event.packageName?.let { packageName ->
                        activePackages.putIfAbsent(packageName, timestamp.coerceAtLeast(start))
                    }
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    event.packageName?.let { packageName ->
                        addUsage(packageName, timestamp)
                    }
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    closeAllActiveSessions(timestamp)
                }

                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    if (timestamp >= start) {
                        unlockCount++
                    }
                }

                UsageEvents.Event.NOTIFICATION_INTERRUPTION -> {
                    event.packageName?.let { packageName ->
                        if (timestamp >= start) {
                            notificationCountByPackage[packageName] =
                                (notificationCountByPackage[packageName] ?: 0) + 1
                        }
                    }
                }
            }
        }

        closeAllActiveSessions(end)

        return DailySummary(usageByPackage, notificationCountByPackage, unlockCount)
    }
}
