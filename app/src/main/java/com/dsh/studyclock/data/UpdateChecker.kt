package com.dsh.studyclock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.dsh.studyclock.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 服务端 latest.json 的内容。 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String?,
    val size: Long,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo, val apk: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/**
 * 应用内更新：读服务端的 latest.json，比较 versionCode，下载 APK（校验 sha256）并拉起系统安装器。
 *
 * 走自己的服务器而不是 GitHub Releases，是因为在国内直连 GitHub 不稳定；
 * GitHub 仓库/Release 作为源码与安装包的公开镜像，latest.json 才是 App 的更新源。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val CONNECT_TIMEOUT = 12_000
    private const val READ_TIMEOUT = 20_000

    val manifestUrl: String get() = BuildConfig.UPDATE_MANIFEST_URL

    /** @return null 表示已是最新（或服务器上没有更新信息） */
    fun check(): UpdateState {
        if (manifestUrl.isBlank()) return UpdateState.Failed("没有配置更新地址")
        return runCatching {
            val json = JSONObject(httpGet(manifestUrl))
            val info = UpdateInfo(
                versionCode = json.optInt("versionCode", 0),
                versionName = json.optString("versionName", ""),
                notes = json.optString("notes", ""),
                apkUrl = json.optString("apkUrl", ""),
                sha256 = json.optString("sha256", "").takeIf { it.isNotBlank() },
                size = json.optLong("size", 0L),
            )
            if (info.versionCode > BuildConfig.VERSION_CODE && info.apkUrl.isNotBlank()) {
                UpdateState.Available(info)
            } else {
                UpdateState.UpToDate
            }
        }.getOrElse { e ->
            Log.w(TAG, "check failed", e)
            UpdateState.Failed(e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    /** 下载到 filesDir/updates/，校验 sha256 后返回文件。 */
    fun download(context: Context, info: UpdateInfo): UpdateState {
        if (info.apkUrl.isBlank()) return UpdateState.Failed("下载地址为空")
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(dir, "studyclock-${info.versionName}.apk")
        return runCatching {
            if (!target.exists() || (info.size > 0 && target.length() != info.size)) {
                httpDownloadToFile(info.apkUrl, target) { pct ->
                    // 简单进度：直接回调（UI 侧可以忽略）
                }
            }
            val digest = sha256(target)
            if (info.sha256 != null && !digest.equals(info.sha256, ignoreCase = true)) {
                target.delete()
                return UpdateState.Failed("校验失败：文件 sha256 不匹配")
            }
            UpdateState.ReadyToInstall(info, target)
        }.getOrElse { e ->
            Log.w(TAG, "download failed", e)
            UpdateState.Failed("下载失败：" + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** 拉起系统安装器（需要 REQUEST_INSTALL_PACKAGES 权限，用户首次要允许"安装未知应用"）。 */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrElse { e ->
        Log.w(TAG, "install failed", e)
        false
    }

    // ---- HTTP ----

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", "application/json")
            // 绕过 CDN/中间层缓存，保证拿到最新版本号
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDownloadToFile(url: String, target: File, onProgress: (Int) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val total = conn.contentLengthLong
            val tmp = File(target.parentFile, target.name + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) error("保存安装包失败")
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
