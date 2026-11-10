package com.dsh.studyclock.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dsh.studyclock.data.DayMath
import com.dsh.studyclock.data.Habit
import com.dsh.studyclock.data.HabitRepo
import java.time.LocalDate

/**
 * 每日必做清单：每天自动出现同样几项，打勾即算完成（不记时段，凭自觉）。
 */
@Composable
fun HabitPage(repo: HabitRepo, dayMath: DayMath, now: Long) {
    val scheme = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    val today: LocalDate = dayMath.studyDate(now)
    val dayKey = today.toString()

    val habits = remember(tick, dayKey) { repo.habits() }
    val checked = remember(tick, dayKey) { repo.checkedOn(dayKey) }
    val history = remember(tick, dayKey) {
        repo.checkDaysSince(today.minusDays(29).toString())
    }

    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Habit?>(null) }
    var deleting by remember { mutableStateOf<Habit?>(null) }

    val doneCount = habits.count { checked.contains(it.id) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "每日必做",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    "${today.monthValue} 月 ${today.dayOfMonth} 日",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            Text(
                if (habits.isEmpty()) "—" else "$doneCount / ${habits.size}",
                style = MaterialTheme.typography.headlineSmall,
                color = if (habits.isNotEmpty() && doneCount == habits.size) scheme.primary
                else scheme.onSurface,
            )
        }

        Spacer(Modifier.height(10.dp))

        if (habits.isNotEmpty()) {
            LinearProgressIndicator(
                progress = if (habits.isEmpty()) 0f else doneCount.toFloat() / habits.size,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (doneCount == habits.size) "今天的清单都完成了 🎉" else "还差 ${habits.size - doneCount} 项",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (habits.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "还没有必做项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "添加每天固定要做的事，比如「背 50 个单词」",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(habits, key = { it.id }) { habit ->
                    val days = history[habit.id] ?: emptySet()
                    HabitRow(
                        habit = habit,
                        checked = checked.contains(habit.id),
                        streak = repo.streak(days, today),
                        last7 = repo.lastNDays(days, today, 7),
                        onToggle = {
                            repo.toggle(habit.id, dayKey, System.currentTimeMillis())
                            tick++
                        },
                        onRename = { renaming = habit },
                        onDelete = { deleting = habit },
                    )
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.10f))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Text("＋ 添加每日必做")
        }
    }

    if (adding) {
        HabitNameDialog(
            title = "添加每日必做",
            initial = "",
            confirmLabel = "添加",
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    repo.addHabit(name, System.currentTimeMillis())
                    tick++
                }
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    renaming?.let { habit ->
        HabitNameDialog(
            title = "重命名",
            initial = habit.name,
            confirmLabel = "保存",
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    repo.renameHabit(habit.id, name)
                    tick++
                }
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { habit ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除「${habit.name}」？") },
            text = { Text("之后每天不再出现。历史打勾记录会保留（不影响已有数据）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.archiveHabit(habit.id)
                        deleting = null
                        tick++
                    }
                ) { Text("删除", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    checked: Boolean,
    streak: Int,
    last7: List<Boolean>,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = habit.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) scheme.onSurface.copy(alpha = 0.55f) else scheme.onSurface,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                last7.forEach { ok ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (ok) scheme.primary
                                else scheme.outline.copy(alpha = 0.28f)
                            )
                    )
                    Spacer(Modifier.width(3.dp))
                }
                if (streak > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "连续 $streak 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun HabitNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                label = { Text("名称") },
                placeholder = { Text("例如：背 50 个单词") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
