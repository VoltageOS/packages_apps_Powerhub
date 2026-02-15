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
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import java.util.ArrayList
import java.util.Calendar
import java.util.HashMap

class NirvanaStatsFragment : Fragment(R.layout.nirvana_stats_fragment) {
    private lateinit var usageManager: UsageStatsManager
    private lateinit var packageManager: PackageManager
    private lateinit var recycler: RecyclerView
    private lateinit var totalTimeText: TextView
    private lateinit var totalUnlocksText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usageManager = requireContext().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        packageManager = requireContext().packageManager
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.nirvana_stats_title)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.rv_stats_list)
        totalTimeText = view.findViewById(R.id.tv_total_time)
        totalUnlocksText = view.findViewById(R.id.tv_total_unlocks)

        recycler.layoutManager = LinearLayoutManager(context)

        loadStats()
    }

    private fun loadStats() {
        Thread {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            val end = System.currentTimeMillis()

            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val launcherPkg = resolveInfo?.activityInfo?.packageName

            val usageMap = HashMap<String, Long>()
            val notifMap = HashMap<String, Int>()
            var unlockCount = 0

            val aggStats = usageManager.queryAndAggregateUsageStats(start, end)
            aggStats.forEach { (pkg, stats) ->
                if (stats.totalTimeInForeground > 0) {
                    usageMap[pkg] = stats.totalTimeInForeground
                }
            }

            val events = usageManager.queryEvents(start, end)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        unlockCount++
                    }
                    12 -> {
                        val pkg = event.packageName
                        notifMap[pkg] = (notifMap[pkg] ?: 0) + 1
                    }
                }
            }

            val mergedList = ArrayList<AppStat>()

            usageMap.forEach { (pkg, time) ->
                mergedList.add(AppStat(pkg, time, notifMap[pkg] ?: 0))
            }

            notifMap.forEach { (pkg, count) ->
                if (!usageMap.containsKey(pkg)) {
                    mergedList.add(AppStat(pkg, 0, count))
                }
            }

            val userAppsList =
                mergedList.filter { stat ->
                    try {
                        if (stat.pkg == launcherPkg) return@filter false

                        if (stat.pkg == "com.android.settings" || stat.pkg == "com.android.systemui") return@filter false

                        val info = packageManager.getApplicationInfo(stat.pkg, 0)
                        val isSystem =
                            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                        !isSystem
                    } catch (e: Exception) {
                        false
                    }
                }

            val totalTime = userAppsList.sumOf { it.timeMillis }

            val displayList =
                userAppsList.filter {
                    it.timeMillis > 300000 || it.notifCount > 10
                }.sortedWith(
                    Comparator { a, b ->
                        if (a.timeMillis != b.timeMillis) {
                            b.timeMillis.compareTo(a.timeMillis)
                        } else {
                            b.notifCount.compareTo(a.notifCount)
                        }
                    },
                )

            val finalUiList =
                displayList.take(30).map { stat ->
                    stat.label = getLabel(stat.pkg)
                    stat.icon = getIcon(stat.pkg)
                    stat
                }

            val maxTime = if (displayList.isNotEmpty()) displayList[0].timeMillis.toFloat() else 1f

            Handler(Looper.getMainLooper()).post {
                if (isAdded) {
                    updateUI(unlockCount, totalTime, finalUiList, maxTime)
                }
            }
        }.start()
    }

    private fun updateUI(
        unlocks: Int,
        totalTime: Long,
        list: List<AppStat>,
        maxTime: Float,
    ) {
        totalUnlocksText.text = unlocks.toString()
        totalTimeText.text = formatDuration(totalTime, true)
        recycler.adapter = StatsAdapter(list, maxTime)
    }

    private fun formatDuration(
        millis: Long,
        full: Boolean,
    ): String {
        if (millis < 60000) {
            return if (full) "0m" else getString(R.string.nirvana_time_fmt_less_min)
        }
        val h = millis / 3600000
        val m = (millis % 3600000) / 60000

        return if (h > 0) {
            getString(R.string.nirvana_time_fmt_hm, h, m)
        } else {
            getString(R.string.nirvana_time_fmt_m, m)
        }
    }

    private fun getLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            info.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun getIcon(pkg: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            requireContext().getDrawable(android.R.drawable.sym_def_app_icon)
        }
    }

    data class AppStat(
        val pkg: String,
        val timeMillis: Long,
        val notifCount: Int,
        var label: String = "",
        var icon: Drawable? = null,
    )

    inner class StatsAdapter(
        private val list: List<AppStat>,
        private val maxTime: Float,
    ) : RecyclerView.Adapter<StatsAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.app_icon)
            val name: TextView = v.findViewById(R.id.app_name)
            val time: TextView = v.findViewById(R.id.app_time)
            val notifContainer: View = v.findViewById(R.id.notif_container)
            val notifCount: TextView = v.findViewById(R.id.notif_count)
            val progress: ProgressBar = v.findViewById(R.id.usage_progress)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): VH {
            val v =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.nirvana_stats_item, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(
            holder: VH,
            position: Int,
        ) {
            val item = list[position]

            holder.name.text = item.label
            holder.time.text = formatDuration(item.timeMillis, false)
            if (item.icon != null) {
                holder.icon.setImageDrawable(item.icon)
            }

            if (item.notifCount > 0) {
                holder.notifContainer.visibility = View.VISIBLE
                holder.notifCount.text = item.notifCount.toString()
            } else {
                holder.notifContainer.visibility = View.GONE
            }

            val progress = if (maxTime > 0) ((item.timeMillis / maxTime) * 100).toInt() else 0
            holder.progress.progress = progress.coerceAtLeast(if (item.timeMillis > 0) 1 else 0)
        }

        override fun getItemCount() = list.size
    }
}
