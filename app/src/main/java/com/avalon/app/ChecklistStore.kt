package com.avalon.app

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal

/** 追番 / 漫画共用清单的类型标记。 */
internal enum class ChecklistKind { Anime, Manga }

/**
 * 清单中的一条记录。进度用 BigDecimal 精确保留小数（漫画可以有 165.5 话），
 * 旧追番数据是整数，等价于小数位为 0。
 */
internal data class ChecklistRecord(
    val id: String,
    val title: String,
    val note: String = "",
    val finished: Boolean = false,
    val progress: BigDecimal = BigDecimal.ZERO,
)

/**
 * 追番 / 漫画清单的本地存储：SharedPreferences 里的 JSON 数组（同一格式、不同 key），
 * 字段沿用旧追番的 id/title/note/finished/episodes；episodes 现在无损存进度小数字符串。
 * 每次增删改同步落盘（数据量小，commit 保证停止前写完），列表同时是 Compose 状态，UI 直接观察。
 */
internal class ChecklistStore(
    private val prefs: SharedPreferences,
    private val storageKey: String,
    // 中文列表名（“追番”/“漫画”），拼进落盘与解析的报错文案。
    private val label: String,
    val kind: ChecklistKind,
) {
    val records: SnapshotStateList<ChecklistRecord> = mutableStateListOf()

    init {
        records.addAll(parseList(prefs.getString(storageKey, null)))
    }

    fun save(existingId: String?, title: String, note: String) {
        val cleanTitle = title.trim()
        val cleanNote = note.trim()
        if (cleanTitle.isEmpty()) throw IllegalArgumentException("请填写作品名称")
        val updated: List<ChecklistRecord> = if (existingId == null) {
            records + ChecklistRecord(newId(), cleanTitle, cleanNote)
        } else {
            val index = records.indexOfFirst { it.id == existingId }
            if (index < 0) throw IllegalArgumentException("该记录已被删除，无法保存")
            // 编辑只改标题与备注，保留原 id、已看完状态与进度。
            records.mapIndexed { i, r -> if (i == index) r.copy(title = cleanTitle, note = cleanNote) else r }
        }
        commit(updated, "${label}记录保存失败，请重试")
    }

    fun setFinished(id: String, finished: Boolean) {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = records[index]
        if (old.finished == finished) return
        commit(
            records.mapIndexed { i, r -> if (i == index) r.copy(finished = finished) else r },
            "${label}记录保存失败，请重试",
        )
    }

    /** 从最新进度加减 delta（通常 ±1）；结果为负时归 0。BigDecimal 运算对小数进度同样精确。 */
    fun adjustProgress(id: String, delta: Int) {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = records[index]
        val adjusted = old.progress.add(BigDecimal(delta))
        val clamped = if (adjusted.signum() < 0) BigDecimal.ZERO else adjusted
        if (clamped.compareTo(old.progress) == 0) return
        commit(
            records.mapIndexed { i, r -> if (i == index) r.copy(progress = clamped) else r },
            "${label}记录保存失败，请重试",
        )
    }

    fun delete(id: String) {
        val updated = records.filterNot { it.id == id }
        if (updated.size == records.size) return
        commit(updated, "${label}记录删除失败，请重试")
    }

    // 先落盘 prospective 列表，commit 成功后才发布到快照；
    // 失败时抛 StorageWriteException，快照保持不变，UI 可保留表单提示重试。
    private fun commit(updated: List<ChecklistRecord>, failureMessage: String) {
        val array = JSONArray()
        for (r in updated) {
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("title", r.title)
                    .put("note", r.note)
                    .put("finished", r.finished)
                    // 进度无损编码为精确小数字符串，沿用旧字段名 episodes。
                    .put("episodes", r.progress.toPlainString())
            )
        }
        if (!prefs.edit().putString(storageKey, array.toString()).commit()) {
            throw StorageWriteException(failureMessage)
        }
        records.clear()
        records.addAll(updated)
    }

    private fun parseList(raw: String?): List<ChecklistRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (e: JSONException) {
            throw IllegalStateException("本地${label}数据已损坏，无法读取", e)
        }
        val list = ArrayList<ChecklistRecord>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
                ?: throw IllegalStateException("本地${label}数据第 ${i + 1} 条格式错误")
            val id = obj.optString("id")
            val title = obj.optString("title")
            if (id.isEmpty() || title.isEmpty()) {
                throw IllegalStateException("本地${label}数据第 ${i + 1} 条缺少标识或名称")
            }
            list.add(
                ChecklistRecord(
                    id = id,
                    title = title,
                    note = obj.optString("note"),
                    finished = obj.optBoolean("finished", false),
                    // 旧数据没有 episodes 字段时缺省 0。
                    progress = parseProgress(obj.opt("episodes"), i),
                )
            )
        }
        return list
    }

    // 兼容两种历史写法：旧追番是 JSON 数字，导入的漫画是精确小数字符串；缺字段或空串按 0。
    private fun parseProgress(raw: Any?, index: Int): BigDecimal = when (raw) {
        null, JSONObject.NULL -> BigDecimal.ZERO
        is BigDecimal -> raw
        is Number -> BigDecimal(raw.toString())
        is String -> {
            val text = raw.trim()
            if (text.isEmpty()) {
                BigDecimal.ZERO
            } else {
                try {
                    BigDecimal(text)
                } catch (e: NumberFormatException) {
                    throw IllegalStateException("本地${label}数据第 ${index + 1} 条进度无效", e)
                }
            }
        }
        else -> throw IllegalStateException("本地${label}数据第 ${index + 1} 条格式错误")
    }
}
