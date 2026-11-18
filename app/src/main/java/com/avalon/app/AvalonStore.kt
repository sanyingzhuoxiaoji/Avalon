package com.avalon.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

/**
 * 追番 / 漫画清单与网站书签的本地存储：SharedPreferences 里的 JSON 数组，
 * 每次增删改同步落盘（数据量小，commit 保证停止前写完）。
 * 两个清单共用 ChecklistStore 的读写逻辑；网站书签仍在本类内处理。
 */
internal class AvalonStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val anime: ChecklistStore = ChecklistStore(prefs, KEY_ANIME, "追番", ChecklistKind.Anime)
    val manga: ChecklistStore = ChecklistStore(prefs, KEY_MANGA, "漫画", ChecklistKind.Manga)
    val websites: SnapshotStateList<WebsiteRecord> = mutableStateListOf()

    init {
        val savedWebsites = prefs.getString(KEY_WEBSITES, null)
        if (savedWebsites == null) {
            // 仅内存播种，不在构造期写盘；首次实际修改会把种子随新列表一起落盘，
            // 种子全删后写入 []，重启不会重新播种。
            websites.add(WebsiteRecord(newId(), "cn.211dm.com", "https://cn.211dm.com/"))
            websites.add(WebsiteRecord(newId(), "skr.skr3.cc", "https://skr.skr3.cc:666/"))
        } else {
            websites.addAll(parseWebsiteList(savedWebsites))
        }
    }

    fun saveWebsite(existingId: String?, name: String, url: String) {
        val cleanUrl = normalizeWebsiteUrl(url)
        val cleanName = name.trim().ifEmpty { hostOf(cleanUrl) }
        val updated: List<WebsiteRecord> = if (existingId == null) {
            websites + WebsiteRecord(newId(), cleanName, cleanUrl)
        } else {
            val index = websites.indexOfFirst { it.id == existingId }
            if (index < 0) throw IllegalArgumentException("该书签已被删除，无法保存")
            websites.mapIndexed { i, r -> if (i == index) r.copy(name = cleanName, url = cleanUrl) else r }
        }
        if (!writeWebsites(updated)) throw StorageWriteException("网站书签保存失败，请重试")
        publishWebsites(updated)
    }

    fun deleteWebsite(id: String) {
        val updated = websites.filterNot { it.id == id }
        if (updated.size == websites.size) return
        if (!writeWebsites(updated)) throw StorageWriteException("网站书签删除失败，请重试")
        publishWebsites(updated)
    }

    // 先落盘 prospective 列表，commit 成功后才发布到快照；
    // 失败时抛 StorageWriteException，快照保持不变，UI 可保留表单提示重试。
    private fun writeWebsites(records: List<WebsiteRecord>): Boolean {
        val array = JSONArray()
        for (r in records) {
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("url", r.url)
            )
        }
        return prefs.edit().putString(KEY_WEBSITES, array.toString()).commit()
    }

    private fun publishWebsites(updated: List<WebsiteRecord>) {
        websites.clear()
        websites.addAll(updated)
    }

    private fun parseWebsiteList(raw: String?): List<WebsiteRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (e: JSONException) {
            throw IllegalStateException("本地网站书签数据已损坏，无法读取", e)
        }
        val list = ArrayList<WebsiteRecord>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
                ?: throw IllegalStateException("本地网站书签数据第 ${i + 1} 条格式错误")
            val id = obj.optString("id")
            val url = obj.optString("url")
            if (id.isEmpty() || url.isEmpty()) {
                throw IllegalStateException("本地网站书签数据第 ${i + 1} 条缺少标识或网址")
            }
            list.add(
                WebsiteRecord(
                    id = id,
                    name = obj.optString("name").ifBlank { hostOf(url) },
                    url = url,
                )
            )
        }
        return list
    }

    private companion object {
        const val PREFS_NAME = "avalon_store"
        const val KEY_ANIME = "anime"
        const val KEY_MANGA = "manga"
        const val KEY_WEBSITES = "websites"
    }
}

private fun hostOf(url: String): String =
    try {
        URI(url).host ?: url
    } catch (e: URISyntaxException) {
        url
    }
