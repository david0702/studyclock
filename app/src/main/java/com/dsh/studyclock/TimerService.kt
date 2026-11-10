package com.dsh.studyclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dsh.studyclock.data.SegmentRepo
import com.dsh.studyclock.data.StudyDb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 计时进行中的前台服务：
 *  - 让系统知道"这个 App 正在干活"，比普通后台进程更不容易被回收（保活）；
 *  - 常驻通知里用系统 chronometer 显示当前段已经走了多久（不用自己每秒刷通知）；
 *  - 通知上带一个「暂停并记一段」按钮，不用打开 App 也能收尾。
 *
 * 真正的时长仍以数据库里的时间戳为准，所以就算服务/进程被杀，时间也不会丢。
 */
class TimerService : Service() {

    private lateinit var repo: SegmentRepo

    override fun onCreate() {
        super.onCreate()
        repo = SegmentRepo(StudyDb(applicationContext))
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                repo.pause(System.currentTimeMillis())
                notifyAppChanged()
                stopForegroundNow()
                return START_NOT_STICKY
            }

            else -> {
                // ACTION_SYNC 或系统重启服务（intent 为 null）：以数据库里的 running 段为准
                val seg = repo.running()
                if (seg == null) {
                    stopForegroundNow()
                    return START_NOT_STICKY
                }
                startAsForeground(seg.startMs)
                return START_STICKY
            }
        }
    }

    /** 从通知/外部点进来时没有绑定界面，直接返回 null。 */
    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(startMs: Long) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(startMs), type)
    }

    private fun stopForegroundNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(startMs: Long): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseAction = PendingIntent.getService(
            this,
            1,
            Intent(this, TimerService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("正在计时")
            .setContentText("从 ${clock(startMs)} 开始 · 点按回到 App")
            .setUsesChronometer(true)
            .setWhen(startMs)
            .setShowWhen(true)
            .setChronometerCountDown(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(0, "暂停并记一段", pauseAction)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "计时进行中",
            // DEFAULT（而不是 LOW）：让它出现在通知栏正常区域而不是"静默"折叠里，但仍然无声无震动
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "正计时进行时的常驻通知"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notifyAppChanged() {
        sendBroadcast(Intent(ACTION_TIMER_CHANGED).setPackage(packageName))
    }

    private fun clock(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    companion object {
        private const val CHANNEL_ID = "timer_running"
        private const val NOTIF_ID = 1001

        const val ACTION_SYNC = "com.dsh.studyclock.action.SYNC"
        const val ACTION_PAUSE = "com.dsh.studyclock.action.PAUSE_FROM_NOTIFICATION"
        const val ACTION_TIMER_CHANGED = "com.dsh.studyclock.action.TIMER_CHANGED"

        /** 开始/刷新前台服务（幂等，重复调用只会刷新通知）。 */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerService::class.java).setAction(ACTION_SYNC),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
