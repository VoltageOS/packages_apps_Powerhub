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

import android.animation.ValueAnimator
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import java.util.ArrayList
import java.util.HashMap

private const val PAYLOAD_HIGHLIGHT = "highlight"

class NirvanaStatsFragment : Fragment(R.layout.nirvana_stats_fragment) {
    private lateinit var usageManager: UsageStatsManager
    private lateinit var packageManager: PackageManager
    private lateinit var scrollView: NestedScrollView
    private lateinit var recycler: RecyclerView
    private lateinit var donutChart: NirvanaDonutChartView
    private lateinit var totalUnlocksText: TextView
    private lateinit var totalNotifsText: TextView

    private var statsAdapter: StatsAdapter? = null
    private var limitMap: Map<String, Int> = emptyMap()
    private val density by lazy { resources.displayMetrics.density }

    private val donutPalette =
        intArrayOf(
            Color.parseColor("#4285F4"),
            Color.parseColor("#EA4335"),
            Color.parseColor("#34A853"),
            Color.parseColor("#FBBC05"),
            Color.parseColor("#A142F4"),
        )
    private val donutOtherColor = Color.parseColor("#9AA0A6")

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

        scrollView = view as NestedScrollView
        recycler = view.findViewById(R.id.rv_stats_list)
        donutChart = view.findViewById(R.id.donut_chart)
        totalUnlocksText = view.findViewById(R.id.tv_total_unlocks)
        totalNotifsText = view.findViewById(R.id.tv_total_notifs)

        recycler.layoutManager = LinearLayoutManager(context)

        donutChart.onSegmentSelected = { segment ->
            val packageName = segment?.key
            if (packageName.isNullOrEmpty()) {
                statsAdapter?.highlight(null)
            } else {
                statsAdapter?.highlight(packageName)
                scrollToApp(packageName)
            }
        }

        limitMap = NirvanaTimeLimitUtils(requireContext()).getLimits()

