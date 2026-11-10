package com.dsh.studyclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dsh.studyclock.data.AppSettings
import com.dsh.studyclock.data.SegmentRepo
import com.dsh.studyclock.data.StudyDb
import com.dsh.studyclock.ui.StudyClockTheme
import com.dsh.studyclock.ui.StudyRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = SegmentRepo(StudyDb(applicationContext))
        val settings = AppSettings(applicationContext)
        setContent {
            StudyClockTheme {
                StudyRoot(repo, settings)
            }
        }
    }
}
