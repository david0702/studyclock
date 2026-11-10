package com.dsh.studyclock.data

import android.content.Context

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("studyclock_settings", Context.MODE_PRIVATE)

    /** 每天几点换日（0–6）。默认 0 点，夜猫子可以改成 3、4 点。 */
    var rolloverHour: Int
        get() = prefs.getInt(KEY_ROLLOVER, 0).coerceIn(0, 6)
        set(value) = prefs.edit().putInt(KEY_ROLLOVER, value.coerceIn(0, 6)).apply()

    var longRunWarnedFor: Long
        get() = prefs.getLong(KEY_LONG_RUN_WARNED, -1L)
        set(value) = prefs.edit().putLong(KEY_LONG_RUN_WARNED, value).apply()

    /** 上次自动检查更新的时间（用于节流，默认 6 小时一次）。 */
    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    companion object {
        private const val KEY_ROLLOVER = "rollover_hour"
        private const val KEY_LONG_RUN_WARNED = "long_run_warned_id"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check_at"
        const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
