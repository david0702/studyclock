package com.dsh.studyclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dsh.studyclock.data.UpdateState
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    rolloverHour: Int,
    appVersion: String,
    updateState: UpdateState,
    syncConfigured: Boolean,
    syncEnabled: Boolean,
    syncEndpoint: String,
    syncLastOk: Long,
    syncLastError: String?,
    backupFolder: String?,
    backupLastSaved: Long,
    onRolloverChange: (Int) -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onUploadNow: () -> Unit,
    onExport: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onClearBackupFolder: () -> Unit,
    onCheckUpdate: () -> Unit,
    onUpdateAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(18.dp))

            Text("每天几点换日", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "默认 0 点。半夜还在学就改成 3 点或 4 点：那个时刻之前的记录都算前一天，\"今天累计\"也跟着变。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (h in 0..6) {
                    FilterChip(
                        selected = rolloverHour == h,
                        onClick = { onRolloverChange(h) },
                        label = { Text("$h 点") },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.dp))

            // ---- 内置云端备份 ----
            Text("云端备份（内置）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "记录一变就自动传到你的服务器，再由服务器同步回电脑 —— 手机上不用装任何同步 App。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (!syncConfigured) {
                Text(
                    "这个安装包没有内置上传地址（编译时未配置），下面的本地文件夹备份仍然可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
            } else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("自动上传", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = syncEnabled,
                        onCheckedChange = onToggleSync,
                    )
                }
                Text(
                    "端点：$syncEndpoint",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (syncLastOk > 0L) "上次成功：${Fmt.csvTime(syncLastOk)}" else "还没成功上传过",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (syncLastError == null) scheme.onSurfaceVariant else scheme.onSurfaceVariant,
                )
                if (syncLastError != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "最近失败：$syncLastError",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onUploadNow, modifier = Modifier.fillMaxWidth()) {
                    Text("立即上传")
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.dp))

            // ---- 自动保存 / Syncthing ----
            Text("保存记录（Syncthing）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "选一个 Syncthing 正在同步的文件夹。之后每次记录有变化，都会自动把 " +
                    "studyclock-latest.csv 和 studyclock-backup.json 覆盖写进去，电脑上就有一份最新的。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            if (backupFolder == null) {
                Button(onClick = onPickBackupFolder, modifier = Modifier.fillMaxWidth()) {
                    Text("选择同步文件夹")
                }
            } else {
                Surface(
                    color = scheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            "当前文件夹：$backupFolder",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (backupLastSaved > 0L) "上次保存：${Fmt.csvTime(backupLastSaved)}"
                            else "还没保存过",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = onBackupNow, modifier = Modifier.weight(1f)) {
                        Text("立即保存")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onClearBackupFolder, modifier = Modifier.weight(1f)) {
                        Text("停止自动保存")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.dp))

            Text("数据", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "所有记录只存在这台手机上，不联网、不需要账号。卸载 App 会一起删掉，" +
                    "所以建议开上面的自动保存（或偶尔手动导出）。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("导出全部记录（CSV）")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.dp))

            // ---- 关于与更新 ----
            Text("关于与更新", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "当前版本 v$appVersion",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            when (val s = updateState) {
                is UpdateState.Idle -> Text(
                    "更新来自 GitHub Releases：直连 api.github.com 查版本，安装包优先直连下载，不通时自动走镜像，并用 GitHub 的 sha256 校验。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )

                is UpdateState.Checking -> Text(
                    "正在检查更新…",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )

                is UpdateState.UpToDate -> Text(
                    "已是最新版本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.primary,
                )

                is UpdateState.Available -> Column {
                    Text(
                        "发现新版本 v${s.info.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.primary,
                    )
                    Text(
                        "来源：GitHub Releases" + if (s.info.source.startsWith("mirror:")) "（镜像下载）" else "（直连）",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            s.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                is UpdateState.Downloading -> Column {
                    Text(
                        "正在下载安装包… ${s.progress}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = (s.progress / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is UpdateState.ReadyToInstall -> Text(
                    "已下载 v${s.info.versionName}，等待安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.primary,
                )

                is UpdateState.Failed -> Text(
                    "更新失败：${s.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = onUpdateAction,
                    enabled = updateState !is UpdateState.Checking &&
                        updateState !is UpdateState.Downloading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (updateState) {
                            is UpdateState.Available -> "下载并安装"
                            is UpdateState.ReadyToInstall -> "继续安装"
                            is UpdateState.Checking -> "检查中…"
                            is UpdateState.Downloading -> "下载中…"
                            else -> "检查更新"
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onCheckUpdate, modifier = Modifier.weight(1f)) {
                    Text("重新检查")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "学习正计时 · v$appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
