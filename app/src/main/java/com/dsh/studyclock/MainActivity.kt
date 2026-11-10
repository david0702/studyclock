package com.dsh.studyclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dsh.studyclock.data.AppSettings
import com.dsh.studyclock.data.HabitRepo
import com.dsh.studyclock.data.SegmentRepo
import com.dsh.studyclock.data.StudyDb
import com.dsh.studyclock.ui.StudyClockTheme
import com.dsh.studyclock.ui.StudyRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = StudyDb(applicationContext)
        val repo = SegmentRepo(db)
        val habits = HabitRepo(db)
        val settings = AppSettings(applicationContext)

        // 调试入口（仅 debug 构建生效）：
        //   adb shell am start -n com.dsh.studyclock.dev/com.dsh.studyclock.MainActivity \
        //       --ez show_settings true --es update_url http://10.0.2.2:8099/release.json
        val debug = BuildConfig.DEBUG
        val startTab = if (debug) intent.getIntExtra("tab", -1) else -1
        val openSettings = debug && intent.getBooleanExtra("show_settings", false)
        val checkUpdate = debug && intent.getBooleanExtra("check_update", false)
        val updateUrl = if (debug) intent.getStringExtra("update_url") else null

        setContent {
            StudyClockTheme {
                StudyRoot(
                    repo = repo,
                    habits = habits,
                    settings = settings,
                    initialTab = startTab,
                    openSettingsOnStart = openSettings,
                    checkUpdateOnStart = checkUpdate,
                    debugUpdateUrl = updateUrl,
                )
            }
        }
    }
}
