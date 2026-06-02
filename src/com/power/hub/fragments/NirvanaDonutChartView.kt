/*
 * Copyright (C) 2026 VoltageOS
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.power.hub.fragments

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val ELLIPSIS = "\u2026"

class NirvanaDonutChartView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        data class Segment(
            val label: String,
            val value: Float,
            val color: Int,
            val displayValue: String = "",
            val key: String = "",
        )

        var onSegmentSelected: ((Segment?) -> Unit)? = null

        private val density = resources.displayMetrics.density

        private val ringWidth = 14f * density
        private val gapDegrees = 5f
        private val labelReserve = 40f * density
        private val labelOffset = 10f * density
        private val labelReserveVertical = 30f * density

        private var segments: List<Segment> = emptyList()
        private val segmentBounds = ArrayList<Pair<Float, Float>>()
        private var centerTitle: String = "TODAY"
        private var centerValue: String = ""

        private var selectedIndex: Int = -1
        private var sweepProgress: Float = 0f
        private var selectionProgress: Float = 1f
        private var sweepAnimator: ValueAnimator? = null
        private var selectionAnimator: ValueAnimator? = null

        private val arcPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = ringWidth
            }

        private val trackPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ringWidth
            }

        private val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f * density
            }

        private val centerTitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f * density
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.10f
            }

        private val centerSelectedTitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f * density
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

        private val centerValuePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 34f * density
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

        private val oval = RectF()

        init {
            isClickable = true
            trackPaint.color = resolveAttrColor(android.R.attr.colorControlHighlight)
            centerValuePaint.color = resolveAttrColor(android.R.attr.textColorPrimary)
            centerTitlePaint.color = resolveAttrColor(android.R.attr.textColorSecondary)
            centerSelectedTitlePaint.color = resolveAttrColor(android.R.attr.colorAccent)
            labelPaint.color = resolveAttrColor(android.R.attr.textColorSecondary)
        }

        fun setData(
            segments: List<Segment>,
            centerTitle: String,
            centerValue: String,
        ) {
            this.segments = segments
            this.centerTitle = centerTitle.uppercase()
            this.centerValue = centerValue
            selectedIndex = -1
            recomputeBounds()
            startSweepAnimation()
        }

        fun setSelectedIndex(
            index: Int,
            notify: Boolean = true,
        ) {
            val newIndex = if (index in segments.indices) index else -1
            selectedIndex = newIndex
            if (newIndex >= 0) {
                startSelectionAnimation()
            } else {
                invalidate()
            }
            if (notify) {
                onSegmentSelected?.invoke(if (newIndex >= 0) segments[newIndex] else null)
            }
        }

        fun clearSelection() = setSelectedIndex(-1, notify = false)

        private fun resolveAttrColor(attr: Int): Int {
            val tv = TypedValue()
            context.theme.resolveAttribute(attr, tv, true)
            return if (tv.resourceId != 0) {
                try {
                    resources.getColor(tv.resourceId, context.theme)
                } catch (e: Exception) {
                    tv.data
                }
            } else {
                tv.data
            }
        }

        private fun recomputeBounds() {
            segmentBounds.clear()
            val total = segments.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return
            val sweepAvailable = 360f - gapDegrees * segments.size
            var start = -90f
            for (seg in segments) {
                val sweep = (seg.value / total) * sweepAvailable
                segmentBounds.add(Pair(start + gapDegrees / 2f, sweep))
                start += sweep + gapDegrees
            }
        }

        private fun startSweepAnimation() {
            sweepAnimator?.cancel()
            sweepProgress = 0f
            sweepAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 850
                    interpolator = DecelerateInterpolator(1.4f)
                    addUpdateListener {
                        sweepProgress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        }

        private fun startSelectionAnimation() {
            selectionAnimator?.cancel()
            selectionProgress = 0f
            selectionAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 280
                    interpolator = OvershootInterpolator(2f)
                    addUpdateListener {
                        selectionProgress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val contentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
            val radius = (contentWidth / 2f - ringWidth / 2f - labelReserve).coerceAtLeast(0f)
            val desiredHeight =
                (2f * radius + ringWidth + 2f * labelReserveVertical).toInt() +
                    paddingTop + paddingBottom
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY),
            )
        }

        private fun computeGeometry(): Triple<Float, Float, Float>? {
            val contentWidth = width - paddingLeft - paddingRight
            val contentHeight = height - paddingTop - paddingBottom
            if (contentWidth <= 0 || contentHeight <= 0) return null
            val centerX = paddingLeft + contentWidth / 2f
            val centerY = paddingTop + contentHeight / 2f
            val radiusByWidth = contentWidth / 2f - ringWidth / 2f - labelReserve
            val radiusByHeight = contentHeight / 2f - ringWidth / 2f - labelReserveVertical
            val radius = min(radiusByWidth, radiusByHeight)
            if (radius <= 0f) return null
            return Triple(centerX, centerY, radius)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val geometry = computeGeometry() ?: return
            val centerX = geometry.first
            val centerY = geometry.second
            val radius = geometry.third

            canvas.drawCircle(centerX, centerY, radius, trackPaint)

            val showSelection = selectedIndex in segments.indices
            val titleText =
                if (showSelection) {
                    segments[selectedIndex].label.let { if (it.length > 16) it.take(15) + ELLIPSIS else it }
                } else {
                    centerTitle
                }
            val valueText = if (showSelection) segments[selectedIndex].displayValue else centerValue
            canvas.drawText(
                titleText,
                centerX,
                centerY - 6f * density,
                if (showSelection) centerSelectedTitlePaint else centerTitlePaint,
            )
            canvas.drawText(valueText, centerX, centerY + centerValuePaint.textSize * 0.55f, centerValuePaint)

            val total = segments.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f || segments.isEmpty() || segmentBounds.size != segments.size) return

            oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

            val labelAlpha = (((sweepProgress - 0.55f) / 0.45f).coerceIn(0f, 1f) * 255).toInt()

            for (i in segments.indices) {
                val segmentStart = segmentBounds[i].first
                val segmentSweep = segmentBounds[i].second
                val animatedSweep = segmentSweep * sweepProgress
                val selected = i == selectedIndex
                val dimmed = selectedIndex >= 0 && !selected

                arcPaint.color = segments[i].color
                arcPaint.alpha = if (dimmed) 90 else 255
                arcPaint.strokeWidth = if (selected) ringWidth * (1f + 0.45f * selectionProgress) else ringWidth

                canvas.drawArc(oval, segmentStart, animatedSweep, false, arcPaint)

                if (labelAlpha > 0 && !dimmed) {
                    labelPaint.alpha = labelAlpha
                    drawLabel(canvas, segments[i].label, segmentStart + segmentSweep / 2f, centerX, centerY, radius)
                }
            }

            arcPaint.alpha = 255
            arcPaint.strokeWidth = ringWidth
            labelPaint.alpha = 255
        }

        private fun drawLabel(
            canvas: Canvas,
            text: String,
            angleDeg: Float,
            centerX: Float,
            centerY: Float,
            radius: Float,
        ) {
            val radians = Math.toRadians(angleDeg.toDouble())
            val cosValue = cos(radians)
            val sinValue = sin(radians)
            val labelRadius = radius + ringWidth / 2f + labelOffset

            val labelX = centerX + (labelRadius * cosValue).toFloat()
            val labelY = centerY + (labelRadius * sinValue).toFloat()

            labelPaint.textAlign =
                when {
                    cosValue > 0.2 -> Paint.Align.LEFT
                    cosValue < -0.2 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }

            val horizontalInset = 4f * density
            val maxLabelWidth =
                when (labelPaint.textAlign) {
                    Paint.Align.LEFT -> width - labelX - horizontalInset
                    Paint.Align.RIGHT -> labelX - horizontalInset
                    Paint.Align.CENTER -> (min(labelX, width - labelX) - horizontalInset) * 2f
                }.coerceAtLeast(0f)
            val fitted = fitLabel(text, maxLabelWidth)
            if (fitted.isNotEmpty()) {
                canvas.drawText(fitted, labelX, labelY + labelPaint.textSize / 3f, labelPaint)
            }
        }

        private fun fitLabel(
            text: String,
            maxWidth: Float,
        ): String {
            if (labelPaint.measureText(text) <= maxWidth) return text
            if (labelPaint.measureText(ELLIPSIS) > maxWidth) return ""

            var end = text.length.coerceAtMost(13)
            while (end > 0) {
                val candidate = text.take(end) + ELLIPSIS
                if (labelPaint.measureText(candidate) <= maxWidth) {
                    return candidate
                }
                end--
            }
            return ELLIPSIS
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_UP -> {
                    val index = findSegmentAt(event.x, event.y)
                    if (index >= 0) {
                        performClick()
                        setSelectedIndex(if (index == selectedIndex) -1 else index)
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun findSegmentAt(
            x: Float,
            y: Float,
        ): Int {
            if (segmentBounds.size != segments.size || segments.isEmpty()) return -1
            val geometry = computeGeometry() ?: return -1
            val centerX = geometry.first
            val centerY = geometry.second
            val radius = geometry.third

            val dx = x - centerX
            val dy = y - centerY
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val band = ringWidth * 1.7f
            if (distance < radius - band || distance > radius + band) return -1

            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (angle < 0f) angle += 360f

            for (i in segmentBounds.indices) {
                val segmentStart = segmentBounds[i].first
                val segmentSweep = segmentBounds[i].second
                if (angleInRange(angle, segmentStart - gapDegrees / 2f, segmentStart + segmentSweep + gapDegrees / 2f)) {
                    return i
                }
            }
            return -1
        }

        private fun angleInRange(
            angle: Float,
            startRaw: Float,
            endRaw: Float,
        ): Boolean {
            val normalizedAngle = normalize360(angle)
            val normalizedStart = normalize360(startRaw)
            val normalizedEnd = normalize360(endRaw)
            return if (normalizedStart <= normalizedEnd) {
                normalizedAngle in normalizedStart..normalizedEnd
            } else {
                normalizedAngle >= normalizedStart || normalizedAngle <= normalizedEnd
            }
        }

        private fun normalize360(value: Float): Float {
            var normalized = value % 360f
            if (normalized < 0f) normalized += 360f
            return normalized
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            sweepAnimator?.cancel()
            selectionAnimator?.cancel()
        }
    }
