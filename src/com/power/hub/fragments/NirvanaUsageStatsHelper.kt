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
        val activeTokens = HashSet<String>()
        val activeTokenCount = HashMap<String, Int>()
        val sessionStartByPackage = HashMap<String, Long>()
        val everResumed = HashSet<String>()
        var keyguardShowing = true
        var unlockCount = 0

        val queryStart = (start - LOOKBACK_WINDOW_MILLIS).coerceAtLeast(0L)
        val events = usageStatsManager.queryEvents(queryStart, end)
        val event = UsageEvents.Event()

        fun accrue(packageName: String, sessionBegin: Long, sessionEnd: Long) {
            val boundedStart = sessionBegin.coerceAtLeast(start)
            val boundedEnd = sessionEnd.coerceAtMost(end)
            if (boundedEnd <= boundedStart) return

            usageByPackage[packageName] = (usageByPackage[packageName] ?: 0L) + (boundedEnd - boundedStart)
        }

        fun openToken(packageName: String, token: String, timestamp: Long) {
            everResumed.add(packageName)
            if (!activeTokens.add(token)) return
            val count = (activeTokenCount[packageName] ?: 0) + 1
            activeTokenCount[packageName] = count
            if (count == 1) {
                sessionStartByPackage[packageName] = timestamp
            }
        }

        fun closeToken(packageName: String, token: String, timestamp: Long) {
            if (!activeTokens.remove(token)) {
                if (!activeTokenCount.containsKey(packageName) && everResumed.add(packageName)) {
                    accrue(packageName, start, timestamp)
                }
                return
            }
            val count = (activeTokenCount[packageName] ?: 1) - 1
            if (count <= 0) {
                activeTokenCount.remove(packageName)
                sessionStartByPackage.remove(packageName)?.let { begin ->
                    accrue(packageName, begin, timestamp)
                }
            } else {
                activeTokenCount[packageName] = count
            }
        }

        fun closeAllSessions(timestamp: Long) {
            if (sessionStartByPackage.isEmpty() && activeTokens.isEmpty()) return
            for ((packageName, begin) in sessionStartByPackage) {
                accrue(packageName, begin, timestamp)
            }
            sessionStartByPackage.clear()
            activeTokenCount.clear()
            activeTokens.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    event.packageName?.let { packageName ->
                        openToken(packageName, "$packageName|${event.className}|${event.instanceId}", timestamp)
                    }
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    event.packageName?.let { packageName ->
                        closeToken(packageName, "$packageName|${event.className}|${event.instanceId}", timestamp)
                    }
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                -> {
                    closeAllSessions(timestamp)
                }

                UsageEvents.Event.KEYGUARD_SHOWN -> {
                    keyguardShowing = true
                }

                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    if (keyguardShowing) {
                        keyguardShowing = false
                        if (timestamp >= start) {
                            unlockCount++
                        }
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

        closeAllSessions(end)

        return DailySummary(usageByPackage, notificationCountByPackage, unlockCount)
    }
}
