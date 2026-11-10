package com.dsh.studyclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dsh.studyclock.data.DayMath
import com.dsh.studyclock.data.Segment

/**
 * 分段编辑面板：两级备注
 *  - 大类：短标签（≤6 字），会画在周历色块上；可以直接点以前用过的分类，也可以现场输入新分类（相当于新增一种）；
 *  - 小类/备注：自由文本，写具体做了什么。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SegmentEditSheet(
    segment: Segment,
    now: Long,
    dayMath: DayMath,
    knownCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (category: String, note: String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by remember(segment.id) { mutableStateOf(segment.category) }
    var note by remember(segment.id) { mutableStateOf(segment.note) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val end = segment.endAt(now)
    val crosses = dayMath.studyDate(segment.startMs) != dayMath.studyDate(end)
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("这一段", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${Fmt.range(segment.startMs, end, crosses)} · ${Fmt.dur(segment.durationAt(now))}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = scheme.onSurfaceVariant,
            )
            if (segment.isRunning) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "还在计时中，暂停后才会定长",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---- 大类 ----
            Text("大类", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "短标签，会画在周历色块上；点下面的旧分类直接复用，或者自己写一个新的（等于加一种分类）。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (knownCategories.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    knownCategories.forEach { item ->
                        val selected = category.trim() == item
                        FilterChip(
                            selected = selected,
                            onClick = { category = if (selected) "" else item },
                            label = { Text(item) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = category,
                onValueChange = { category = it.take(6) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("大类（最多 6 字，可留空）") },
                placeholder = { Text("例如：数学 / 英语 / 写代码") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(Modifier.height(16.dp))

            // ---- 小类 / 备注 ----
            Text("小类 / 备注", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "自由填写：这一段具体做了什么。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                label = { Text("备注") },
                placeholder = { Text("例如：第三章习题 12 道，错了 3 题") },
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("删除这一段")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(category.trim(), note.trim()) }) {
                    Text("保存")
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除这一段？") },
            text = { Text("删除后无法恢复。如果只是备注写错了，直接改完保存就行。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("取消") }
            },
        )
    }
}
