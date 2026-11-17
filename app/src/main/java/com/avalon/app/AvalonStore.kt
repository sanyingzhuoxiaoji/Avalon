package com.avalon.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

/**
 * 追番与网站书签的本地存储：SharedPreferences 里的 JSON 数组，
 * 每次增删改同步落盘（数据量小，commit 保证停止前写完）。
 * 列表同时是 Compose 状态，UI 直接观察。
 */
internal class AvalonStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val anime: SnapshotStateList<AnimeRecord> = mutableStateListOf()
    val websites: SnapshotStateList<WebsiteRecord> = mutableStateListOf()

    init {
        anime.addAll(parseAnimeList(prefs.getString(KEY_ANIME, null)))
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

    fun saveAnime(existingId: String?, title: String, note: String) {
        val cleanTitle = title.trim()
        val cleanNote = note.trim()
        if (cleanTitle.isEmpty()) throw IllegalArgumentException("请填写作品名称")
        val updated: List<AnimeRecord> = if (existingId == null) {
            anime + AnimeRecord(newId(), cleanTitle, cleanNote)
        } else {
            val index = anime.indexOfFirst { it.id == existingId }
            if (index < 0) throw IllegalArgumentException("该记录已被删除，无法保存")
            // 编辑只改标题与备注，保留原 id、已看完状态与剧集数。
            anime.mapIndexed { i, r -> if (i == index) r.copy(title = cleanTitle, note = cleanNote) else r }
        }
        if (!writeAnime(updated)) throw StorageWriteException("追番记录保存失败，请重试")
        publishAnime(updated)
    }

    fun setAnimeFinished(id: String, finished: Boolean) {
        val index = anime.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = anime[index]
        if (old.finished == finished) return
        val updated = anime.mapIndexed { i, r -> if (i == index) r.copy(finished = finished) else r }
        if (!writeAnime(updated)) throw StorageWriteException("追番记录保存失败，请重试")
        publishAnime(updated)
    }

    fun setAnimeEpisodes(id: String, episodes: Int) {
        // 步进器不会出现负数；万一传入直接归 0，避免崩溃。
        val cleanEpisodes = episodes.coerceAtLeast(0)
        val index = anime.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = anime[index]
        if (old.episodes == cleanEpisodes) return
        val updated = anime.mapIndexed { i, r -> if (i == index) r.copy(episodes = cleanEpisodes) else r }
        if (!writeAnime(updated)) throw StorageWriteException("追番记录保存失败，请重试")
        publishAnime(updated)
    }

    fun deleteAnime(id: String) {
        val updated = anime.filterNot { it.id == id }
        if (updated.size == anime.size) return
        if (!writeAnime(updated)) throw StorageWriteException("追番记录删除失败，请重试")
        publishAnime(updated)
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
    private fun writeAnime(records: List<AnimeRecord>): Boolean {
        val array = JSONArray()
        for (r in records) {
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("title", r.title)
                    .put("note", r.note)
                    .put("finished", r.finished)
                    .put("episodes", r.episodes)
            )
        }
        return prefs.edit().putString(KEY_ANIME, array.toString()).commit()
    }

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

    private fun publishAnime(updated: List<AnimeRecord>) {
        anime.clear()
        anime.addAll(updated)
    }

    private fun publishWebsites(updated: List<WebsiteRecord>) {
        websites.clear()
        websites.addAll(updated)
    }

    private fun parseAnimeList(raw: String?): List<AnimeRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (e: JSONException) {
            throw IllegalStateException("本地追番数据已损坏，无法读取", e)
        }
        val list = ArrayList<AnimeRecord>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
                ?: throw IllegalStateException("本地追番数据第 ${i + 1} 条格式错误")
            val id = obj.optString("id")
            val title = obj.optString("title")
            if (id.isEmpty() || title.isEmpty()) {
                throw IllegalStateException("本地追番数据第 ${i + 1} 条缺少标识或名称")
            }
            list.add(
                AnimeRecord(
                    id = id,
                    title = title,
                    note = obj.optString("note"),
                    finished = obj.optBoolean("finished", false),
                    // 旧数据没有 episodes 字段，缺省 0。
                    episodes = obj.optInt("episodes", 0),
                )
            )
        }
        return list
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
        const val KEY_WEBSITES = "websites"
    }
}

private fun newId(): String = UUID.randomUUID().toString()

private fun hostOf(url: String): String =
    try {
        URI(url).host ?: url
    } catch (e: URISyntaxException) {
        url
    }
