package com.dsh.studyclock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dsh.studyclock.ui.Fmt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出全部记录成 CSV（带 UTF-8 BOM，Excel 直接打开不乱码）。
 * 返回 content:// URI，交给系统分享面板选择存哪。
 */
object CsvExporter {

    /** 生成 CSV 文本；自动备份与手动导出共用这一份格式。 */
    fun csvText(repo: SegmentRepo, dayMath: DayMath): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("开始时间,结束时间,时长(分钟),学习日,大类,备注\r\n")
        repo.all().forEach { seg ->
            val end = seg.endMs ?: System.currentTimeMillis()
            val minutes = ((end - seg.startMs) / 60000.0)
            val studyDay = dayMath.studyDate(seg.startMs).toString()
            sb.append(csv(Fmt.csvTime(seg.startMs))).append(',')
                .append(csv(Fmt.csvTime(end))).append(',')
                .append(String.format(Locale.ROOT, "%.1f", minutes)).append(',')
                .append(csv(studyDay)).append(',')
                .append(csv(seg.category)).append(',')
                .append(csv(seg.note)).append("\r\n")
        }
        return sb.toString()
    }

    fun export(context: Context, repo: SegmentRepo, dayMath: DayMath): Uri? {
        if (repo.all().isEmpty()) return null

        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        val file = File(dir, "studyclock-$stamp.csv")
        file.writeText(csvText(repo, dayMath), Charsets.UTF_8)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun shareIntent(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "学习正计时 · 导出记录")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "导出 CSV 到…")
    }

    private fun csv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
