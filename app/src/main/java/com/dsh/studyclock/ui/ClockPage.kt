package com.dsh.studyclock.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.studyclock.data.Segment
import androidx.compose.foundation.Canvas

@Composable
fun ClockPage(
    state: AppState,
    onOpenSettings: () -> Unit,
    onEditSegment: (Long) -> Unit,
) {
    val running = state.running
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "学习正计时",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = scheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = Fmt.hms(state.currentSessionMs),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 54.sp,
                letterSpacing = 0.sp,
            ),
            color = if (running != null) scheme.primary else scheme.onSurface.copy(alpha = 0.35f),
        )

        Spacer(Modifier.height(6.dp))

        // 未开始时不再显示提示行（按钮本身已经说明）；计时中保留"从几点开始"
        if (running != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在计时 · ${Fmt.clock(running.startMs)} 开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        TodaySummary(state)

        Spacer(Modifier.height(16.dp))

        if (state.longRunningMs > 0L) {
            LongRunWarning(state)
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                if (running != null) {
                    val kept = state.pause()
                    if (!kept) {
                        Toast.makeText(
                            context,
                            "太短了，这一段没记（不足 1 秒）",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    state.start()
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running != null) scheme.inverseSurface else scheme.primary,
                contentColor = if (running != null) scheme.inverseOnSurface else scheme.onPrimary,
            ),
        ) {
            PlayPauseGlyph(running = running != null, color = if (running != null) scheme.inverseOnSurface else scheme.onPrimary)
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (running != null) "暂停并记一段" else "开始计时",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(22.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "今天的分段",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.todaySegments.size} 段",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Spacer(Modifier.height(6.dp))

        if (state.todaySegments.isEmpty()) {
            EmptyToday()
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(state.todaySegments, key = { it.id }) { seg ->
                    SegmentRow(
                        seg = seg,
                        now = state.now,
                        dayMath = state.dayMath,
                        onClick = { onEditSegment(seg.id) },
                    )
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun TodaySummary(state: AppState) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "今天累计",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    Fmt.dur(state.todayTotalMs),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "本段" ,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.running != null) Fmt.dur(state.currentSessionMs) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LongRunWarning(state: AppState) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.errorContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "这一段已经 ${Fmt.dur(state.longRunningMs)}没停了，忘记暂停了吗？",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { state.pause() }) {
                Text("现在暂停", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EmptyToday() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "还没有记录",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "点「开始计时」，每次暂停会自动记一段",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun SegmentRow(
    seg: Segment,
    now: Long,
    dayMath: com.dsh.studyclock.data.DayMath,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val end = seg.endAt(now)
    val startsBefore = seg.startMs < dayMath.dayStart(now)
    val endsAfter = end > dayMath.dayEnd(now)
    val crosses = startsBefore || endsAfter
    val color = segmentColor(((seg.id - 1).mod(8)).toInt())
    // 跨天时：本段在今天这个学习日里只贡献一部分时长
    val todayPart = if (crosses) {
        val s = maxOf(seg.startMs, dayMath.dayStart(now))
        val e = minOf(end, dayMath.dayEnd(now))
        (e - s).coerceAtLeast(0L)
    } else 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 3.dp)
                .width(4.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Fmt.range(seg.startMs, end, crosses),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.onSurface,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = Fmt.dur(seg.durationAt(now)),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (crosses && todayPart > 0 && todayPart < seg.durationAt(now)) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "今日计 ${Fmt.dur(todayPart)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary.copy(alpha = 0.85f),
                    )
                }
                if (seg.category.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = scheme.secondaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = seg.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSecondaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
                if (seg.isRunning) {
                    Spacer(Modifier.width(8.dp))
                    RunningChip()
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = seg.note.ifBlank { "点一下写备注：这段时间做了什么" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (seg.note.isBlank()) scheme.onSurfaceVariant.copy(alpha = 0.55f) else scheme.onSurface.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RunningChip() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = "进行中",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PlayPauseGlyph(running: Boolean, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        drawPlayPause(running, color)
    }
}

private fun DrawScope.drawPlayPause(running: Boolean, color: Color) {
    if (running) {
        val barW = size.width * 0.26f
        val r = CornerRadius(size.width * 0.06f, size.width * 0.06f)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.14f, size.height * 0.08f),
            size = Size(barW, size.height * 0.84f),
            cornerRadius = r,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.60f, size.height * 0.08f),
            size = Size(barW, size.height * 0.84f),
            cornerRadius = r,
        )
    } else {
        val path = Path().apply {
            moveTo(size.width * 0.20f, size.height * 0.06f)
            lineTo(size.width * 0.94f, size.height * 0.5f)
            lineTo(size.width * 0.20f, size.height * 0.94f)
            close()
        }
        drawPath(path, color)
    }
}
