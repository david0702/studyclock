package com.dsh.studyclock.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.dsh.studyclock.BuildConfig
import com.dsh.studyclock.TimerService
import com.dsh.studyclock.data.AppSettings
import com.dsh.studyclock.data.AutoBackup
import com.dsh.studyclock.data.CsvExporter
import com.dsh.studyclock.data.HabitRepo
import com.dsh.studyclock.data.SegmentRepo
import com.dsh.studyclock.data.ServerSync
import com.dsh.studyclock.data.UpdateChecker
import com.dsh.studyclock.data.UpdateInfo
import com.dsh.studyclock.data.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext

@Composable
fun StudyRoot(repo: SegmentRepo, habits: HabitRepo, settings: AppSettings) {
    // 同步读一次库：避免首帧先闪一下"0 段"再刷出数据
    val state = remember { AppState(repo, settings).also { it.refresh() } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- 应用内更新 ----
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var updateDialogFor by remember { mutableStateOf<UpdateInfo?>(null) }

    val startInstall: (UpdateInfo) -> Unit = { info ->
        scope.launch {
            updateState = UpdateState.Downloading(0)
            val result = withContext(Dispatchers.IO) {
                UpdateChecker.download(context, info) { st ->
                    updateState = st
                    android.util.Log.d("UpdateChecker", "progress ${st.progress}% ${st.readBytes}/${st.totalBytes}B ${st.bytesPerSec}B/s")
                }
            }
            updateState = result
            if (result is UpdateState.ReadyToInstall) {
                if (!UpdateChecker.install(context, result.apk)) {
                    updateState = UpdateState.Failed("无法拉起系统安装器")
                }
            }
        }
    }

    val checkUpdateNow: () -> Unit = {
        scope.launch {
            updateState = UpdateState.Checking
            val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
            updateState = result
            settings.lastUpdateCheckAt = System.currentTimeMillis()
            if (result is UpdateState.Available) updateDialogFor = result.info
        }
    }

    // 启动时静默检查一次（6 小时节流）
    LaunchedEffect(Unit) {
        val due = System.currentTimeMillis() - settings.lastUpdateCheckAt >
            AppSettings.UPDATE_CHECK_INTERVAL_MS
        if (due) {
            val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
            updateState = result
            settings.lastUpdateCheckAt = System.currentTimeMillis()
            if (result is UpdateState.Available) updateDialogFor = result.info
        }
    }

    // 自动保存相关：选中目录后每次记录变化都覆盖写一份（防抖 1.5s）
    var backupTick by remember { mutableIntStateOf(0) }
    val pickBackupFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            AutoBackup.setFolder(context, uri)
            val dayMathRef = state.dayMath
            scope.launch {
                withContext(Dispatchers.IO) { AutoBackup.saveNow(context, repo, dayMathRef) }
                backupTick++
            }
        }
    }

    LaunchedEffect(Unit) { state.refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            state.tick()
        }
    }

    LaunchedEffect(state.version, context) {
        if (state.version > 0 && AutoBackup.isConfigured(context)) {
            delay(1500L)
            val dayMathRef = state.dayMath
            withContext(Dispatchers.IO) { AutoBackup.saveNow(context, repo, dayMathRef) }
            backupTick++
        }
    }

    // 内置云端备份：记录一变就推一份到自建端点（防抖 2s）
    var syncTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.version, context) {
        if (state.version > 0 && ServerSync.isEnabled(context)) {
            delay(2000L)
            val dayMathRef = state.dayMath
            withContext(Dispatchers.IO) { ServerSync.upload(context, repo, dayMathRef) }
            syncTick++
        }
    }

    // 计时状态一变就同步前台服务：跑起来 → 常驻通知保活；暂停 → 收起通知
    LaunchedEffect(state.running != null) {
        if (state.running != null) TimerService.start(context) else TimerService.stop(context)
    }

    // 从通知栏的「暂停」回来时，刷新界面
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                state.refresh()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TimerService.ACTION_TIMER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 从后台回到前台（含点通知进来）时重新读库，避免显示旧状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ 需要通知权限，否则常驻通知不显示；首次进入时问一次
    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    label = { Text("计时") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("周历") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    label = { Text("清单") },
                )
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                0 -> ClockPage(
                    state = state,
                    onOpenSettings = { showSettings = true },
                    onEditSegment = { editingId = it },
                )

                1 -> WeekPage(
                    state = state,
                    onEditSegment = { editingId = it },
                )

                else -> HabitPage(
                    repo = habits,
                    dayMath = state.dayMath,
                    now = state.now,
                )
            }
        }
    }

    val editingSegment = remember(editingId, state.version) {
        editingId?.let { state.segment(it) }
    }
    if (editingSegment != null) {
        val knownCategories = remember(state.version) { state.recentCategories() }
        SegmentEditSheet(
            segment = editingSegment,
            now = state.now,
            dayMath = state.dayMath,
            knownCategories = knownCategories,
            onDismiss = { editingId = null },
            onSave = { category, note ->
                state.setNote(editingSegment.id, category, note)
                editingId = null
            },
            onDelete = {
                state.delete(editingSegment.id)
                editingId = null
            },
        )
    } else if (editingId != null) {
        LaunchedEffect(editingId) { editingId = null }
    }

    if (showSettings) {
        val backupFolder = remember(backupTick, state.version) { AutoBackup.folderName(context) }
        val backupLast = remember(backupTick, state.version) { AutoBackup.lastSavedAt(context) }
        val syncEnabled = remember(syncTick) { ServerSync.isEnabled(context) }
        val syncLastOk = remember(syncTick) { ServerSync.lastOkAt(context) }
        val syncLastError = remember(syncTick) { ServerSync.lastError(context) }
        SettingsSheet(
            rolloverHour = state.rolloverHour,
            appVersion = BuildConfig.VERSION_NAME,
            updateState = updateState,
            syncConfigured = ServerSync.isConfigured(context),
            syncEnabled = syncEnabled,
            syncEndpoint = ServerSync.endpoint(context),
            syncLastOk = syncLastOk,
            syncLastError = syncLastError,
            backupFolder = backupFolder,
            backupLastSaved = backupLast,
            onRolloverChange = { state.setRollover(it) },
            onToggleSync = {
                ServerSync.setEnabled(context, it)
                syncTick++
                if (it) {
                    val dayMathRef = state.dayMath
                    scope.launch {
                        withContext(Dispatchers.IO) { ServerSync.upload(context, repo, dayMathRef) }
                        syncTick++
                    }
                }
            },
            onUploadNow = {
                val dayMathRef = state.dayMath
                scope.launch {
                    withContext(Dispatchers.IO) { ServerSync.upload(context, repo, dayMathRef) }
                    syncTick++
                }
            },
            onExport = {
                val uri = CsvExporter.export(context, repo, state.dayMath)
                if (uri != null) {
                    runCatching { context.startActivity(CsvExporter.shareIntent(uri)) }
                }
                showSettings = false
            },
            onPickBackupFolder = { pickBackupFolder.launch(null) },
            onBackupNow = {
                val dayMathRef = state.dayMath
                scope.launch {
                    withContext(Dispatchers.IO) { AutoBackup.saveNow(context, repo, dayMathRef) }
                    backupTick++
                }
            },
            onClearBackupFolder = {
                AutoBackup.clearFolder(context)
                backupTick++
            },
            onCheckUpdate = checkUpdateNow,
            onUpdateAction = {
                when (val s = updateState) {
                    is UpdateState.Available -> startInstall(s.info)
                    is UpdateState.ReadyToInstall -> UpdateChecker.install(context, s.apk)
                    else -> checkUpdateNow()
                }
            },
            onDismiss = { showSettings = false },
        )
    }

    // 发现新版本时的提示
    updateDialogFor?.let { info ->
        AlertDialog(
            onDismissRequest = { updateDialogFor = null },
            title = { Text("发现新版本 v${info.versionName}") },
            text = { Text(info.notes.ifBlank { "包含一些修复与改进。" }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateDialogFor = null
                        startInstall(info)
                    }
                ) { Text("下载并安装") }
            },
            dismissButton = {
                TextButton(onClick = { updateDialogFor = null }) { Text("以后再说") }
            },
        )
    }
}
