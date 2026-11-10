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

/** 一条更新信息（来自 GitHub Releases）。 */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String?,
    val size: Long,
    /** 实际使用的下载方式："direct" 直连 GitHub；"mirror:<host>" 走镜像 */
    val source: String,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(
        val progress: Int,
        val readBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val bytesPerSec: Long = 0L,
    ) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo, val apk: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/**
 * 应用内更新：直接从 GitHub Releases 取版本。
 *
 * - 元数据：api.github.com（Release 的 tag / body / 资产 digest）
 * - 下载：先用资产的 browser_download_url 直连；直连不通（github.com /
 *   objects.githubusercontent.com 在部分网络被墙）再依次走 gh-proxy / ghfast 镜像
 * - 完整性：用 GitHub 资产自带的 sha256 digest 校验，镜像也篡改不了内容
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val CONNECT_TIMEOUT = 12_000
    private const val READ_TIMEOUT = 25_000
    private const val GITHUB_REPO = "david0702/studyclock"
    private val API_URLS = listOf(
        "https://api.github.com/repos/$GITHUB_REPO/releases/latest",
        "https://gh-proxy.com/https://api.github.com/repos/$GITHUB_REPO/releases/latest",
    )
    private val MIRRORS = listOf("https://gh-proxy.com/", "https://ghfast.top/")

    fun check(): UpdateState {
        val errors = ArrayList<String>()
        val sources = urlOverride?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: API_URLS
        for (api in sources) {
            val state = runCatching { checkFrom(api) }
                .onFailure { errors += (it.message ?: it.javaClass.simpleName) }
                .getOrNull()
            if (state != null) return state
        }
        Log.w(TAG, "check failed: $errors")
        return UpdateState.Failed("连不上 GitHub：" + errors.joinToString("；").take(120))
    }

    private fun checkFrom(apiUrl: String): UpdateState {
        val json = JSONObject(
            httpGet(apiUrl, accept = "application/vnd.github+json", userAgent = "studyclock-android")
        )
        val tag = json.optString("tag_name", "").removePrefix("v")
        if (tag.isBlank()) error("Release 没有 tag")
        if (!isNewer(tag, BuildConfig.VERSION_NAME)) return UpdateState.UpToDate

        val assets = json.optJSONArray("assets")
        var apkUrl = ""
        var digest: String? = null
        var size = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (!a.optString("name").endsWith(".apk", ignoreCase = true)) continue
                apkUrl = a.optString("browser_download_url", "")
                digest = a.optString("digest", "").removePrefix("sha256:")
                    .takeIf { it.isNotBlank() }
                size = a.optLong("size", 0L)
                break
            }
        }
        if (apkUrl.isBlank()) error("Release 里没有 apk 资产")
        return UpdateState.Available(
            UpdateInfo(
                versionName = tag,
                notes = json.optString("body", "").take(600),
                apkUrl = apkUrl,
                sha256 = digest,
                size = size,
                source = "direct",
            )
        )
    }

    fun download(context: Context, info: UpdateInfo, onProgress: (UpdateState.Downloading) -> Unit = {}): UpdateState {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(dir, "studyclock-${info.versionName}.apk")

        val candidates = ArrayList<String>()
        if (info.apkUrl.isNotBlank()) candidates += info.apkUrl
        MIRRORS.forEach { m -> if (info.apkUrl.isNotBlank()) candidates += m + info.apkUrl }

        val errors = ArrayList<String>()
        for (url in candidates) {
            // 直连 GitHub 在部分网络会卡很久：先用 2.5s 的 TCP 探测，不通就直接换镜像
            if (url == info.apkUrl && !hostReachable(url)) {
                errors += "直连不可达"
                continue
            }
            val result = runCatching {
                if (!target.exists() || (info.size > 0 && target.length() != info.size)) {
                    httpDownloadToFile(url, target, info.size, onProgress)
                }
                if (info.sha256 != null) {
                    val actual = sha256(target)
                    if (!actual.equals(info.sha256, ignoreCase = true)) {
                        target.delete()
                        error("sha256 不匹配")
                    }
                }
                val how = when {
                    url == info.apkUrl -> "direct"
                    else -> "mirror:" + url.substringBefore("://").substringAfter("://")
                        .substringBefore("/")
                }
                UpdateState.ReadyToInstall(info.copy(source = how), target)
            }.getOrElse {
                errors += (it.message ?: it.javaClass.simpleName)
                null
            }
            if (result != null) return result
        }
        return UpdateState.Failed("下载失败：" + errors.joinToString(" / ").take(140))
    }

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

    /** 调试用：把首个更新源替换成本地地址（仅 debug 构建会被设置）。 */
    @Volatile
    var urlOverride: String? = null

    private fun isNewer(candidate: String, current: String): Boolean = isNewerVersion(candidate, current)

    /** 2.5 秒内能否建立 TCP 连接（用来快速跳过被墙/黑洞的源）。 */
    private fun hostReachable(url: String, timeoutMs: Int = 2500): Boolean = runCatching {
        val parsed = URL(url)
        val port = if (parsed.port > 0) parsed.port else if (parsed.protocol == "https") 443 else 80
        java.net.Socket().use { it.connect(java.net.InetSocketAddress(parsed.host, port), timeoutMs) }
        true
    }.getOrDefault(false)

    private fun httpGet(url: String, accept: String, userAgent: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", accept)
            setRequestProperty("Cache-Control", "no-cache")
            if (userAgent != null) setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDownloadToFile(url: String, target: File, sizeHint: Long, onProgress: (UpdateState.Downloading) -> Unit = {}) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "studyclock-android")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val headerTotal = conn.contentLengthLong
            val total = if (headerTotal > 0) headerTotal else sizeHint
            val tmp = File(target.parentFile, target.name + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastEmit = System.currentTimeMillis()
                    var lastBytes = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
                        val nowMs = System.currentTimeMillis()
                        // 每 ~350ms 刷新一次进度/网速，避免过于频繁的重组
                        if (nowMs - lastEmit >= 350) {
                            val speed = if (nowMs > lastEmit)
                                (read - lastBytes) * 1000L / (nowMs - lastEmit) else 0L
                            val pct = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else 0
                            onProgress(
                                UpdateState.Downloading(
                                    progress = pct,
                                    readBytes = read,
                                    totalBytes = total,
                                    bytesPerSec = speed.coerceAtLeast(0L),
                                )
                            )
                            lastEmit = nowMs
                            lastBytes = read
                            lastPct = pct
                        }
                    }
                    val finalPct = if (total > 0 && lastPct != 100) 100 else lastPct.coerceAtLeast(0)
                    onProgress(
                        UpdateState.Downloading(
                            progress = finalPct,
                            readBytes = read,
                            totalBytes = total,
                            bytesPerSec = 0L,
                        )
                    )
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
