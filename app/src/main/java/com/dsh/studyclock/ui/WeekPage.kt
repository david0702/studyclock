package com.dsh.studyclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HourHeight = 56.dp
private val AxisWidth = 34.dp

/** 短于这个长度的段算"碎片"：周历里抬到最小可见高度并描边。 */
private const val FRAGMENT_MS = 10 * 60 * 1000L

@Composable
fun WeekPage(state: AppState, onEditSegment: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val dayMath = state.dayMath
    val today = dayMath.studyDate(state.now)
    val monday = remember(weekOffset, today, state.rolloverHour) {
        dayMath.mondayOf(today).plusWeeks(weekOffset.toLong())
    }
    val minuteKey = state.now / 60_000L
    val columns = remember(state.version, monday, state.rolloverHour, minuteKey) {
        state.week(monday)
    }
    val weekTotal = columns.sumOf { it.totalMs }
    val isThisWeek = weekOffset == 0

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { weekOffset -= 1 }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一周")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Fmt.weekRange(columns.first().windowStart, columns.last().windowStart),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isThisWeek) "本周合计 ${Fmt.dur(weekTotal)}" else "合计 ${Fmt.dur(weekTotal)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { if (weekOffset < 0) weekOffset += 1 },
                enabled = weekOffset < 0,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = "下一周",
                    tint = if (weekOffset < 0) scheme.onSurface else scheme.onSurface.copy(alpha = 0.25f),
                )
            }
        }

        // 星期 + 日期 + 每天合计
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
        ) {
            Spacer(Modifier.width(AxisWidth))
            columns.forEach { col ->
                val isToday = col.date == today
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = weekdayLabel(col),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) scheme.primary else scheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isToday) scheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = col.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) scheme.onPrimary else scheme.onSurface,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (col.totalMs > 0) Fmt.durShort(col.totalMs) else "—",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }

        val density = LocalDensity.current
        val scroll = rememberScrollState()
        val hourPx = with(density) { HourHeight.toPx() }

        LaunchedEffect(monday.toEpochDay(), state.rolloverHour) {
            // 打开时滚到"今天当前时刻"或"本周第一块"附近
            val anchor = run {
                val todayCol = columns.firstOrNull { it.date == today }
                if (todayCol != null) {
                    ((state.now - todayCol.windowStart) / 3_600_000L).toInt() - 2
                } else {
                    val first = columns.flatMap { it.blocks }.minByOrNull { it.startMs }
                    if (first == null) 7
                    else {
                        val col = columns.first { c -> c.blocks.any { it.segmentId == first.segmentId && it.startMs == first.startMs } }
                        ((first.startMs - col.windowStart) / 3_600_000L).toInt() - 1
                    }
                }
            }.coerceIn(0, 23)
            scroll.scrollTo((anchor * hourPx).toInt())
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Row {
                    HourAxis(state.rolloverHour, HourHeight)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(HourHeight * 24)
                    ) {
                        WeekGrid(
                            columns = columns,
                            now = state.now,
                            today = today,
                            onTapBlock = { onEditSegment(it) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Text(
            text = "点色块可写/改备注 · 竖着是时间，横着是一周七天",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HourAxis(rolloverHour: Int, hourHeight: Dp) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.width(AxisWidth)) {
        repeat(24) { i ->
            val label = ((rolloverHour + i) % 24).toString().padStart(2, '0')
            Box(
                modifier = Modifier.height(hourHeight).fillMaxWidth(),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(
    columns: List<DayColumn>,
    now: Long,
    today: java.time.LocalDate,
    onTapBlock: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outline
    val primary = scheme.primary
    val gapPx = with(LocalDensity.current) { 1.5.dp.toPx() }
    val cornerPx = with(LocalDensity.current) { 3.dp.toPx() }
    val todayTint = primary.copy(alpha = 0.05f)
    val todayIndex = columns.indexOfFirst { it.date == today }

    // 色块上的"大类"文字用原生 canvas 画，避免实验性 API
    val catPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD,
            )
            color = android.graphics.Color.WHITE
        }
    }
    val catTextPaint = remember { android.text.TextPaint() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(columns) {
                detectTapGestures { pos ->
                    val colW = size.width / 7f
                    val colIndex = (pos.x / colW).toInt().coerceIn(0, 6)
                    val col = columns[colIndex]
                    val win = (col.windowEnd - col.windowStart).toFloat()
                    val gridH = size.height.toFloat()
                    val minFragmentPx = 8.dp.toPx()

                    fun topOf(b: WeekBlock): Float =
                        (((b.startMs - col.windowStart) / win) * gridH)
                            .let { raw ->
                                val rawBottom = ((b.endMs - col.windowStart) / win) * gridH
                                val isFragment = (b.endMs - b.startMs) < FRAGMENT_MS
                                val h = (rawBottom - raw).coerceAtLeast(if (isFragment) minFragmentPx else 2.5f)
                                if (raw + h > gridH) (gridH - h).coerceAtLeast(0f) else raw
                            }

                    fun heightOf(b: WeekBlock): Float {
                        val raw = ((b.startMs - col.windowStart) / win) * gridH
                        val rawBottom = ((b.endMs - col.windowStart) / win) * gridH
                        val isFragment = (b.endMs - b.startMs) < FRAGMENT_MS
                        return (rawBottom - raw).coerceAtLeast(if (isFragment) minFragmentPx else 2.5f)
                    }

                    val hit = col.blocks
                        .filter { b ->
                            val top = topOf(b)
                            pos.y >= top - 6f && pos.y <= top + heightOf(b) + 6f
                        }
                        .minByOrNull { b -> heightOf(b) }
                    if (hit != null) onTapBlock(hit.segmentId)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val colW = w / 7f

            // 今天那一列的淡色底
            if (todayIndex >= 0) {
                drawRect(
                    color = todayTint,
                    topLeft = Offset(colW * todayIndex, 0f),
                    size = Size(colW, h),
                )
            }

            // 横向小时线
            repeat(25) { i ->
                val y = (h / 24f) * i
                drawLine(
                    color = outline.copy(alpha = if (i % 6 == 0) 0.22f else 0.10f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (i % 6 == 0) 1.2f else 0.8f,
                )
            }

            // 纵向列分隔线
            repeat(8) { i ->
                val x = colW * i
                drawLine(
                    color = outline.copy(alpha = 0.10f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 0.8f,
                )
            }

            // 色块
            val minFragmentPx = 8.dp.toPx()
            val textPad = 3.dp.toPx()
            columns.forEachIndexed { index, col ->
                val win = (col.windowEnd - col.windowStart).toFloat()
                col.blocks.forEach { block ->
                    val rawTop = ((block.startMs - col.windowStart) / win) * h
                    val rawBottom = ((block.endMs - col.windowStart) / win) * h
                    val isFragment = (block.endMs - block.startMs) < FRAGMENT_MS
                    // 碎片段（<10 分钟）抬高到最小可见高度，否则在 24 小时轴上只剩一条线
                    var height = (rawBottom - rawTop)
                        .coerceAtLeast(if (isFragment) minFragmentPx else 2.5f)
                        .coerceAtMost(h)
                    var top = rawTop
                    if (top + height > h) top = (h - height).coerceAtLeast(0f)
                    val color = segmentColor(block.colorIndex)
                    val x = colW * index + gapPx
                    val bw = colW - gapPx * 2
                    drawRoundRect(
                        color = if (block.isRunning) color else color.copy(alpha = 0.88f),
                        topLeft = Offset(x, top),
                        size = Size(bw, height),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                    )
                    if (isFragment) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(x + 1f, top + 1f),
                            size = Size(
                                (bw - 2f).coerceAtLeast(1f),
                                (height - 2f).coerceAtLeast(1f),
                            ),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f),
                        )
                    }
                    if (block.isRunning) {
                        // 进行中的块描一圈细白边，和已结束的块区分开
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.75f),
                            topLeft = Offset(x + 1.5f, top + 1.5f),
                            size = Size(
                                (bw - 3f).coerceAtLeast(1f),
                                (height - 3f).coerceAtLeast(1f),
                            ),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                        )
                    }
                    // 大类短标签（块够高才画，避免糊成一团）
                    if (block.category.isNotBlank() && height >= 13.dp.toPx()) {
                        catPaint.textSize = 9.sp.toPx()
                        catTextPaint.set(catPaint)
                        val maxW = (bw - textPad * 2).coerceAtLeast(1f)
                        val shown = android.text.TextUtils.ellipsize(
                            block.category,
                            catTextPaint,
                            maxW,
                            android.text.TextUtils.TruncateAt.END,
                        )
                        val fm = catPaint.fontMetrics
                        drawContext.canvas.nativeCanvas.drawText(
                            shown.toString(),
                            x + textPad,
                            top + 2.dp.toPx() - fm.ascent,
                            catPaint,
                        )
                    }
                }
            }

            // 当前时刻线
            if (todayIndex >= 0) {
                val win = (columns[todayIndex].windowEnd - columns[todayIndex].windowStart).toFloat()
                val y = ((now - columns[todayIndex].windowStart) / win) * h
                val x = colW * todayIndex
                drawLine(
                    color = Color(0xFFE5484D),
                    start = Offset(x, y),
                    end = Offset(x + colW, y),
                    strokeWidth = 2f,
                )
                drawCircle(
                    color = Color(0xFFE5484D),
                    radius = 3.2.dp.toPx(),
                    center = Offset(x + 2f, y),
                )
            }
        }
    }
}

private fun weekdayLabel(col: DayColumn): String =
    when (col.date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
