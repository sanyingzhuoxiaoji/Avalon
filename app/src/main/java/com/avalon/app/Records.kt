package com.avalon.app

import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

internal data class AnimeRecord(
    val id: String,
    val title: String,
    val note: String = "",
    val finished: Boolean = false,
    val episodes: Int = 0,
)

internal data class WebsiteRecord(
    val id: String,
    val name: String,
    val url: String,
)

internal data class ProbeResult(
    val elapsedMs: Long,
    val httpCode: Int? = null,
    val error: String? = null,
)

/** SharedPreferences 落盘失败（commit 返回 false）时抛出；message 可直接展示。 */
internal class StorageWriteException(message: String) : IOException(message)

// 仅识别开头处的协议（如 https://），查询串里的 “://” 不算协议。
private val LEADING_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

/**
 * 归一化用户输入的网址：去首尾空白；无协议时补 https://；仅接受 http/https；
 * 必须有有效域名与合法端口；原样保留显式端口（如 :666）、路径、查询串。
 * 无效输入抛 IllegalArgumentException（中文提示）。
 */
internal fun normalizeWebsiteUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) throw IllegalArgumentException("网址不能为空")

    val candidate = when {
        trimmed.startsWith("//") -> "https://" + trimmed.substring(2)
        LEADING_SCHEME.containsMatchIn(trimmed) -> {
            val scheme = trimmed.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") {
                throw IllegalArgumentException("仅支持 http:// 或 https:// 开头的网址")
            }
            scheme + trimmed.substring(trimmed.indexOf("://"))
        }
        else -> "https://" + trimmed
    }

    val uri = try {
        URI(candidate)
    } catch (e: URISyntaxException) {
        throw IllegalArgumentException("网址格式不正确，请检查拼写或多余字符", e)
    }

    val host = uri.host
    if (host.isNullOrEmpty() || host.none { it.isLetterOrDigit() }) {
        throw IllegalArgumentException("网址缺少有效域名，例如 cn.211dm.com")
    }
    val port = uri.port
    if (port != -1 && (port < 1 || port > 65535)) {
        throw IllegalArgumentException("端口号无效，需在 1–65535 之间")
    }
    return candidate
}