        loadStats()
    }

    private fun scrollToApp(packageName: String) {
        val position = statsAdapter?.indexOf(packageName) ?: -1
        if (position < 0) return
        recycler.post {
            val viewHolder = recycler.findViewHolderForAdapterPosition(position)
            if (viewHolder != null) {
                val targetY = (recycler.top + viewHolder.itemView.top - 24f * density).toInt().coerceAtLeast(0)
                scrollView.smoothScrollTo(0, targetY)
            }
        }
    }

    private fun loadStats() {
        Thread {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val launcherPackage = resolveInfo?.activityInfo?.packageName

            val dailySummary = NirvanaUsageStatsHelper.queryTodaySummary(usageManager)
            val usageMap = HashMap(dailySummary.usageByPackage)
            val notificationMap = HashMap(dailySummary.notificationCountByPackage)
            val unlockCount = dailySummary.unlockCount

            val mergedList = ArrayList<AppStat>()

            usageMap.forEach { (packageName, time) ->
                mergedList.add(AppStat(packageName, time, notificationMap[packageName] ?: 0))
            }

            notificationMap.forEach { (packageName, count) ->
                if (!usageMap.containsKey(packageName)) {
                    mergedList.add(AppStat(packageName, 0, count))
                }
            }

            val userAppsList =
                mergedList.filter { stat ->
                    try {
                        if (stat.packageName == launcherPackage) return@filter false
                        if (stat.packageName == "com.android.systemui") return@filter false

                        packageManager.getLaunchIntentForPackage(stat.packageName) != null
                    } catch (e: Exception) {
                        false
                    }
                }

            val totalTime = userAppsList.sumOf { it.timeMillis }
            val totalNotifications = mergedList.sumOf { it.notificationCount }

            val timeSorted = userAppsList.filter { it.timeMillis > 0 }.sortedByDescending { it.timeMillis }
            val topCount = 4
            val donutSegments = ArrayList<NirvanaDonutChartView.Segment>()
            timeSorted.take(topCount).forEachIndexed { index, stat ->
                donutSegments.add(
                    NirvanaDonutChartView.Segment(
                        label = getLabel(stat.packageName),
                        value = stat.timeMillis.toFloat(),
                        color = donutPalette[index % donutPalette.size],
                        displayValue = formatDuration(stat.timeMillis, true),
                        key = stat.packageName,
                    ),
                )
            }
            val otherSum = timeSorted.drop(topCount).sumOf { it.timeMillis }
            if (otherSum > 0) {
                donutSegments.add(
                    NirvanaDonutChartView.Segment(
                        label = "Other",
                        value = otherSum.toFloat(),
                        color = donutOtherColor,
                        displayValue = formatDuration(otherSum, true),
                        key = "",
                    ),
                )
            }

            val displayList =
                userAppsList.filter {
                    it.timeMillis > 0 || it.notificationCount > 0
                }.sortedWith(
                    Comparator { a, b ->
                        if (a.timeMillis != b.timeMillis) {
                            b.timeMillis.compareTo(a.timeMillis)
                        } else {
                            b.notificationCount.compareTo(a.notificationCount)
                        }
                    },
                )

            val finalUiList =
                displayList.take(30).map { stat ->
                    stat.label = getLabel(stat.packageName)
                    stat.icon = getIcon(stat.packageName)
                    stat
                }

            val maxTime = if (displayList.isNotEmpty()) displayList[0].timeMillis.toFloat() else 1f

            Handler(Looper.getMainLooper()).post {
                if (isAdded) {
                    updateUI(unlockCount, totalNotifications, totalTime, donutSegments, finalUiList, maxTime)
                }
            }
        }.start()
    }

    private fun updateUI(
        unlocks: Int,
        totalNotifications: Int,
        totalTime: Long,
        donutSegments: List<NirvanaDonutChartView.Segment>,
        list: List<AppStat>,
        maxTime: Float,
    ) {
        statsAdapter = StatsAdapter(list, maxTime)
        recycler.adapter = statsAdapter

        donutChart.clearSelection()
        donutChart.setData(donutSegments, "Today", formatDuration(totalTime, true))

        totalUnlocksText.text = unlocks.toString()
        totalNotifsText.text = totalNotifications.toString()
    }

    private fun formatDuration(
        millis: Long,
        full: Boolean,
    ): String {
        if (millis < 60000) {
            return if (full) "0m" else getString(R.string.nirvana_time_fmt_less_min)
        }
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000

        return if (hours > 0) {
            getString(R.string.nirvana_time_fmt_hm, hours, minutes)
        } else {
            getString(R.string.nirvana_time_fmt_m, minutes)
        }
    }

    private fun getLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun getIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            requireContext().getDrawable(android.R.drawable.sym_def_app_icon)
        }
    }

    data class AppStat(
        val packageName: String,
        val timeMillis: Long,
        val notificationCount: Int,
        var label: String = "",
        var icon: Drawable? = null,
        var dominantColor: Int? = null,
    )

    inner class StatsAdapter(
        private val list: List<AppStat>,
        private val maxTime: Float,
    ) : RecyclerView.Adapter<StatsAdapter.ViewHolder>() {
        private var highlightedPackage: String? = null

        fun highlight(packageName: String?) {
            if (highlightedPackage == packageName) return
            val oldPosition = highlightedPackage?.let { indexOf(it) } ?: -1
            highlightedPackage = packageName
            val newPosition = highlightedPackage?.let { indexOf(it) } ?: -1

            if (oldPosition >= 0) {
                notifyItemChanged(oldPosition, PAYLOAD_HIGHLIGHT)
            }
            if (newPosition >= 0 && newPosition != oldPosition) {
                notifyItemChanged(newPosition, PAYLOAD_HIGHLIGHT)
            }
        }

        fun indexOf(packageName: String): Int = list.indexOfFirst { it.packageName == packageName }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val time: TextView = view.findViewById(R.id.app_time)
            val notificationContainer: View = view.findViewById(R.id.notif_container)
            val notificationCount: TextView = view.findViewById(R.id.notif_count)
            val progress: ProgressBar = view.findViewById(R.id.usage_progress)

            init {
                progress.progressDrawable = progress.progressDrawable?.mutate()
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.nirvana_stats_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
            payloads: MutableList<Any>,
        ) {
            if (payloads.contains(PAYLOAD_HIGHLIGHT)) {
                val item = list[position]
                val dominantColor = item.dominantColor ?: extractDominantColor(item).also { item.dominantColor = it }
                applyRowChrome(holder, item, dominantColor)
                return
            }
            super.onBindViewHolder(holder, position, payloads)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val item = list[position]

            holder.name.text = item.label

            val limitMinutes = limitMap[item.packageName] ?: 0
            holder.time.text =
                if (limitMinutes > 0) {
                    getString(
                        R.string.nirvana_time_limit_used_of_total,
                        formatDuration(item.timeMillis, false),
                        formatDuration(limitMinutes * 60000L, false),
                    )
                } else {
                    formatDuration(item.timeMillis, false)
                }
            item.icon?.let { holder.icon.setImageDrawable(it) }

            val dominantColor = item.dominantColor ?: extractDominantColor(item).also { item.dominantColor = it }
            applyRowChrome(holder, item, dominantColor)

            val params = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = 24
                holder.itemView.layoutParams = params
            }

            if (item.notificationCount > 0) {
                holder.notificationContainer.visibility = View.VISIBLE
                holder.notificationCount.text = item.notificationCount.toString()
            } else {
                holder.notificationContainer.visibility = View.GONE
            }

            val limitMillis = limitMinutes * 60000f
            val targetProgress =
                if (limitMillis > 0f) {
                    ((item.timeMillis / limitMillis) * 100).toInt().coerceIn(0, 100)
                } else if (maxTime > 0) {
                    ((item.timeMillis / maxTime) * 100).toInt()
                } else {
                    0
                }
            val safeProgress = targetProgress.coerceAtLeast(if (item.timeMillis > 0) 1 else 0)

            holder.progress.progress = 0
            val animator = ValueAnimator.ofInt(0, safeProgress)
            animator.duration = 600
            animator.interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            animator.addUpdateListener {
                holder.progress.progress = it.animatedValue as Int
            }
            animator.start()

            holder.itemView.setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                view.animate()
                    .scaleX(1.03f)
                    .scaleY(1.03f)
                    .setDuration(120)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180)
                            .setInterpolator(android.view.animation.BounceInterpolator())
                            .withEndAction {
                                val intent =
                                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${item.packageName}")
                                    }
                                view.context.startActivity(intent)
                            }
                            .start()
                    }
                    .start()
            }
        }

        private fun extractDominantColor(item: AppStat): Int {
            var dominantColor = Color.parseColor("#888888")
            val icon = item.icon ?: return dominantColor

            try {
                val bitmap =
                    if (icon is BitmapDrawable) {
                        icon.bitmap
                    } else {
                        val intrinsicWidth = icon.intrinsicWidth.coerceAtLeast(1)
                        val intrinsicHeight = icon.intrinsicHeight.coerceAtLeast(1)
                        val targetWidth = if (intrinsicWidth > 64) 64 else intrinsicWidth
                        val targetHeight = if (intrinsicHeight > 64) 64 else intrinsicHeight

                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        icon.setBounds(0, 0, canvas.width, canvas.height)
                        icon.draw(canvas)
                        bitmap
                    }

                val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
                val hsv = FloatArray(3)
                var bestScore = 0f
                var selectedColor = dominantColor

                for (x in 0 until 8) {
                    for (y in 0 until 8) {
                        val pixelColor = scaled.getPixel(x, y)
                        val alpha = Color.alpha(pixelColor)
                        if (alpha < 200) continue

                        Color.colorToHSV(pixelColor, hsv)
                        val saturation = hsv[1]
                        val value = hsv[2]

                        if (saturation > 0.3f && value > 0.3f) {
                            val score = saturation * value
                            if (score > bestScore) {
                                bestScore = score
                                selectedColor = pixelColor
                            }
                        }
                    }
                }
                dominantColor =
                    if (bestScore > 0f) {
                        selectedColor
                    } else {
                        Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
                    }
            } catch (e: Exception) {
            }

            return dominantColor
        }

        private fun applyRowChrome(
            holder: ViewHolder,
            item: AppStat,
            dominantColor: Int,
        ) {
            val isHighlighted = item.packageName == highlightedPackage
            val backgroundAlpha = if (isHighlighted) 56 else 20
            val rowColor = Color.argb(backgroundAlpha, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor))
            val background = GradientDrawable()
            background.setColor(rowColor)
            background.cornerRadius = 32f
            if (isHighlighted) {
                background.setStroke((2f * density).toInt(), dominantColor)
            }
            holder.itemView.background = background

            holder.progress.progressTintList =
                android.content.res.ColorStateList.valueOf(dominantColor)
            holder.progress.progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.argb(45, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor)),
                )

            if (item.notificationCount > 0) {
                holder.notificationContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(rowColor)
                holder.notificationCount.setTextColor(dominantColor)
                holder.itemView.findViewById<ImageView>(R.id.notif_icon).setColorFilter(dominantColor)
            }
        }

        override fun getItemCount() = list.size
    }
}
