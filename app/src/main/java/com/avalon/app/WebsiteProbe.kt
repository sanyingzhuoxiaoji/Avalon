package com.avalon.app

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 10_000
private const val MAX_REDIRECTS = 5
private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * 对完整网址发 GET，测到响应头为止的真实耗时（单调时钟），不读正文、不缓存。
 * HTTP 状态码无论是否 4xx/5xx 都作为 httpCode 返回（error 为 null）；
 * 超时、DNS、TLS、连接失败等网络层错误分类为中文 error。
 * 默认证书与主机名校验，不放宽；手动跟随重定向（含 http↔https 跨协议）。
 * 取消协程会正常抛出 CancellationException，不会被吞掉。
 */
internal suspend fun probeWebsite(url: String): ProbeResult = withContext(Dispatchers.IO) {
    performWebsiteProbe(url)
}

private suspend fun performWebsiteProbe(url: String): ProbeResult {
    coroutineContext.ensureActive()
    val startedAt = SystemClock.elapsedRealtime()
    fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startedAt

    var target = url
    var reachedServer = false
    var redirects = 0
    while (true) {
        coroutineContext.ensureActive()
        reachedServer = false
        try {
            val next = URL(target)
            // http/https 之外（如恶意重定向的 file:）直接报错，不打开连接。
            if (next.protocol != "http" && next.protocol != "https") {
                return ProbeResult(elapsedMs(), null, "仅支持 http/https 网址")
            }
            val connection = openProbeConnection(next)
            try {
                connection.connect()
                reachedServer = true
                val code = connection.responseCode
                if (code == -1) {
                    return ProbeResult(elapsedMs(), null, "服务器未返回有效的 HTTP 响应")
                }
                if (code in 300..399 && redirects < MAX_REDIRECTS) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        target = URL(next, location.trim().replace(" ", "%20")).toString()
                        redirects++
                        continue
                    }
                }
                return ProbeResult(elapsedMs(), code, null)
            } finally {
                connection.disconnect()
            }
        } catch (e: MalformedURLException) {
            return ProbeResult(elapsedMs(), null, "网址无效")
        } catch (e: UnknownHostException) {
            return ProbeResult(elapsedMs(), null, "域名解析失败（DNS），请检查网址和网络")
        } catch (e: SocketTimeoutException) {
            val reason = if (reachedServer) {
                "响应超时：已连上服务器，但迟迟未返回数据"
            } else {
                "连接超时：限时内无法连上服务器"
            }
            return ProbeResult(elapsedMs(), null, reason)
        } catch (e: SSLException) {
            return ProbeResult(elapsedMs(), null, "TLS/证书错误：${e.message?.take(80) ?: "安全连接建立失败"}")
        } catch (e: ConnectException) {
            return ProbeResult(elapsedMs(), null, "连接失败：服务器拒绝连接或地址不可达")
        } catch (e: IOException) {
            return ProbeResult(elapsedMs(), null, "网络错误：${e.message?.take(80) ?: e.javaClass.simpleName}")
        }
    }
}

private fun openProbeConnection(target: URL): HttpURLConnection {
    val connection = target.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.useCaches = false
    // 手动跟随重定向，才能跨 http/https 协议并保持对最终状态的如实呈现。
    connection.instanceFollowRedirects = false
    connection.connectTimeout = CONNECT_TIMEOUT_MS
    connection.readTimeout = READ_TIMEOUT_MS
    connection.setRequestProperty("User-Agent", USER_AGENT)
    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
    return connection
}
