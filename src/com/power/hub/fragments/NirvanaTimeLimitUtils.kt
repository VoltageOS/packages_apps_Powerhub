/*
 * Copyright (C) 2026 VoltageOS
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.power.hub.fragments

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.SuspendDialogInfo
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.android.settings.R
import java.time.Duration
import java.util.Calendar

class NirvanaTimeLimitUtils(private val context: Context) {
    companion object {
        private const val TAG = "NirvanaTimeLimitUtils"

        private const val KEY_LIMITS = "nirvana_app_time_limits"
        private const val KEY_BLOCKED_TODAY = "nirvana_app_time_limit_blocked"
        private const val KEY_BLOCKED_DAY = "nirvana_app_time_limit_blocked_day"
        private const val KEY_OBSERVERS = "nirvana_app_time_limit_observers"

        private const val OBSERVER_ID_BASE = 710000
        private const val OBSERVER_ID_MASK = 0xFFFF
        private const val DAILY_RESET_REQUEST_CODE = 709999

        const val EXTRA_LIMIT_PACKAGE = "com.power.hub.extra.NIRVANA_LIMIT_PACKAGE"
        const val EXTRA_LIMIT_USER = "com.power.hub.extra.NIRVANA_LIMIT_USER"
    }

    private val resolver = context.contentResolver
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val nirvanaUtils = NirvanaModeUtils(context)

    fun getLimits(): Map<String, Int> {
        val raw = Settings.Secure.getString(resolver, KEY_LIMITS) ?: ""
        if (raw.isBlank()) return emptyMap()

        val limits = LinkedHashMap<String, Int>()
        for (entry in raw.split(",")) {
            val parts = entry.split(":")
            if (parts.size != 2) continue
            val packageName = parts[0].trim()
            val minutes = parts[1].trim().toIntOrNull() ?: continue
            if (packageName.isEmpty() || minutes <= 0) continue
            limits[packageName] = minutes
        }
        return limits
    }

    fun getLimitMinutes(packageName: String): Int = getLimits()[packageName] ?: 0

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
        saveLimits(limits)

        if (minutes <= 0) {
            releaseForAllUsers(packageName)
        }
        refresh()
    }

    fun clearAllLimits() {
        for (packageName in getLimits().keys) {
            releaseForAllUsers(packageName)
        }
        saveLimits(emptyMap())
        refresh()
    }

    fun onBoot() {
        rollBlockedStateIfNeeded()
        refresh()
    }

    fun onPackagesChanged() {
        refresh()
    }

    fun onDailyReset() {
        releaseBlocked()
        clearBlockedState()
        refresh()
    }

    fun onLimitReached(
        packageName: String,
        userId: Int,
    ) {
        rollBlockedStateIfNeeded()

        if (getLimitMinutes(packageName) <= 0) return
        if (isOwnedByNirvana(packageName)) return

        if (suspendForLimit(packageName, userId)) {
            val blocked = getBlockedToday().toMutableSet()
            blocked.add("$userId|$packageName")
            saveBlockedToday(blocked)
        }

        scheduleDailyReset()
    }

    fun refresh() {
        rollBlockedStateIfNeeded()

        val limits = pruneUninstalled(getLimits())
        unregisterAllObservers()

        if (limits.isEmpty()) {
            cancelDailyReset()
            return
        }

        val nirvanaActive = nirvanaUtils.shouldNirvanaModeBeActive()
        val nirvanaApps = nirvanaUtils.getSelectedApps()
        val blocked = getBlockedToday().toMutableSet()
        val observers = mutableSetOf<String>()

        for (userHandle in userManager.userProfiles) {
            val userId = userHandle.identifier
            val userContext = contextForUser(userHandle) ?: continue
            val usageStatsManager =
                userContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: continue
            val usage = NirvanaUsageStatsHelper.queryTodaySummary(usageStatsManager).usageByPackage

            for ((packageName, minutes) in limits) {
                if (!isInstalledForUser(userContext, packageName)) continue
                if (nirvanaActive && nirvanaApps.contains(packageName)) continue

                val key = "$userId|$packageName"
                val limitMillis = minutes * 60000L
                val usedMillis = usage[packageName] ?: 0L

                if (blocked.contains(key) || usedMillis >= limitMillis) {
                    if (suspendForLimit(packageName, userId)) {
                        blocked.add(key)
                    }
                    continue
                }

                val observerId = observerIdFor(userId, packageName)
                try {
                    usageStatsManager.registerAppUsageLimitObserver(
                        observerId,
                        arrayOf(packageName),
                        Duration.ofMillis(limitMillis),
                        Duration.ofMillis(usedMillis),
                        buildCallbackIntent(packageName, userId, observerId),
                    )
                    observers.add("$userId|$packageName|$observerId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register usage limit observer for $packageName", e)
                }
            }
        }

        saveBlockedToday(blocked)
        saveObservers(observers)
        scheduleDailyReset()
    }

    private fun isOwnedByNirvana(packageName: String): Boolean =
        nirvanaUtils.shouldNirvanaModeBeActive() && nirvanaUtils.getSelectedApps().contains(packageName)

    private fun suspendForLimit(
        packageName: String,
        userId: Int,
    ): Boolean {
        if (isOwnedByNirvana(packageName)) return false

        return try {
            if (nirvanaUtils.isPackageSuspendedForUser(packageName, userId)) {
                nirvanaUtils.isSuspendedByMe(packageName, userId)
            } else {
                nirvanaUtils.applyBatchSuspension(
                    arrayOf(packageName),
                    true,
                    userId,
                    buildDialogInfo(packageName),
                )
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to suspend $packageName for user $userId", e)
            false
        }
    }

    private fun releaseBlocked() {
        for (entry in getBlockedRaw()) {
            val parts = entry.split("|")
            if (parts.size != 2) continue
            val userId = parts[0].toIntOrNull() ?: continue
            releaseForUser(parts[1], userId)
        }
    }

    private fun releaseForAllUsers(packageName: String) {
        for (userHandle in userManager.userProfiles) {
            releaseForUser(packageName, userHandle.identifier)
        }
        val blocked = getBlockedRaw().filter { !it.endsWith("|$packageName") }.toSet()
        saveBlockedToday(blocked)
    }

    private fun releaseForUser(
        packageName: String,
        userId: Int,
    ) {
        if (isOwnedByNirvana(packageName)) return

        try {
            if (nirvanaUtils.isPackageSuspendedForUser(packageName, userId) &&
                nirvanaUtils.isSuspendedByMe(packageName, userId)
            ) {
                nirvanaUtils.applyBatchSuspension(arrayOf(packageName), false, userId, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsuspend $packageName for user $userId", e)
        }
    }

    private fun pruneUninstalled(limits: Map<String, Int>): Map<String, Int> {
        if (limits.isEmpty()) return limits

        val profileContexts = userManager.userProfiles.mapNotNull { contextForUser(it) }
        val kept =
            limits.filterKeys { packageName ->
                profileContexts.any { isInstalledForUser(it, packageName) }
            }

        if (kept.size != limits.size) {
            saveLimits(kept)
            val blocked = getBlockedRaw().filter { entry -> kept.keys.any { entry.endsWith("|$it") } }.toSet()
            saveBlockedToday(blocked)
            Log.i(TAG, "Pruned uninstalled time limited packages")
        }
        return kept
    }

    private fun unregisterAllObservers() {
        val saved = getObservers()
        if (saved.isEmpty()) return

        for (entry in saved) {
            val parts = entry.split("|")
            if (parts.size != 3) continue
            val userId = parts[0].toIntOrNull() ?: continue
            val observerId = parts[2].toIntOrNull() ?: continue
            val userContext = contextForUser(UserHandle.of(userId)) ?: continue
            val usageStatsManager =
                userContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: continue
            try {
                usageStatsManager.unregisterAppUsageLimitObserver(observerId)
            } catch (e: Exception) {
            }
        }
        saveObservers(emptySet())
    }

    private fun observerIdFor(
        userId: Int,
        packageName: String,
    ): Int = OBSERVER_ID_BASE + (((userId * 31) + packageName.hashCode()) and OBSERVER_ID_MASK)

    private fun buildCallbackIntent(
        packageName: String,
        userId: Int,
        observerId: Int,
    ): PendingIntent {
        val intent =
            Intent(context, NirvanaModeReceiver::class.java).apply {
                action = NirvanaModeReceiver.ACTION_NIRVANA_TIME_LIMIT_REACHED
                data = Uri.parse("nirvana-limit://$userId/$packageName")
                putExtra(EXTRA_LIMIT_PACKAGE, packageName)
                putExtra(EXTRA_LIMIT_USER, userId)
            }
        return PendingIntent.getBroadcast(
            context,
            observerId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildDialogInfo(packageName: String): SuspendDialogInfo {
        val label =
            try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                info.loadLabel(context.packageManager).toString()
            } catch (e: Exception) {
                packageName
            }

        val builder =
            SuspendDialogInfo.Builder()
                .setTitle(R.string.nirvana_time_limit_dialog_title)
                .setMessage(context.getString(R.string.nirvana_time_limit_dialog_message, label))

        try {
            val resId = context.resources.getIdentifier("ic_nirvana_mode", "drawable", context.packageName)
            if (resId != 0) builder.setIcon(resId)
        } catch (e: Exception) {
        }

        return builder.build()
    }

    fun scheduleDailyReset() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMidnight(),
            dailyResetIntent(),
        )
    }

    private fun cancelDailyReset() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(dailyResetIntent())
    }

    private fun dailyResetIntent(): PendingIntent {
        val intent =
            Intent(context, NirvanaModeReceiver::class.java).apply {
                action = NirvanaModeReceiver.ACTION_NIRVANA_DAILY_RESET
            }
        return PendingIntent.getBroadcast(
            context,
            DAILY_RESET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextMidnight(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 5)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    private fun rollBlockedStateIfNeeded() {
        val storedDay = Settings.Secure.getInt(resolver, KEY_BLOCKED_DAY, 0)
        if (storedDay != currentDayStamp()) {
            releaseBlocked()
            clearBlockedState()
        }
    }

    private fun currentDayStamp(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun getBlockedToday(): Set<String> {
        val storedDay = Settings.Secure.getInt(resolver, KEY_BLOCKED_DAY, 0)
        if (storedDay != currentDayStamp()) return emptySet()
        return getBlockedRaw()
    }

    private fun getBlockedRaw(): Set<String> {
        val raw = Settings.Secure.getString(resolver, KEY_BLOCKED_TODAY) ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun saveBlockedToday(blocked: Set<String>) {
        Settings.Secure.putString(resolver, KEY_BLOCKED_TODAY, blocked.joinToString(","))
        Settings.Secure.putInt(resolver, KEY_BLOCKED_DAY, currentDayStamp())
    }

    private fun clearBlockedState() {
        Settings.Secure.putString(resolver, KEY_BLOCKED_TODAY, "")
        Settings.Secure.putInt(resolver, KEY_BLOCKED_DAY, currentDayStamp())
    }

    private fun saveLimits(limits: Map<String, Int>) {
        val raw = limits.entries.joinToString(",") { "${it.key}:${it.value}" }
        Settings.Secure.putString(resolver, KEY_LIMITS, raw)
    }

    private fun getObservers(): Set<String> {
        val raw = Settings.Secure.getString(resolver, KEY_OBSERVERS) ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun saveObservers(observers: Set<String>) {
        Settings.Secure.putString(resolver, KEY_OBSERVERS, observers.joinToString(","))
    }

    private fun contextForUser(userHandle: UserHandle): Context? {
        return try {
            if (userHandle.identifier == UserHandle.myUserId()) {
                context
            } else {
                context.createContextAsUser(userHandle, 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isInstalledForUser(
        userContext: Context?,
        packageName: String,
    ): Boolean {
        if (userContext == null) return false
        return try {
            userContext.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
