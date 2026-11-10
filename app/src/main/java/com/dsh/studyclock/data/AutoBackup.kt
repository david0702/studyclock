package com.dsh.studyclock.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「自动保存到文件夹」：用户通过系统文件选择器指定一个目录（最好是 Syncthing 正在同步的目录），
 * 之后每次记录有变化，就把下面两个文件**覆盖写**进去：
 *   - studyclock-latest.csv   给人看的表格（Excel 直接开）
 *   - studyclock-backup.json  给程序用的完整备份（含 id/起止/大类/备注，将来可做导入）
 *
 * 用 SAF（Storage Access Framework）拿持久化写权限，不需要任何存储权限。
 */
object AutoBackup {

    private const val PREFS = "studyclock_backup"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_LAST = "last_saved_at"
    private const val CSV_NAME = "studyclock-latest.csv"
    private const val JSON_NAME = "studyclock-backup.json"
    private const val TAG = "AutoBackup"

    fun isConfigured(context: Context): Boolean = folderUri(context) != null

    fun folderUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_TREE, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /** 记住目录并长期保留读写权限。 */
    fun setFolder(context: Context, tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs(context).edit().putString(KEY_TREE, tree.toString()).apply()
    }

    fun clearFolder(context: Context) {
        folderUri(context)?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs(context).edit().remove(KEY_TREE).remove(KEY_LAST).apply()
    }

    fun lastSavedAt(context: Context): Long = prefs(context).getLong(KEY_LAST, 0L)

    /** 目录显示名，用于设置页展示。 */
    fun folderName(context: Context): String? {
        val tree = folderUri(context) ?: return null
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "已选择的文件夹"
    }

    /**
     * 立刻把当前记录写进所选目录（覆盖同名文件）。
     * 需要调用方在后台线程执行。
     * @return true 写入成功
     */
    fun saveNow(context: Context, repo: SegmentRepo, dayMath: DayMath): Boolean {
        val tree = folderUri(context) ?: return false
        return runCatching {
            val csv = CsvExporter.csvText(repo, dayMath).toByteArray(Charsets.UTF_8)
            val json = jsonText(repo, dayMath).toByteArray(Charsets.UTF_8)
            writeFile(context, tree, CSV_NAME, "text/csv", csv)
            writeFile(context, tree, JSON_NAME, "application/json", json)
            prefs(context).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
            true
        }.onFailure { Log.w(TAG, "save failed", it) }.getOrDefault(false)
    }

    private fun writeFile(
        context: Context,
        tree: Uri,
        name: String,
        mime: String,
        bytes: ByteArray,
    ) {
        val resolver = context.contentResolver
        val existing = findChild(context, tree, name)
        if (existing != null) {
            resolver.openOutputStream(existing, "wt")?.use { it.write(bytes) }
                ?: throw IllegalStateException("openOutputStream null for $name")
        } else {
            // 注意：createDocument 需要父目录的 document URI，不能直接传 tree URI
            val treeDocId = DocumentsContract.getTreeDocumentId(tree)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
            val created = DocumentsContract.createDocument(
                resolver,
                parentUri,
                mime,
                name,
            ) ?: throw IllegalStateException("createDocument null for $name")
            resolver.openOutputStream(created, "wt")?.use { it.write(bytes) }
                ?: throw IllegalStateException("openOutputStream null for $name")
        }
    }

    /** 在目录里按文件名找子文档。 */
    private fun findChild(context: Context, tree: Uri, name: String): Uri? {
        val resolver: ContentResolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        return null
    }

    /** 完整备份 JSON（云端上传 ServerSync 也复用它）。 */
    fun jsonText(repo: SegmentRepo, dayMath: DayMath): String {
        val root = JSONObject()
        root.put("schema", 1)
        root.put("app", "studyclock")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportedAtText", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()))
        root.put("rolloverHour", dayMath.rolloverHour)
        val arr = JSONArray()
        repo.all().forEach { seg ->
            arr.put(
                JSONObject().apply {
                    put("id", seg.id)
                    put("start", seg.startMs)
                    put("end", seg.endMs ?: JSONObject.NULL)
                    put("category", seg.category)
                    put("note", seg.note)
                }
            )
        }
        root.put("segments", arr)
        return root.toString(2)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
