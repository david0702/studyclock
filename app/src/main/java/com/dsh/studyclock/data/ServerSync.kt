package com.dsh.studyclock.data

import android.content.Context
import android.util.Log
import com.dsh.studyclock.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 内置云端备份：把 CSV + JSON 一次性 POST 到自建端点（默认 https://eregion.cn/studyclock/upload）。
 * 服务器落盘后由那台机器上的 Syncthing 同步回电脑，所以手机上**不用装任何第三方同步 App**。
 *
 * 端点与令牌在编译时从 gradle.properties 注入（BuildConfig），用户不用填。
 */
object ServerSync {

    private const val PREFS = "studyclock_serversync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_OK = "last_ok_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_ENDPOINT = "endpoint_override"
    private const val KEY_TOKEN = "token_override"
    private const val TAG = "ServerSync"

    /** 端点：优先用本地覆盖（调试/换服务器用），否则用编译时内置值。 */
    fun endpoint(context: Context): String =
        prefs(context).getString(KEY_ENDPOINT, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SYNC_URL

    private fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SYNC_TOKEN

    /** 编译时是否注入了端点+令牌。 */
    fun isConfigured(context: Context): Boolean =
        endpoint(context).isNotBlank() && token(context).isNotBlank()

    fun isEnabled(context: Context): Boolean =
        isConfigured(context) && prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lastOkAt(context: Context): Long = prefs(context).getLong(KEY_LAST_OK, 0L)

    fun lastError(context: Context): String? =
        prefs(context).getString(KEY_LAST_ERROR, null)?.takeIf { it.isNotBlank() }

    /**
     * 上传一次。需要调用方在后台线程执行。
     * @return true 表示服务器已确认写入
     */
    fun upload(context: Context, repo: SegmentRepo, dayMath: DayMath): Boolean {
        if (!isConfigured(context)) return false
        return runCatching {
            val payload = JSONObject().apply {
                put("csv", CsvExporter.csvText(repo, dayMath))
                put("json", AutoBackup.jsonText(repo, dayMath))
                put("sentAt", System.currentTimeMillis())
            }.toString()

            val conn = (URL(endpoint(context)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Token", token(context))
            }
            try {
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    prefs(context).edit()
                        .putLong(KEY_LAST_OK, System.currentTimeMillis())
                        .remove(KEY_LAST_ERROR)
                        .apply()
                    true
                } else {
                    recordError(context, "服务器返回 HTTP $code")
                    false
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure {
            Log.w(TAG, "upload failed", it)
            recordError(context, it.javaClass.simpleName + ": " + (it.message ?: ""))
        }.getOrDefault(false)
    }

    private fun recordError(context: Context, message: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message.take(120)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
